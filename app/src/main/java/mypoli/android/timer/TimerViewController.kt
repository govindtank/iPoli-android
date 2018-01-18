package mypoli.android.timer

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.ionicons_typeface_library.Ionicons
import kotlinx.android.synthetic.main.controller_timer.view.*
import kotlinx.android.synthetic.main.item_timer_progress.view.*
import mypoli.android.R
import mypoli.android.common.ViewUtils
import mypoli.android.common.mvi.MviViewController
import mypoli.android.common.view.*
import space.traversal.kapsule.required

/**
 * Created by Venelin Valkov <venelin@ipoli.io>
 * on 6.01.18.
 */
class TimerViewController :
    MviViewController<TimerViewState, TimerViewController, TimerPresenter, TimerIntent> {

    private lateinit var questId: String

    private lateinit var handler: Handler

    private val presenter by required { timerPresenter }

    constructor(args: Bundle? = null) : super(args)

    constructor(questId: String) : super() {
        this.questId = questId
    }

    override fun createPresenter() = presenter

    private lateinit var updateTimer: () -> Unit

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.controller_timer, container, false)

        renderStartStopButton(view.startStop, true)

        return view
    }

    private fun startTimer(view: View) {
        handler = Handler(Looper.getMainLooper())
        updateTimer = {
            view.timerProgress.progress = view.timerProgress.progress + 1
            handler.postDelayed(updateTimer, 1000)
        }

        handler.postDelayed(updateTimer, 1000)
    }

    private fun createProgressView(view: View) =
        LayoutInflater.from(view.context).inflate(
            R.layout.item_timer_progress,
            view.timerProgressContainer,
            false
        )

    override fun onAttach(view: View) {
        super.onAttach(view)
        enterFullScreen()
        send(TimerIntent.LoadData(questId))
    }

    override fun onDetach(view: View) {
        exitFullScreen()
        super.onDetach(view)
    }

    override fun render(state: TimerViewState, view: View) {

        view.questName.text = state.questName
        view.timerLabel.text = state.timerLabel

        when (state.type) {
            TimerViewState.StateType.SHOW_POMODORO -> {
                view.startStop.sendOnClick(TimerIntent.Start)
                renderTimerProgress(state, view)
            }

            TimerViewState.StateType.TIMER_STARTED -> {
                view.timerProgress.max = state.maxTimerProgress
                view.timerProgress.secondaryProgress = state.maxTimerProgress
                view.timerProgress.progress = state.timerProgress
                handler = Handler(Looper.getMainLooper())
                updateTimer = {
                    send(TimerIntent.Tick)
                    handler.postDelayed(updateTimer, 1000)
                }

                handler.postDelayed(updateTimer, 1000)

                renderStartStopButton(view.startStop, false)
                view.startStop.sendOnClick(TimerIntent.Stop)

                playBlinkIndicatorAnimation(view.timerProgressContainer.getChildAt(state.currentProgressIndicator))

                view.setOnClickListener {
                    playShowNotImportantViewsAnimation(view)
                }
                playHideNotImportantViewsAnimation(view)
            }

            TimerViewState.StateType.TIMER_STOPPED -> {
                handler.removeCallbacksAndMessages(null)
                view.notImportantGroup.views().forEach {
                    it.animate().cancel()
                    it.alpha = 1f
                }
                val indicator =
                    view.timerProgressContainer.getChildAt(state.currentProgressIndicator)
                indicator.animate().cancel()
                indicator.alpha = 1f

                renderStartStopButton(view.startStop, true)
                view.startStop.sendOnClick(TimerIntent.Start)
                view.setOnClickListener(null)
            }

            TimerViewState.StateType.RUNNING -> {
                view.timerProgress.progress = state.timerProgress
            }

        }
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

    private fun renderStartStopButton(button: ImageView, start: Boolean) {
        val icon = IconicsDrawable(button.context)
            .icon(if (start) Ionicons.Icon.ion_play else Ionicons.Icon.ion_stop)
            .color(attr(R.attr.colorAccent))
            .sizeDp(22)

        button.setImageDrawable(icon)
    }

    private fun playShowNotImportantViewsAnimation(view: View) {
        view.notImportantGroup.views().forEach {
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

    private fun playHideNotImportantViewsAnimation(view: View) {
        view.notImportantGroup.views().forEach {
            it
                .animate()
                .alpha(0f)
                .setDuration(longAnimTime)
                .setStartDelay(3000)
                .start()
        }
    }

    private fun renderTimerProgress(state: TimerViewState, view: View) {
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
                progressDrawable.setColor(attr(R.attr.colorAccent))
            }

            PomodoroProgress.INCOMPLETE_SHORT_BREAK -> {
                progressDrawable.setColor(colorRes(R.color.md_grey_300))
                progressView.setScale(0.5f)
            }

            PomodoroProgress.COMPLETE_SHORT_BREAK -> {
                progressDrawable.setColor(attr(R.attr.colorAccent))
                progressView.setScale(0.5f)
            }

            PomodoroProgress.INCOMPLETE_LONG_BREAK -> {
                progressDrawable.setColor(colorRes(R.color.md_grey_300))
                progressView.setScale(0.75f)
            }

            PomodoroProgress.COMPLETE_LONG_BREAK -> {
                progressDrawable.setColor(attr(R.attr.colorAccent))
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
}

enum class PomodoroProgress() {
    INCOMPLETE_SHORT_BREAK,
    COMPLETE_SHORT_BREAK,
    INCOMPLETE_LONG_BREAK,
    COMPLETE_LONG_BREAK,
    INCOMPLETE_WORK,
    COMPLETE_WORK
}