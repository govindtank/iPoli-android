package io.ipoli.android.onboarding

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.v4.content.ContextCompat
import android.support.v4.widget.TintableCompoundButton
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.github.florent37.tutoshowcase.TutoShowcase
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.mvi.ViewState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.anim.TypewriterTextAnimator
import io.ipoli.android.onboarding.OnboardViewState.StateType.*
import io.ipoli.android.pet.AndroidPetAvatar
import io.ipoli.android.player.data.AndroidAvatar
import io.ipoli.android.player.data.Avatar
import io.ipoli.android.quest.schedule.calendar.dayview.view.widget.CalendarDayView
import io.ipoli.android.quest.schedule.calendar.dayview.view.widget.CalendarEvent
import io.ipoli.android.quest.schedule.calendar.dayview.view.widget.ScheduledEventsAdapter
import kotlinx.android.synthetic.main.calendar_hour_cell.view.*
import kotlinx.android.synthetic.main.controller_onboard.view.*
import kotlinx.android.synthetic.main.controller_onboard_avatar.view.*
import kotlinx.android.synthetic.main.controller_onboard_first_quest.view.*
import kotlinx.android.synthetic.main.controller_onboard_pet.view.*
import kotlinx.android.synthetic.main.controller_onboard_story.view.*
import kotlinx.android.synthetic.main.item_calendar_quest.view.*
import kotlinx.android.synthetic.main.popup_quest_complete.view.*
import kotlinx.android.synthetic.main.view_default_toolbar.view.*
import java.util.*

sealed class OnboardAction : Action {
    data class SelectAvatar(val index: Int) : OnboardAction()
    object ShowNext : OnboardAction()
}

object OnboardReducer : BaseViewStateReducer<OnboardViewState>() {

    override fun reduce(
        state: AppState,
        subState: OnboardViewState,
        action: Action
    ): OnboardViewState {
        return when (action) {
            OnboardAction.ShowNext -> {
                subState.copy(
                    type = NEXT_PAGE,
                    adapterPosition = subState.adapterPosition + 1
                )
            }

            is OnboardAction.SelectAvatar -> {
                subState.copy(
                    type = AVATAR_SELECTED,
                    avatar = subState.avatars[action.index]
                )
            }

            else -> subState
        }
    }

    override fun defaultState() =
        OnboardViewState(
            INITIAL,
            adapterPosition = 0,
            avatar = Avatar.AVATAR_03,
            avatars = listOf(
                Avatar.AVATAR_03,
                Avatar.AVATAR_02,
                Avatar.AVATAR_01,
                Avatar.AVATAR_04,
                Avatar.AVATAR_05,
                Avatar.AVATAR_06,
                Avatar.AVATAR_07,
                Avatar.AVATAR_11
            )
        )

    override val stateKey = key<OnboardViewState>()
}

data class OnboardViewState(
    val type: StateType,
    val adapterPosition: Int,
    val avatar: Avatar,
    val avatars: List<Avatar>
) : ViewState {
    enum class StateType {
        INITIAL,
        NEXT_PAGE,
        AVATAR_SELECTED
    }
}

class OnboardViewController(args: Bundle? = null) :
    ReduxViewController<OnboardAction, OnboardViewState, OnboardReducer>(
        args
    ) {

    override val reducer = OnboardReducer

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
//        enterFullScreen()
        val view = container.inflate(R.layout.controller_onboard)
        return view
    }

    override fun render(state: OnboardViewState, view: View) {
        when (state.type) {
            NEXT_PAGE,
            INITIAL -> {
                changeChildController(
                    view = view,
//                    adapterPosition = state.adapterPosition,
                    adapterPosition = 3,
                    animate = false
                )
            }
        }
    }

    private fun changeChildController(
        view: View,
        adapterPosition: Int,
        animate: Boolean = true
    ) {
        val childRouter = getChildRouter(view.onboardPager)

        val changeHandler = if (animate) HorizontalChangeHandler() else null

        val transaction = RouterTransaction.with(
            createControllerForPosition(adapterPosition)
        )
            .popChangeHandler(changeHandler)
            .pushChangeHandler(changeHandler)
        childRouter.pushController(transaction)
    }

    private fun createControllerForPosition(position: Int): Controller =
        when (position) {
            STORY_INDEX -> StoryViewController()
            AVATAR_INDEX -> AvatarViewController()
            PET_INDEX -> PetViewController()
            ADD_QUEST_INDEX -> FirstQuestViewController()
            else -> throw IllegalArgumentException("Unknown controller position $position")
        }

    class StoryViewController(args: Bundle? = null) :
        BaseViewController<OnboardAction, OnboardViewState>(
            args
        ) {

        override val stateKey = OnboardReducer.stateKey

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup,
            savedViewState: Bundle?
        ): View {
            val view = container.inflate(R.layout.controller_onboard_story)
            view.storyTrees.setAnimation("onboarding_trees.json")
            view.storyStars.setAnimation("onboarding_stars.json")
            view.storySnail.setAnimation("onboarding_snail.json")
            view.storyTrees.playAnimation()
            view.storyStars.playAnimation()
            view.storySnail.playAnimation()

            view.storyNext.dispatchOnClick { OnboardAction.ShowNext }
            return view
        }

        override fun onAttach(view: View) {
            super.onAttach(view)
            TypewriterTextAnimator.of(
                view.storyText,
                "The days were getting darker. Procrastination started winning more and more battles. One day, something really mesmerising came down from the clouds."
            ).start()

        }

        override fun render(state: OnboardViewState, view: View) {

        }

    }

    class AvatarViewController(args: Bundle? = null) :
        BaseViewController<OnboardAction, OnboardViewState>(
            args
        ) {

        override val stateKey = OnboardReducer.stateKey

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup,
            savedViewState: Bundle?
        ): View {
            val view = container.inflate(R.layout.controller_onboard_avatar)
            view.avatarButterflies.setAnimation("onboarding_butterflies.json")
            view.avatarSun.setAnimation("onboarding_sun.json")
            view.avatarTrees.setAnimation("onboarding_avatar_trees.json")
            view.avatarButterflies.playAnimation()
            view.avatarSun.playAnimation()
            view.avatarTrees.playAnimation()

            view.avatarNext.dispatchOnClick { OnboardAction.ShowNext }

            return view
        }

        override fun onAttach(view: View) {
            super.onAttach(view)
            TypewriterTextAnimator.of(
                view.avatarText,
                "The days were getting darker. Procrastination started winning more and more battles. One day, something really mesmerising came down from the clouds."
            ).start()

        }

        override fun render(state: OnboardViewState, view: View) {
            view.avatarImage.setImageResource(AndroidAvatar.valueOf(state.avatar.name).image)

            val avatarViews =
                view.topAvatarsContainer.children + view.bottomAvatarsContainer.children

            state.avatars.forEachIndexed { index, avatar ->
                val v = avatarViews[index] as ImageView
                v.setImageResource(AndroidAvatar.valueOf(avatar.name).image)
                v.dispatchOnClick { OnboardAction.SelectAvatar(index) }
            }

        }

    }

    class PetViewController(args: Bundle? = null) :
        BaseViewController<OnboardAction, OnboardViewState>(
            args
        ) {

        override val stateKey = OnboardReducer.stateKey

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup,
            savedViewState: Bundle?
        ): View {
            val view = container.inflate(R.layout.controller_onboard_pet)
            view.petSea.setAnimation("onboarding_pet_sea.json")
            view.petSun.setAnimation("onboarding_sun.json")
            view.petSea.playAnimation()
            view.petSun.playAnimation()

//            view.storyNext.dispatchOnClick(OnboardAction.ShowNext)
            return view
        }

        override fun onAttach(view: View) {
            super.onAttach(view)
//            TypewriterTextAnimator.of(
//                view.storyText,
//                "The days were getting darker. Procrastination started winning more and more battles. One day, something really mesmerising came down from the clouds."
//            ).start()

        }

        override fun render(state: OnboardViewState, view: View) {

        }

    }

    class FirstQuestCompletePopup(
        @DrawableRes private val petImage: Int,
        private val earnedXP: Int,
        private val earnedCoins: Int
    ) : Popup(
        position = Popup.Position.BOTTOM,
        isAutoHide = false,
        overlayBackground = null
    ) {
        override fun createView(inflater: LayoutInflater): View {
            val view = inflater.inflate(R.layout.popup_quest_complete, null)

            return view
        }

        override fun onViewShown(contentView: View) {
            super.onViewShown(contentView)

            contentView.pet.setImageResource(petImage)
            startTypingAnimation(contentView)
        }

        private fun startTypingAnimation(contentView: View) {
            val title = contentView.message
            val message = "You`re a natural"
            val typewriterAnim = TypewriterTextAnimator.of(title, message)
            typewriterAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    startEarnedRewardAnimation(contentView)
                }
            })
            typewriterAnim.start()
        }

        private fun startEarnedRewardAnimation(contentView: View) {

            val xpAnim = ValueAnimator.ofInt(0, earnedXP)
            xpAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    contentView.earnedXP.visible = true
                }
            })
            xpAnim.addUpdateListener {
                contentView.earnedXP.text = "${it.animatedValue}"
            }

            val coinsAnim = ValueAnimator.ofInt(0, earnedCoins)

            coinsAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    contentView.earnedCoins.visible = true
                }
            })

            coinsAnim.addUpdateListener {
                contentView.earnedCoins.text = "${it.animatedValue}"
            }

            val anim = AnimatorSet()
            anim.duration = 300
            anim.playSequentially(xpAnim, coinsAnim)

            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {

                }
            })

            anim.start()
        }

    }

    class FirstQuestViewController(args: Bundle? = null) :
        BaseViewController<OnboardAction, OnboardViewState>(
            args
        ) {

        override val stateKey = OnboardReducer.stateKey

        private val nameWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.length > 2) {
                    view!!.saveQuest.visible()
                } else {
                    view!!.saveQuest.gone()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }

        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup,
            savedViewState: Bundle?
        ): View {
            val view = container.inflate(R.layout.controller_onboard_first_quest)

            view.saveQuest.setImageDrawable(
                IconicsDrawable(activity!!)
                    .icon(GoogleMaterial.Icon.gmd_send)
                    .color(attrData(R.attr.colorAccent))
                    .sizeDp(24)
                    .respectFontBounds(true)
            )
            view.saveQuest.onDebounceClick {
                view.addQuestContainer.gone()
                view.addContainerBackground.gone()
                ViewUtils.hideKeyboard(view)

                val eventsAdapter =
                    OnboardQuestAdapter(
                        activity!!,
                        mutableListOf(
                            OnboardQuestViewModel(
                                "",
                                view.firstQuestName.text.toString(),
                                60,
                                Time.now().toMinuteOfDay()
                            )
                        )
                    )
                view.calendar.setScheduledEventsAdapter(eventsAdapter)

                view.calendar.postDelayed({
                    val showcase = TutoShowcase.from(activity!!)
                    showcase
                        .setContentView(R.layout.view_onboard_complete_quest)
                        .on(R.id.calendarQuestContainer)
                        .addRoundRect()
                        .withBorder()
                        .onClick {
                            showcase.dismiss()
                            view.checkBox.isChecked = true


                            FirstQuestCompletePopup(
                                AndroidPetAvatar.BEAR.headImage,
                                Constants.DEFAULT_PLAYER_XP.toInt(),
                                Constants.DEFAULT_PLAYER_COINS
                            ).show(view.context)
                        }
                        .show()
                }, 500)
            }

            view.firstQuestName.addTextChangedListener(nameWatcher)

            setToolbar(view.toolbar)
            toolbarTitle = "My amazing day"

            view.calendar.setHourAdapter(object : CalendarDayView.HourCellAdapter {
                override fun bind(view: View, hour: Int) {
                    if (hour > 0) {
                        view.timeLabel.text = hour.toString() + ":00"
                    }
                }
            })
            view.calendar.scrollToNow()


            return view
        }

        override fun onAttach(view: View) {
            super.onAttach(view)
//            activity!!.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
            val showcase = TutoShowcase.from(activity!!)
            showcase
                .setContentView(R.layout.view_onboard_calendar)
                .on(R.id.addQuest)
                .addCircle()
                .withBorder()
                .onClick {
                    showcase.dismiss()
                    view.addQuest.gone()
                    view.addQuestContainer.visible()
                    view.addContainerBackground.visible()
                    view.firstQuestName.requestFocus()
                    ViewUtils.showKeyboard(view.context, view.firstQuestName)
                }
                .show()
        }

        override fun render(state: OnboardViewState, view: View) {

        }

        data class OnboardQuestViewModel(
            override val id: String,
            val name: String,
            override val duration: Int,
            override val startMinute: Int
        ) : CalendarEvent {

            val startTime: String get() = Time.of(startMinute).toString()

            val endTime: String get() = Time.of(startMinute).plus(duration).toString()
        }

        inner class OnboardQuestAdapter(
            context: Context,
            events: MutableList<OnboardQuestViewModel>
        ) :
            ScheduledEventsAdapter<OnboardQuestViewModel>(
                context,
                R.layout.item_calendar_quest,
                events
            ) {
            override fun bindView(view: View, position: Int) {
                val vm = getItem(position)
                view.checkBox.visible()
                view.backgroundView.setBackgroundColor(colorRes(R.color.md_green_500))

                view.questName.text = vm.name
                view.questName.setTextColor(colorRes(R.color.md_white))

                view.questSchedule.text = "${vm.startTime} - ${vm.endTime}"
                view.questSchedule.setTextColor(colorRes(R.color.md_light_text_70))

                view.questIcon.visible = true
                view.questIcon.setImageDrawable(
                    IconicsDrawable(context).normalIcon(
                        CommunityMaterial.Icon.cmd_duck,
                        R.color.md_green_200
                    )
                )

                view.questCategoryIndicator.setBackgroundResource(R.color.md_green_900)

                (view.checkBox as TintableCompoundButton).supportButtonTintList =
                    ContextCompat.getColorStateList(context, R.color.md_green_200)
                view.completedBackgroundView.invisible()
                view.repeatIndicator.gone()
                view.challengeIndicator.gone()

                view.checkBox.setOnCheckedChangeListener { cb, checked ->
                    if (checked) {
                        (view.checkBox as TintableCompoundButton).supportButtonTintList =
                            ContextCompat.getColorStateList(context, R.color.md_grey_700)
                        val anim = RevealAnimator().create(view.completedBackgroundView, cb)
                        anim.addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator?) {
                                view.questCategoryIndicator.setBackgroundResource(R.color.md_grey_400)
                                view.completedBackgroundView.visibility = View.VISIBLE

                                view.questIcon.setImageDrawable(
                                    IconicsDrawable(context).normalIcon(
                                        CommunityMaterial.Icon.cmd_duck,
                                        R.color.md_dark_text_38
                                    )
                                )
                            }

                            override fun onAnimationEnd(animation: Animator?) {

                            }

                        })
                        anim.start()
                    }
                }
            }

            override fun adaptViewForHeight(adapterView: View, height: Float) {
            }

            override fun rescheduleEvent(position: Int, startTime: Time, duration: Int) {
            }

        }
    }

    companion object {
        const val STORY_INDEX = 0
        const val AVATAR_INDEX = 1
        const val PET_INDEX = 2
        const val ADD_QUEST_INDEX = 3
        const val PICK_REPEATING_QUESTS_INDEX = 4
    }

}