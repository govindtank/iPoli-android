package io.ipoli.android.challenge.sideeffect

import io.ipoli.android.challenge.QuestPickerAction
import io.ipoli.android.challenge.QuestPickerViewState
import io.ipoli.android.challenge.add.EditChallengeAction
import io.ipoli.android.challenge.add.EditChallengeViewState
import io.ipoli.android.challenge.complete.CompleteChallengePopup
import io.ipoli.android.challenge.predefined.PersonalizeChallengeAction
import io.ipoli.android.challenge.predefined.PersonalizeChallengeViewState
import io.ipoli.android.challenge.predefined.entity.PredefinedChallengeData
import io.ipoli.android.challenge.predefined.usecase.SchedulePredefinedChallengeUseCase
import io.ipoli.android.challenge.show.ChallengeAction
import io.ipoli.android.challenge.show.ChallengeViewState
import io.ipoli.android.challenge.usecase.*
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.view.asThemedWrapper
import io.ipoli.android.myPoliApp
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.RepeatingQuest
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.threeten.bp.LocalDate
import space.traversal.kapsule.required

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/7/18.
 */
object ChallengeSideEffectHandler : AppSideEffectHandler() {

    private val saveQuestsForChallengeUseCase by required { saveQuestsForChallengeUseCase }
    private val saveChallengeUseCase by required { saveChallengeUseCase }
    private val removeChallengeUseCase by required { removeChallengeUseCase }
    private val removeQuestFromChallengeUseCase by required { removeQuestFromChallengeUseCase }
    private val loadQuestPickerQuestsUseCase by required { loadQuestPickerQuestsUseCase }
    private val completeChallengeUseCase by required { completeChallengeUseCase }
    private val schedulePredefinedChallengeUseCase by required { schedulePredefinedChallengeUseCase }

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {
            is QuestPickerAction.Load -> {
                val result = loadQuestPickerQuestsUseCase.execute(
                    LoadQuestPickerQuestsUseCase.Params(action.challengeId)
                )
                dispatch(
                    QuestPickerAction.Loaded(result.quests, result.repeatingQuests)
                )
            }

            is QuestPickerAction.Save -> {
                val pickerState = state.stateFor(QuestPickerViewState::class.java)
                val challengeId = pickerState.challengeId

                val quests = pickerState.allQuests.map {
                    it.baseQuest
                }

                saveQuestsForChallengeUseCase.execute(
                    SaveQuestsForChallengeUseCase.Params.WithExistingQuests(
                        challengeId,
                        quests,
                        pickerState.selectedQuests
                    )
                )
            }

            is EditChallengeAction.Save -> {
                val s = state.stateFor(EditChallengeViewState::class.java)
                val params = if (s.id.isEmpty()) {
                    SaveChallengeUseCase.Params.WithExistingQuests(
                        name = s.name,
                        tags = s.challengeTags,
                        color = s.color,
                        icon = s.icon,
                        difficulty = s.difficulty,
                        end = s.end,
                        motivations = listOf(s.motivation1, s.motivation2, s.motivation3),
                        allQuests = s.allQuests,
                        selectedQuestIds = s.selectedQuestIds,
                        note = s.note
                    )
                } else {
                    SaveChallengeUseCase.Params.WithExistingQuests(
                        id = s.id,
                        name = s.name,
                        tags = s.challengeTags,
                        color = s.color,
                        icon = s.icon,
                        difficulty = s.difficulty,
                        end = s.end,
                        motivations = listOf(s.motivation1, s.motivation2, s.motivation3),
                        note = s.note
                    )
                }

                saveChallengeUseCase.execute(
                    params
                )
            }

            is ChallengeAction.Remove ->
                removeChallengeUseCase.execute(
                    RemoveChallengeUseCase.Params(
                        action.challengeId
                    )
                )

            is ChallengeAction.RemoveQuestFromChallenge -> {
                val s = state.stateFor(ChallengeViewState::class.java) as ChallengeViewState.Changed
                val q = s.quests[action.questIndex]
                val params = if (q is Quest) {
                    RemoveQuestFromChallengeUseCase.Params.WithQuestId(q.id)
                } else if (q is RepeatingQuest) {
                    RemoveQuestFromChallengeUseCase.Params.WithRepeatingQuestId(q.id)
                } else {
                    throw IllegalArgumentException("Unknown quest type ${q}")
                }

                removeQuestFromChallengeUseCase.execute(params)
            }

            is ChallengeAction.Complete -> {
                val c =
                    completeChallengeUseCase.execute(CompleteChallengeUseCase.Params(action.challengeId))
                launch(UI) {
                    CompleteChallengePopup(c).show(myPoliApp.instance.asThemedWrapper())
                }
            }

            is PersonalizeChallengeAction.AcceptChallenge -> {
                val s = state.stateFor(PersonalizeChallengeViewState::class.java)
                val predefinedChallenge = s.challenge!!

                val challenge =
                    PredefinedChallengeData(
                        predefinedChallenge.category,
                        predefinedChallenge.quests.filter { s.selectedQuests.contains(it) },
                        predefinedChallenge.durationDays
                    )
                val baseQuests = schedulePredefinedChallengeUseCase.execute(
                    SchedulePredefinedChallengeUseCase.Params(challenge)
                )
                saveChallengeUseCase.execute(
                    SaveChallengeUseCase.Params.WithNewQuests(
                        id = "",
                        tags = emptyList(),
                        name = predefinedChallenge.title,
                        color = predefinedChallenge.color,
                        icon = predefinedChallenge.icon,
                        difficulty = predefinedChallenge.difficulty,
                        motivations = predefinedChallenge.motivations,
                        end = LocalDate.now().plusDays((predefinedChallenge.durationDays - 1).toLong()),
                        quests = baseQuests
                    )
                )
            }

        }

    }

    override fun canHandle(action: Action) =
        action is QuestPickerAction
            || action is EditChallengeAction
            || action is ChallengeAction
            || action is PersonalizeChallengeAction

}