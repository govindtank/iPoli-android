package io.ipoli.android.store.avatar.sideeffect

import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.store.avatar.AvatarStoreAction
import io.ipoli.android.store.avatar.usecase.BuyAvatarUseCase
import io.ipoli.android.store.avatar.usecase.ChangeAvatarUseCase
import space.traversal.kapsule.required

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/25/2018.
 */

data class BuyAvatarCompletedAction(val result: BuyAvatarUseCase.Result) : Action

object AvatarSideEffectHandler : AppSideEffectHandler() {

    private val buyAvatarUseCase by required { buyAvatarUseCase }
    private val changeAvatarUseCase by required { changeAvatarUseCase }

    override fun canHandle(action: Action) = action is AvatarStoreAction

    override suspend fun doExecute(action: Action, state: AppState) {

        when (action) {
            is AvatarStoreAction.Buy -> {
                val result = buyAvatarUseCase.execute(BuyAvatarUseCase.Params(action.avatar))
                dispatch(BuyAvatarCompletedAction(result))
            }

            is AvatarStoreAction.Change ->
                changeAvatarUseCase.execute(ChangeAvatarUseCase.Params(action.avatar))
        }
    }

}