package io.ipoli.android.common.di

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.view.LayoutInflater
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.firestore.FirebaseFirestore
import io.ipoli.android.challenge.persistence.ChallengeRepository
import io.ipoli.android.challenge.persistence.FirestoreChallengeRepository
import io.ipoli.android.challenge.predefined.usecase.SchedulePredefinedChallengeUseCase
import io.ipoli.android.challenge.sideeffect.ChallengeSideEffectHandler
import io.ipoli.android.challenge.usecase.*
import io.ipoli.android.common.*
import io.ipoli.android.common.analytics.EventLogger
import io.ipoli.android.common.analytics.FirebaseEventLogger
import io.ipoli.android.common.middleware.LogEventsMiddleWare
import io.ipoli.android.common.permission.AndroidPermissionChecker
import io.ipoli.android.common.permission.PermissionChecker
import io.ipoli.android.common.persistence.TagProvider
import io.ipoli.android.common.rate.AndroidRatePopupScheduler
import io.ipoli.android.common.rate.RatePopupScheduler
import io.ipoli.android.common.rate.RatePresenter
import io.ipoli.android.common.redux.CoroutineSideEffectHandlerExecutor
import io.ipoli.android.common.redux.StateStore
import io.ipoli.android.common.text.CalendarFormatter
import io.ipoli.android.common.view.PetMessagePresenter
import io.ipoli.android.event.persistence.AndroidCalendarEventRepository
import io.ipoli.android.event.persistence.AndroidCalendarRepository
import io.ipoli.android.event.persistence.CalendarRepository
import io.ipoli.android.event.persistence.EventRepository
import io.ipoli.android.event.sideeffect.CalendarSideEffectHandler
import io.ipoli.android.event.usecase.FindEventsBetweenDatesUseCase
import io.ipoli.android.event.usecase.SaveSyncCalendarsUseCase
import io.ipoli.android.note.usecase.SaveQuestNoteUseCase
import io.ipoli.android.pet.AndroidJobLowerPetStatsScheduler
import io.ipoli.android.pet.LowerPetStatsScheduler
import io.ipoli.android.pet.PetDialogPresenter
import io.ipoli.android.pet.sideeffect.PetSideEffectHandler
import io.ipoli.android.pet.usecase.*
import io.ipoli.android.player.AndroidLevelDownScheduler
import io.ipoli.android.player.AndroidLevelUpScheduler
import io.ipoli.android.player.LevelDownScheduler
import io.ipoli.android.player.LevelUpScheduler
import io.ipoli.android.player.auth.saga.AuthSideEffectHandler
import io.ipoli.android.player.persistence.FirestorePlayerRepository
import io.ipoli.android.player.persistence.PlayerRepository
import io.ipoli.android.player.usecase.*
import io.ipoli.android.player.view.LevelUpPresenter
import io.ipoli.android.quest.bucketlist.sideeffect.BucketListSideEffectHandler
import io.ipoli.android.quest.bucketlist.usecase.CreateBucketListItemsUseCase
import io.ipoli.android.quest.data.persistence.FirestoreQuestRepository
import io.ipoli.android.quest.data.persistence.QuestRepository
import io.ipoli.android.quest.edit.sideeffect.EditQuestSideEffectHandler
import io.ipoli.android.quest.job.AndroidJobQuestCompleteScheduler
import io.ipoli.android.quest.job.AndroidJobReminderScheduler
import io.ipoli.android.quest.job.QuestCompleteScheduler
import io.ipoli.android.quest.job.ReminderScheduler
import io.ipoli.android.quest.reminder.formatter.TimeUnitFormatter
import io.ipoli.android.quest.schedule.agenda.sideeffect.AgendaSideEffectHandler
import io.ipoli.android.quest.schedule.agenda.usecase.CreateAgendaItemsUseCase
import io.ipoli.android.quest.schedule.agenda.usecase.FindAgendaDatesUseCase
import io.ipoli.android.quest.schedule.calendar.sideeffect.DayViewSideEffectHandler
import io.ipoli.android.quest.show.job.AndroidJobTimerCompleteScheduler
import io.ipoli.android.quest.show.job.TimerCompleteScheduler
import io.ipoli.android.quest.show.sideeffect.QuestSideEffectHandler
import io.ipoli.android.quest.show.usecase.*
import io.ipoli.android.quest.subquest.usecase.*
import io.ipoli.android.quest.usecase.*
import io.ipoli.android.quest.view.QuestCompletePresenter
import io.ipoli.android.repeatingquest.AndroidSaveQuestsForRepeatingQuestScheduler
import io.ipoli.android.repeatingquest.SaveQuestsForRepeatingQuestScheduler
import io.ipoli.android.repeatingquest.persistence.FirestoreRepeatingQuestRepository
import io.ipoli.android.repeatingquest.persistence.RepeatingQuestRepository
import io.ipoli.android.repeatingquest.sideeffect.RepeatingQuestSideEffectHandler
import io.ipoli.android.repeatingquest.usecase.*
import io.ipoli.android.store.avatar.sideeffect.AvatarSideEffectHandler
import io.ipoli.android.store.avatar.usecase.BuyAvatarUseCase
import io.ipoli.android.store.avatar.usecase.ChangeAvatarUseCase
import io.ipoli.android.store.gem.GemPackSideEffectHandler
import io.ipoli.android.store.membership.MembershipSideEffectHandler
import io.ipoli.android.store.membership.job.AndroidCheckMembershipStatusScheduler
import io.ipoli.android.store.membership.job.CheckMembershipStatusScheduler
import io.ipoli.android.store.membership.usecase.CalculateMembershipPlanPriceUseCase
import io.ipoli.android.store.membership.usecase.RemoveMembershipUseCase
import io.ipoli.android.store.membership.usecase.UpdatePlayerMembershipUseCase
import io.ipoli.android.store.powerup.job.AndroidRemoveExpiredPowerUpsScheduler
import io.ipoli.android.store.powerup.job.RemoveExpiredPowerUpsScheduler
import io.ipoli.android.store.powerup.middleware.CheckEnabledPowerUpMiddleWare
import io.ipoli.android.store.powerup.sideeffect.PowerUpSideEffectHandler
import io.ipoli.android.store.powerup.usecase.BuyPowerUpUseCase
import io.ipoli.android.store.powerup.usecase.EnableAllPowerUpsUseCase
import io.ipoli.android.store.powerup.usecase.RemoveExpiredPowerUpsUseCase
import io.ipoli.android.store.sideeffect.StoreSideEffectHandler
import io.ipoli.android.store.theme.sideeffect.ThemeSideEffectHandler
import io.ipoli.android.store.theme.usecase.BuyThemeUseCase
import io.ipoli.android.store.theme.usecase.ChangeThemeUseCase
import io.ipoli.android.store.usecase.PurchaseGemPackUseCase
import io.ipoli.android.tag.persistence.FirestoreTagRepository
import io.ipoli.android.tag.persistence.TagRepository
import io.ipoli.android.tag.sideeffect.TagSideEffectHandler
import io.ipoli.android.tag.usecase.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import space.traversal.kapsule.HasModules
import space.traversal.kapsule.Injects
import space.traversal.kapsule.required

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 9/10/17.
 */
interface RepositoryModule {
    val tagProvider: TagProvider
    val questRepository: QuestRepository
    val playerRepository: PlayerRepository
    val repeatingQuestRepository: RepeatingQuestRepository
    val challengeRepository: ChallengeRepository
    val eventRepository: EventRepository
    val calendarRepository: CalendarRepository
    val tagRepository: TagRepository
}

class FirestoreRepositoryModule : RepositoryModule, Injects<Module> {

    override val tagProvider = TagProvider()

    override val questRepository by required {
        FirestoreQuestRepository(
            database,
            job + CommonPool,
            sharedPreferences,
            FirestoreTagRepository(database, job + CommonPool, sharedPreferences)
        )
    }

    override val playerRepository
            by required {
                FirestorePlayerRepository(
                    database,
                    job + CommonPool,
                    sharedPreferences
                )
            }

    override val repeatingQuestRepository
            by required {
                FirestoreRepeatingQuestRepository(
                    database,
                    job + CommonPool,
                    sharedPreferences,
                    FirestoreTagRepository(database, job + CommonPool, sharedPreferences)
                )
            }

    override val challengeRepository by required {
        FirestoreChallengeRepository(
            database,
            job + CommonPool,
            sharedPreferences,
            FirestoreTagRepository(database, job + CommonPool, sharedPreferences)
        )
    }

    override val eventRepository by required {
        AndroidCalendarEventRepository()
    }

    override val calendarRepository by required {
        AndroidCalendarRepository()
    }

    override val tagRepository
            by required {
                FirestoreTagRepository(
                    database,
                    job + CommonPool,
                    sharedPreferences
                )
            }

}

interface AndroidModule {
    val layoutInflater: LayoutInflater

    val sharedPreferences: SharedPreferences

    val timeUnitFormatter: TimeUnitFormatter

    val calendarFormatter: CalendarFormatter

    val database: FirebaseFirestore

    val eventLogger: EventLogger

    val reminderScheduler: ReminderScheduler

    val timerCompleteScheduler: TimerCompleteScheduler

    val questCompleteScheduler: QuestCompleteScheduler

    val levelUpScheduler: LevelUpScheduler

    val levelDownScheduler: LevelDownScheduler

    val lowerPetStatsScheduler: LowerPetStatsScheduler

    val saveQuestsForRepeatingQuestScheduler: SaveQuestsForRepeatingQuestScheduler

    val removeExpiredPowerUpsScheduler: RemoveExpiredPowerUpsScheduler

    val checkMembershipStatusScheduler: CheckMembershipStatusScheduler

    val ratePopupScheduler: RatePopupScheduler

    val permissionChecker: PermissionChecker

    val job: Job
}

class MainAndroidModule(
    private val context: Context,
    firebaseAnalytics: FirebaseAnalytics
) : AndroidModule {
    override val layoutInflater: LayoutInflater get() = LayoutInflater.from(context)

    override val sharedPreferences: SharedPreferences
        get() =
            PreferenceManager.getDefaultSharedPreferences(
                context
            )

    override val timeUnitFormatter get() = TimeUnitFormatter(context)

    override val calendarFormatter get() = CalendarFormatter(context)

    override val reminderScheduler get() = AndroidJobReminderScheduler()

    override val timerCompleteScheduler get() = AndroidJobTimerCompleteScheduler()

    override val questCompleteScheduler get() = AndroidJobQuestCompleteScheduler()

    override val levelUpScheduler get() = AndroidLevelUpScheduler()

    override val levelDownScheduler get() = AndroidLevelDownScheduler()

    override val lowerPetStatsScheduler get() = AndroidJobLowerPetStatsScheduler()

    override val saveQuestsForRepeatingQuestScheduler get() = AndroidSaveQuestsForRepeatingQuestScheduler()

    override val removeExpiredPowerUpsScheduler get() = AndroidRemoveExpiredPowerUpsScheduler()

    override val checkMembershipStatusScheduler
        get() = AndroidCheckMembershipStatusScheduler()

    override val ratePopupScheduler get() = AndroidRatePopupScheduler()

    override val database get() = FirebaseFirestore.getInstance()

    override val eventLogger = FirebaseEventLogger(firebaseAnalytics)

    override val permissionChecker
        get() = AndroidPermissionChecker()

    override val job get() = Job()
}

class MainUseCaseModule : UseCaseModule, Injects<Module> {
    private val questRepository by required { questRepository }
    private val eventRepository by required { eventRepository }
    private val permissionChecker by required { permissionChecker }
    private val repeatingQuestRepository by required { repeatingQuestRepository }
    private val playerRepository by required { playerRepository }
    private val challengeRepository by required { challengeRepository }
    private val tagRepository by required { tagRepository }
    private val reminderScheduler by required { reminderScheduler }
    private val questCompleteScheduler by required { questCompleteScheduler }
    private val levelUpScheduler by required { levelUpScheduler }
    private val levelDownScheduler by required { levelDownScheduler }
    private val rateDialogScheduler by required { ratePopupScheduler }
    private val timerCompleteScheduler by required { timerCompleteScheduler }

    override val loadScheduleForDateUseCase
        get() = LoadScheduleForDateUseCase()
    override val saveQuestUseCase
        get() = SaveQuestUseCase(
            questRepository,
            reminderScheduler
        )
    override val removeQuestUseCase
        get() = RemoveQuestUseCase(
            questRepository,
            timerCompleteScheduler,
            reminderScheduler
        )
    override val undoRemoveQuestUseCase get() = UndoRemovedQuestUseCase(questRepository)
    override val findQuestToRemindUseCase get() = FindQuestsToRemindUseCase(questRepository)
    override val snoozeQuestUseCase get() = SnoozeQuestUseCase(questRepository, reminderScheduler)
    override val completeQuestUseCase
        get() = CompleteQuestUseCase(
            questRepository,
            playerRepository,
            reminderScheduler,
            questCompleteScheduler,
            rateDialogScheduler,
            rewardPlayerUseCase
        )
    override val undoCompletedQuestUseCase
        get() = UndoCompletedQuestUseCase(
            questRepository,
            reminderScheduler,
            removeRewardFromPlayerUseCase
        )
    override val listenForPlayerChangesUseCase
        get() = ListenForPlayerChangesUseCase(
            playerRepository
        )
    override val rewardPlayerUseCase get() = RewardPlayerUseCase(playerRepository, levelUpScheduler)
    override val removeRewardFromPlayerUseCase
        get() = RemoveRewardFromPlayerUseCase(
            playerRepository,
            levelDownScheduler
        )
    override val feedPetUseCase get() = FeedPetUseCase(playerRepository)
    override val revivePetUseCase get() = RevivePetUseCase(playerRepository)
    override val buyPetUseCase get() = BuyPetUseCase(playerRepository)
    override val changePetUseCase get() = ChangePetUseCase(playerRepository)
    override val findPetUseCase get() = FindPetUseCase(playerRepository)
    override val changeThemeUseCase get() = ChangeThemeUseCase(playerRepository)
    override val buyThemeUseCase get() = BuyThemeUseCase(playerRepository)
    override val renamePetUseCase get() = RenamePetUseCase(playerRepository)
    override val buyIconPackUseCase get() = BuyIconPackUseCase(playerRepository)
    override val buyColorPackUseCase get() = BuyColorPackUseCase(playerRepository)
    override val convertCoinsToGemsUseCase get() = ConvertCoinsToGemsUseCase(playerRepository)
    override val comparePetItemsUseCase get() = ComparePetItemsUseCase()
    override val buyPetItemUseCase get() = BuyPetItemUseCase(playerRepository)
    override val equipPetItemUseCase get() = EquipPetItemUseCase(playerRepository)
    override val takeOffPetItemUseCase get() = TakeOffPetItemUseCase(playerRepository)
    override val purchaseGemPackUseCase get() = PurchaseGemPackUseCase(playerRepository)
    override val schedulePredefinedChallengeUseCase get() = SchedulePredefinedChallengeUseCase()
    override val buyChallengeUseCase get() = BuyChallengeUseCase(playerRepository)
    override val splitDurationForPomodoroTimerUseCase get() = SplitDurationForPomodoroTimerUseCase()
    override val completeTimeRangeUseCase
        get() = CompleteTimeRangeUseCase(
            questRepository,
            splitDurationForPomodoroTimerUseCase,
            completeQuestUseCase,
            timerCompleteScheduler
        )
    override val cancelTimerUseCase
        get() = CancelTimerUseCase(
            questRepository,
            timerCompleteScheduler
        )
    override val addPomodoroUseCase
        get() = AddPomodoroUseCase(
            questRepository,
            splitDurationForPomodoroTimerUseCase
        )
    override val removePomodoroUseCase
        get() = RemovePomodoroUseCase(
            questRepository,
            splitDurationForPomodoroTimerUseCase
        )
    override val addTimerToQuestUseCase: AddTimerToQuestUseCase
        get() = AddTimerToQuestUseCase(
            questRepository,
            cancelTimerUseCase,
            timerCompleteScheduler
        )

    override val findAgendaDatesUseCase get() = FindAgendaDatesUseCase(questRepository)
    override val createAgendaItemsUseCase get() = CreateAgendaItemsUseCase()

    override val findPlayerLevelUseCase
        get() = FindPlayerLevelUseCase(playerRepository)

    override val lowerPetStatsUseCase
        get() = LowerPetStatsUseCase(
            questRepository,
            playerRepository
        )

    override val saveRepeatingQuestUseCase
        get() = SaveRepeatingQuestUseCase(
            questRepository,
            repeatingQuestRepository,
            saveQuestsForRepeatingQuestUseCase
        )

    override val findNextDateForRepeatingQuestUseCase
        get() = FindNextDateForRepeatingQuestUseCase(
            questRepository
        )

    override val findPeriodProgressForRepeatingQuestUseCase
        get() = FindPeriodProgressForRepeatingQuestUseCase(
            questRepository
        )

    override val saveQuestsForRepeatingQuestUseCase
        get() = SaveQuestsForRepeatingQuestUseCase(
            questRepository
        )
    override val removeRepeatingQuestUseCase
        get() = RemoveRepeatingQuestUseCase(
            questRepository,
            repeatingQuestRepository
        )
    override val createRepeatingQuestHistoryUseCase
        get() = CreateRepeatingQuestHistoryUseCase(
            questRepository,
            repeatingQuestRepository
        )
    override val createPlaceholderQuestsForRepeatingQuestsUseCase
        get() = CreatePlaceholderQuestsForRepeatingQuestsUseCase(
            questRepository,
            repeatingQuestRepository
        )

    override val saveQuestsForChallengeUseCase
        get() = SaveQuestsForChallengeUseCase(
            questRepository,
            repeatingQuestRepository,
            saveRepeatingQuestUseCase
        )

    override val removeQuestFromChallengeUseCase
        get() = RemoveQuestFromChallengeUseCase(questRepository, repeatingQuestRepository)

    override val saveChallengeUseCase
        get() = SaveChallengeUseCase(challengeRepository, saveQuestsForChallengeUseCase)

    override val removeChallengeUseCase
        get() = RemoveChallengeUseCase(
            challengeRepository,
            questRepository,
            repeatingQuestRepository
        )

    override val loadQuestPickerQuestsUseCase
        get() = LoadQuestPickerQuestsUseCase(
            questRepository,
            repeatingQuestRepository
        )

    override val findQuestsForChallengeUseCase
        get() = FindQuestsForChallengeUseCase(
            questRepository,
            repeatingQuestRepository
        )

    override val findNextDateForChallengeUseCase
        get() = FindNextDateForChallengeUseCase()

    override val findChallengeProgressUseCase
        get() = FindChallengeProgressUseCase()

    override val completeChallengeUseCase
        get() = CompleteChallengeUseCase(
            challengeRepository,
            playerRepository
        )

    override val buyPowerUpUseCase
        get() = BuyPowerUpUseCase(playerRepository)

    override val removeExpiredPowerUpsUseCase
        get() = RemoveExpiredPowerUpsUseCase(playerRepository)

    override val enableAllPowerUpsUseCase
        get() = EnableAllPowerUpsUseCase(playerRepository)

    override val updatePlayerMembershipUseCase
        get() = UpdatePlayerMembershipUseCase(
            playerRepository,
            enableAllPowerUpsUseCase
        )

    override val calculateMembershipPlanPriceUseCase
        get() = CalculateMembershipPlanPriceUseCase()

    override val removeMembershipUseCase
        get() = RemoveMembershipUseCase(playerRepository)

    override val buyAvatarUseCase
        get() = BuyAvatarUseCase(playerRepository)

    override val changeAvatarUseCase
        get() = ChangeAvatarUseCase(playerRepository)

    override val completeSubQuestUseCase
        get() = CompleteSubQuestUseCase(questRepository)

    override val undoCompletedSubQuestUseCase
        get() = UndoCompletedSubQuestUseCase(questRepository)

    override val saveSubQuestNameUseCase
        get() = SaveSubQuestNameUseCase(questRepository)

    override val addSubQuestUseCase
        get() = AddSubQuestUseCase(questRepository)

    override val removeSubQuestUseCase
        get() = RemoveSubQuestUseCase(questRepository)

    override val reorderSubQuestUseCase
        get() = ReorderSubQuestUseCase(questRepository)

    override val saveQuestNoteUseCase
        get() = SaveQuestNoteUseCase(questRepository)

    override val findEventsBetweenDatesUseCase: FindEventsBetweenDatesUseCase
        get() = FindEventsBetweenDatesUseCase(playerRepository, eventRepository, permissionChecker)

    override val saveSyncCalendarsUseCase
        get() = SaveSyncCalendarsUseCase(playerRepository)

    override val saveTagUseCase
        get() = SaveTagUseCase(tagRepository)

    override val favoriteTagUseCase
        get() = FavoriteTagUseCase(tagRepository)

    override val unfavoriteTagUseCase
        get() = UnfavoriteTagUseCase(tagRepository)

    override val createTagItemsUseCase
        get() = CreateTagItemsUseCase()

    override val addQuestCountToTagUseCase
        get() = AddQuestCountToTagUseCase(questRepository)

    override val removeTagUseCase
        get() = RemoveTagUseCase(
            questRepository,
            repeatingQuestRepository,
            challengeRepository,
            tagRepository
        )

    override val createBucketListItemsUseCase
        get() = CreateBucketListItemsUseCase()

    override val rescheduleQuestUseCase
        get() = RescheduleQuestUseCase(questRepository)
}

interface UseCaseModule {
    val loadScheduleForDateUseCase: LoadScheduleForDateUseCase
    val saveQuestUseCase: SaveQuestUseCase
    val removeQuestUseCase: RemoveQuestUseCase
    val undoRemoveQuestUseCase: UndoRemovedQuestUseCase
    val findQuestToRemindUseCase: FindQuestsToRemindUseCase
    val snoozeQuestUseCase: SnoozeQuestUseCase
    val completeQuestUseCase: CompleteQuestUseCase
    val undoCompletedQuestUseCase: UndoCompletedQuestUseCase
    val listenForPlayerChangesUseCase: ListenForPlayerChangesUseCase
    val rewardPlayerUseCase: RewardPlayerUseCase
    val removeRewardFromPlayerUseCase: RemoveRewardFromPlayerUseCase
    val feedPetUseCase: FeedPetUseCase
    val revivePetUseCase: RevivePetUseCase
    val buyPetUseCase: BuyPetUseCase
    val changePetUseCase: ChangePetUseCase
    val findPetUseCase: FindPetUseCase
    val changeThemeUseCase: ChangeThemeUseCase
    val buyThemeUseCase: BuyThemeUseCase
    val renamePetUseCase: RenamePetUseCase
    val buyIconPackUseCase: BuyIconPackUseCase
    val buyColorPackUseCase: BuyColorPackUseCase
    val convertCoinsToGemsUseCase: ConvertCoinsToGemsUseCase
    val comparePetItemsUseCase: ComparePetItemsUseCase
    val buyPetItemUseCase: BuyPetItemUseCase
    val equipPetItemUseCase: EquipPetItemUseCase
    val takeOffPetItemUseCase: TakeOffPetItemUseCase
    val purchaseGemPackUseCase: PurchaseGemPackUseCase
    val schedulePredefinedChallengeUseCase: SchedulePredefinedChallengeUseCase
    val buyChallengeUseCase: BuyChallengeUseCase
    val splitDurationForPomodoroTimerUseCase: SplitDurationForPomodoroTimerUseCase
    val findPlayerLevelUseCase: FindPlayerLevelUseCase
    val lowerPetStatsUseCase: LowerPetStatsUseCase
    val completeTimeRangeUseCase: CompleteTimeRangeUseCase
    val cancelTimerUseCase: CancelTimerUseCase
    val addPomodoroUseCase: AddPomodoroUseCase
    val removePomodoroUseCase: RemovePomodoroUseCase
    val addTimerToQuestUseCase: AddTimerToQuestUseCase
    val findAgendaDatesUseCase: FindAgendaDatesUseCase
    val createAgendaItemsUseCase: CreateAgendaItemsUseCase
    val saveRepeatingQuestUseCase: SaveRepeatingQuestUseCase
    val findNextDateForRepeatingQuestUseCase: FindNextDateForRepeatingQuestUseCase
    val findPeriodProgressForRepeatingQuestUseCase: FindPeriodProgressForRepeatingQuestUseCase
    val saveQuestsForRepeatingQuestUseCase: SaveQuestsForRepeatingQuestUseCase
    val removeRepeatingQuestUseCase: RemoveRepeatingQuestUseCase
    val createRepeatingQuestHistoryUseCase: CreateRepeatingQuestHistoryUseCase
    val createPlaceholderQuestsForRepeatingQuestsUseCase: CreatePlaceholderQuestsForRepeatingQuestsUseCase
    val saveChallengeUseCase: SaveChallengeUseCase
    val saveQuestsForChallengeUseCase: SaveQuestsForChallengeUseCase
    val removeQuestFromChallengeUseCase: RemoveQuestFromChallengeUseCase
    val removeChallengeUseCase: RemoveChallengeUseCase
    val loadQuestPickerQuestsUseCase: LoadQuestPickerQuestsUseCase
    val findQuestsForChallengeUseCase: FindQuestsForChallengeUseCase
    val findNextDateForChallengeUseCase: FindNextDateForChallengeUseCase
    val findChallengeProgressUseCase: FindChallengeProgressUseCase
    val completeChallengeUseCase: CompleteChallengeUseCase
    val buyPowerUpUseCase: BuyPowerUpUseCase
    val removeExpiredPowerUpsUseCase: RemoveExpiredPowerUpsUseCase
    val enableAllPowerUpsUseCase: EnableAllPowerUpsUseCase
    val updatePlayerMembershipUseCase: UpdatePlayerMembershipUseCase
    val calculateMembershipPlanPriceUseCase: CalculateMembershipPlanPriceUseCase
    val removeMembershipUseCase: RemoveMembershipUseCase
    val buyAvatarUseCase: BuyAvatarUseCase
    val changeAvatarUseCase: ChangeAvatarUseCase
    val completeSubQuestUseCase: CompleteSubQuestUseCase
    val undoCompletedSubQuestUseCase: UndoCompletedSubQuestUseCase
    val saveSubQuestNameUseCase: SaveSubQuestNameUseCase
    val addSubQuestUseCase: AddSubQuestUseCase
    val removeSubQuestUseCase: RemoveSubQuestUseCase
    val reorderSubQuestUseCase: ReorderSubQuestUseCase
    val saveQuestNoteUseCase: SaveQuestNoteUseCase
    val findEventsBetweenDatesUseCase: FindEventsBetweenDatesUseCase
    val saveSyncCalendarsUseCase: SaveSyncCalendarsUseCase
    val saveTagUseCase: SaveTagUseCase
    val favoriteTagUseCase: FavoriteTagUseCase
    val unfavoriteTagUseCase: UnfavoriteTagUseCase
    val createTagItemsUseCase: CreateTagItemsUseCase
    val addQuestCountToTagUseCase: AddQuestCountToTagUseCase
    val removeTagUseCase: RemoveTagUseCase
    val createBucketListItemsUseCase: CreateBucketListItemsUseCase
    val rescheduleQuestUseCase: RescheduleQuestUseCase
}

interface PresenterModule {
    val petDialogPresenter: PetDialogPresenter
    val petMessagePresenter: PetMessagePresenter
    val levelUpPresenter: LevelUpPresenter
    val questCompletePresenter: QuestCompletePresenter
    val ratePresenter: RatePresenter
}

class AndroidPresenterModule : PresenterModule, Injects<Module> {

    private val listenForPlayerChangesUseCase by required { listenForPlayerChangesUseCase }
    private val findPetUseCase by required { findPetUseCase }
    private val timeUnitFormatter by required { timeUnitFormatter }
    private val job by required { job }

    override val petDialogPresenter get() = PetDialogPresenter(findPetUseCase, job)

    override val petMessagePresenter get() = PetMessagePresenter(listenForPlayerChangesUseCase, job)
    override val levelUpPresenter get() = LevelUpPresenter(listenForPlayerChangesUseCase, job)
    override val questCompletePresenter
        get() = QuestCompletePresenter(
            listenForPlayerChangesUseCase,
            job
        )
    override val ratePresenter get() = RatePresenter(listenForPlayerChangesUseCase, job)
}

interface StateStoreModule {
    val stateStore: StateStore<AppState>
}

class AndroidStateStoreModule : StateStoreModule, Injects<Module> {

    override val stateStore by required {
        StateStore(
            initialState = AppState(
                data = mapOf(
                    AppDataState::class.java.simpleName to AppDataReducer.defaultState()
                )
            ),
            reducers = setOf(
                AppDataReducer
            ),
            sideEffectHandlers = setOf(
                LoadAllDataSideEffectHandler(),
                AuthSideEffectHandler(),
                AgendaSideEffectHandler(),
                BuyPredefinedChallengeSideEffectHandler(),
                ChangePetSideEffectHandler(),
                BuyPetSideEffectHandler(),
                DayViewSideEffectHandler(),
                RepeatingQuestSideEffectHandler(),
                ChallengeSideEffectHandler(),
                CalendarSideEffectHandler(),
                MembershipSideEffectHandler(),
                PowerUpSideEffectHandler(),
                QuestSideEffectHandler(),
                EditQuestSideEffectHandler(),
                AvatarSideEffectHandler(),
                ThemeSideEffectHandler(),
                TagSideEffectHandler(),
                BucketListSideEffectHandler(),
                GemPackSideEffectHandler(),
                StoreSideEffectHandler(),
                PetSideEffectHandler()
            ),
            sideEffectHandlerExecutor = CoroutineSideEffectHandlerExecutor(job + CommonPool),
            middleware = setOf(
                LogEventsMiddleWare(),
                CheckEnabledPowerUpMiddleWare()
            )
        )
    }
}

class Module(
    androidModule: AndroidModule,
    repositoryModule: RepositoryModule,
    useCaseModule: UseCaseModule,
    presenterModule: PresenterModule,
    stateStoreModule: StateStoreModule
) :
    AndroidModule by androidModule,
    RepositoryModule by repositoryModule,
    UseCaseModule by useCaseModule,
    PresenterModule by presenterModule,
    StateStoreModule by stateStoreModule,
    HasModules {

    override val modules =
        setOf(
            androidModule,
            repositoryModule,
            useCaseModule,
            presenterModule,
            stateStoreModule
        )
}