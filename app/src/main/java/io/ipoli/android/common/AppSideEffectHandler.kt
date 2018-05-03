package io.ipoli.android.common

import io.ipoli.android.challenge.entity.Challenge
import io.ipoli.android.challenge.predefined.category.list.ChallengeListForCategoryAction
import io.ipoli.android.challenge.usecase.BuyChallengeUseCase
import io.ipoli.android.challenge.usecase.FindChallengeProgressUseCase
import io.ipoli.android.challenge.usecase.FindNextDateForChallengeUseCase
import io.ipoli.android.challenge.usecase.FindQuestsForChallengeUseCase
import io.ipoli.android.common.async.ChannelRelay
import io.ipoli.android.common.di.Module
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.Dispatcher
import io.ipoli.android.common.redux.SideEffectHandler
import io.ipoli.android.common.view.AppWidgetUtil
import io.ipoli.android.myPoliApp
import io.ipoli.android.pet.store.PetStoreAction
import io.ipoli.android.pet.usecase.BuyPetUseCase
import io.ipoli.android.player.Player
import io.ipoli.android.quest.Quest
import io.ipoli.android.quest.RepeatingQuest
import io.ipoli.android.repeatingquest.usecase.FindNextDateForRepeatingQuestUseCase
import io.ipoli.android.repeatingquest.usecase.FindPeriodProgressForRepeatingQuestUseCase
import io.ipoli.android.tag.Tag
import io.ipoli.android.tag.usecase.AddQuestCountToTagUseCase
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.withContext
import org.threeten.bp.LocalDate
import space.traversal.kapsule.Injects
import space.traversal.kapsule.inject
import space.traversal.kapsule.required

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 01/27/2018.
 */

abstract class AppSideEffectHandler : SideEffectHandler<AppState>,
    Injects<Module> {

    private var dispatcher: Dispatcher? = null

    override suspend fun execute(action: Action, state: AppState, dispatcher: Dispatcher) {
        inject(myPoliApp.module(myPoliApp.instance))
        this.dispatcher = dispatcher
        doExecute(action, state)
    }

    abstract suspend fun doExecute(
        action: Action,
        state: AppState
    )

    fun dispatch(action: Action) {
        dispatcher!!.dispatch(action)
    }
}

object BuyPredefinedChallengeSideEffectHandler : AppSideEffectHandler() {

    override suspend fun doExecute(action: Action, state: AppState) {
        val challenge = (action as ChallengeListForCategoryAction.BuyChallenge).challenge
        val result = buyChallengeUseCase.execute(BuyChallengeUseCase.Params(challenge))
        when (result) {
            is BuyChallengeUseCase.Result.ChallengeBought -> {
                dispatch(ChallengeListForCategoryAction.ChallengeBought(challenge))
            }

            BuyChallengeUseCase.Result.TooExpensive -> {
                dispatch(
                    ChallengeListForCategoryAction.ChallengeTooExpensive(
                        challenge
                    )
                )
            }
        }
    }

    private val buyChallengeUseCase by required { buyChallengeUseCase }

    override fun canHandle(action: Action) = action is ChallengeListForCategoryAction.BuyChallenge
}

object ChangePetSideEffectHandler : AppSideEffectHandler() {

    private val changePetUseCase by required { changePetUseCase }

    override suspend fun doExecute(action: Action, state: AppState) {
        changePetUseCase.execute((action as PetStoreAction.ChangePet).pet)
    }

    override fun canHandle(action: Action) = action is PetStoreAction.ChangePet
}

object BuyPetSideEffectHandler : AppSideEffectHandler() {

    private val buyPetUseCase by required { buyPetUseCase }

    override suspend fun doExecute(action: Action, state: AppState) {
        val result = buyPetUseCase.execute((action as PetStoreAction.BuyPet).pet)
        when (result) {
            is BuyPetUseCase.Result.PetBought -> {
                dispatch(PetStoreAction.PetBought)
            }
            BuyPetUseCase.Result.TooExpensive -> {
                dispatch(PetStoreAction.PetTooExpensive)
            }
        }
    }

    override fun canHandle(action: Action) = action is PetStoreAction.BuyPet
}

object LoadAllDataSideEffectHandler : AppSideEffectHandler() {

    private val tagProvider by required { tagProvider }
    private val playerRepository by required { playerRepository }
    private val questRepository by required { questRepository }
    private val challengeRepository by required { challengeRepository }
    private val repeatingQuestRepository by required { repeatingQuestRepository }
    private val tagRepository by required { tagRepository }
    private val findNextDateForRepeatingQuestUseCase by required { findNextDateForRepeatingQuestUseCase }
    private val findPeriodProgressForRepeatingQuestUseCase by required { findPeriodProgressForRepeatingQuestUseCase }
    private val findQuestsForChallengeUseCase by required { findQuestsForChallengeUseCase }
    private val findNextDateForChallengeUseCase by required { findNextDateForChallengeUseCase }
    private val findChallengeProgressUseCase by required { findChallengeProgressUseCase }
    private val addQuestCountToTagUseCase by required { addQuestCountToTagUseCase }

    private val playerChannelRelay = ChannelRelay<Player?, Unit>(
        producer = { c, _ ->
            playerRepository.listen(c)
        },
        consumer = { p, _ ->
            dispatch(DataLoadedAction.PlayerChanged(p!!))
        }
    )

    data class TodayQuestsParams(val currentDate: LocalDate)

    private val todayQuestsChannelRelay = ChannelRelay<List<Quest>, TodayQuestsParams>(
        producer = { c, p ->
            questRepository.listenForScheduledAt(p.currentDate, c)
        },
        consumer = { qs, _ ->
            withContext(UI) {
                updateWidgets()
            }
            dispatch(DataLoadedAction.TodayQuestsChanged(qs))
        }
    )

    private val tagsChannelRelay = ChannelRelay<List<Tag>, Unit>(
        producer = { c, _ ->
            tagRepository.listenForAll(c)
        },
        consumer = { ts, _ ->
            val tags = ts
                .map {
                    addQuestCountToTagUseCase.execute(AddQuestCountToTagUseCase.Params(it))
                }
            tagProvider.updateTags(tags)
            dispatch(DataLoadedAction.TagsChanged(tags))
        }
    )

    private val repeatingQuestsChannelRelay = ChannelRelay<List<RepeatingQuest>, Unit>(
        producer = { c, _ ->
            repeatingQuestRepository.listenForAll(c)
        },
        consumer = { rqs, _ ->
            val repeatingQuests = rqs.map {
                findNextDateForRepeatingQuestUseCase.execute(
                    FindNextDateForRepeatingQuestUseCase.Params(it)
                )
            }.map {
                findPeriodProgressForRepeatingQuestUseCase.execute(
                    FindPeriodProgressForRepeatingQuestUseCase.Params(it)
                )
            }
            dispatch(DataLoadedAction.RepeatingQuestsChanged(repeatingQuests))
        }
    )

    private val challengesChannelRelay = ChannelRelay<List<Challenge>, Unit>(
        producer = { c, _ ->
            challengeRepository.listenForAll(c)
        },
        consumer = { cs, _ ->
            val challenges = cs.map {
                findQuestsForChallengeUseCase.execute(
                    FindQuestsForChallengeUseCase.Params(it)
                )
            }.map {
                findNextDateForChallengeUseCase.execute(
                    FindNextDateForChallengeUseCase.Params(it)
                )
            }.map {
                findChallengeProgressUseCase.execute(
                    FindChallengeProgressUseCase.Params(it)
                )
            }
            dispatch(DataLoadedAction.ChallengesChanged(challenges))
        }
    )

    private val unscheduledQuestsChannelRelay = ChannelRelay<List<Quest>, Unit>(
        producer = { c, _ ->
            questRepository.listenForAllUnscheduled(c)
        },
        consumer = { qs, _ ->
            dispatch(DataLoadedAction.UnscheduledQuestsChanged(qs))
        }
    )

    override suspend fun doExecute(action: Action, state: AppState) {

        if (action is LoadDataAction.ChangePlayer) {
            playerRepository.purge(action.oldPlayerId)
            listenForPlayerData(state)
        }

        if (action == LoadDataAction.All) {
            listenForPlayerData(state)
        }
    }

    private fun listenForPlayerData(state: AppState) {
        playerChannelRelay.listen(Unit)
        todayQuestsChannelRelay.listen(TodayQuestsParams(state.dataState.today))
        repeatingQuestsChannelRelay.listen(Unit)
        challengesChannelRelay.listen(Unit)
        tagsChannelRelay.listen(Unit)
        unscheduledQuestsChannelRelay.listen(Unit)
    }

    private fun updateWidgets() {
        AppWidgetUtil.updateAgendaWidget(myPoliApp.instance)
    }

    override fun canHandle(action: Action) =
        action == LoadDataAction.All
                || action is LoadDataAction.ChangePlayer
}