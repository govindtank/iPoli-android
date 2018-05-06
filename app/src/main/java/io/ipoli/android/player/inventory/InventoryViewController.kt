package io.ipoli.android.player.inventory

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.DataLoadedAction
import io.ipoli.android.common.mvi.ViewState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.CurrencyConverterDialogController
import io.ipoli.android.common.view.gone
import kotlinx.android.synthetic.main.controller_inventory.view.*
import java.util.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 01/28/2018.
 */

sealed class InventoryViewState : ViewState {
    object Loading : InventoryViewState()
    data class Changed(val gems: Int, val coins: Int) : InventoryViewState()
}

class InventoryReducer(private val someKey: String) : BaseViewStateReducer<InventoryViewState>() {

    override val stateKey = key<InventoryViewState>() + someKey

    override fun reduce(
        state: AppState,
        subState: InventoryViewState,
        action: Action
    ) =
        when (action) {
            LoadInventory -> {
                val p = state.dataState.player
                p?.let {
                    InventoryViewState.Changed(it.gems, it.coins)
                } ?: InventoryViewState.Loading
            }

            is DataLoadedAction.PlayerChanged -> {
                val p = action.player
                InventoryViewState.Changed(p.gems, p.coins)
            }

            else -> subState
        }

    override fun defaultState() = InventoryViewState.Loading
}

object LoadInventory : Action

class InventoryViewController :
    ReduxViewController<LoadInventory, InventoryViewState, InventoryReducer> {

    private var showCurrencyConverter: Boolean = true
    private var showGems: Boolean = true
    private var showCoins: Boolean = false

    override val reducer = InventoryReducer(UUID.randomUUID().toString())

    constructor(args: Bundle? = null) : super(args)

    constructor(
        showCurrencyConverter: Boolean,
        showCoins: Boolean = false,
        showGems: Boolean = true
    ) : super() {
        this.showCurrencyConverter = showCurrencyConverter
        this.showGems = showGems
        this.showCoins = showCoins
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.controller_inventory, container, false)

        if (!showGems) {
            view.inventoryGems.gone()
        }

        if (!showCoins) {
            view.inventoryCoins.gone()
        }

        if (!(showCoins && showGems)) {
            view.inventorySpace.gone()
        }

        if (showCurrencyConverter) {
            view.onDebounceClick {
                CurrencyConverterDialogController().show(
                    parentController!!.router,
                    "currency-converter"
                )
            }
        } else {
            view.setOnClickListener(null)
        }
        return view
    }

    override fun onCreateLoadAction() = LoadInventory

    override fun render(state: InventoryViewState, view: View) {
        when (state) {
            is InventoryViewState.Changed -> {
                view.inventoryGems.text = state.gems.toString()
                view.inventoryCoins.text = state.coins.toString()
            }
        }
    }
}