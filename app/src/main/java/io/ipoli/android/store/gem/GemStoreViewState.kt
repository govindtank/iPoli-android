package io.ipoli.android.store.gem

import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.mvi.ViewState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.store.purchase.GemPack
import io.ipoli.android.store.purchase.InAppPurchaseManager

/**
 * Created by Venelin Valkov <venelin@io.ipoli.io>
 * on 27.12.17.
 */

sealed class GemStoreAction : Action {
    data class Load(val purchaseManager: InAppPurchaseManager) : GemStoreAction()
    data class BuyGemPack(val gemPack: GemPack) :
        GemStoreAction()

    data class GemPackPurchased(val dogUnlocked: Boolean) : GemStoreAction()
    object PurchaseFailed : GemStoreAction()
}

object GemStoreReducer : BaseViewStateReducer<GemStoreViewState>() {
    override fun reduce(
        state: AppState,
        subState: GemStoreViewState,
        action: Action
    ) =
        when (action) {

            is GemStoreAction.Load ->
                subState.copy(
                    type = GemStoreViewState.StateType.PLAYER_CHANGED,
                    isGiftPurchased = state.dataState.player!!.hasPet(PetAvatar.DOG)

                )

            is DataLoadedAction.PlayerChanged ->
                subState.copy(
                    type = GemStoreViewState.StateType.PLAYER_CHANGED,
                    isGiftPurchased = action.player.hasPet(PetAvatar.DOG)
                )

            is DataLoadedAction.GemPacksLoaded ->
                subState.copy(
                    type = GemStoreViewState.StateType.GEM_PACKS_LOADED,
                    gemPacks = action.gemPacks
                )

            is GemStoreAction.BuyGemPack ->
                subState.copy(
                    type = GemStoreViewState.StateType.PURCHASING
                )

            is GemStoreAction.GemPackPurchased -> {
                val type =
                    if (action.dogUnlocked)
                        GemStoreViewState.StateType.DOG_UNLOCKED
                    else
                        GemStoreViewState.StateType.GEM_PACK_PURCHASED
                subState.copy(
                    type = type
                )
            }

            GemStoreAction.PurchaseFailed ->
                subState.copy(
                    type = GemStoreViewState.StateType.PURCHASE_FAILED
                )

            else -> subState
        }

    override fun defaultState() = GemStoreViewState(GemStoreViewState.StateType.LOADING)

    override val stateKey = key<GemStoreViewState>()

}

data class GemStoreViewState(
    val type: StateType = StateType.DATA_LOADED,
    val playerGems: Int = 0,
    val isGiftPurchased: Boolean = false,
    val gemPacks: List<GemPack> = listOf()
//    val gems: Int
) : ViewState {
    enum class StateType {
        LOADING,
        DATA_LOADED,
        PLAYER_CHANGED,
        GEM_PACKS_LOADED,
        DOG_UNLOCKED,
        GEM_PACK_PURCHASED,
        PURCHASE_FAILED,
        PURCHASING
    }
}