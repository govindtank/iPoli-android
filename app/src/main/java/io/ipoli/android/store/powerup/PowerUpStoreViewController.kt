package io.ipoli.android.store.powerup

import android.Manifest
import android.os.Bundle
import android.support.annotation.ColorInt
import android.support.annotation.ColorRes
import android.support.annotation.StringRes
import android.support.transition.TransitionManager
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.RouterTransaction
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.ionicons_typeface_library.Ionicons
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.DateFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.pager.BasePagerAdapter
import io.ipoli.android.event.calendar.picker.CalendarPickerDialogController
import io.ipoli.android.player.inventory.InventoryViewController
import io.ipoli.android.store.membership.MembershipViewController
import kotlinx.android.synthetic.main.controller_power_up_store.view.*
import kotlinx.android.synthetic.main.item_disabled_power_up.view.*
import kotlinx.android.synthetic.main.item_enabled_power_up.view.*
import kotlinx.android.synthetic.main.view_inventory_toolbar.view.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/15/2018.
 */
enum class AndroidPowerUp(
    @StringRes val title: Int,
    @StringRes val subTitle: Int,
    @StringRes val longDescription: Int,
    val icon: IIcon,
    @ColorRes val backgroundColor: Int,
    @ColorRes val darkBackgroundColor: Int

) {
    REMINDERS(
        R.string.reminders,
        R.string.power_up_reminders_sub_title,
        R.string.power_up_reminders_long_desc,
        GoogleMaterial.Icon.gmd_notifications_active,
        R.color.md_green_600,
        R.color.md_green_800
    ),
    CHALLENGES(
        R.string.challenges,
        R.string.power_up_challenges_sub_title,
        R.string.power_up_challenges_long_desc,
        Ionicons.Icon.ion_ios_flag,
        R.color.md_deep_purple_300,
        R.color.md_deep_purple_400
    ),
    TAGS(
        R.string.power_up_tags_title,
        R.string.power_up_tags_sub_title,
        R.string.power_up_tags_long_desc,
        MaterialDesignIconic.Icon.gmi_labels,
        R.color.md_indigo_500,
        R.color.md_indigo_700
    ),
    CALENDAR_SYNC(
        R.string.settings_sync_google_calendars,
        R.string.power_up_sync_calendars_sub_title,
        R.string.power_up_sync_calendars_long_desc,
        GoogleMaterial.Icon.gmd_event_available,
        R.color.md_red_400,
        R.color.md_red_600
    ),
    TIMER(
        R.string.timer,
        R.string.power_up_timer_sub_title,
        R.string.power_up_timer_long_desc,
        GoogleMaterial.Icon.gmd_timer,
        R.color.md_teal_500,
        R.color.md_teal_700
    ),
    SUB_QUESTS(
        R.string.sub_quests,
        R.string.power_up_sub_quests_sub_title,
        R.string.power_up_sub_quests_long_desc,
        GoogleMaterial.Icon.gmd_list,
        R.color.md_pink_400,
        R.color.md_pink_600
    ),
    NOTES(
        R.string.notes,
        R.string.power_up_notes_sub_title,
        R.string.power_up_notes_long_desc,
        GoogleMaterial.Icon.gmd_note_add,
        R.color.md_blue_600,
        R.color.md_blue_800
    ),
    CUSTOM_DURATION(
        R.string.custom_duration,
        R.string.power_up_custom_duration_sub_title,
        R.string.power_up_custom_duration_long_desc,
        GoogleMaterial.Icon.gmd_timelapse,
        R.color.md_deep_orange_500,
        R.color.md_deep_orange_700
    ),
    GROWTH(
        R.string.custom_duration,
        R.string.power_up_custom_duration_sub_title,
        R.string.power_up_custom_duration_long_desc,
        GoogleMaterial.Icon.gmd_timelapse,
        R.color.md_deep_orange_500,
        R.color.md_deep_orange_700
    )
}

class PowerUpStoreViewController(args: Bundle? = null) :
    ReduxViewController<PowerUpStoreAction, PowerUpStoreViewState, PowerUpStoreReducer>(args) {

    override val reducer = PowerUpStoreReducer

    private val onPageChangeListener = object : ViewPager.OnPageChangeListener {
        override fun onPageScrollStateChanged(state: Int) {}

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) {
        }

        override fun onPageSelected(position: Int) {
            val vm = (view!!.powerUpPager.adapter as PowerUpAdapter).itemAt(position)
            colorLayout(vm)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = inflater.inflate(
            R.layout.controller_power_up_store, container, false
        )

        view.icon.setImageDrawable(
            IconicsDrawable(view.context)
                .icon(GoogleMaterial.Icon.gmd_card_membership)
                .color(attrData(R.attr.colorAccent))
                .sizeDp(32)
        )

        view.powerUpPager.addOnPageChangeListener(onPageChangeListener)

        view.powerUpPager.adapter = PowerUpAdapter()
        view.powerUpPager.clipToPadding = false
        view.powerUpPager.pageMargin = ViewUtils.dpToPx(8f, view.context).toInt()

        view.hide.setOnClickListener {
            TransitionManager.beginDelayedTransition(view.rootCoordinator as ViewGroup  )
            view.membershipHint.gone()
        }

        view.join.onDebounceClick {
            router.pushController(RouterTransaction.with(MembershipViewController()))
        }

        setChildController(
            view.playerGems,
            InventoryViewController(
                showCurrencyConverter = true,
                showCoins = true,
                showGems = false
            )
        )

        return view
    }

    override fun onCreateLoadAction() = PowerUpStoreAction.Load

    override fun onAttach(view: View) {
        super.onAttach(view)
        setToolbar(view.toolbar)
        showBackButton()
        view.toolbarTitle.text = stringRes(R.string.controller_power_up_store_title)
    }

    override fun onDestroyView(view: View) {
        view.powerUpPager.removeOnPageChangeListener(onPageChangeListener)
        super.onDestroyView(view)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            return router.handleBack()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun colorLayout(vm: PowerUpViewModel) {
        view!!.toolbar.setBackgroundColor(vm.backgroundColor)
        activity?.window?.navigationBarColor = vm.backgroundColor
        activity?.window?.statusBarColor = vm.darkBackgroundColor
    }

    override fun render(state: PowerUpStoreViewState, view: View) {
        when (state) {
            is PowerUpStoreViewState.Changed -> {
                val adapter = view.powerUpPager.adapter as PowerUpAdapter
                adapter.updateAll(state.powerUpViewModels)
                val vm = adapter.itemAt(view.powerUpPager.currentItem)
                colorLayout(vm)
            }

            is PowerUpStoreViewState.PowerUpBought -> {
                showLongToast(state.message)
                if (state.type == PowerUp.Type.CALENDAR_SYNC) {
                    requestPermissions(
                        mapOf(Manifest.permission.READ_CALENDAR to stringRes(R.string.allow_read_calendars_perm_reason)),
                        Constants.RC_CALENDAR_PERM
                    )
                }
            }

            PowerUpStoreViewState.PowerUpTooExpensive ->
                showShortToast(R.string.power_up_too_expensive)
        }
    }


    private fun showCalendarPicker() {
        CalendarPickerDialogController({ calendarIds ->
            dispatch(PowerUpStoreAction.SyncCalendarsSelected(calendarIds))
        }).show(router)
    }

    override fun onPermissionsGranted(requestCode: Int, permissions: List<String>) {
        showCalendarPicker()
    }

    sealed class PowerUpViewModel(
        open val icon: IIcon,
        @ColorInt open val backgroundColor: Int,
        @ColorInt open val darkBackgroundColor: Int,
        open val name: String,
        open val slogan: String,
        open val description: String
    ) {
        data class Enabled(
            override val icon: IIcon,
            @ColorInt override val backgroundColor: Int,
            @ColorInt override val darkBackgroundColor: Int,
            override val name: String,
            override val slogan: String,
            override val description: String,
            val expirationMessage: String
        ) : PowerUpViewModel(icon, backgroundColor, darkBackgroundColor, name, slogan, description)

        data class Disabled(
            val type: PowerUp.Type,
            override val icon: IIcon,
            @ColorInt override val backgroundColor: Int,
            @ColorInt override val darkBackgroundColor: Int,
            override val name: String,
            override val slogan: String,
            override val description: String,
            val coinPrice: String
        ) : PowerUpViewModel(icon, backgroundColor, darkBackgroundColor, name, slogan, description)
    }

    inner class PowerUpAdapter :
        BasePagerAdapter<PowerUpViewModel>() {

        override fun layoutResourceFor(
            item: PowerUpViewModel
        ): Int =
            when (item) {
                is PowerUpViewModel.Enabled ->
                    R.layout.item_enabled_power_up
                is PowerUpViewModel.Disabled ->
                    R.layout.item_disabled_power_up
            }

        override fun bindItem(item: PowerUpViewModel, view: View) {
            when (item) {
                is PowerUpViewModel.Enabled ->
                    bindEnabled(item, view)
                is PowerUpViewModel.Disabled ->
                    bindDisabled(item, view)
            }
        }

        private fun bindDisabled(
            item: PowerUpViewModel.Disabled,
            view: View
        ) {

            view.pImage.setImageDrawable(
                IconicsDrawable(view.context)
                    .icon(item.icon)
                    .colorRes(R.color.md_white)
                    .sizeDp(52)
            )
            view.pImageContainer.setCardBackgroundColor(item.backgroundColor)

            view.pName.text = item.name
            view.pSlogan.text = item.slogan
            view.pDescription.text = item.description
            view.pBuy.text = item.coinPrice

            view.pBuy.setOnClickListener {
                dispatch(PowerUpStoreAction.Enable(item.type))
            }
        }

        private fun bindEnabled(
            item: PowerUpViewModel.Enabled,
            view: View
        ) {
            view.eImage.setImageDrawable(
                IconicsDrawable(view.context)
                    .icon(item.icon)
                    .colorRes(R.color.md_white)
                    .sizeDp(52)
            )
            view.eImageContainer.setCardBackgroundColor(item.backgroundColor)

            view.eName.text = item.name
            view.eSlogan.text = item.slogan
            view.eDescription.text = item.description
            view.eExpiration.text = item.expirationMessage
        }
    }

    private val PowerUpStoreViewState.PowerUpBought.message: String
        get() = stringRes(
            R.string.power_up_bought,
            stringRes(AndroidPowerUp.valueOf(type.name).title)
        )

    private val PowerUpStoreViewState.Changed.powerUpViewModels
        get() = powerUps.map {
            when (it) {
                is PowerUpItem.Enabled -> {
                    val expirationMessage = if (it.showExpirationDate)
                        stringRes(
                            R.string.power_up_expires_on,
                            it.daysUntilExpiration,
                            DateFormatter.formatWithoutYear(
                                activity!!,
                                it.expirationDate
                            )
                        )
                    else
                        stringRes(
                            R.string.power_up_all_unlocked
                        )
                    val ap = AndroidPowerUp.valueOf(it.type.name)
                    PowerUpViewModel.Enabled(
                        ap.icon,
                        colorRes(ap.backgroundColor),
                        colorRes(ap.darkBackgroundColor),
                        stringRes(ap.title),
                        stringRes(ap.subTitle),
                        stringRes(ap.longDescription),
                        expirationMessage
                    )
                }

                is PowerUpItem.Disabled -> {
                    val ap = AndroidPowerUp.valueOf(it.type.name)
                    PowerUpViewModel.Disabled(
                        it.type,
                        ap.icon,
                        colorRes(ap.backgroundColor),
                        colorRes(ap.darkBackgroundColor),
                        stringRes(ap.title),
                        stringRes(ap.subTitle),
                        stringRes(ap.longDescription),
                        it.coinPrice.toString() + " *"
                    )
                }

            }

        }
}