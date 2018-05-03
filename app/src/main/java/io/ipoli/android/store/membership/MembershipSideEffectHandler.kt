package io.ipoli.android.store.membership

import com.crashlytics.android.Crashlytics
import io.ipoli.android.common.AppSideEffectHandler
import io.ipoli.android.common.AppState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.store.membership.error.SubscriptionError
import io.ipoli.android.store.membership.usecase.CalculateMembershipPlanPriceUseCase
import io.ipoli.android.store.membership.usecase.UpdatePlayerMembershipUseCase
import io.ipoli.android.store.purchase.SubscriptionManager
import space.traversal.kapsule.required

/**i
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/16/18.
 */
object MembershipSideEffectHandler : AppSideEffectHandler() {
    private val updatePlayerMembershipUseCase by required { updatePlayerMembershipUseCase }
    private val calculateMembershipPlanPriceUseCase by required { calculateMembershipPlanPriceUseCase }

    private lateinit var subscriptionManager: SubscriptionManager

    override suspend fun doExecute(action: Action, state: AppState) {
        when(action) {
            is MembershipAction.Load -> {
                subscriptionManager = action.subscriptionManager
                subscriptionManager.loadInventory { product, activeSkus ->
                    val monthlySku = product.getSku(MembershipPlan.MONTHLY.sku)!!
                    val yearlySku = product.getSku(MembershipPlan.YEARLY.sku)!!
                    val quarterlySku = product.getSku(MembershipPlan.QUARTERLY.sku)!!
                    dispatch(
                        MembershipAction.Loaded(
                            monthlyPrice = calculateMembershipPlanPriceUseCase.execute(
                                CalculateMembershipPlanPriceUseCase.Params(
                                    monthlySku.detailedPrice.amount,
                                    monthlySku.detailedPrice.currency,
                                    1
                                )
                            ),
                            yearlyPrice = calculateMembershipPlanPriceUseCase.execute(
                                CalculateMembershipPlanPriceUseCase.Params(
                                    yearlySku.detailedPrice.amount,
                                    yearlySku.detailedPrice.currency,
                                    12
                                )
                            ),
                            quarterlyPrice = calculateMembershipPlanPriceUseCase.execute(
                                CalculateMembershipPlanPriceUseCase.Params(
                                    quarterlySku.detailedPrice.amount,
                                    quarterlySku.detailedPrice.currency,
                                    3
                                )
                            ),
                            activeSkus = activeSkus
                        )
                    )
                }
            }

            is MembershipAction.GoPremium -> {
                if (action.activeSkus.isNotEmpty()) {
                    subscriptionManager.changeSubscription(action.plan.sku, action.activeSkus,
                        { startDate, expirationDate ->
                            updatePlayerMembershipUseCase.execute(
                                UpdatePlayerMembershipUseCase.Params(
                                    plan = action.plan,
                                    purchasedDate = startDate,
                                    expirationDate = expirationDate
                                )
                            )
                            dispatch(MembershipAction.Subscribed(action.plan, setOf(action.plan.sku)))
                        },
                        { message, exception ->
                            Crashlytics.logException(
                                SubscriptionError(
                                    "Change subscription error message: $message",
                                    exception
                                )
                            )
                            dispatch(MembershipAction.SubscriptionError)
                        }
                    )
                } else {
                    subscriptionManager.subscribe(action.plan.sku,
                        { purchaseDate ->
                            updatePlayerMembershipUseCase.execute(
                                UpdatePlayerMembershipUseCase.Params(
                                    purchasedDate = purchaseDate,
                                    plan = action.plan
                                )
                            )
                            dispatch(MembershipAction.Subscribed(action.plan, setOf(action.plan.sku)))
                        },
                        { responseCode, exception ->
                            SubscriptionError(
                                "Subscribe with response code $responseCode",
                                exception
                            )
                            dispatch(MembershipAction.SubscriptionError)
                        }
                    )
                }
            }
        }
    }

    override fun canHandle(action: Action) = action is MembershipAction
}