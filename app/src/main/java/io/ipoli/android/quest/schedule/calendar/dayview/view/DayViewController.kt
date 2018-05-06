package io.ipoli.android.quest.schedule.calendar.dayview.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.v4.content.ContextCompat
import android.support.v4.widget.TextViewCompat
import android.support.v4.widget.TintableCompoundButton
import android.text.Editable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.StrikethroughSpan
import android.util.TypedValue
import android.view.*
import android.widget.TextView
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.mikepenz.iconics.IconicsDrawable
import io.ipoli.android.Constants
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.datetime.DateUtils
import io.ipoli.android.common.datetime.Time
import io.ipoli.android.common.datetime.isNotEqual
import io.ipoli.android.common.datetime.startOfDayUTC
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.quest.CompletedQuestViewController
import io.ipoli.android.quest.Icon
import io.ipoli.android.quest.Reminder
import io.ipoli.android.quest.edit.EditQuestViewController
import io.ipoli.android.quest.reminder.picker.ReminderPickerDialogController
import io.ipoli.android.quest.reminder.picker.ReminderViewModel
import io.ipoli.android.quest.schedule.calendar.CalendarViewController
import io.ipoli.android.quest.schedule.calendar.dayview.view.DayViewState.StateType.*
import io.ipoli.android.quest.schedule.calendar.dayview.view.widget.*
import io.ipoli.android.quest.show.QuestViewController
import kotlinx.android.synthetic.main.calendar_hour_cell.view.*
import kotlinx.android.synthetic.main.controller_day_view.view.*
import kotlinx.android.synthetic.main.item_calendar_drag.view.*
import kotlinx.android.synthetic.main.item_calendar_quest.view.*
import kotlinx.android.synthetic.main.item_unscheduled_quest.view.*
import kotlinx.android.synthetic.main.view_calendar_day.view.*
import org.threeten.bp.LocalDate
import java.util.*

class DayViewController :
    ReduxViewController<DayViewAction, DayViewState, DayViewReducer>,
    CalendarDayView.CalendarChangeListener {

    private lateinit var currentDate: LocalDate

    private var actionMode: ActionMode? = null

    private lateinit var eventsAdapter: QuestScheduledEventsAdapter

    private lateinit var unscheduledEventsAdapter: UnscheduledQuestsAdapter

    private var colorPickListener: () -> Unit = {}

    private var iconPickedListener: () -> Unit = {}

    private var reminderPickedListener: () -> Unit = {}

    private var datePickedListener: () -> Unit = {}

    private var fullEditListener: () -> Unit = {}

    private lateinit var calendarDayView: CalendarDayView

    override var namespace: String? = UUID.randomUUID().toString()

    override val reducer = DayViewReducer(namespace!!)

    constructor(currentDate: LocalDate) : this() {
        this.currentDate = currentDate
    }

    constructor(args: Bundle? = null) : super(args)

    override fun onSaveViewState(view: View, outState: Bundle) {
        outState.putLong("current_date", currentDate.startOfDayUTC())
    }

    override fun onRestoreViewState(view: View, savedViewState: Bundle) {
        currentDate = DateUtils.fromMillis(savedViewState.getLong("current_date"))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.controller_day_view, container, false)

        calendarDayView = view.calendar
        calendarDayView.setCalendarChangeListener(this)
        calendarDayView.setHourAdapter(object : CalendarDayView.HourCellAdapter {
            override fun bind(view: View, hour: Int) {
                if (hour > 0) {
                    view.timeLabel.text = hour.toString() + ":00"
                }
            }
        })

        calendarDayView.scrollToNow()

        return view
    }

    override fun onCreateLoadAction() =
        DayViewAction.Load(currentDate)

    override fun onAttach(view: View) {
        super.onAttach(view)
        if (currentDate.isNotEqual(LocalDate.now())) {
            calendarDayView.hideTimeline()
        }
    }

    override fun handleBack(): Boolean {
        cancelEdit()
        return super.handleBack()
    }

    private fun cancelEdit() {
        ViewUtils.hideKeyboard(calendarDayView)
        calendarDayView.cancelEdit()
    }

    override fun render(state: DayViewState, view: View) {
        val color = state.color?.androidColor
        val icon = state.icon?.androidIcon
        fullEditListener = {
            startFullEdit(state)
        }

        when (state.type) {
            SCHEDULE_LOADED -> {
                eventsAdapter =
                        QuestScheduledEventsAdapter(
                            activity!!,
                            state.scheduledQuests,
                            calendarDayView
                        )
                calendarDayView.setScheduledEventsAdapter(eventsAdapter)
                unscheduledEventsAdapter =
                        UnscheduledQuestsAdapter(state.unscheduledQuests, calendarDayView)
                calendarDayView.setUnscheduledEventsAdapter(unscheduledEventsAdapter)
                updateUnscheduledQuestsHeight(view)
            }

            ADD_NEW_SCHEDULED_QUEST -> {
                colorPickListener = { showColorPicker(color) }
                iconPickedListener = { showIconPicker(icon) }
                reminderPickedListener = { showReminderPicker(state.reminder) }
                datePickedListener = { showDatePicker(state.scheduledDate ?: currentDate) }

                startActionMode()
                (parentController as CalendarViewController).onStartEdit()
                val dragView = view.dragContainer
                dragView.dragStartTime.visibility = View.VISIBLE
                dragView.dragEndTime.visibility = View.VISIBLE
                startEditScheduledEvent(dragView, state.startTime!!, state.endTime!!)
                setupDragViewUI(dragView, state.name, color!!, icon)
            }

            START_EDIT_SCHEDULED_QUEST -> {
                colorPickListener = { showColorPicker(color) }
                iconPickedListener = { showIconPicker(icon) }
                reminderPickedListener = { showReminderPicker(state.reminder) }
                datePickedListener = { showDatePicker(state.scheduledDate ?: currentDate) }

                startActionMode()
                (parentController as CalendarViewController).onStartEdit()
                val dragView = view.dragContainer
                dragView.dragStartTime.visibility = View.VISIBLE
                dragView.dragEndTime.visibility = View.VISIBLE
                startEditScheduledEvent(dragView, state.startTime!!, state.endTime!!)
                setupDragViewUI(dragView, state.name, color!!, icon, state.reminder)
            }

            START_EDIT_UNSCHEDULED_QUEST -> {
                colorPickListener = { showColorPicker(color) }
                iconPickedListener = { showIconPicker(icon) }
                reminderPickedListener = { showReminderPicker(state.reminder) }
                datePickedListener = { showDatePicker(state.scheduledDate ?: currentDate) }

                startActionMode()
                (parentController as CalendarViewController).onStartEdit()
                val dragView = view.dragContainer
                dragView.dragStartTime.visibility = View.GONE
                dragView.dragEndTime.visibility = View.GONE
                setupDragViewUI(dragView, state.name, color!!, icon, state.reminder)
            }

            EVENT_UPDATED -> {
                onStopEditMode()
                calendarDayView.onEventUpdated()
                ViewUtils.hideKeyboard(calendarDayView)
            }

            EVENT_VALIDATION_EMPTY_NAME -> {
                calendarDayView.onEventValidationError()
            }

            EVENT_VALIDATION_TIMER_RUNNING -> {
                showShortToast(R.string.validation_timer_running)
            }

            EVENT_REMOVED -> {
                PetMessagePopup(
                    stringRes(R.string.remove_quest_undo_message),
                    { dispatch(DayViewAction.UndoRemoveQuest(state.removedEventId)) }
                ).show(view.context)
            }

            NEW_EVENT_REMOVED -> {

            }

            COLOR_PICKED -> {
                colorPickListener = { showColorPicker(color) }
                val dragView = view.dragContainer
                ObjectAnimator.ofArgb(
                    dragView,
                    "backgroundColor",
                    (dragView.background as ColorDrawable).color,
                    ContextCompat.getColor(dragView.context, color!!.color500)
                )
                    .setDuration(dragView.context.resources.getInteger(android.R.integer.config_longAnimTime).toLong())
                    .start()
            }

            ICON_PICKED -> {
                iconPickedListener = { showIconPicker(icon) }
                val dragIcon = view.dragContainer.dragIcon
                if (icon == null) {
                    dragIcon.setImageDrawable(null)
                } else {
                    dragIcon.setImageDrawable(
                        IconicsDrawable(dragIcon.context)
                            .icon(icon.icon)
                            .colorRes(R.color.md_white)
                            .sizeDp(24)
                    )
                }
            }

            QUEST_COMPLETED -> {
            }

            UNDO_QUEST_COMPLETED -> {
            }

            EDIT_QUEST -> {
                colorPickListener = { showColorPicker(color) }
                iconPickedListener = { showIconPicker(icon) }
                reminderPickedListener = { showReminderPicker(state.reminder) }
                datePickedListener = { showDatePicker(state.scheduledDate ?: currentDate) }
                startActionMode()
            }

            EDIT_VIEW_DRAGGED -> {
                val dragView = view.dragContainer
                if (state.startTime == null && state.endTime == null) {
                    dragView.dragStartTime.visibility = View.GONE
                    dragView.dragEndTime.visibility = View.GONE
                }
                if (state.startTime != null) {
                    dragView.dragStartTime.visibility = View.VISIBLE
                    dragView.dragStartTime.text = state.startTime.toString()
                }
                if (state.endTime != null) {
                    dragView.dragEndTime.visibility = View.VISIBLE
                    dragView.dragEndTime.text = state.endTime.toString()
                }
            }

            REMINDER_PICKED -> {
                reminderPickedListener = { showReminderPicker(state.reminder) }
            }

            DATE_PICKED -> {
                datePickedListener = { showDatePicker(state.scheduledDate ?: currentDate) }
            }
        }
    }

    private fun startFullEdit(state: DayViewState) {
        val fadeChangeHandler = FadeChangeHandler()
        pushWithRootRouter(
            RouterTransaction.with(
                EditQuestViewController(
                    questId = state.editId,
                    params = EditQuestViewController.Params(
                        name = state.name,
                        scheduleDate = state.scheduledDate,
                        startTime = state.startTime,
                        duration = state.duration,
                        color = state.color,
                        icon = state.icon,
                        reminderViewModel = state.reminder
                    )
                )
            )
                .pushChangeHandler(fadeChangeHandler)
                .popChangeHandler(fadeChangeHandler)
        )
        onStopEditMode()
    }

    private fun updateUnscheduledQuestsHeight(view: View) {
        val unscheduledQuestsToShow = Math.min(
            unscheduledEventsAdapter.itemCount,
            Constants.MAX_UNSCHEDULED_QUEST_VISIBLE_COUNT
        )

        val itemHeight = resources!!.getDimensionPixelSize(R.dimen.unscheduled_quest_item_height)

        val layoutParams = view.unscheduledEvents.layoutParams
        layoutParams.height = unscheduledQuestsToShow * itemHeight
        if (unscheduledQuestsToShow == Constants.MAX_UNSCHEDULED_QUEST_VISIBLE_COUNT) {
            layoutParams.height = layoutParams.height - itemHeight / 2
        }
        view.unscheduledEvents.layoutParams = layoutParams
    }

    private fun startEditScheduledEvent(dragView: View, startTime: Time, endTime: Time) {
        dragView.dragStartTime.text = startTime.toString()
        dragView.dragEndTime.text = endTime.toString()
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            dragView.dragStartTime,
            8,
            14,
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            dragView.dragEndTime,
            8,
            14,
            1,
            TypedValue.COMPLEX_UNIT_SP
        )
    }

    override fun onStartEditNewScheduledEvent(startTime: Time, duration: Int) {
        dispatch(DayViewAction.AddNewScheduledQuest(startTime, duration))
    }

    private fun setupDragViewUI(
        dragView: View,
        name: String,
        color: AndroidColor,
        icon: AndroidIcon? = null,
        reminder: ReminderViewModel? = null
    ) {
        dragView.dragName.setText(name)
        dragView.setBackgroundColor(ContextCompat.getColor(dragView.context, color.color500))
        if (icon != null) {
            dragView.dragIcon.setImageDrawable(
                IconicsDrawable(dragView.context)
                    .icon(icon.icon)
                    .colorRes(R.color.md_white)
                    .sizeDp(24)
            )

        } else {
            dragView.dragIcon.setImageDrawable(null)
        }
        dragView.dragName.setOnFocusChangeListener { _, isFocused ->
            if (isFocused) {
                calendarDayView.startEditDragEventName()
            }
        }

        dragView.dragName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                dispatch(DayViewAction.ChangeEditViewName(text.toString()))
            }

        })
    }

    override fun onDragViewClick(dragView: View) {
        ViewUtils.hideKeyboard(calendarDayView)
        dragView.requestFocus()
    }

    override fun onEventValidationError(dragView: View) {
        dragView.dragName.error = stringRes(R.string.think_of_a_name)
    }

    override fun onRemoveEvent(eventId: String) {
        onStopEditMode()
        dispatch(DayViewAction.RemoveQuest(eventId))
    }

    override fun onMoveEvent(startTime: Time?, endTime: Time?) {
        dispatch(DayViewAction.DragMoveView(startTime, endTime))
    }

    override fun onResizeEvent(startTime: Time?, endTime: Time?, duration: Int) {
        dispatch(DayViewAction.DragResizeView(startTime, endTime, duration))
    }

    override fun onZoomEvent(adapterView: View) {
        eventsAdapter.adaptViewForHeight(
            adapterView,
            ViewUtils.pxToDp(adapterView.height, adapterView.context)
        )
    }

    override fun onAddEvent() {
        dispatch(DayViewAction.AddQuest)
    }

    override fun onEditCalendarEvent() {
        dispatch(DayViewAction.EditQuest)
    }

    override fun onEditUnscheduledCalendarEvent() {
        dispatch(DayViewAction.EditQuest)
    }

    override fun onEditUnscheduledEvent() {
        dispatch(DayViewAction.EditUnscheduledQuest)
    }

    private fun startActionMode() {
        parentController?.view?.startActionMode(object : ActionMode.Callback {
            override fun onActionItemClicked(am: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    R.id.fullEdit -> fullEditListener()
                    R.id.chooseDate -> datePickedListener()
                    R.id.chooseReminder -> reminderPickedListener()
                    R.id.chooseIcon -> iconPickedListener()
                    R.id.chooseColor -> colorPickListener()
                    R.id.removeEvent -> calendarDayView.onRemoveEvent()
                }
                return true
            }

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                actionMode = mode
                mode.menuInflater.inflate(R.menu.schedule_quest_edit_menu, menu)
                return true
            }

            override fun onPrepareActionMode(p0: ActionMode?, menu: Menu?): Boolean {
                menu!!.findItem(R.id.fullEdit).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.findItem(R.id.chooseDate).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.findItem(R.id.chooseReminder).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.findItem(R.id.chooseIcon).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.findItem(R.id.chooseColor).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.findItem(R.id.removeEvent).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                return true
            }


            override fun onDestroyActionMode(p0: ActionMode?) {
                cancelEdit()
                (parentController as CalendarViewController).onStopEdit()
                actionMode = null
            }
        })
    }

    private fun showDatePicker(selectedDate: LocalDate) {
        val datePickerDialog = DatePickerDialog(
            view!!.context, R.style.Theme_myPoli_AlertDialog,
            DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                dispatch(DayViewAction.DatePicked(LocalDate.of(year, month + 1, dayOfMonth)))
            }, selectedDate.year, selectedDate.month.value - 1, selectedDate.dayOfMonth
        )
        datePickerDialog.setButton(
            Dialog.BUTTON_NEUTRAL,
            view!!.context.getString(R.string.do_not_know),
            { _, _ -> dispatch(DayViewAction.DatePicked(null)) })
        datePickerDialog.show()
    }

    private fun showReminderPicker(selectedReminder: ReminderViewModel?) {
        ReminderPickerDialogController(object :
            ReminderPickerDialogController.ReminderPickedListener {
            override fun onReminderPicked(reminder: ReminderViewModel?) {
                dispatch(DayViewAction.ReminderPicked(reminder))
            }
        }, selectedReminder)
            .show(router, "reminder-picker")
    }

    private fun showIconPicker(selectedIcon: AndroidIcon?) {
        IconPickerDialogController({ icon ->
            val ic = icon?.let {
                Icon.valueOf(it.name)
            }
            dispatch(DayViewAction.IconPicked(ic))
        }, selectedIcon?.toIcon).show(router, "icon-picker")
    }

    private fun showColorPicker(selectedColor: AndroidColor?) {
        ColorPickerDialogController({
            dispatch(DayViewAction.ColorPicked(it))
        }, selectedColor?.color)
            .show(router, "pick_color_tag")
    }

    private fun onStopEditMode() {
        (parentController as CalendarViewController).onStopEdit()
        actionMode?.finish()
    }

    sealed class ScheduledEventViewModel : CalendarEvent {


        data class Quest(
            override val id: String,
            val name: String,
            val tags: List<String>,
            override val duration: Int,
            override val startMinute: Int,
            val startTime: String,
            val endTime: String,
            val icon: AndroidIcon?,
            val backgroundColor: AndroidColor,
            val reminder: ReminderViewModel?,
            val isCompleted: Boolean,
            val isStarted: Boolean,
            val repeatingQuestId: String?,
            val challengeId: String?,
            val isPlaceholder: Boolean
        ) : ScheduledEventViewModel() {
            val isRepeating: Boolean
                get() = repeatingQuestId != null && repeatingQuestId.isNotEmpty()

            val isFromChallenge: Boolean
                get() = challengeId != null && challengeId.isNotEmpty()
        }

        data class Event(
            override val id: String,
            val name: String,
            override val duration: Int,
            override val startMinute: Int,
            val startTime: String,
            val endTime: String,
            val backgroundColor: Int,
            val isRepeating: Boolean
        ) : ScheduledEventViewModel()
    }

    inner class QuestScheduledEventsAdapter(
        context: Context,
        events: List<ScheduledEventViewModel>,
        private val calendarDayView: CalendarDayView
    ) :
        ScheduledEventsAdapter<ScheduledEventViewModel>(
            context,
            R.layout.item_calendar_quest,
            events.toMutableList()
        ) {

        override fun bindView(view: View, position: Int) {
            val vm = getItem(position)
            when (vm) {
                is ScheduledEventViewModel.Quest ->
                    showQuest(view, vm)
                is ScheduledEventViewModel.Event ->
                    showEvent(view, vm)
            }
        }

        private fun showEvent(
            view: View,
            vm: ScheduledEventViewModel.Event
        ) {
            view.checkBox.gone()

            view.questSchedule.text = "${vm.startTime} - ${vm.endTime}"
            view.backgroundView.setBackgroundColor(vm.backgroundColor)

            view.questName.text = vm.name

            view.questSchedule.setTextColor(colorRes(R.color.md_light_text_70))
            view.questName.setTextColor(colorRes(R.color.md_white))

            view.repeatIndicator.visible = vm.isRepeating
            view.challengeIndicator.gone()
        }

        private fun showQuest(
            view: View,
            vm: ScheduledEventViewModel.Quest
        ) {

            view.checkBox.visible()

            view.setOnLongClickListener {

                if (vm.isStarted) {
                    showShortToast(R.string.validation_timer_running)
                } else {
                    dispatch(DayViewAction.StartEditScheduledQuest(vm))
                    calendarDayView.startEventRescheduling(vm)
                }
                true
            }

            view.questSchedule.text = "${vm.startTime} - ${vm.endTime}"

            view.backgroundView.setBackgroundColor(colorRes(vm.backgroundColor.color600))

            if (vm.isCompleted) {
                val span = SpannableString(vm.name)
                span.setSpan(StrikethroughSpan(), 0, vm.name.length, 0)
                view.questName.text = span
                view.questName.setTextColor(colorRes(R.color.md_dark_text_54))
                view.questSchedule.setTextColor(colorRes(R.color.md_dark_text_54))
                view.questTags.setTextColor(colorRes(R.color.md_dark_text_54))
                view.questTags.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_tag_black_16dp,
                    0,
                    0,
                    0
                )

                view.checkBox.isChecked = true
                (view.checkBox as TintableCompoundButton).supportButtonTintList =
                        tintList(R.color.md_grey_700)
                view.completedBackgroundView.visibility = View.VISIBLE

                vm.icon?.let {
                    val icon = IconicsDrawable(context)
                        .icon(it.icon)
                        .colorRes(R.color.md_dark_text_38)
                        .sizeDp(24)
                    view.questIcon.visible = true
                    view.questIcon.setImageDrawable(icon)
                }

                view.setOnClickListener {
                    showCompletedQuest(vm.id)
                }
            } else {

                view.questCategoryIndicator.setBackgroundResource(vm.backgroundColor.color900)
                view.questSchedule.setTextColor(colorRes(R.color.md_light_text_70))
                view.questTags.setTextColor(colorRes(R.color.md_light_text_70))
                view.questTags.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_tag_white_16dp,
                    0,
                    0,
                    0
                )

                view.questName.text = vm.name
                view.questName.setTextColor(colorRes(R.color.md_white))

                vm.icon?.let {
                    val icon = IconicsDrawable(context)
                        .icon(it.icon)
                        .colorRes(vm.backgroundColor.color200)
                        .sizeDp(24)
                    view.questIcon.visible = true
                    view.questIcon.setImageDrawable(icon)
                }

                (view.checkBox as TintableCompoundButton).supportButtonTintList =
                        tintList(vm.backgroundColor.color200)
                view.completedBackgroundView.visibility = View.INVISIBLE

                if (vm.isPlaceholder) {
                    view.setOnClickListener(null)
                    view.checkBox.visible = false
                } else {

                    view.checkBox.visible = true
                    view.setOnClickListener {
                        showQuest(vm.id)
                    }
                }

            }

            view.repeatIndicator.visibility = if (vm.isRepeating) View.VISIBLE else View.GONE
            view.challengeIndicator.visibility = if (vm.isFromChallenge) View.VISIBLE else View.GONE
            if (vm.tags.isEmpty()) {
                view.questTags.gone()
            } else {
                view.questTags.text = vm.tags.joinToString()
                view.questTags.visible()
            }

            view.checkBox.setOnCheckedChangeListener { cb, checked ->
                if (checked) {
                    (view.checkBox as TintableCompoundButton).supportButtonTintList =
                            tintList(R.color.md_grey_700)
                    val anim = RevealAnimator().create(view.completedBackgroundView, cb)
                    anim.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator?) {
                            view.completedBackgroundView.visibility = View.VISIBLE
                        }

                        override fun onAnimationEnd(animation: Animator?) {
                            dispatch(DayViewAction.CompleteQuest(vm.id, vm.isStarted))
                        }

                    })
                    anim.start()
                } else {

                    val anim = RevealAnimator().create(
                        view.completedBackgroundView,
                        cb,
                        reverse = true
                    )

                    anim.addListener(object : AnimatorListenerAdapter() {

                        override fun onAnimationEnd(animation: Animator?) {
                            view.completedBackgroundView.visibility = View.INVISIBLE
                            dispatch(DayViewAction.UndoCompleteQuest(vm.id))
                        }
                    })
                    anim.start()

                }
            }

            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                view.questName,
                8,
                16,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )
            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                view.questSchedule,
                8,
                14,
                1,
                TypedValue.COMPLEX_UNIT_SP
            )

            view.post {
                adaptViewForHeight(view, ViewUtils.pxToDp(view.height, context))
            }
        }

        override fun rescheduleEvent(position: Int, startTime: Time, duration: Int) {
            val vm = getItem(position) as ScheduledEventViewModel.Quest
            events[position] = vm.copy(
                startMinute = startTime.toMinuteOfDay(),
                startTime = startTime.toString(),
                duration = duration,
                endTime = Time.plusMinutes(startTime, duration).toString()
            )
            notifyDataSetChanged()
        }

        override fun adaptViewForHeight(adapterView: View, height: Float) {
            val cbHeight = adapterView.checkBox.height
            val avHeight = adapterView.height
            val shHeight = adapterView.questSchedule.height
            val nameHeight = adapterView.questName.height

            with(adapterView) {
                when {
                    avHeight <= cbHeight -> {
                        ViewUtils.goneViews(checkBox, questSchedule)
                        setQuestNameMarginStart(adapterView.questName, 16f)
                    }
                    avHeight > cbHeight && avHeight < shHeight + nameHeight + ViewUtils.dpToPx(
                        8f,
                        adapterView.context
                    ) -> {
                        ViewUtils.showViews(checkBox)
                        ViewUtils.goneViews(questSchedule)
                        setQuestNameMarginStart(adapterView.questName, 8f)
                    }
                    else -> {
                        ViewUtils.showViews(checkBox, questSchedule)
                        setQuestNameMarginStart(adapterView.questName, 8f)
                    }
                }
            }
        }

        private fun setQuestNameMarginStart(questName: View, marginDP: Float) {
            val lp = questName.layoutParams as ViewGroup.MarginLayoutParams
            lp.marginStart = ViewUtils.dpToPx(marginDP, questName.context).toInt()
            questName.layoutParams = lp
        }

        private fun tintList(@ColorRes color: Int) = ContextCompat.getColorStateList(context, color)
    }

    private fun showCompletedQuest(questId: String) {
        pushWithRootRouter(RouterTransaction.with(CompletedQuestViewController(questId)))
    }

    data class TagViewModel(val name: String, @ColorRes val color: Int)

    data class UnscheduledQuestViewModel(
        override val id: String,
        val name: String,
        override val duration: Int,
        val icon: AndroidIcon?,
        val backgroundColor: AndroidColor,
        @ColorRes val textColor: Int,
        val isCompleted: Boolean,
        val isStarted: Boolean,
        val reminder: ReminderViewModel? = null,
        val repeatingQuestId: String?,
        val challengeId: String?,
        val isPlaceholder: Boolean,
        val tags: List<TagViewModel>
    ) : UnscheduledEvent {
        val isRepeating: Boolean
            get() = repeatingQuestId != null && repeatingQuestId.isNotEmpty()

        val isFromChallenge: Boolean
            get() = challengeId != null && challengeId.isNotEmpty()
    }

    inner class UnscheduledQuestsAdapter(
        items: List<UnscheduledQuestViewModel>,
        calendarDayView: CalendarDayView
    ) :
        UnscheduledEventsAdapter<UnscheduledQuestViewModel>
            (R.layout.item_unscheduled_quest, items.toMutableList(), calendarDayView) {

        override fun ViewHolder.bind(
            viewModel: UnscheduledQuestViewModel,
            calendarDayView: CalendarDayView
        ) {
            itemView.setOnLongClickListener {
                if (viewModel.isStarted) {
                    showShortToast(R.string.validation_timer_running)
                } else {
                    dispatch(DayViewAction.StartEditUnscheduledQuest(viewModel))
                    calendarDayView.startEventRescheduling(events[adapterPosition])
                }
                true
            }

            (itemView.unscheduledCheckBox as TintableCompoundButton).supportButtonTintList =
                    tintList(viewModel.backgroundColor.color500, itemView.context)

            if (viewModel.tags.isNotEmpty()) {
                itemView.questTagName.visible()
                renderTag(itemView.questTagName, viewModel.tags.first())
            } else {
                itemView.questTagName.gone()
            }

            if (viewModel.isCompleted) {
                val span = SpannableString(viewModel.name)
                span.setSpan(StrikethroughSpan(), 0, viewModel.name.length, 0)
                itemView.unscheduledQuestName.text = span
                itemView.unscheduledCheckBox.isChecked = true

                viewModel.icon?.let {
                    val icon = IconicsDrawable(itemView.context)
                        .icon(it.icon)
                        .colorRes(R.color.md_dark_text_38)
                        .sizeDp(24)
                    itemView.unscheduledQuestIcon.visible = true
                    itemView.unscheduledQuestIcon.setImageDrawable(icon)
                }

                itemView.setOnClickListener {
                    showCompletedQuest(viewModel.id)
                }

            } else {
                itemView.unscheduledQuestName.text = viewModel.name
                itemView.unscheduledQuestName.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        viewModel.textColor
                    )
                )

                viewModel.icon?.let {
                    val icon = IconicsDrawable(itemView.context)
                        .icon(it.icon)
                        .colorRes(it.color)
                        .sizeDp(24)
                    itemView.unscheduledQuestIcon.visible = true
                    itemView.unscheduledQuestIcon.setImageDrawable(icon)
                }

                if (viewModel.isPlaceholder) {
                    itemView.setOnClickListener(null)
                    itemView.unscheduledCheckBox.visible = false
                } else {

                    itemView.unscheduledCheckBox.visible = true
                    itemView.setOnClickListener {
                        showQuest(viewModel.id)
                    }
                }

            }

            itemView.unscheduledQuestRepeatIndicator.visibility =
                    if (viewModel.isRepeating) View.VISIBLE else View.GONE

            itemView.unscheduledQuestChallengeIndicator.visibility =
                    if (viewModel.isFromChallenge) View.VISIBLE else View.GONE

            itemView.unscheduledCheckBox.setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    dispatch(DayViewAction.CompleteQuest(viewModel.id, viewModel.isStarted))
                } else {
                    dispatch(DayViewAction.UndoCompleteQuest(viewModel.id))
                }

            }
        }

        private fun renderTag(view: TextView, tag: TagViewModel) {
            view.text = tag.name
            TextViewCompat.setTextAppearance(
                view.questTagName,
                R.style.TextAppearance_AppCompat_Caption
            )

            val indicator = view.questTagName.compoundDrawablesRelative[0] as GradientDrawable
            indicator.mutate()
            val size = ViewUtils.dpToPx(8f, view.context).toInt()
            indicator.setSize(size, size)
            indicator.setColor(colorRes(tag.color))
            view.questTagName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                indicator,
                null,
                null,
                null
            )
        }

        private fun tintList(@ColorRes color: Int, context: Context) =
            ContextCompat.getColorStateList(context, color)
    }

    private fun showQuest(questId: String) {
        pushWithRootRouter(
            QuestViewController.routerTransaction(questId)
        )
    }

    private val DayViewState.unscheduledQuests
        get() = schedule!!.unscheduledQuests.map {
            val color = it.color.androidColor
            DayViewController.UnscheduledQuestViewModel(
                id = it.id,
                name = it.name,
                duration = it.duration,
                icon = it.icon?.androidIcon,
                backgroundColor = color,
                textColor = color.color900,
                isCompleted = it.isCompleted,
                isStarted = it.isStarted,
                repeatingQuestId = it.repeatingQuestId,
                challengeId = it.challengeId,
                isPlaceholder = it.id.isEmpty(),
                tags = it.tags.map {
                    TagViewModel(
                        it.name,
                        AndroidColor.valueOf(it.color.name).color500
                    )
                }
            )
        }

    private val DayViewState.scheduledQuests: List<ScheduledEventViewModel>
        get() {
            val questVms = schedule!!.scheduledQuests.map { q ->
                val color = q.color.androidColor

                val reminder = q.reminders.firstOrNull()?.let {
                    if (it is Reminder.Relative)
                        ReminderViewModel(
                            it.message,
                            it.minutesFromStart
                        )
                    else
                        null
                }

                DayViewController.ScheduledEventViewModel.Quest(
                    id = q.id,
                    name = q.name,
                    tags = q.tags.map { it.name },
                    duration = q.duration,
                    startMinute = q.startTime!!.toMinuteOfDay(),
                    startTime = q.startTime.toString(),
                    endTime = q.endTime.toString(),
                    icon = q.icon?.androidIcon,
                    backgroundColor = color,
                    reminder = reminder,
                    isCompleted = q.isCompleted,
                    isStarted = q.isStarted,
                    repeatingQuestId = q.repeatingQuestId,
                    challengeId = q.challengeId,
                    isPlaceholder = q.id.isEmpty()
                )
            }

            val eventVms = schedule.events.map { e ->
                DayViewController.ScheduledEventViewModel.Event(
                    id = e.id,
                    name = e.name,
                    duration = e.duration.intValue,
                    startMinute = e.startTime.toMinuteOfDay(),
                    startTime = e.startTime.toString(),
                    endTime = e.endTime.toString(),
                    backgroundColor = e.color,
                    isRepeating = e.isRepeating
                )
            }

            return questVms + eventVms
        }
}