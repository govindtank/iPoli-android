package io.ipoli.android.store.gem

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import io.ipoli.android.BillingConstants
import io.ipoli.android.R
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.player.inventory.InventoryViewController
import io.ipoli.android.store.gem.GemStoreViewState.StateType.*
import io.ipoli.android.store.purchase.AndroidInAppPurchaseManager
import io.ipoli.android.store.purchase.GemPackType
import io.ipoli.android.store.purchase.InAppPurchaseManager
import kotlinx.android.synthetic.main.controller_gem_store.view.*
import kotlinx.android.synthetic.main.view_inventory_toolbar.view.*
import org.solovyev.android.checkout.Billing
import org.solovyev.android.checkout.Checkout
import org.solovyev.android.checkout.UiCheckout

/**
 * Created by Venelin Valkov <venelin@io.ipoli.io>
 * on 27.12.17.
 */
class GemStoreViewController(args: Bundle? = null) :
    ReduxViewController<GemStoreAction, GemStoreViewState, GemStoreReducer>(
        args
    ) {

    override val reducer = GemStoreReducer

    private lateinit var checkout: UiCheckout
    private lateinit var inAppPurchaseManager: InAppPurchaseManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = inflater.inflate(R.layout.controller_gem_store, container, false)

        setToolbar(view.toolbar)
        view.toolbarTitle.setText(R.string.gem_store)

        val billing = Billing(activity!!, object : Billing.DefaultConfiguration() {
            override fun getPublicKey() =
                BillingConstants.APP_PUBLIC_KEY
        })

        setChildController(
            view.playerGems,
            InventoryViewController(showCurrencyConverter = false)
        )

        checkout = Checkout.forActivity(activity!!, billing)
        checkout.start()
        inAppPurchaseManager = createPurchaseManager(checkout)

        registerForActivityResult(AndroidInAppPurchaseManager.PURCHASE_REQUEST_CODE)

        view.mostPopular.setCompoundDrawablesWithIntrinsicBounds(
            IconicsDrawable(view.context)
                .icon(GoogleMaterial.Icon.gmd_favorite)
                .colorRes(R.color.md_white)
                .sizeDp(24),
            null, null, null
        )

        return view
    }

    override fun onCreateLoadAction() =
        GemStoreAction.Load(createPurchaseManager(checkout))

    private fun createPurchaseManager(checkout: UiCheckout): InAppPurchaseManager =
        AndroidInAppPurchaseManager(checkout, activity!!.resources)

    override fun onAttach(view: View) {
        super.onAttach(view)
        showBackButton()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            router.popCurrentController()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        checkout.stop()
        super.onDestroy()
    }

    override fun render(state: GemStoreViewState, view: View) {

        when (state.type) {
            PLAYER_CHANGED ->
                if (state.isGiftPurchased)
                    showMostPopular(view)

            GEM_PACKS_LOADED ->
                state.gemPacks.forEach {
                    val gp = it
                    when (it.type) {
                        GemPackType.BASIC -> {
                            view.basicPackPrice.text = it.price
                            view.basicPackTitle.text = it.title
                            view.basicPackGems.text = "x ${it.gems}"
                            view.basicPackBuy.onDebounceClick {
                                disableButtons()
                                dispatch(GemStoreAction.BuyGemPack(gp))
                            }
                        }
                        GemPackType.SMART -> {
                            view.smartPackPrice.text = it.price
                            view.smartPackTitle.text = it.title
                            view.smartPackGems.text = "x ${it.gems}"
                            view.smartPackBuy.onDebounceClick  {
                                disableButtons()
                                dispatch(GemStoreAction.BuyGemPack(gp))
                            }
                        }
                        GemPackType.PLATINUM -> {
                            view.platinumPackPrice.text = it.price
                            view.platinumPackTitle.text = it.title
                            view.platinumPackGems.text = "x ${it.gems}"
                            view.platinumPackBuy.onDebounceClick {
                                disableButtons()
                                dispatch(GemStoreAction.BuyGemPack(gp))
                            }
                        }
                    }
                }

            GEM_PACK_PURCHASED -> {
                enableButtons()
                Toast.makeText(view.context, R.string.gem_pack_purchased, Toast.LENGTH_LONG).show()
            }

            DOG_UNLOCKED -> {
                showMostPopular(view)
                Toast.makeText(view.context, R.string.gift_unlocked, Toast.LENGTH_LONG).show()
            }

            PURCHASE_FAILED -> {
                enableButtons()
                Toast.makeText(view.context, R.string.purchase_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enableButtons() {
        view!!.basicPackBuy.enableClick()
        view!!.smartPackBuy.enableClick()
        view!!.platinumPackBuy.enableClick()
    }

    private fun disableButtons() {
        view!!.basicPackBuy.disableClick()
        view!!.smartPackBuy.disableClick()
        view!!.platinumPackBuy.disableClick()
    }

    private fun showMostPopular(view: View) {
        view.giftContainer.visibility = View.GONE
        view.mostPopular.visibility = View.VISIBLE
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        checkout.onActivityResult(requestCode, resultCode, data)
    }

}