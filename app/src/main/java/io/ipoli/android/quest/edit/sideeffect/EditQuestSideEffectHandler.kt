package io.ipoli.android.quest.edit.sideeffect

import io.ipoli.android.Constants
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.Reminder
import io.ipoli.android.quest.bucketlist.BucketListAction
import io.ipoli.android.quest.edit.EditQuestAction
import io.ipoli.android.quest.edit.EditQuestViewState
import io.ipoli.android.quest.schedule.addquest.AddQuestAction
import io.ipoli.android.quest.schedule.addquest.AddQuestViewState
import io.ipoli.android.quest.subquest.SubQuest
import io.ipoli.android.quest.usecase.CompleteQuestUseCase
import io.ipoli.android.quest.usecase.RescheduleQuestUseCase
import io.ipoli.android.quest.usecase.Result
import io.ipoli.android.quest.usecase.SaveQuestUseCase
import io.ipoli.android.tag.show.TagAction
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 4/10/18.
 */
object EditQuestSideEffectHandler : AppSideEffectHandler() {

    private val questRepository by required { questRepository }
    private val saveQuestUseCase by required { saveQuestUseCase }
    private val completeQuestUseCase by required { completeQuestUseCase }
    private val rescheduleQuestUseCase by required { rescheduleQuestUseCase }

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {
            is EditQuestAction.Load -> {
                val quest = questRepository.findById(action.questId)
                dispatch(EditQuestAction.Loaded(quest!!, action.params))
            }

            is AddQuestAction.Save -> {
                val addQuestState = state.stateFor(AddQuestViewState::class.java)
                val scheduledDate = addQuestState.date
                val questParams = SaveQuestUseCase.Parameters(
                    name = action.name,
                    tags = addQuestState.tags,
                    subQuests = emptyList(),
                    color = addQuestState.color ?: Color.GREEN,
                    icon = addQuestState.icon,
                    scheduledDate = scheduledDate,
                    startTime = addQuestState.time,
                    duration = addQuestState.duration ?: Constants.DEFAULT_QUEST_DURATION,
                    reminders = listOf(
                        Reminder.Relative(
                            "",
                            Constants.DEFAULT_RELATIVE_REMINDER_MINUTES_FROM_START
                        )
                    )
                )

                val result = saveQuestUseCase.execute(questParams)

                when (result) {
                    is Result.Invalid -> {
                        dispatch(AddQuestAction.SaveInvalidQuest(result.error))
                    }
                    else -> dispatch(AddQuestAction.QuestSaved)
                }
            }

            is EditQuestAction.Save -> {
                val s = state.stateFor(EditQuestViewState::class.java)

                val reminder = s.reminder?.let {
                    Reminder.Relative(it.message, it.minutesFromStart)
                }

                val subQuests = action.newSubQuestNames.entries.map {
                    var subQuest = SubQuest(it.value, null, null)
                    if (s.subQuests.containsKey(it.key)) {
                        val sq = s.subQuests[it.key]!!
                        subQuest = subQuest.copy(
                            completedAtDate = sq.completedAtDate,
                            completedAtTime = sq.completedAtTime
                        )
                    }
                    subQuest
                }

                val questParams = SaveQuestUseCase.Parameters(
                    id = s.id,
                    name = s.name,
                    subQuests = subQuests,
                    color = s.color,
                    icon = s.icon,
                    scheduledDate = s.scheduleDate,
                    startTime = s.startTime,
                    duration = s.duration,
                    reminders = reminder?.let { listOf(it) },
                    challengeId = s.challenge?.id,
                    repeatingQuestId = s.repeatingQuestId,
                    note = s.note,
                    tags = s.questTags
                )

                saveQuestUseCase.execute(questParams)

            }

            is TagAction.CompleteQuest ->
                completeQuest(action.questId)

            is BucketListAction.CompleteQuest ->
                completeQuest(action.questId)

            is BucketListAction.ScheduleForToday ->
                rescheduleQuestUseCase.execute(
                    RescheduleQuestUseCase.Params(
                        action.questId,
                        LocalDate.now()
                    )
                )
        }
    }

    private fun completeQuest(questId: String) {
        completeQuestUseCase.execute(CompleteQuestUseCase.Params.WithQuestId(questId))
    }

    override fun canHandle(action: Action) =
        action is EditQuestAction
                || action is AddQuestAction
                || action is TagAction.CompleteQuest
                || action is BucketListAction.CompleteQuest
                || action is BucketListAction.ScheduleForToday

}