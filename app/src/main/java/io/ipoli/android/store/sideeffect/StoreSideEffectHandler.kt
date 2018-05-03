package io.ipoli.android.store.sideeffect

import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.view.ColorPickerAction
import io.ipoli.android.common.view.IconPickerAction
import io.ipoli.android.player.usecase.BuyColorPackUseCase
import io.ipoli.android.player.usecase.BuyIconPackUseCase
import space.traversal.kapsule.required

object StoreSideEffectHandler : AppSideEffectHandler() {

    private val buyColorPackUseCase by required { buyColorPackUseCase }
    private val buyIconPackUseCase by required { buyIconPackUseCase }

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {
            is ColorPickerAction.BuyColorPack -> {
                val result =
                    buyColorPackUseCase.execute(BuyColorPackUseCase.Params(action.colorPack))
                dispatch(ColorPickerAction.BuyColorPackTransactionComplete(result))
            }

            is IconPickerAction.BuyIconPack -> {
                val result =
                    buyIconPackUseCase.execute(BuyIconPackUseCase.Params(action.iconPack))
                dispatch(IconPickerAction.BuyIconPackTransactionComplete(result))
            }
        }
    }

    override fun canHandle(action: Action) =
        action is ColorPickerAction
                || action is IconPickerAction

}