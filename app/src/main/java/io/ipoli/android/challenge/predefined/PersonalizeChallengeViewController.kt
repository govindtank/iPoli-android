package io.ipoli.android.challenge.predefined

import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import kotlinx.android.synthetic.main.controller_personalize_challenge.view.*
import kotlinx.android.synthetic.main.item_predefeined_challenge_quest.view.*
import io.ipoli.android.R
import io.ipoli.android.challenge.predefined.PersonalizeChallengeViewState.StateType.*
import io.ipoli.android.challenge.predefined.entity.AndroidPredefinedChallenge
import io.ipoli.android.challenge.predefined.entity.PredefinedChallenge
import io.ipoli.android.challenge.predefined.entity.PredefinedChallengeData
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*


/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 12/29/17.
 */
class PersonalizeChallengeViewController(args: Bundle? = null) :
    ReduxViewController<PersonalizeChallengeAction, PersonalizeChallengeViewState, PersonalizeChallengeReducer>(
        args
    ) {

    override val reducer = PersonalizeChallengeReducer

    private lateinit var challenge: PredefinedChallenge

    constructor(challenge: PredefinedChallenge) : this() {
        this.challenge = challenge
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = inflater.inflate(R.layout.controller_personalize_challenge, container, false)
        view.challengeQuestList.layoutManager =
                LinearLayoutManager(container.context, LinearLayoutManager.VERTICAL, false)
        val androidChallenge = AndroidPredefinedChallenge.valueOf(challenge.name)
        view.challengeBackgroundImage.setBackgroundResource(androidChallenge.backgroundImage)
        view.challengeImage.setBackgroundResource(androidChallenge.smallImage)
        view.collapsingToolbarContainer.title = stringRes(androidChallenge.title)

        view.challengeQuestList.adapter = ChallengeQuestAdapter()
        setToolbar(view.toolbar)
        view.acceptChallenge.dispatchOnClick { PersonalizeChallengeAction.Validate }
        return view
    }

    override fun onCreateLoadAction() =
        PersonalizeChallengeAction.Load(challenge)

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            router.handleBack()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onAttach(view: View) {
        showBackButton()
        super.onAttach(view)
        colorStatusBar(android.R.color.transparent)
    }

    private fun colorStatusBar(@ColorRes color: Int) {
        val window = activity!!.window

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = colorRes(color)
    }

    override fun render(state: PersonalizeChallengeViewState, view: View) {
        when (state.type) {
            DATA_LOADED -> {
                (view.challengeQuestList.adapter as ChallengeQuestAdapter).updateAll(state.viewModels)
            }

            VALIDATION_SUCCESSFUL -> {
                dispatch(PersonalizeChallengeAction.AcceptChallenge)
                showShortToast(R.string.challenge_accepted)
                router.popCurrentController()
            }

            VALIDATION_ERROR_NO_QUESTS_SELECTED -> {
                showShortToast(R.string.no_quest_selected)
            }

            else -> {
            }
        }
    }

    data class ChallengeQuestViewModel(
        val name: String,
        val isSelected: Boolean,
        val quest: PredefinedChallengeData.Quest
    )

    inner class ChallengeQuestAdapter(private var viewModels: List<ChallengeQuestViewModel> = listOf()) :
        RecyclerView.Adapter<ChallengeQuestAdapter.ViewHolder>() {
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val vm = viewModels[position]
            val itemView = holder.itemView
            itemView.dispatchOnClick {
                itemView.challengeQuestCheckbox.isChecked =
                        !itemView.challengeQuestCheckbox.isChecked
                PersonalizeChallengeAction.ToggleSelected(vm.quest)
            }


            itemView.challengeQuestCheckbox.isChecked = vm.isSelected
            itemView.challengeQuestCheckbox.setOnCheckedChangeListener { _, _ ->
                dispatch(PersonalizeChallengeAction.ToggleSelected(vm.quest))
            }
            itemView.challengeQuestName.text = vm.name
        }

        override fun getItemCount() = viewModels.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(
                LayoutInflater.from(parent.context).inflate(
                    R.layout.item_predefeined_challenge_quest,
                    parent,
                    false
                )
            )

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view)

        fun updateAll(viewModels: List<ChallengeQuestViewModel>) {
            this.viewModels = viewModels
            notifyDataSetChanged()
        }

    }

    private val PersonalizeChallengeViewState.viewModels: List<ChallengeQuestViewModel>
        get() = challenge!!.quests.map {
            when (it) {
                is PredefinedChallengeData.Quest.OneTime -> {
                    PersonalizeChallengeViewController.ChallengeQuestViewModel(
                        it.text,
                        it.isSelected,
                        it
                    )
                }

                is PredefinedChallengeData.Quest.Repeating -> {
                    PersonalizeChallengeViewController.ChallengeQuestViewModel(
                        it.text,
                        it.isSelected,
                        it
                    )
                }
            }
        }

}