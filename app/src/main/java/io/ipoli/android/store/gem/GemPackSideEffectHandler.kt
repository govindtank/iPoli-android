package io.ipoli.android.store.gem

import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.view.CurrencyConverterAction
import io.ipoli.android.player.usecase.ConvertCoinsToGemsUseCase
import io.ipoli.android.store.purchase.InAppPurchaseManager
import io.ipoli.android.store.usecase.PurchaseGemPackUseCase
import space.traversal.kapsule.required

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 04/24/2018.
 */

class GemPackSideEffectHandler : AppSideEffectHandler() {

    private val purchaseGemPackUseCase by required { purchaseGemPackUseCase }
    private val convertCoinsToGemsUseCase by required { convertCoinsToGemsUseCase }

    private lateinit var inAppPurchaseManager: InAppPurchaseManager

    override suspend fun doExecute(action: Action, state: AppState) {
        when (action) {

            is CurrencyConverterAction.Load -> {
                inAppPurchaseManager = action.purchaseManager
                inAppPurchaseManager.loadAll {
                    dispatch(DataLoadedAction.GemPacksLoaded(it))
                }
            }

            is GemStoreAction.Load -> {
                inAppPurchaseManager = action.purchaseManager
                inAppPurchaseManager.loadAll {
                    dispatch(DataLoadedAction.GemPacksLoaded(it))
                }
            }

            is CurrencyConverterAction.Convert ->
                dispatch(
                    CurrencyConverterAction.ConvertTransactionComplete(
                        convertCoinsToGemsUseCase.execute(ConvertCoinsToGemsUseCase.Params(action.gems))
                    )
                )

            is GemStoreAction.BuyGemPack ->
                inAppPurchaseManager.purchase(
                    action.gemPack.type,
                    object : InAppPurchaseManager.PurchaseListener {
                        override fun onPurchased() {
                            val result =
                                purchaseGemPackUseCase
                                    .execute(PurchaseGemPackUseCase.Params(action.gemPack))
                            dispatch(GemStoreAction.GemPackPurchased(result.hasUnlockedPet))
                        }

                        override fun onError() {
                            dispatch(GemStoreAction.PurchaseFailed)
                        }

                    })
        }
    }

    override fun canHandle(action: Action) =
        action is GemStoreAction || action is CurrencyConverterAction

}