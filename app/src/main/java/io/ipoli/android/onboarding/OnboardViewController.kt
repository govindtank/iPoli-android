package io.ipoli.android.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.github.florent37.tutoshowcase.TutoShowcase
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.mvi.ViewState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.anim.TypewriterTextAnimator
import io.ipoli.android.onboarding.OnboardViewState.StateType.*
import io.ipoli.android.player.data.AndroidAvatar
import io.ipoli.android.player.data.Avatar
import kotlinx.android.synthetic.main.controller_onboard.view.*
import kotlinx.android.synthetic.main.controller_onboard_first_quest.view.*
import kotlinx.android.synthetic.main.controller_onboard_avatar.view.*
import kotlinx.android.synthetic.main.controller_onboard_pet.view.*
import kotlinx.android.synthetic.main.controller_onboard_story.view.*
import timber.log.Timber

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
        val childRouter = getChildRouter(view.pager)

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

    class FirstQuestViewController(args: Bundle? = null) :
        BaseViewController<OnboardAction, OnboardViewState>(
            args
        ) {

        override val stateKey = OnboardReducer.stateKey

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup,
            savedViewState: Bundle?
        ): View {
            val view = container.inflate(R.layout.controller_onboard_first_quest)
            view.saveQuest.setImageDrawable(
                IconicsDrawable(activity!!).normalIcon(
                    GoogleMaterial.Icon.gmd_send,
                    R.color.md_dark_text_54
                ).respectFontBounds(true)
            )
            return view
        }

        override fun onAttach(view: View) {
            super.onAttach(view)
            activity!!.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
            val showcase = TutoShowcase.from(activity!!)
            showcase
//                .setFitsSystemWindows(true)
                .setContentView(R.layout.view_onboard_calendar)
                .on(R.id.addQuest)
                .addCircle()
                .withBorder()
                .onClick {
                    showcase.dismiss()
                    view.addQuest.gone()
                    view.addQuestContainer.visible()
                    view.saveQuest.visible()
                    view.addContainerBackground.visible()
                    view.questName.requestFocus()
                    ViewUtils.showKeyboard(view.context, view.questName)
                }
                .show()
        }

        override fun render(state: OnboardViewState, view: View) {

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