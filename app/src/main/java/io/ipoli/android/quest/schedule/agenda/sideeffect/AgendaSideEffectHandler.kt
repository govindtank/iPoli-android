package io.ipoli.android.quest.schedule.agenda.sideeffect

import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.LoadDataAction
import io.ipoli.android.common.async.ChannelRelay
import io.ipoli.android.common.redux.Action
import io.ipoli.android.event.usecase.FindEventsBetweenDatesUseCase
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.schedule.agenda.AgendaAction
import io.ipoli.android.quest.schedule.agenda.AgendaReducer
import io.ipoli.android.quest.schedule.agenda.AgendaViewState
import io.ipoli.android.quest.schedule.agenda.usecase.CreateAgendaItemsUseCase
import io.ipoli.android.quest.schedule.agenda.usecase.FindAgendaDatesUseCase
import io.ipoli.android.quest.show.usecase.CompleteTimeRangeUseCase
import io.ipoli.android.quest.usecase.CompleteQuestUseCase
import io.ipoli.android.repeatingquest.usecase.CreatePlaceholderQuestsForRepeatingQuestsUseCase
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required

object AgendaSideEffectHandler : AppSideEffectHandler() {

    private val completeQuestUseCase by required { completeQuestUseCase }
    private val undoCompletedQuestUseCase by required { undoCompletedQuestUseCase }
    private val findAgendaDatesUseCase by required { findAgendaDatesUseCase }
    private val createAgendaItemsUseCase by required { createAgendaItemsUseCase }
    private val questRepository by required { questRepository }
    private val findEventsBetweenDatesUseCase by required { findEventsBetweenDatesUseCase }
    private val completeTimeRangeUseCase by required { completeTimeRangeUseCase }
    private val createPlaceholderQuestsForRepeatingQuestsUseCase by required { createPlaceholderQuestsForRepeatingQuestsUseCase }

    data class AgendaConsumerParams(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val currentDate: LocalDate,
        val changeCurrentAgendaItem: Boolean
    )

    private val agendaChannelRelay = ChannelRelay<List<Quest>, AgendaConsumerParams>(
        producer = { c, p ->
            questRepository.listenForScheduledBetween(
                startDate = p.startDate,
                endDate = p.endDate,
                channel = c
            )
        },
        consumer = { quests, p ->
            val placeholderQuests =
                createPlaceholderQuestsForRepeatingQuestsUseCase.execute(
                    CreatePlaceholderQuestsForRepeatingQuestsUseCase.Params(
                        startDate = p.startDate,
                        endDate = p.endDate
                    )
                )

            val events = findEventsBetweenDatesUseCase.execute(
                FindEventsBetweenDatesUseCase.Params(
                    startDate = p.startDate,
                    endDate = p.endDate
                )
            )

            val agendaItems = createAgendaItemsUseCase.execute(
                CreateAgendaItemsUseCase.Params(
                    date = p.currentDate,
                    scheduledQuests = quests + placeholderQuests,
                    events = events,
                    itemsBefore = AgendaReducer.ITEMS_BEFORE_COUNT,
                    itemsAfter = AgendaReducer.ITEMS_AFTER_COUNT
                )
            )

            dispatch(
                DataLoadedAction.AgendaItemsChanged(
                    start = p.startDate,
                    end = p.endDate,
                    agendaItems = agendaItems,
                    currentAgendaItemDate = if (p.changeCurrentAgendaItem) p.currentDate else null
                )
            )
        }
    )

    override suspend fun doExecute(action: Action, state: AppState) {

        when (action) {

            is AgendaAction.Load -> {
                val pair = findAllAgendaDates(action.startDate)
                listenForAgendaItems(
                    startDate = pair.first,
                    endDate = pair.second,
                    agendaDate = action.startDate,
                    changeCurrentAgendaItem = true
                )
            }

            is AgendaAction.LoadBefore -> {
                val agendaItems = state.stateFor(AgendaViewState::class.java).agendaItems
                val position = action.itemPosition
                val agendaItem = agendaItems[position]
                val agendaDate = agendaItem.startDate()
                val result = findAgendaDatesUseCase.execute(
                    FindAgendaDatesUseCase.Params.Before(
                        agendaDate,
                        AgendaReducer.ITEMS_BEFORE_COUNT
                    )
                )
                var startDate = agendaDate.minusMonths(3)
                (result as FindAgendaDatesUseCase.Result.Before).date?.let {
                    startDate = it
                }
                val endDate =
                    agendaItems[position + AgendaReducer.ITEMS_AFTER_COUNT - 1].startDate()
                listenForAgendaItems(
                    startDate = startDate,
                    endDate = endDate,
                    agendaDate = agendaDate,
                    changeCurrentAgendaItem = false
                )
            }
            is AgendaAction.LoadAfter -> {
                val agendaItems = state.stateFor(AgendaViewState::class.java).agendaItems
                val position = action.itemPosition
                val agendaItem = agendaItems[position]
                val agendaDate = agendaItem.startDate()
                val result = findAgendaDatesUseCase.execute(
                    FindAgendaDatesUseCase.Params.After(
                        agendaDate,
                        AgendaReducer.ITEMS_AFTER_COUNT
                    )
                )
                val startDate = agendaItems[position - AgendaReducer.ITEMS_BEFORE_COUNT].startDate()
                var endDate = agendaDate.plusMonths(3)
                (result as FindAgendaDatesUseCase.Result.After).date?.let {
                    endDate = it
                }
                listenForAgendaItems(
                    startDate = startDate,
                    endDate = endDate,
                    agendaDate = agendaDate,
                    changeCurrentAgendaItem = false
                )
            }

            is AgendaAction.CompleteQuest -> {
                val adapterPos = action.itemPosition
                val agendaState = state.stateFor(AgendaViewState::class.java)
                val questItem =
                    agendaState.agendaItems[adapterPos] as CreateAgendaItemsUseCase.AgendaItem.QuestItem

                val quest = questItem.quest
                if (quest.isStarted) {
                    completeTimeRangeUseCase.execute(
                        CompleteTimeRangeUseCase.Params(
                            quest.id
                        )
                    )
                } else {
                    completeQuestUseCase.execute(
                        CompleteQuestUseCase.Params.WithQuest(
                            quest
                        )
                    )
                }
            }

            is AgendaAction.UndoCompleteQuest -> {
                val agendaItems = state.stateFor(AgendaViewState::class.java).agendaItems
                val adapterPos = action.itemPosition
                val questItem =
                    agendaItems[adapterPos] as CreateAgendaItemsUseCase.AgendaItem.QuestItem
                undoCompletedQuestUseCase.execute(questItem.quest.id)
            }

        }
    }

    private fun listenForAgendaItems(
        startDate: LocalDate,
        endDate: LocalDate,
        agendaDate: LocalDate,
        changeCurrentAgendaItem: Boolean
    ) {
        agendaChannelRelay.listen(
            AgendaConsumerParams(
                startDate = startDate,
                endDate = endDate,
                currentDate = agendaDate,
                changeCurrentAgendaItem = changeCurrentAgendaItem
            )
        )
    }

    private fun findAllAgendaDates(
        agendaDate: LocalDate
    ): Pair<LocalDate, LocalDate> {
        val result = findAgendaDatesUseCase.execute(
            FindAgendaDatesUseCase.Params.All(
                agendaDate,
                AgendaReducer.ITEMS_BEFORE_COUNT,
                AgendaReducer.ITEMS_AFTER_COUNT
            )
        ) as FindAgendaDatesUseCase.Result.All
        val start = result.start ?: agendaDate.minusMonths(3)
        val end = result.end ?: agendaDate.plusMonths(3)
        return Pair(start, end)
    }

    override fun canHandle(action: Action) =
        action == LoadDataAction.All
                || action is AgendaAction
}