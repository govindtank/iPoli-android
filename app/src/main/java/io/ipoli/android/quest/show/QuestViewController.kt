package io.ipoli.android.quest.show

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.Activity
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.ionicons_typeface_library.Ionicons
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.ReorderItemHelper
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.note.NoteViewController
import io.ipoli.android.quest.CompletedQuestViewController
import io.ipoli.android.quest.edit.EditQuestViewController
import io.ipoli.android.tag.Tag
import kotlinx.android.synthetic.main.controller_quest.view.*
import kotlinx.android.synthetic.main.item_quest_sub_quest.view.*
import kotlinx.android.synthetic.main.item_quest_tag_list.view.*
import kotlinx.android.synthetic.main.item_timer_progress.view.*
import kotlinx.android.synthetic.main.view_quest_sub_quests.view.*
import kotlinx.android.synthetic.main.view_timer.view.*

/**
 * Created by Venelin Valkov <venelin@io.ipoli.io>
 * on 6.01.18.
 */
class QuestViewController : ReduxViewController<QuestAction, QuestViewState, QuestReducer> {

    private lateinit var questId: String

    private val handler = Handler(Looper.getMainLooper())

    override val reducer = QuestReducer

    private lateinit var touchHelper: ItemTouchHelper

    private lateinit var newSubQuestWatcher: TextWatcher

    private lateinit var noteViewController: NoteViewController

    constructor(args: Bundle? = null) : super(args)

    private constructor(questId: String) : super() {
        this.questId = questId
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.controller_quest, container, false)

        renderTimerButton(view.startStop, TimerButton.START)

        val icon = IconicsDrawable(view.context)
            .icon(Ionicons.Icon.ion_android_done)
            .color(attrData(R.attr.colorAccent))
            .sizeDp(22)

        view.complete.setImageDrawable(icon)

        view.complete.post {
            view.complete.visibility = View.GONE
        }

        val minusIcon = IconicsDrawable(view.context)
            .icon(Ionicons.Icon.ion_minus)
            .color(attrData(R.attr.colorAccent))
            .sizeDp(22)

        view.removePomodoro.setImageDrawable(minusIcon)

        val addIcon = IconicsDrawable(view.context)
            .icon(Ionicons.Icon.ion_plus)
            .color(attrData(R.attr.colorAccent))
            .sizeDp(22)

        view.addPomodoro.setImageDrawable(addIcon)

        setupBottomNavigation(view)

        setupSubQuestList(view)

        noteViewController = NoteViewController("", { note ->
            dispatch(QuestAction.SaveNote(note))
        })
        setChildController(
            view.noteContainer,
            noteViewController
        )

        view.newSubQuestName.setOnEditTextImeBackListener(object : EditTextImeBackListener {
            override fun onImeBack(ctrl: EditTextBackEvent, text: String) {
                enterFullScreen()
            }
        })

        view.editQuest.setOnClickListener {
            val fadeChangeHandler = FadeChangeHandler()
            pushWithRootRouter(
                RouterTransaction.with(
                    EditQuestViewController(questId)
                )
                    .pushChangeHandler(fadeChangeHandler)
                    .popChangeHandler(fadeChangeHandler)
            )
        }

        return view
    }

    override fun onActivityResumed(activity: Activity) {
        super.onActivityResumed(activity)
        enterFullScreen()
    }

    private fun setupSubQuestList(view: View) {
        view.subQuestList.layoutManager = LinearLayoutManager(activity!!)
        view.subQuestList.adapter = SubQuestAdapter()

        val dragHelper =
            ReorderItemHelper(
                onItemMoved = { oldPosition, newPosition ->
                    (view.subQuestList.adapter as SubQuestAdapter).move(oldPosition, newPosition)
                },
                onItemReordered = { oldPosition, newPosition ->
                    dispatch(QuestAction.ReorderSubQuest(oldPosition, newPosition))
                }
            )

        touchHelper = ItemTouchHelper(dragHelper)
        touchHelper.attachToRecyclerView(view.subQuestList)

        newSubQuestWatcher = object : TextWatcher {
            override fun afterTextChanged(editable: Editable) {
                if (editable.isBlank()) {
                    view.addSubQuest.invisible()
                } else {
                    view.addSubQuest.visible()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {

            }
        }

        view.newSubQuestName.addTextChangedListener(newSubQuestWatcher)
        view.newSubQuestName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                disableEditForAllSubQuests()
            }
        }
    }

    private fun setupBottomNavigation(view: View) {
        view.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.actionTimer -> {
                    view.switcher.displayedChild = 0
                }
                R.id.actionSubquest -> {
                    view.switcher.displayedChild = 1
                }
                R.id.actionNote -> {
                    view.switcher.displayedChild = 2
                }
            }
            true
        }
    }

    private fun createProgressView(view: View) =
        LayoutInflater.from(view.context).inflate(
            R.layout.item_timer_progress,
            view.timerProgressContainer,
            false
        )

    override fun onCreateLoadAction() = QuestAction.Load(questId)

    override fun onChangeEnded(
        changeHandler: ControllerChangeHandler,
        changeType: ControllerChangeType
    ) {
        if (changeType == ControllerChangeType.PUSH_ENTER) {
            enterFullScreen()
        } else if (changeType == ControllerChangeType.POP_ENTER) {
            enterFullScreen()
        } else if (changeType == ControllerChangeType.POP_EXIT) {
            exitFullScreen()
        }
        super.onChangeEnded(changeHandler, changeType)
    }

    override fun onDetach(view: View) {
        handler.removeCallbacksAndMessages(null)
        cancelAnimations(view)
        super.onDetach(view)
    }

    override fun onDestroyView(view: View) {
        view.newSubQuestName.removeTextChangedListener(newSubQuestWatcher)
        super.onDestroyView(view)
    }

    override fun render(state: QuestViewState, view: View) {
        view.questName.text = state.questName
        view.timerLabel.text = state.timerLabel

        renderTags(view, state.tags)

        renderSubQuests(state, view)

        when (state.type) {
            QuestViewState.StateType.SHOW_POMODORO -> {
                renderTimerProgress(view, state)
                renderTypeSwitch(view, state)
                view.pomodoroIndicatorsGroup.visible = true
                renderTimerIndicatorsProgress(view, state)

                view.addPomodoro.dispatchOnClick { QuestAction.AddPomodoro }
                view.removePomodoro.dispatchOnClick { QuestAction.RemovePomodoro }
                view.startStop.dispatchOnClick { QuestAction.Start }
                view.complete.visibility = View.GONE

                dispatch(QuestAction.UpdateNote(state.note))
            }

            QuestViewState.StateType.SHOW_COUNTDOWN -> {
                renderTimerProgress(view, state)
                renderTypeSwitch(view, state)
                view.startStop.dispatchOnClick { QuestAction.Start }
                view.pomodoroIndicatorsGroup.visible = false

                dispatch(QuestAction.UpdateNote(state.note))
            }

            QuestViewState.StateType.RESUMED -> {
                startTimer(view, state)

                dispatch(QuestAction.UpdateNote(state.note))
            }

            QuestViewState.StateType.TIMER_REPLACED -> {
                showShortToast(R.string.timer_replaced)
            }

            QuestViewState.StateType.TIMER_STOPPED -> {
                handler.removeCallbacksAndMessages(null)
                cancelAnimations(view)

                renderTimerButton(view.startStop, TimerButton.START)
                view.startStop.dispatchOnClick { QuestAction.Start }
                view.setOnClickListener(null)
                view.complete.visibility = View.GONE
            }

            QuestViewState.StateType.RUNNING -> {
                view.timerProgress.progress = state.timerProgress
                if (state.showCompletePomodoroButton) {
                    renderTimerButton(view.startStop, TimerButton.DONE)
                    view.startStop.dispatchOnClick { QuestAction.CompletePomodoro }
                }
            }

            QuestViewState.StateType.SUB_QUEST_ADDED -> {
                view.newSubQuestName.setText("")
                view.addSubQuest.invisible()
            }

            QuestViewState.StateType.QUEST_COMPLETED ->
                showCompletedQuest(state.quest!!.id)
        }
    }

    private fun renderTags(
        view: View,
        tags: List<Tag>
    ) {
        view.tagList.removeAllViews()

        val inflater = LayoutInflater.from(activity!!)
        tags.forEach { tag ->
            val item = inflater.inflate(R.layout.item_quest_tag_list, view.tagList, false)
            renderTag(item, tag)
            view.tagList.addView(item)
        }
    }

    private fun renderTag(view: View, tag: Tag) {
        view.tagName.text = tag.name
        val indicator = view.tagName.compoundDrawablesRelative[0] as GradientDrawable
        indicator.setColor(colorRes(tag.color.androidColor.color500))

    }

    private fun renderSubQuests(state: QuestViewState, view: View) {
        val adapter = view.subQuestList.adapter as SubQuestAdapter
        adapter.updateAll(state.subQuestViewModels)
        view.addSubQuest.setOnClickListener {
            addSubQuest(view)
        }

        view.newSubQuestName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addSubQuest(view)
            }
            true
        }

        val animator = ObjectAnimator.ofInt(
            view.subQuestListProgress,
            "progress",
            view.subQuestListProgress.progress,
            state.subQuestListProgressPercent
        )
        animator.duration = intRes(android.R.integer.config_shortAnimTime).toLong()
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {

                val drawable = view.subQuestListProgress.progressDrawable as LayerDrawable
                val backgroundDrawable = drawable.getDrawable(0)
                val backgroundColor =
                    if
                            (state.allSubQuestsDone) colorRes(R.color.md_green_700)
                    else
                        attrData(R.attr.colorPrimaryDark)

                backgroundDrawable.setColorFilter(
                    backgroundColor,
                    PorterDuff.Mode.SRC_ATOP
                )

                val progressDrawable = drawable.getDrawable(1)
                val progressColor =
                    if
                            (state.allSubQuestsDone) colorRes(R.color.md_green_500)
                    else
                        attrData(R.attr.colorPrimary)
                progressDrawable.setColorFilter(
                    progressColor,
                    PorterDuff.Mode.SRC_ATOP
                )
            }
        })
        animator.start()



        if (state.hasSubQuests) {
            view.subQuestListProgressLabel.text = "${state.subQuestListProgressPercent}%"
            view.subQuestListProgressLabel.visible()
            view.doneLabel.setText(R.string.done)
            view.doneLabel.setAllCaps(true)
        } else {
            view.subQuestListProgressLabel.gone()
            view.doneLabel.text = stringRes(R.string.empty_sub_quests)
            view.doneLabel.setAllCaps(false)
        }

    }

    private fun addSubQuest(view: View) {
        val name = view.newSubQuestName.text.toString()
        dispatch(QuestAction.AddSubQuest(name))
    }

    private fun cancelAnimations(view: View) {
        notImportantViews().forEach {
            it.animate().cancel()
            it.alpha = 1f
        }

        view.startStop.animate().cancel()
        view.startStop.y = originalTimerButtonsY(view)

        view.complete.animate().cancel()
        view.complete.y = originalTimerButtonsY(view)

        val childCount = view.timerProgressContainer.childCount
        for (i in 0 until childCount) {
            val child = view.timerProgressContainer.getChildAt(i)
            child.animate().cancel()
            child.alpha = 1f
        }
    }

    private fun originalTimerButtonsY(view: View) =
        view.timerLabel.y + view.timerLabel.height + ViewUtils.dpToPx(32f, view.context)

    private fun renderTypeSwitch(view: View, state: QuestViewState) {
        view.timerType.visible = state.showTimerTypeSwitch
        view.timerType.isChecked = state.timerType == QuestViewState.TimerType.POMODORO
        view.timerType.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) dispatch(QuestAction.ShowPomodoroTimer)
            else dispatch(QuestAction.ShowCountDownTimer)
        }
    }

    private fun startTimer(view: View, state: QuestViewState) {

        renderTimerProgress(view, state)
        renderTypeSwitch(view, state)
        renderTimerIndicatorsProgress(view, state)

        handler.removeCallbacksAndMessages(null)
        cancelAnimations(view)

        var updateTimer = {}

        updateTimer = {
            dispatch(QuestAction.Tick)
            handler.postDelayed(updateTimer, 1000)
        }

        handler.postDelayed(updateTimer, 1000)

        renderTimerButton(view.startStop, TimerButton.STOP)
        view.startStop.dispatchOnClick { QuestAction.Stop }

        if (state.timerType == QuestViewState.TimerType.POMODORO) {
            view.pomodoroIndicatorsGroup.visible = true
            playBlinkIndicatorAnimation(view.timerProgressContainer.getChildAt(state.currentProgressIndicator))
        } else {
            view.complete.visibility = View.VISIBLE
            view.pomodoroIndicatorsGroup.visible = false
            view.complete.dispatchOnClick { QuestAction.CompleteQuest }
        }

        view.timerProgressLayout.setOnClickListener {
            playShowNotImportantViewsAnimation(view)
        }
        playHideNotImportantViewsAnimation(view)
    }

    private fun renderTimerProgress(
        view: View,
        state: QuestViewState
    ) {
        view.timerProgress.max = state.maxTimerProgress
        view.timerProgress.secondaryProgress = state.maxTimerProgress
        view.timerProgress.progress = state.timerProgress
    }

    private fun playBlinkIndicatorAnimation(view: View, reverse: Boolean = false) {
        view
            .animate()
            .alpha(if (reverse) 1f else 0f)
            .setDuration(mediumAnimTime)
            .withEndAction {
                playBlinkIndicatorAnimation(view, !reverse)
            }
            .start()
    }

    private fun renderTimerButton(button: ImageView, type: TimerButton) {
        val iconImage = when (type) {
            TimerButton.START -> Ionicons.Icon.ion_play
            TimerButton.STOP -> Ionicons.Icon.ion_stop
            TimerButton.DONE -> Ionicons.Icon.ion_android_done
        }

        val icon = IconicsDrawable(button.context)
            .icon(iconImage)
            .color(attrData(R.attr.colorAccent))
            .sizeDp(22)

        button.setImageDrawable(icon)
    }

    private fun playShowNotImportantViewsAnimation(view: View) {
        view.startStop
            .animate()
            .y(originalTimerButtonsY(view))
            .setDuration(shortAnimTime)
            .withEndAction {
                notImportantViews().forEach {
                    it
                        .animate()
                        .alpha(1f)
                        .setDuration(longAnimTime)
                        .setStartDelay(0)
                        .withEndAction {
                            playHideNotImportantViewsAnimation(view)
                        }
                        .start()
                }
            }
            .start()

        view.complete
            .animate()
            .y(originalTimerButtonsY(view))
            .setDuration(shortAnimTime)
            .start()
    }

    private fun playHideNotImportantViewsAnimation(view: View) {
        notImportantViews().forEach {
            it
                .animate()
                .alpha(0f)
                .setDuration(longAnimTime)
                .setStartDelay(3000)
                .withEndAction {
                    val centerY = view.timerProgress.y + view.timerProgress.height / 2
                    val y = centerY - view.startStop.height / 2
                    view.startStop
                        .animate()
                        .y(y)
                        .setDuration(shortAnimTime)
                        .start()

                    view.complete
                        .animate()
                        .y(y)
                        .setDuration(shortAnimTime)
                        .start()
                }
                .start()
        }

    }

    private fun renderTimerIndicatorsProgress(view: View, state: QuestViewState) {
        view.timerProgressContainer.removeAllViews()
        state.pomodoroProgress.forEach {
            addProgressIndicator(view, it)
        }
    }

    private fun addProgressIndicator(view: View, progress: PomodoroProgress) {
        val progressView = createProgressView(view)
        val progressDrawable = resources!!.getDrawable(
            R.drawable.timer_progress_item,
            view.context.theme
        ) as GradientDrawable

        when (progress) {
            PomodoroProgress.INCOMPLETE_WORK -> {
                progressDrawable.setColor(colorRes(R.color.md_grey_300))
            }

            PomodoroProgress.COMPLETE_WORK -> {
                progressDrawable.setColor(attrData(R.attr.colorAccent))
            }

            PomodoroProgress.INCOMPLETE_SHORT_BREAK -> {
                progressDrawable.setColor(colorRes(R.color.md_grey_300))
                progressView.setScale(0.5f)
            }

            PomodoroProgress.COMPLETE_SHORT_BREAK -> {
                progressDrawable.setColor(attrData(R.attr.colorAccent))
                progressView.setScale(0.5f)
            }

            PomodoroProgress.INCOMPLETE_LONG_BREAK -> {
                progressDrawable.setColor(colorRes(R.color.md_grey_300))
                progressView.setScale(0.75f)
            }

            PomodoroProgress.COMPLETE_LONG_BREAK -> {
                progressDrawable.setColor(attrData(R.attr.colorAccent))
                progressView.setScale(0.75f)
            }
        }
        progressView.timerItemProgress.background = progressDrawable

        if (view.timerProgressContainer.childCount > 0) {
            val lp = progressView.layoutParams as ViewGroup.MarginLayoutParams
            lp.marginStart = ViewUtils.dpToPx(4f, view.context).toInt()
        }

        view.timerProgressContainer.addView(progressView)
    }

    private fun notImportantViews(): List<View> {
        val views = view!!.notImportantGroup.views().toMutableList()
        views.add(view!!.timerLabel)
        return views
    }

    private fun showCompletedQuest(questId: String) {
        exitFullScreen()
        pushWithRootRouter(RouterTransaction.with(CompletedQuestViewController(questId)))
    }

    private val QuestViewState.subQuestViewModels: List<SubQuestViewModel>
        get() = subQuests.map {
            SubQuestViewModel(
                name = it.name,
                isCompleted = it.completedAtDate != null
            )
        }

    enum class TimerButton {
        START, STOP, DONE
    }

    data class SubQuestViewModel(
        val name: String,
        val isCompleted: Boolean
    ) : RecyclerViewViewModel {
        override val id: String
            get() = name + isCompleted
    }

    inner class SubQuestAdapter :
        BaseRecyclerViewAdapter<SubQuestViewModel>(R.layout.item_quest_sub_quest) {

        override fun onBindViewModel(vm: SubQuestViewModel, view: View, holder: SimpleViewHolder) {

            view.subQuestCheckBox.setOnCheckedChangeListener(null)

            view.subQuestCheckBox.isChecked = vm.isCompleted

            view.subQuestCheckBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    dispatch(QuestAction.CompleteSubQuest(holder.adapterPosition))
                } else {
                    dispatch(QuestAction.UndoCompletedSubQuest(holder.adapterPosition))
                }
            }

            view.editSubQuestName.setText(vm.name)

            if (vm.isCompleted) {
                view.editSubQuestName.paintFlags = view.editSubQuestName.paintFlags or
                        Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                view.editSubQuestName.paintFlags = view.editSubQuestName.paintFlags and
                        Paint.STRIKE_THRU_TEXT_FLAG.inv()

            }

            view.editSubQuestName.setOnFocusChangeListener { _, hasFocus ->

                val adapterPosition = holder.adapterPosition
                if (adapterPosition == RecyclerView.NO_POSITION) {
                    return@setOnFocusChangeListener
                }

                if (hasFocus) {
                    startEdit(view)
                } else {
                    dispatch(
                        QuestAction.SaveSubQuestName(
                            view.editSubQuestName.text.toString(),
                            adapterPosition
                        )
                    )
                }

            }

            view.editSubQuestName.setOnEditTextImeBackListener(object : EditTextImeBackListener {
                override fun onImeBack(ctrl: EditTextBackEvent, text: String) {
                    enterFullScreen()
                }
            })

            view.reorderButton.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper.startDrag(holder)
                }
                false
            }

            view.removeButton.setOnClickListener {
                removeAt(holder.adapterPosition)
                dispatch(QuestAction.RemoveSubQuest(holder.adapterPosition))
            }
        }

        private fun startEdit(view: View) {
            disableEditForAllSubQuests()
            view.reorderButton.gone()
            view.removeButton.visible()
            view.editSubQuestName.requestFocus()
            ViewUtils.showKeyboard(view.context, view.editSubQuestName)
            view.editSubQuestName.setSelection(view.editSubQuestName.length())
        }
    }

    private fun disableEditForAllSubQuests() {
        view!!.subQuestList.children.forEach {
            it.removeButton.gone()
            it.reorderButton.visible()
        }
    }

    companion object {
        const val TAG = "QuestViewController"

        fun routerTransaction(questId: String) =
            RouterTransaction.with(QuestViewController(questId)).tag(
                QuestViewController.TAG
            )
                .pushChangeHandler(VerticalChangeHandler())
                .popChangeHandler(VerticalChangeHandler())
    }
}

enum class PomodoroProgress {
    INCOMPLETE_SHORT_BREAK,
    COMPLETE_SHORT_BREAK,
    INCOMPLETE_LONG_BREAK,
    COMPLETE_LONG_BREAK,
    INCOMPLETE_WORK,
    COMPLETE_WORK
}