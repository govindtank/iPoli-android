package io.ipoli.android.challenge.add

import android.graphics.PorterDuff
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.R
import io.ipoli.android.challenge.add.EditChallengeViewState.StateType.*
import io.ipoli.android.common.redux.android.BaseViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.tag.widget.EditItemAutocompleteTagAdapter
import io.ipoli.android.tag.widget.EditItemTagAdapter
import kotlinx.android.synthetic.main.controller_add_challenge_name.view.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 3/8/18.
 */
class AddChallengeNameViewController(args: Bundle? = null) :
    BaseViewController<EditChallengeAction, EditChallengeViewState>(
        args
    ) {
    override val stateKey = EditChallengeReducer.stateKey

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = inflater.inflate(R.layout.controller_add_challenge_name, container, false)

        view.challengeDifficulty.background.setColorFilter(
            colorRes(R.color.md_white),
            PorterDuff.Mode.SRC_ATOP
        )
        view.challengeDifficulty.adapter = ArrayAdapter<String>(
            view.context,
            R.layout.item_add_challenge_difficulty_item,
            R.id.spinnerItemId,
            view.resources.getStringArray(R.array.difficulties)
        )

        view.challengeDifficulty.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {
                    }

                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        dispatch(EditChallengeAction.ChangeDifficulty(position))
                    }

                }

        view.challengeTagList.layoutManager = LinearLayoutManager(activity!!)
        view.challengeTagList.adapter = EditItemTagAdapter(removeTagCallback = {
            dispatch(EditChallengeAction.RemoveTag(it))
        })
        return view
    }

    override fun onCreateLoadAction() = EditChallengeAction.LoadTags

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.next_wizard_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            R.id.actionNext -> {
                dispatch(EditChallengeAction.ValidateName(view!!.challengeName.text.toString()))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }

    override fun render(state: EditChallengeViewState, view: View) {
        view.challengeName.setText(state.name)
        renderColor(view, state)
        renderIcon(view, state)
        when (state.type) {
            TAGS_CHANGED -> {
                renderTags(view, state)
            }

            VALIDATION_ERROR_EMPTY_NAME -> {
                view.challengeName.error = stringRes(R.string.think_of_a_name)
            }

            VALIDATION_NAME_SUCCESSFUL -> {
                dispatch(EditChallengeAction.ShowNext)
            }
        }
    }

    override fun colorLayoutBars() {}

    private fun renderTags(
        view: View,
        state: EditChallengeViewState
    ) {
        (view.challengeTagList.adapter as EditItemTagAdapter).updateAll(state.tagViewModels)
        val add = view.addChallengeTag
        if (state.maxTagsReached) {
            add.gone()
            view.maxTagsMessage.visible()
        } else {
            add.visible()
            view.maxTagsMessage.gone()

            val adapter = EditItemAutocompleteTagAdapter(state.tags, activity!!)
            add.setAdapter(adapter)
            add.setOnItemClickListener { _, _, position, _ ->
                dispatch(EditChallengeAction.AddTag(adapter.getItem(position).name))
                add.setText("")
            }
            add.threshold = 0
            add.setOnTouchListener { v, event ->
                add.showDropDown()
                false
            }
        }
    }

    private fun renderIcon(
        view: View,
        state: EditChallengeViewState
    ) {
        view.challengeSelectedIcon.setImageDrawable(
            IconicsDrawable(view.context)
                .largeIcon(state.iicon)
        )

        view.challengeIcon.onDebounceClick {
            IconPickerDialogController({ icon ->
                dispatch(EditChallengeAction.ChangeIcon(icon))
            }, state.icon).show(
                router,
                "pick_icon_tag"
            )

        }
    }

    private fun renderColor(
        view: View,
        state: EditChallengeViewState
    ) {
        colorLayout(view, state)
        view.challengeColor.onDebounceClick {
            ColorPickerDialogController({
                dispatch(EditChallengeAction.ChangeColor(it))
            }, state.color).show(
                router,
                "pick_color_tag"
            )
        }
    }

    private fun colorLayout(
        view: View,
        state: EditChallengeViewState
    ) {
        view.challengeDifficulty.setPopupBackgroundResource(state.color.androidColor.color500)
    }

    private val EditChallengeViewState.iicon: IIcon
        get() = icon?.androidIcon?.icon ?: GoogleMaterial.Icon.gmd_local_florist

    private val EditChallengeViewState.tagViewModels: List<EditItemTagAdapter.TagViewModel>
        get() = challengeTags.map {
            EditItemTagAdapter.TagViewModel(
                name = it.name,
                icon = it.icon?.androidIcon?.icon ?: MaterialDesignIconic.Icon.gmi_label,
                tag = it
            )
        }
}