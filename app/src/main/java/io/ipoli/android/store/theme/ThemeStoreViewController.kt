package io.ipoli.android.store.theme

import android.content.res.ColorStateList
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.annotation.ColorInt
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.pager.BasePagerAdapter
import io.ipoli.android.player.Theme
import io.ipoli.android.player.inventory.InventoryViewController
import io.ipoli.android.quest.schedule.calendar.dayview.view.widget.CalendarDayView
import kotlinx.android.synthetic.main.calendar_hour_cell.view.*
import kotlinx.android.synthetic.main.calendar_time_line.view.*
import kotlinx.android.synthetic.main.controller_theme_store.view.*
import kotlinx.android.synthetic.main.item_theme_store.view.*
import kotlinx.android.synthetic.main.view_inventory_toolbar.view.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 12/12/17.
 */
class ThemeStoreViewController(args: Bundle? = null) :
    ReduxViewController<ThemeStoreAction, ThemeStoreViewState, ThemeStoreReducer>(args) {

    override val reducer = ThemeStoreReducer

    private val onPageChangeListener = object : ViewPager.OnPageChangeListener {
        override fun onPageScrollStateChanged(state: Int) {}

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
        }

        override fun onPageSelected(position: Int) {
            val vm =
                (view!!.themePager.adapter as ThemePagerAdapter).itemAt(position)
            colorLayout(vm)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {

        setHasOptionsMenu(true)
        val view = inflater.inflate(R.layout.controller_theme_store, container, false)

        setChildController(
            view.playerGems,
            InventoryViewController()
        )

        view.themePager.addOnPageChangeListener(onPageChangeListener)

        view.themePager.clipToPadding = false
        view.themePager.pageMargin = ViewUtils.dpToPx(16f, view.context).toInt()

        view.themePager.adapter = ThemePagerAdapter()
        return view
    }

    override fun onCreateLoadAction() = ThemeStoreAction.Load

    override fun onAttach(view: View) {
        super.onAttach(view)
        setToolbar(view.toolbar)
        showBackButton()
        view.toolbarTitle.setText(R.string.themes)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            return router.handleBack()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroyView(view: View) {
        view.themePager.removeOnPageChangeListener(onPageChangeListener)
        super.onDestroyView(view)
    }

    private fun colorLayout(viewModel: ThemeViewModel) {
        view!!.toolbar.setBackgroundColor(viewModel.primaryColor)
        activity?.window?.navigationBarColor = viewModel.primaryColor
        activity?.window?.statusBarColor = viewModel.primaryColorDark
    }

    override fun render(state: ThemeStoreViewState, view: View) {
        when (state) {

            is ThemeStoreViewState.Changed -> {
                val adapter = view.themePager.adapter as ThemePagerAdapter
                adapter.updateAll(state.viewModels)
                val vm = adapter.itemAt(view.themePager.currentItem)
                colorLayout(vm)
            }

            ThemeStoreViewState.ThemeBought ->
                showLongToast(R.string.theme_bought)

            ThemeStoreViewState.ThemeTooExpensive -> {
                CurrencyConverterDialogController().show(router, "currency-converter")
                showShortToast(R.string.theme_too_expensive)
            }

            is ThemeStoreViewState.ThemeChanged -> {
                val pm = PreferenceManager.getDefaultSharedPreferences(activity!!)
                pm.registerOnSharedPreferenceChangeListener { _, key ->
                    if (key == Constants.KEY_THEME) {
                        activity!!.setTheme(AndroidTheme.valueOf(state.theme.name).style)
                    }
                }
                pm.edit().putString(Constants.KEY_THEME, state.theme.name).apply()
            }
        }
    }

    inner class ThemePagerAdapter :
        BasePagerAdapter<ThemeViewModel>() {
        override fun layoutResourceFor(item: ThemeViewModel) =
            R.layout.item_theme_store

        override fun bindItem(item: ThemeViewModel, view: View) {

            view.themeToolbar.setBackgroundColor(item.primaryColor)
            view.themeNavigationBar.setBackgroundColor(item.primaryColor)
            view.themeStatusBar.setBackgroundColor(item.primaryColorDark)
            view.themeFab.backgroundTintList = ColorStateList.valueOf(item.accentColor)

            view.themeName.text = item.name

            view.themePrice.text = if (item.theme.gemPrice == 0) {
                stringRes(R.string.free)
            } else {
                item.theme.gemPrice.toString()
            }

            val action = view.themeAction
            val current = view.themeCurrent

            when {
                item.isCurrent -> {
                    action.visible = false
                    current.visible = true
                }
                item.isBought -> {
                    action.visible = true
                    current.visible = false
                    action.text = stringRes(R.string.pick_me)
                    action.dispatchOnClick { ThemeStoreAction.Change(item.theme) }
                }
                else -> {
                    action.visible = true
                    current.visible = false
                    action.text = stringRes(R.string.store_buy_theme)
                    action.dispatchOnClick { ThemeStoreAction.Buy(item.theme) }
                }
            }

            view.themeCalendar.setHourAdapter(object : CalendarDayView.HourCellAdapter {
                override fun bind(view: View, hour: Int) {
                    if (hour > 0) {
                        view.timeLabel.text = hour.toString() + ":00"
                    }
                }
            })

            view.themeCalendar.timeLine.setBackgroundColor(item.accentColor)
            view.themeCalendar.timeLineIndicator.backgroundTintList =
                    ColorStateList.valueOf(item.accentColor)

            view.themeCalendar.scrollToNow()
        }
    }

    private val ThemeStoreViewState.Changed.viewModels
        get() = themes.map {

            val at = AndroidTheme.valueOf(it.theme.name)

            val attrs = intArrayOf(
                R.attr.colorPrimary,
                R.attr.colorPrimaryDark,
                R.attr.colorAccent
            ).sortedArray()
            val a = activity!!.theme.obtainStyledAttributes(
                at.style,
                attrs
            )

            val primaryColor = a.getResourceId(a.getIndex(attrs.indexOf(R.attr.colorPrimary)), 0)
            val primaryDarkColor =
                a.getResourceId(a.getIndex(attrs.indexOf(R.attr.colorPrimaryDark)), 0)
            val accentColor = a.getColor(a.getIndex(attrs.indexOf(R.attr.colorAccent)), 0)

            a.recycle()

            ThemeViewModel(
                theme = it.theme,
                name = stringRes(at.title),
                primaryColor = colorRes(primaryColor),
                primaryColorDark = colorRes(primaryDarkColor),
                accentColor = accentColor,
                isCurrent = it is ThemeItem.Current,
                isBought = it is ThemeItem.Bought
            )
        }

}

data class ThemeViewModel(
    val theme: Theme,
    val name: String,
    @ColorInt val primaryColor: Int,
    @ColorInt val primaryColorDark: Int,
    @ColorInt val accentColor: Int,
    val isBought: Boolean = false,
    val isCurrent: Boolean = false
)