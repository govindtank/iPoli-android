package io.ipoli.android.store.powerup.middleware

import io.ipoli.android.Constants
import io.ipoli.android.challenge.list.ChallengeListAction
import io.ipoli.android.common.AppState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.Dispatcher
import io.ipoli.android.common.redux.MiddleWare
import io.ipoli.android.note.NoteAction
import io.ipoli.android.player.Inventory
import io.ipoli.android.quest.show.QuestAction
import io.ipoli.android.repeatingquest.add.EditRepeatingQuestAction
import io.ipoli.android.settings.SettingsAction
import io.ipoli.android.store.powerup.PowerUp
import io.ipoli.android.tag.list.TagListAction

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/20/2018.
 */
data class ShowBuyPowerUpAction(val powerUp: PowerUp.Type) : Action

object CheckEnabledPowerUpMiddleWare : MiddleWare<AppState> {

    override fun execute(
        state: AppState,
        dispatcher: Dispatcher,
        action: Action
    ): MiddleWare.Result {
        val p = state.dataState.player ?: return MiddleWare.Result.Continue

        val inventory = p.inventory

        return when (action) {
            ChallengeListAction.AddChallenge ->
                checkForAvailablePowerUp(PowerUp.Type.CHALLENGES, inventory, dispatcher)

            QuestAction.Start ->
                checkForAvailablePowerUp(PowerUp.Type.TIMER, inventory, dispatcher)

            TagListAction.AddTag ->
                if (state.dataState.tags.size < Constants.MAX_FREE_TAGS)
                    MiddleWare.Result.Continue
                else
                    checkForAvailablePowerUp(PowerUp.Type.TAGS, inventory, dispatcher)

            is QuestAction.AddSubQuest,
            is EditRepeatingQuestAction.AddSubQuest ->
                checkForAvailablePowerUp(PowerUp.Type.SUB_QUESTS, inventory, dispatcher)

            is NoteAction.Save ->
                checkForAvailablePowerUp(PowerUp.Type.NOTES, inventory, dispatcher)

            is SettingsAction.ToggleSyncCalendar ->
                checkForAvailablePowerUp(PowerUp.Type.CALENDAR_SYNC, inventory, dispatcher)

            else -> MiddleWare.Result.Continue
        }
    }

    private fun checkForAvailablePowerUp(
        powerUp: PowerUp.Type,
        inventory: Inventory,
        dispatcher: Dispatcher
    ) =
        when {
            inventory.isPowerUpEnabled(powerUp) -> MiddleWare.Result.Continue
            else -> {
                dispatcher.dispatch(ShowBuyPowerUpAction(powerUp))
                MiddleWare.Result.Stop
            }
        }

}