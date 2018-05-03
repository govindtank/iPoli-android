package io.ipoli.android.store.membership

import android.content.Intent
import android.content.IntentSender
import android.content.res.ColorStateList
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.annotation.StringRes
import android.support.transition.TransitionManager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import com.bluelinelabs.conductor.RestoreViewOnCreateController
import kotlinx.android.synthetic.main.controller_membership.view.*
import kotlinx.android.synthetic.main.view_no_elevation_toolbar.view.*
import io.ipoli.android.BillingConstants
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.LoaderDialogController
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.store.membership.MembershipViewState.StateType.*
import io.ipoli.android.store.purchase.AndroidSubscriptionManager
import io.ipoli.android.store.purchase.SubscriptionManager
import org.solovyev.android.checkout.Billing
import org.solovyev.android.checkout.Checkout
import org.solovyev.android.checkout.IntentStarter
import org.solovyev.android.checkout.UiCheckout

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/16/18.
 */
class MembershipViewController(args: Bundle? = null) :
    ReduxViewController<MembershipAction, MembershipViewState, MembershipReducer>(args) {

    override val reducer = MembershipReducer

    private lateinit var checkout: UiCheckout
    private lateinit var subscriptionManager: SubscriptionManager

    private var loader: LoaderDialogController? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = inflater.inflate(R.layout.controller_membership, container, false)
        setToolbar(view.toolbar)

        val billing = Billing(activity!!, object : Billing.DefaultConfiguration() {
            override fun getPublicKey() =
                BillingConstants.APP_PUBLIC_KEY
        })

        checkout = Checkout.forUi(
            MyIntentStarter(
                this
            ), this, billing
        )
        checkout.start()
        subscriptionManager = AndroidSubscriptionManager(checkout)

        return view
    }

    inner class MyIntentStarter(
        private val controller: RestoreViewOnCreateController
    ) : IntentStarter {

        @Throws(IntentSender.SendIntentException::class)
        override fun startForResult(intentSender: IntentSender, requestCode: Int, intent: Intent) {
            controller.registerForActivityResult(requestCode)
            controller.startIntentSenderForResult(
                intentSender,
                requestCode,
                intent,
                0,
                0,
                0,
                null
            )
        }
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        toolbarTitle = stringRes(R.string.membership_store_title)
        showBackButton()
    }

    override fun colorLayoutBars() {
        activity?.window?.statusBarColor = colorRes(R.color.md_blue_900)
        activity?.window?.navigationBarColor = colorRes(R.color.md_blue_500)
        view!!.toolbar.setBackgroundColor(colorRes(R.color.md_blue_800))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            return router.handleBack()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateLoadAction() =
        MembershipAction.Load(subscriptionManager)

    private fun createLoader() =
        LoaderDialogController()

    private fun hideLoader() {
        loader?.dismiss()
        loader = null
    }

    private fun showLoader() {
        loader = createLoader()
        loader!!.show(router, "loader")
    }

    override fun render(state: MembershipViewState, view: View) {
        hideLoader()
        when (state.type) {
            DATA_CHANGED -> {

                view.membershipCurrentPlan.visible = state.showCurrentPlan
                view.goPremium.visible = !state.showCurrentPlan

                TransitionManager.beginDelayedTransition(view as ViewGroup)
                colorLayout(view, state)
                view.membershipMostPopular.visibleOrGone(state.showMostPopular)
                view.membershipTitle.setText(state.androidMembershipPlan.title)

                renderReasons(view, state)
                renderPrices(view, state)

                view.monthlyPlanContainer.dispatchOnClick(MembershipAction.SelectPlan(io.ipoli.android.store.membership.MembershipPlan.MONTHLY))
                view.yearlyPlanContainer.dispatchOnClick(MembershipAction.SelectPlan(io.ipoli.android.store.membership.MembershipPlan.YEARLY))
                view.quarterlyPlanContainer.dispatchOnClick(MembershipAction.SelectPlan(io.ipoli.android.store.membership.MembershipPlan.QUARTERLY))

                if (!state.showCurrentPlan) {
                    playGoPremiumAnimation(view)
                    view.goPremium.isEnabled = true
                    view.goPremium.text = state.premiumButtonText
                    view.goPremium.dispatchOnClickAndExec(
                        MembershipAction.GoPremium(state.selectedPlan, state.activeSkus),
                        {
                            view.goPremium.isEnabled = false
                        }
                    )
                }
            }

            SUBCRIPTON_IN_PROGRESS -> {
                showLoader()
            }

            SUBSCRIBED -> {
                showShortToast(R.string.premium_player)
                view.membershipCurrentPlan.visible = state.showCurrentPlan
                view.goPremium.visible = !state.showCurrentPlan
            }

            SUBSCRIPTION_ERROR -> showShortToast(R.string.something_went_wrong)
        }

    }

    private fun playGoPremiumAnimation(view: ViewGroup) {
        view.goPremium.startAnimation(
            AnimationUtils.loadAnimation(
                view.context,
                R.anim.shake
            )
        )
    }

    private fun renderPrices(view: View, state: MembershipViewState) {
        view.membershipMonthlyPrice.text = state.monthlyPlanPrice
        view.membershipYearlyPrice.text = state.yearlyPlanPrice
        view.membershipQuarterlyPrice.text = state.quarterlyPlanPrice
    }

    private fun renderReasons(view: View, state: MembershipViewState) {
        val plan = state.androidMembershipPlan
        view.membershipMonthlyReasons.visibleOrGone(plan == AndroidMembershipPlan.MONTHLY)
        view.membershipYearlyReasons.visibleOrGone(plan == AndroidMembershipPlan.YEARLY)
        view.membershipQuarterlyReasons.visibleOrGone(plan == AndroidMembershipPlan.QUARTERLY)
        view.membershipMonthlyFreeTrial.text = stringRes(
            R.string.membership_benefit_free_trial,
            Constants.POWER_UPS_TRIAL_PERIOD_DAYS
        )
        view.membershipYearlyFreeTrial.text = stringRes(
            R.string.membership_benefit_free_trial,
            Constants.POWER_UPS_TRIAL_PERIOD_DAYS
        )
        view.membershipQuarterlyFreeTrial.text = stringRes(
            R.string.membership_benefit_free_trial,
            Constants.POWER_UPS_TRIAL_PERIOD_DAYS
        )
    }

    private fun colorLayout(view: View, state: MembershipViewState) {
        val plan = AndroidMembershipPlan.valueOf(state.selectedPlan.name)
        activity?.window?.statusBarColor = colorRes(plan.darkerColor)
        val lighterColor = colorRes(plan.lighterColor)
        activity?.window?.navigationBarColor = lighterColor
        view.toolbar.setBackgroundColor(colorRes(plan.color))
        view.membershipHeaderBackground.setBackgroundColor(colorRes(plan.color))
        view.membershipHeader.setBackgroundColor(lighterColor)
        view.goPremium.backgroundTintList = ColorStateList.valueOf(lighterColor)

        val (selectedContainer, selectedLayout) = when (plan) {
            AndroidMembershipPlan.MONTHLY -> Pair(view.monthlyPlanContainer, view.monthlyPlanLayout)
            AndroidMembershipPlan.YEARLY -> Pair(view.yearlyPlanContainer, view.yearlyPlanLayout)
            AndroidMembershipPlan.QUARTERLY -> Pair(
                view.quarterlyPlanContainer,
                view.quarterlyPlanLayout
            )
        }
        resetCards(view)

        val white = colorRes(R.color.md_white)
        selectedContainer.setCardBackgroundColor(lighterColor)
        selectedLayout.children.forEach {
            (it as TextView).setTextColor(white)
        }
    }

    private fun resetCards(view: View) {
        val defaultBackground = attrResourceId(android.R.attr.background)
        view.monthlyPlanContainer.setCardBackgroundColor(defaultBackground)
        view.yearlyPlanContainer.setCardBackgroundColor(defaultBackground)
        view.quarterlyPlanContainer.setCardBackgroundColor(defaultBackground)

        val black = colorRes(R.color.md_black)
        view.monthlyPlanLayout.children.forEach {
            (it as TextView).setTextColor(black)
        }
        view.yearlyPlanLayout.children.forEach {
            (it as TextView).setTextColor(black)
        }
        view.quarterlyPlanLayout.children.forEach {
            (it as TextView).setTextColor(black)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        checkout.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        checkout.stop()
        super.onDestroy()
    }

    private val MembershipViewState.showMostPopular: Boolean
        get() = selectedPlan == io.ipoli.android.store.membership.MembershipPlan.YEARLY

    private val MembershipViewState.androidMembershipPlan: AndroidMembershipPlan
        get() = AndroidMembershipPlan.valueOf(selectedPlan.name)

    private val MembershipViewState.premiumButtonText: String
        get() =
            if (currentPlan != null)
                stringRes(R.string.upgrade_now)
            else
                stringRes(R.string.go_premium)

    enum class AndroidMembershipPlan(
        @StringRes val title: Int,
        @ColorRes val lighterColor: Int,
        @ColorRes val color: Int,
        @ColorRes val darkerColor: Int
    ) {
        MONTHLY(
            R.string.monthly_plan,
            R.color.md_red_500,
            R.color.md_red_800,
            R.color.md_red_900
        ),
        YEARLY(
            R.string.yearly_plan,
            R.color.md_blue_500,
            R.color.md_blue_800,
            R.color.md_blue_900
        ),
        QUARTERLY(
            R.string.quarterly_plan,
            R.color.md_green_500,
            R.color.md_green_800,
            R.color.md_green_900
        )
    }
}