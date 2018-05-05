package io.ipoli.android.common.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import io.ipoli.android.BillingConstants
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.mvi.ViewState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.view.CurrencyConverterViewState.Type.*
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.pet.PetAvatar
import io.ipoli.android.player.Player
import io.ipoli.android.player.usecase.ConvertCoinsToGemsUseCase
import io.ipoli.android.store.gem.GemStoreViewController
import io.ipoli.android.store.purchase.AndroidInAppPurchaseManager
import io.ipoli.android.store.purchase.GemPack
import io.ipoli.android.store.purchase.GemPackType
import io.ipoli.android.store.purchase.InAppPurchaseManager
import kotlinx.android.synthetic.main.dialog_currency_converter.view.*
import kotlinx.android.synthetic.main.view_currency_converter_dialog_header.view.*
import org.solovyev.android.checkout.ActivityCheckout
import org.solovyev.android.checkout.Billing
import org.solovyev.android.checkout.Checkout

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 12/21/17.
 */

sealed class CurrencyConverterAction : Action {
    data class Load(val purchaseManager: InAppPurchaseManager) : CurrencyConverterAction()
    data class ChangeConvertAmount(val gems: Int) : CurrencyConverterAction()
    data class Convert(val gems: Int) : CurrencyConverterAction()
    data class ConvertTransactionComplete(val result: ConvertCoinsToGemsUseCase.Result) :
        CurrencyConverterAction()
}

data class CurrencyConverterViewState(
    val type: Type,
    val petAvatar: PetAvatar? = null,
    val playerCoins: Int = 0,
    val playerGems: Int = 0,
    val maxGemsToConvert: Int = 0,
    val convertCoins: Int = 0,
    val convertGems: Int = 0,
    val enableConvert: Boolean = false,
    val exchangeRateCoins: Int = 0,
    val gemPacks: List<GemPack> = listOf()
) : ViewState {
    enum class Type {
        LOADING,
        DATA_CHANGED,
        CONVERT_DEAL_CHANGED,
        GEMS_CONVERTED,
        GEMS_TOO_EXPENSIVE,
        GEM_PACKS_LOADED
    }
}

object CurrencyConverterReducer : BaseViewStateReducer<CurrencyConverterViewState>() {
    override fun reduce(
        state: AppState,
        subState: CurrencyConverterViewState,
        action: Action
    ) = when (action) {

        is CurrencyConverterAction.Load -> {
            val player = state.dataState.player!!
            createPlayerChangeState(subState, player)
        }

        is DataLoadedAction.PlayerChanged -> {
            createPlayerChangeState(subState, action.player)
        }

        is DataLoadedAction.GemPacksLoaded ->
            subState.copy(
                type = GEM_PACKS_LOADED,
                gemPacks = action.gemPacks
            )

        is CurrencyConverterAction.ChangeConvertAmount ->
            subState.copy(
                type = CONVERT_DEAL_CHANGED,
                convertCoins = action.gems * Constants.GEM_COINS_PRICE,
                convertGems = action.gems,
                enableConvert = action.gems > 0
            )

        is CurrencyConverterAction.ConvertTransactionComplete -> {
            val type = when (action.result) {
                is ConvertCoinsToGemsUseCase.Result.TooExpensive -> GEMS_TOO_EXPENSIVE
                is ConvertCoinsToGemsUseCase.Result.GemsConverted -> GEMS_CONVERTED
            }
            subState.copy(
                type = type
            )
        }

        else -> subState
    }

    private fun createPlayerChangeState(
        subState: CurrencyConverterViewState,
        player: Player
    ): CurrencyConverterViewState {

        val maxGemsToBuy = player.coins / Constants.GEM_COINS_PRICE

        return subState.copy(
            type = DATA_CHANGED,
            petAvatar = player.pet.avatar,
            playerCoins = player.coins,
            playerGems = player.gems,
            maxGemsToConvert = maxGemsToBuy,
            convertCoins = maxGemsToBuy * Constants.GEM_COINS_PRICE,
            convertGems = maxGemsToBuy,
            enableConvert = maxGemsToBuy > 0,
            exchangeRateCoins = Constants.GEM_COINS_PRICE
        )
    }

    override fun defaultState() = CurrencyConverterViewState(LOADING)

    override val stateKey = key<CurrencyConverterViewState>()
}

class CurrencyConverterDialogController :
    ReduxDialogController<CurrencyConverterAction, CurrencyConverterViewState, CurrencyConverterReducer>() {

    override val reducer = CurrencyConverterReducer

    private lateinit var checkout: ActivityCheckout

    override fun createHeaderView(inflater: LayoutInflater): View =
        inflater.inflate(R.layout.view_currency_converter_dialog_header, null)

    override fun onHeaderViewCreated(headerView: View) {
        headerView.dialogHeaderTitle.setText(R.string.currency_converter_title)
    }

    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_currency_converter, null)
        view.basicPackContainer.onDebounceClick {
            showGemStore()
        }
        view.smartPackContainer.onDebounceClick {
            showGemStore()
        }
        view.platinumPackContainer.onDebounceClick {
            showGemStore()
        }

        val billing = Billing(activity!!, object : Billing.DefaultConfiguration() {
            override fun getPublicKey() =
                BillingConstants.APP_PUBLIC_KEY
        })

        checkout = Checkout.forActivity(activity!!, billing)
        checkout.start()
        return view
    }

    override fun onCreateDialog(
        dialogBuilder: AlertDialog.Builder,
        contentView: View,
        savedViewState: Bundle?
    ): AlertDialog =
        dialogBuilder
            .setNegativeButton(R.string.done, null)
            .create()

    override fun onCreateLoadAction() =
        CurrencyConverterAction.Load(AndroidInAppPurchaseManager(checkout, activity!!.resources))

    override fun render(state: CurrencyConverterViewState, view: View) {
        when (state.type) {
            DATA_CHANGED -> {
                changeIcon(AndroidPetAvatar.valueOf(state.petAvatar!!.name).headImage)
                dialog.findViewById<TextView>(R.id.headerCoins)!!.text =
                        state.playerCoins.toString()
                dialog.findViewById<TextView>(R.id.headerGems)!!.text = state.playerGems.toString()
                view.coins.text = state.convertCoins.toString()
                view.gems.text = state.convertGems.toString()
                view.exchangeRateCoins.text = state.exchangeRateCoins.toString()

                view.seekBar.max = state.maxGemsToConvert
                view.seekBar.progress = state.maxGemsToConvert

                view.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            dispatch(CurrencyConverterAction.ChangeConvertAmount(progress))
                        }
                    }

                    override fun onStartTrackingTouch(p0: SeekBar?) {
                    }

                    override fun onStopTrackingTouch(p0: SeekBar?) {
                    }

                })

                enableConvertButton(view, state.enableConvert)

                view.convert.setOnClickListener {
                    playConvertAnimation(
                        view,
                        { dispatch(CurrencyConverterAction.Convert(view.seekBar.progress)) })
                }

            }

            GEM_PACKS_LOADED -> {
                state.gemPacks.forEach {
                    when (it.type) {
                        GemPackType.BASIC -> {
                            view.basicPackPrice.text = it.price
                            view.basicPackTitle.text = it.shortTitle
                        }
                        GemPackType.SMART -> {
                            view.smartPackPrice.text = it.price
                            view.smartPackTitle.text = it.shortTitle
                        }
                        GemPackType.PLATINUM -> {
                            view.platinumPackPrice.text = it.price
                            view.platinumPackTitle.text = it.shortTitle
                        }
                    }
                }
            }

            CONVERT_DEAL_CHANGED -> {
                view.coins.text = state.convertCoins.toString()
                view.gems.text = state.convertGems.toString()
                enableConvertButton(view, state.enableConvert)
            }

            GEMS_CONVERTED -> {

            }

            GEMS_TOO_EXPENSIVE -> {
                Toast.makeText(
                    view.context,
                    stringRes(R.string.iconvert_gems_not_enough_coins),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun enableConvertButton(view: View, enabled: Boolean) {
        view.convert.isEnabled = enabled
        if (enabled) {
            view.convert.setTextColor(colorRes(R.color.md_white))
        } else {
            view.convert.setTextColor(colorRes(R.color.md_dark_text_38))
        }
    }

    private fun playConvertAnimation(view: View, endListener: () -> Unit) {
        view.convert.visibility = View.INVISIBLE

        val duration: Long = 1600

        val rotationAnim = createRotationAnimation(view, duration)
        val lifeCoinAnim = createLifeCoinAnimation(view, duration / 2, duration / 4)
        lifeCoinAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                playGemAlphaAnimation(view, duration / 4)
            }
        })

        val set = AnimatorSet()
        set.playTogether(rotationAnim, lifeCoinAnim)

        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                view.rotationImage.visibility = View.VISIBLE
                view.lifeCoin.visibility = View.VISIBLE
            }

            override fun onAnimationEnd(animation: Animator?) {
                view.rotationImage.visibility = View.INVISIBLE
                view.lifeCoin.visibility = View.INVISIBLE
                view.lifeCoin.alpha = 1f

                playGemTranslationAnimation(view, endListener)
            }
        })

        set.start()
    }

    private fun playGemAlphaAnimation(view: View, duration: Long) {
        val gemAlphaAnim = ObjectAnimator.ofFloat(view.gem, "alpha", 0f, 1f)
        gemAlphaAnim.duration = duration
        gemAlphaAnim.start()
    }

    private fun createLifeCoinAnimation(view: View, duration: Long, delay: Long): ObjectAnimator {
        val lifeCoinAlphaAnim = ObjectAnimator.ofFloat(view.lifeCoin, "alpha", 0f, 1f, 0f, 1f, 0f)
        lifeCoinAlphaAnim.startDelay = delay
        lifeCoinAlphaAnim.duration = duration
        lifeCoinAlphaAnim.interpolator = AccelerateDecelerateInterpolator()
        return lifeCoinAlphaAnim
    }

    private fun createRotationAnimation(view: View, duration: Long): ObjectAnimator {
        val rotationAnim = ObjectAnimator.ofFloat(view.rotationImage, "rotation", 0f, 720f)
        rotationAnim.duration = duration
        return rotationAnim
    }

    private fun playGemTranslationAnimation(view: View, endListener: () -> Unit) {
        val originalX = view.gem.x
        val originalY = view.gem.y

        val x = ObjectAnimator.ofFloat(
            view.gem,
            "x",
            view.width.toFloat() - 2 * dialog.findViewById<TextView>(R.id.headerGems)!!.width
        )
        val y = ObjectAnimator.ofFloat(view.gem, "y", 0f)
        val alpha = ObjectAnimator.ofFloat(view.gem, "alpha", 1f, 0f)

        val set = AnimatorSet()
        set.playTogether(x, y, alpha)
        set.duration = longAnimTime

        set.addListener(object : AnimatorListenerAdapter() {

            override fun onAnimationEnd(animation: Animator?) {
                endListener()
                view.convert.visibility = View.VISIBLE
                view.gem.x = originalX
                view.gem.y = originalY
            }
        })
        set.start()
    }

    private fun showGemStore() {
        dismiss()
        val handler = FadeChangeHandler()
        router.pushController(
            RouterTransaction.with(GemStoreViewController())
                .pushChangeHandler(handler)
                .popChangeHandler(handler)
        )

    }

    override fun onDestroy() {
        checkout.stop()
        super.onDestroy()
    }
}