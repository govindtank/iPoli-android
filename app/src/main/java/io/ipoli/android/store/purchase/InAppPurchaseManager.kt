package io.ipoli.android.store.purchase

import android.content.res.Resources
import io.ipoli.android.R
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.solovyev.android.checkout.*
import java.util.*


/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 12/28/17.
 */
enum class GemPackType {
    BASIC, SMART, PLATINUM
}

data class GemPack(
    val title: String,
    val shortTitle: String,
    val price: String,
    val gems: Int,
    val type: GemPackType
)

interface InAppPurchaseManager {

    interface PurchaseListener {
        fun onPurchased()
        fun onError()
    }

    fun loadAll(cb: (List<GemPack>) -> Unit)

    fun purchase(gemPack: GemPackType, purchaseListener: PurchaseListener)
}

class AndroidInAppPurchaseManager(
    private val checkout: UiCheckout,
    private val resources: Resources
) : InAppPurchaseManager {

    companion object {

        val PURCHASE_REQUEST_CODE = 321

        val GEM_PACK_TYPE_TO_SKU = mapOf(
            GemPackType.BASIC to "gems_8",
            GemPackType.SMART to "gems_15",
            GemPackType.PLATINUM to "gems_28"
        )

        val GEM_PACK_TYPE_TO_GEMS = mapOf(
            GemPackType.BASIC to 8,
            GemPackType.SMART to 15,
            GemPackType.PLATINUM to 28
        )
    }

    override fun loadAll(cb: (List<GemPack>) -> Unit) {

        val skus = GEM_PACK_TYPE_TO_SKU.values.toList()

        launch(UI) {
            checkout.loadInventory(
                Inventory.Request.create()
                    .loadAllPurchases()
                    .loadSkus(ProductTypes.IN_APP, skus)
            ) { products ->
                val appProducts = products.get(ProductTypes.IN_APP)

                consumeAlreadyPurchased(appProducts)

                val gemPacks = GEM_PACK_TYPE_TO_SKU.map { (k, v) ->
                    val sku = appProducts.getSku(v)!!
                    val gems = GEM_PACK_TYPE_TO_GEMS[k]!!

                    val titleRes = when (k) {
                        GemPackType.BASIC -> R.string.gem_pack_basic_title
                        GemPackType.SMART -> R.string.gem_pack_smart_title
                        GemPackType.PLATINUM -> R.string.gem_pack_platinum_title
                    }

                    val shortTitleRes = when (k) {
                        GemPackType.BASIC -> R.string.gem_pack_basic_title_short
                        GemPackType.SMART -> R.string.gem_pack_smart_title_short
                        GemPackType.PLATINUM -> R.string.gem_pack_platinum_title_short
                    }

                    GemPack(
                        resources.getString(titleRes),
                        resources.getString(shortTitleRes),
                        sku.price,
                        gems,
                        k
                    )
                }

                cb(gemPacks)
            }
        }
    }

    private fun consumeAlreadyPurchased(appProducts: Inventory.Product) {
        appProducts.purchases.forEach {
            if (it.state == Purchase.State.PURCHASED) {
                checkout.whenReady(object : Checkout.EmptyListener() {
                    override fun onReady(requests: BillingRequests) {
                        requests.consume(it.token, object : RequestListener<Any> {
                            override fun onSuccess(result: Any) {
                            }

                            override fun onError(response: Int, e: java.lang.Exception) {
                            }

                        })
                    }
                })
            }
        }
    }

    override fun purchase(
        gemPack: GemPackType,
        purchaseListener: InAppPurchaseManager.PurchaseListener
    ) {
        val payload = UUID.randomUUID().toString()

        launch(UI) {
            doPurchase(gemPack, payload, purchaseListener)
        }
    }

    private fun doPurchase(
        gemPack: GemPackType,
        payload: String,
        purchaseListener: InAppPurchaseManager.PurchaseListener
    ) {
        checkout.whenReady(object : Checkout.EmptyListener() {
            override fun onReady(requests: BillingRequests) {
                requests.purchase(
                    ProductTypes.IN_APP,
                    GEM_PACK_TYPE_TO_SKU[gemPack]!!,
                    payload,
                    checkout.createOneShotPurchaseFlow(
                        PURCHASE_REQUEST_CODE,
                        object : RequestListener<Purchase> {
                            override fun onSuccess(purchase: Purchase) {

                                requests.consume(purchase.token, object : RequestListener<Any> {

                                    override fun onError(response: Int, e: Exception) {
                                        launch {
                                            purchaseListener.onError()
                                        }
                                    }

                                    override fun onSuccess(result: Any) {
                                        launch {
                                            purchaseListener.onPurchased()
                                        }
                                    }
                                })
                            }

                            override fun onError(response: Int, e: Exception) {
                                launch {
                                    purchaseListener.onError()
                                }
                            }
                        })
                )
            }
        })
    }
}