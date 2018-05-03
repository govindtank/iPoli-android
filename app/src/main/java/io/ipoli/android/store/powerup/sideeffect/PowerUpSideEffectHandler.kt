package io.ipoli.android.store.powerup.sideeffect

import io.ipoli.android.Constants
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.store.powerup.PowerUp
import io.ipoli.android.store.powerup.PowerUpStoreAction
import io.ipoli.android.store.powerup.buy.BuyPowerUpAction
import io.ipoli.android.store.powerup.usecase.BuyPowerUpUseCase
import space.traversal.kapsule.required

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/20/2018.
 */

data class BuyPowerUpCompletedAction(val result: BuyPowerUpUseCase.Result) : Action

object PowerUpSideEffectHandler : AppSideEffectHandler() {

    private val buyPowerUpUseCase by required { buyPowerUpUseCase }

    override fun canHandle(action: Action) =
        action is PowerUpStoreAction.Enable || action is BuyPowerUpAction.Buy

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {

            is BuyPowerUpAction.Buy -> {
                buyPowerUp(action.powerUp)
            }

            is PowerUpStoreAction.Enable -> {
                buyPowerUp(action.type)
            }
        }
    }

    private fun buyPowerUp(powerUp: PowerUp.Type) {
        val result = buyPowerUpUseCase.execute(
            BuyPowerUpUseCase.Params(
                powerUp,
                Constants.POWER_UP_PURCHASE_DURATION_DAYS
            )
        )

        dispatch(BuyPowerUpCompletedAction(result))
    }

}