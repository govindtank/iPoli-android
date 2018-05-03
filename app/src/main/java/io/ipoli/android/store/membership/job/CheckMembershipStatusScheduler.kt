package io.ipoli.android.store.membership.job

import com.evernote.android.job.DailyJob
import com.evernote.android.job.JobRequest
import io.ipoli.android.BillingConstants
import io.ipoli.android.Constants
import io.ipoli.android.common.api.Api
import io.ipoli.android.common.datetime.isBetween
import io.ipoli.android.common.di.Module
import io.ipoli.android.myPoliApp
import io.ipoli.android.player.Membership
import io.ipoli.android.store.membership.usecase.RemoveMembershipUseCase
import io.ipoli.android.store.powerup.usecase.EnableAllPowerUpsUseCase
import io.ipoli.android.store.purchase.AndroidSubscriptionManager
import kotlinx.coroutines.experimental.runBlocking
import org.solovyev.android.checkout.*
import org.threeten.bp.LocalDate
import space.traversal.kapsule.Injects
import space.traversal.kapsule.Kapsule
import java.util.concurrent.TimeUnit
import kotlin.coroutines.experimental.suspendCoroutine

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/23/2018.
 */
class CheckMembershipStatusJob : DailyJob(), Injects<Module> {

    override fun onRunDailyJob(params: Params): DailyJobResult {
        val kap = Kapsule<Module>()
        val playerRepository by kap.required { playerRepository }
        val removeMembershipUseCase by kap.required { removeMembershipUseCase }
        val enableAllPowerUpsUseCase by kap.required { enableAllPowerUpsUseCase }
        kap.inject(myPoliApp.module(context))

        val p = playerRepository.find()
        requireNotNull(p)

        if (p!!.membership == Membership.NONE) {
            return DailyJobResult.SUCCESS
        }

        val billing = Billing(context, object : Billing.DefaultConfiguration() {
            override fun getPublicKey() = BillingConstants.APP_PUBLIC_KEY
        })

        val checkout = Checkout.forApplication(billing)
        checkout.start()

        runBlocking {
            checkMembershipStatus(checkout, removeMembershipUseCase, enableAllPowerUpsUseCase)
        }

        return DailyJobResult.SUCCESS
    }

    private suspend fun checkMembershipStatus(
        checkout: Checkout,
        removeMembershipUseCase: RemoveMembershipUseCase,
        enableAllPowerUpsUseCase: EnableAllPowerUpsUseCase
    ) {
        val activePurchase = loadActivePurchase(checkout)

        if (activePurchase == null) {
            removeMembershipUseCase.execute(Unit)
        } else {
            val status = Api.getMembershipStatus(activePurchase.sku, activePurchase.token)
            if (status.isAutoRenewing) {
                updatePowerUpsExpirationDate(status, enableAllPowerUpsUseCase)
            }
        }
    }

    private fun updatePowerUpsExpirationDate(
        status: Api.MembershipStatus,
        enableAllPowerUpsUseCase: EnableAllPowerUpsUseCase
    ) {
        val gracePeriodStart =
            status.expirationDate.minusDays((Constants.POWER_UP_GRACE_PERIOD_DAYS - 1).toLong())
        if (isInGracePeriod(gracePeriodStart, status.expirationDate)) {
            enableAllPowerUpsUseCase.execute(EnableAllPowerUpsUseCase.Params(status.expirationDate))
        } else {
            enableAllPowerUpsUseCase.execute(
                EnableAllPowerUpsUseCase.Params(
                    status.expirationDate.minusDays(
                        Constants.POWER_UP_GRACE_PERIOD_DAYS.toLong()
                    )
                )
            )
        }
    }

    private fun isInGracePeriod(
        gracePeriodStart: LocalDate,
        expirationDate: LocalDate
    ) = LocalDate.now().isBetween(gracePeriodStart, expirationDate)

    private suspend fun loadActivePurchase(checkout: Checkout) =
        suspendCoroutine<Purchase?> { continuation ->
            checkout.loadInventory(
                Inventory.Request.create().loadAllPurchases()
                    .loadSkus(ProductTypes.SUBSCRIPTION, AndroidSubscriptionManager.SKUS)
            ) { products ->
                val subscriptions = products.get(ProductTypes.SUBSCRIPTION)
                continuation.resume(getActivePurchase(subscriptions.purchases))
            }
        }

    private fun getActivePurchase(purchases: List<Purchase>) =
        purchases.firstOrNull { it.state == Purchase.State.PURCHASED }

    companion object {
        const val TAG = "check_membership_status_tag"
    }
}

class AndroidCheckMembershipStatusScheduler : CheckMembershipStatusScheduler {
    override fun schedule() {
        DailyJob.schedule(
            JobRequest.Builder(CheckMembershipStatusJob.TAG)
                .setUpdateCurrent(true)
                .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                .setRequirementsEnforced(true),
            0,
            TimeUnit.HOURS.toMillis(1)
        )
    }
}

interface CheckMembershipStatusScheduler {
    fun schedule()
}