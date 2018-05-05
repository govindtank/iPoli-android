package io.ipoli.android.repeatingquest.edit

import android.app.Dialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.view.inputmethod.EditorInfo
import com.mikepenz.google_material_typeface_library.GoogleMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.R
import io.ipoli.android.challenge.picker.ChallengePickerDialogController
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.DurationFormatter
import io.ipoli.android.common.text.RepeatPatternFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.note.NoteDialogViewController
import io.ipoli.android.quest.Color
import io.ipoli.android.quest.reminder.formatter.ReminderTimeFormatter
import io.ipoli.android.quest.reminder.picker.ReminderPickerDialogController
import io.ipoli.android.quest.reminder.picker.ReminderViewModel
import io.ipoli.android.quest.subquest.view.ReadOnlySubQuestAdapter
import io.ipoli.android.repeatingquest.add.EditRepeatingQuestAction
import io.ipoli.android.repeatingquest.add.EditRepeatingQuestReducer
import io.ipoli.android.repeatingquest.add.EditRepeatingQuestViewState
import io.ipoli.android.repeatingquest.edit.picker.RepeatPatternPickerDialogController
import io.ipoli.android.tag.widget.EditItemAutocompleteTagAdapter
import io.ipoli.android.tag.widget.EditItemTagAdapter
import kotlinx.android.synthetic.main.controller_add_repeating_quest_summary.view.*
import kotlinx.android.synthetic.main.controller_edit_repeating_quest.view.*
import kotlinx.android.synthetic.main.view_no_elevation_toolbar.view.*
import java.util.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 2/26/18.
 */
class EditRepeatingQuestViewController(args: Bundle? = null) :
    ReduxViewController<EditRepeatingQuestAction, EditRepeatingQuestViewState, EditRepeatingQuestReducer>(
        args, renderDuplicateStates = true
    ) {
    override val reducer = EditRepeatingQuestReducer

    private lateinit var repeatingQuestId: String

    private lateinit var newSubQuestWatcher: TextWatcher

    constructor(
        repeatingQuestId: String
    ) : this() {
        this.repeatingQuestId = repeatingQuestId
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        setHasOptionsMenu(true)
        val view = inflater.inflate(
            R.layout.controller_edit_repeating_quest, container, false
        )
        setToolbar(view.toolbar)
        toolbarTitle = ""

        view.summarySubQuestList.layoutManager = LinearLayoutManager(activity!!)
        view.summarySubQuestList.adapter =
                ReadOnlySubQuestAdapter(view.summarySubQuestList, useLightTheme = true)

        newSubQuestWatcher = object : TextWatcher {
            override fun afterTextChanged(editable: Editable) {
                if (editable.isBlank()) {
                    view.summaryAddSubQuest.invisible()
                } else {
                    view.summaryAddSubQuest.visible()
                }
            }

            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

            }
        }

        view.summarySubQuestName.addTextChangedListener(newSubQuestWatcher)

        view.summaryAddSubQuest.onDebounceClick {
            addSubQuest(view)
        }

        view.summarySubQuestName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addSubQuest(view)
            }
            true
        }

        view.summaryTagList.layoutManager = LinearLayoutManager(activity!!)
        view.summaryTagList.adapter = EditItemTagAdapter(removeTagCallback = {
            dispatch(EditRepeatingQuestAction.RemoveTag(it))
        })

        return view
    }

    private fun addSubQuest(view: View) {
        val name = view.summarySubQuestName.text.toString()
        dispatch(EditRepeatingQuestAction.AddSubQuest(name))
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        showBackButton()
        view.summaryContainer.requestFocus()
    }

    override fun onDestroyView(view: View) {
        view.summarySubQuestName.removeTextChangedListener(newSubQuestWatcher)
        super.onDestroyView(view)
    }

    override fun onCreateLoadAction() =
        EditRepeatingQuestAction.Load(repeatingQuestId)

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.edit_repeating_quest_menu, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        when (item.itemId) {
            android.R.id.home ->
                router.handleBack()

            R.id.actionSave -> {
                dispatch(EditRepeatingQuestAction.ValidateName(view!!.summaryName.text.toString()))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }


    override fun render(state: EditRepeatingQuestViewState, view: View) {

        view.summaryNameLayout.isErrorEnabled = false

        when (state.type) {

            EditRepeatingQuestViewState.StateType.SUMMARY_DATA_LOADED -> {
                view.summaryName.setText(state.name)
                renderTags(view, state)
                renderRepeatPattern(view, state)
                renderStartTime(view, state)
                renderDuration(view, state)
                renderReminder(view, state)
                renderIcon(view, state)
                renderColor(view, state)
                renderChallenge(view, state)
                renderNote(view, state)
            }

            EditRepeatingQuestViewState.StateType.TAGS_CHANGED ->
                renderTags(view, state)

            EditRepeatingQuestViewState.StateType.SUB_QUEST_ADDED -> {
                (view.summarySubQuestList.adapter as ReadOnlySubQuestAdapter).add(
                    ReadOnlySubQuestAdapter.ReadOnlySubQuestViewModel(
                        UUID.randomUUID().toString(),
                        state.newSubQuestName
                    )
                )
                view.summarySubQuestName.setText("")
                view.summarySubQuestName.requestFocus()
                view.summaryAddSubQuest.invisible()
            }

            EditRepeatingQuestViewState.StateType.REPEAT_PATTERN_CHANGED ->
                renderRepeatPattern(view, state)

            EditRepeatingQuestViewState.StateType.START_TIME_CHANGED ->
                renderStartTime(view, state)

            EditRepeatingQuestViewState.StateType.DURATION_CHANGED ->
                renderDuration(view, state)

            EditRepeatingQuestViewState.StateType.REMINDER_CHANGED ->
                renderReminder(view, state)

            EditRepeatingQuestViewState.StateType.COLOR_CHANGED ->
                renderColor(view, state)

            EditRepeatingQuestViewState.StateType.ICON_CHANGED ->
                renderIcon(view, state)

            EditRepeatingQuestViewState.StateType.CHALLENGE_CHANGED ->
                renderChallenge(view, state)

            EditRepeatingQuestViewState.StateType.NOTE_CHANGED ->
                renderNote(view, state)

            EditRepeatingQuestViewState.StateType.VALIDATION_ERROR_EMPTY_NAME -> {
                view.summaryNameLayout.isErrorEnabled = true
                view.summaryNameLayout.error = stringRes(R.string.name_validation)
            }

            EditRepeatingQuestViewState.StateType.VALID_NAME ->
                dispatch(EditRepeatingQuestAction.Save)

            EditRepeatingQuestViewState.StateType.CLOSE ->
                router.popController(this)

            else -> {
            }

        }
    }

    private fun renderTags(
        view: View,
        state: EditRepeatingQuestViewState
    ) {
        (view.summaryTagList.adapter as EditItemTagAdapter).updateAll(state.tagViewModels)
        val add = view.summaryNewTag
        if (state.maxTagsReached) {
            add.gone()
            view.maxTagsMessage.visible()
        } else {
            add.visible()
            view.maxTagsMessage.gone()

            val adapter = EditItemAutocompleteTagAdapter(state.tags, activity!!)
            add.setAdapter(adapter)
            add.setOnItemClickListener { _, _, position, _ ->
                dispatch(EditRepeatingQuestAction.AddTag(adapter.getItem(position).name))
                add.setText("")
            }
            add.threshold = 0
            add.setOnTouchListener { _, _ ->
                add.showDropDown()
                false
            }
        }
    }

    private fun renderColor(
        view: View,
        state: EditRepeatingQuestViewState
    ) {
        colorLayout(view, state.color)
        view.summaryColor.onDebounceClick {
            ColorPickerDialogController({
                dispatch(EditRepeatingQuestAction.ChangeColor(it))
            }, state.color).show(
                router,
                "pick_color_tag"
            )
        }
    }

    private fun renderIcon(
        view: View,
        state: EditRepeatingQuestViewState
    ) {
        view.summarySelectedIcon.setImageDrawable(
            IconicsDrawable(view.context).largeIcon(state.iicon)
        )


        view.summaryIcon.onDebounceClick {
            IconPickerDialogController({ icon ->
                dispatch(EditRepeatingQuestAction.ChangeIcon(icon))
            }, state.icon).show(
                router,
                "pick_icon_tag"
            )
        }
    }

    private fun renderDuration(view: View, state: EditRepeatingQuestViewState) {

        view.summaryDuration.text = state.durationText
        view.summaryDuration.onDebounceClick {
            DurationPickerDialogController(
                state.duration.intValue,
                { dispatch(EditRepeatingQuestAction.DurationPicked(it)) }
            ).show(router, "pick_duration_tag")
        }
    }

    private fun renderRepeatPattern(view: View, state: EditRepeatingQuestViewState) {
        view.summaryRepeatPattern.text = state.repeatPatternText
        view.summaryRepeatPattern.onDebounceClick {
            RepeatPatternPickerDialogController(state.repeatPattern, { p ->
                dispatch(EditRepeatingQuestAction.ChangeRepeatPattern(p))
            }).show(router)
        }
    }

    private fun renderStartTime(
        view: View,
        state: EditRepeatingQuestViewState
    ) {
        view.summaryStartTime.text = state.startTimeText
        view.summaryStartTime.onDebounceClick {
            val startTime = state.startTime ?: Time.now()
            val dialog = TimePickerDialog(
                view.context,
                R.style.Theme_myPoli_AlertDialog,
                TimePickerDialog.OnTimeSetListener { _, hour, minute ->
                    dispatch(
                        EditRepeatingQuestAction.ChangeStartTime(
                            Time.at(
                                hour,
                                minute
                            )
                        )
                    )
                }, startTime.hours, startTime.getMinutes(), false
            )
            dialog.setButton(
                Dialog.BUTTON_NEUTRAL,
                view.context.getString(R.string.do_not_know),
                { _, _ ->
                    dispatch(EditRepeatingQuestAction.ChangeStartTime(null))
                })
            dialog.show()
        }
    }

    private fun renderReminder(view: View, state: EditRepeatingQuestViewState) {
        view.summaryReminder.text = state.reminderText
        view.summaryReminder.onDebounceClick {
            ReminderPickerDialogController(
                object : ReminderPickerDialogController.ReminderPickedListener {
                    override fun onReminderPicked(reminder: ReminderViewModel?) {
                        dispatch(EditRepeatingQuestAction.ChangeReminder(reminder))
                    }
                }, state.reminder
            ).show(router, "pick_reminder_tag")
        }
    }


    private fun renderChallenge(
        view: View,
        state: EditRepeatingQuestViewState
    ) {
        view.summaryChallenge.text = state.challengeText
        view.summaryChallenge.onDebounceClick {
            ChallengePickerDialogController(state.challenge, { challenge ->
                dispatch(EditRepeatingQuestAction.ChangeChallenge(challenge))
            }).show(router)
        }
    }

    private fun renderNote(view: View, state: EditRepeatingQuestViewState) {
        view.summaryNote.text = state.noteText
        view.summaryNote.onDebounceClick {
            NoteDialogViewController(state.note, { text ->
                dispatch(EditRepeatingQuestAction.ChangeNote(text))
            }).show(router)
        }
    }

    private fun colorLayout(
        view: View,
        color: Color
    ) {
        val color500 = colorRes(color.androidColor.color500)
        val color700 = colorRes(color.androidColor.color700)
        view.appbar.setBackgroundColor(color500)
        view.toolbar.setBackgroundColor(color500)
        view.rootContainer.setBackgroundColor(color500)
        activity?.window?.navigationBarColor = color500
        activity?.window?.statusBarColor = color700
    }

    private val EditRepeatingQuestViewState.iicon: IIcon
        get() = icon?.androidIcon?.icon ?: GoogleMaterial.Icon.gmd_local_florist

    private val EditRepeatingQuestViewState.startTimeText: String
        get() = startTime?.let { "At $it" } ?: stringRes(R.string.unscheduled)

    private val EditRepeatingQuestViewState.durationText: String
        get() = "For ${DurationFormatter.formatReadable(activity!!, duration.intValue)}"

    private val EditRepeatingQuestViewState.repeatPatternText: String
        get() = "Repeat ${RepeatPatternFormatter.format(activity!!, repeatPattern!!)}"

    private val EditRepeatingQuestViewState.reminderText: String
        get() = reminder?.let {
            ReminderTimeFormatter.format(
                it.minutesFromStart.toInt(),
                activity!!
            )
        } ?: stringRes(R.string.do_not_remind)

    private val EditRepeatingQuestViewState.challengeText: String
        get() = challenge?.name ?: stringRes(R.string.add_to_challenge)

    private val EditRepeatingQuestViewState.noteText: String
        get() = if (note.isBlank()) stringRes(R.string.tap_to_add_note) else note

    private val EditRepeatingQuestViewState.tagViewModels: List<EditItemTagAdapter.TagViewModel>
        get() = questTags.map {
            EditItemTagAdapter.TagViewModel(
                name = it.name,
                icon = it.icon?.androidIcon?.icon ?: MaterialDesignIconic.Icon.gmi_label,
                tag = it
            )
        }
}

