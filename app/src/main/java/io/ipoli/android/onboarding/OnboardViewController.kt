package io.ipoli.android.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.BaseViewStateReducer
import io.ipoli.android.common.mvi.ViewState
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.anim.TypewriterTextAnimator
import io.ipoli.android.common.view.enterFullScreen
import io.ipoli.android.onboarding.OnboardViewState.StateType.INITIAL
import io.ipoli.android.onboarding.OnboardViewState.StateType.NEXT_PAGE
import kotlinx.android.synthetic.main.controller_add_repeating_quest.view.*
import kotlinx.android.synthetic.main.controller_onboard_avatar.view.*
import kotlinx.android.synthetic.main.controller_onboard_story.view.*

sealed class OnboardAction : Action {
    object ShowNext : OnboardAction()
}

data class OnboardViewState(val type: StateType, val adapterPosition: Int) : ViewState {
    enum class StateType {
        INITIAL,
        NEXT_PAGE
    }
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
            else -> subState
        }
    }

    override fun defaultState() =
        OnboardViewState(
            INITIAL,
            adapterPosition = 0
        )

    override val stateKey = key<OnboardViewState>()
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
        enterFullScreen()
        val view = container.inflate(R.layout.controller_onboard)
        return view
    }

    override fun render(state: OnboardViewState, view: View) {
        when (state.type) {
            NEXT_PAGE,
            INITIAL -> {
                changeChildController(
                    view = view,
                    adapterPosition = state.adapterPosition,
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

            view.storyNext.dispatchOnClick(OnboardAction.ShowNext)
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


            Glide.with(view.context).load(R.drawable.avatar_01)
                .apply(RequestOptions.circleCropTransform())
                .into(view.avatar)
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