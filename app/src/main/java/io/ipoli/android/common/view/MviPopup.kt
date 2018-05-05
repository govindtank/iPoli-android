package io.ipoli.android.common.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.support.annotation.DrawableRes
import android.support.annotation.MainThread
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.view.*
import android.widget.RelativeLayout
import io.ipoli.android.R
import io.ipoli.android.common.AppState
import io.ipoli.android.common.NamespaceAction
import io.ipoli.android.common.UIAction
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.di.Module
import io.ipoli.android.common.mvi.Intent
import io.ipoli.android.common.mvi.MviPresenter
import io.ipoli.android.common.mvi.ViewState
import io.ipoli.android.common.mvi.ViewStateRenderer
import io.ipoli.android.common.redux.Action
import io.ipoli.android.common.redux.StateStore
import io.ipoli.android.common.redux.ViewStateReducer
import io.ipoli.android.myPoliApp
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import space.traversal.kapsule.Injects
import space.traversal.kapsule.inject
import space.traversal.kapsule.required

class PopupBackgroundLayout : RelativeLayout {
    private var onBackPressed: () -> Unit = {}

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
            onBackPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    fun setOnBackPressed(action: () -> Unit) {
        onBackPressed = action
    }
}

abstract class MviPopup<in VS : ViewState, in V : ViewStateRenderer<VS>, out P : MviPresenter<V, VS, I>, in I : Intent>
    (
    private val isAutoHide: Boolean = false,
    private val position: Position = Position.CENTER,
    @DrawableRes private val overlayBackground: Int? = R.color.md_dark_text_12
) :
    ViewStateRenderer<VS>, Injects<Module> {

    enum class Position {
        CENTER, TOP, BOTTOM
    }

    private lateinit var overlayView: PopupBackgroundLayout
    private lateinit var contentView: ViewGroup
    private lateinit var windowManager: WindowManager
    private lateinit var presenter: P
    private val autoHideHandler = Handler(Looper.getMainLooper())

    private val autoHideRunnable = {
        hide()
    }

    protected abstract fun createPresenter(): P

    abstract fun createView(inflater: LayoutInflater): View

    fun show(context: Context) {
        inject(myPoliApp.module(context))

        presenter = createPresenter()
        intentChannel = presenter.intentChannel()

        contentView = createView(LayoutInflater.from(context)) as ViewGroup
        contentView.visibility = View.INVISIBLE

        overlayView = PopupBackgroundLayout(context)
        overlayView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        overlayBackground?.let {
            overlayView.setBackgroundResource(it)
        }

        val contentLp = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        when (position) {
            Position.CENTER -> {
                contentLp.marginStart = ViewUtils.dpToPx(32f, context).toInt()
                contentLp.marginEnd = ViewUtils.dpToPx(32f, context).toInt()
                contentLp.addRule(
                    RelativeLayout.CENTER_IN_PARENT,
                    RelativeLayout.TRUE
                )
            }
            Position.TOP -> {
                contentLp.marginStart = ViewUtils.dpToPx(16f, context).toInt()
                contentLp.marginEnd = ViewUtils.dpToPx(16f, context).toInt()
                contentLp.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE)
                contentLp.topMargin = ViewUtils.dpToPx(24f, context).toInt()
            }
            else -> {
                contentLp.marginStart = ViewUtils.dpToPx(16f, context).toInt()
                contentLp.marginEnd = ViewUtils.dpToPx(16f, context).toInt()
                contentLp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                contentLp.bottomMargin = ViewUtils.dpToPx(24f, context).toInt()
            }
        }

        overlayView.addView(contentView, contentLp)

        overlayView.setOnBackPressed {
            if (!isAutoHide) {
                hide()
            }
        }
        if (!isAutoHide) {
            overlayView.setOnClickListener { hide() }
        }

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        addViewToWindowManager(overlayView)
        overlayView.post {
            presenter.onAttachView(this as V)
            playEnterAnimation(contentView)
        }
    }

    protected open fun playEnterAnimation(contentView: View) {
        val transAnim = ObjectAnimator.ofFloat(
            contentView,
            "y",
            getScreenHeight(contentView.context).toFloat(),
            contentView.y
        )
        val fadeAnim = ObjectAnimator.ofFloat(contentView, "alpha", 0f, 1f)
        transAnim.duration =
                contentView.context.resources.getInteger(android.R.integer.config_mediumAnimTime)
                    .toLong()
        fadeAnim.duration =
                contentView.context.resources.getInteger(android.R.integer.config_longAnimTime)
                    .toLong()
        val animSet = AnimatorSet()
        animSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                contentView.visible = true
            }

            override fun onAnimationEnd(animation: Animator) {
                onViewShown(contentView)
            }
        })
        animSet.playTogether(transAnim, fadeAnim)
        animSet.start()
    }

    protected open fun onViewShown(contentView: View) {

    }

    protected open fun playExitAnimation(contentView: View) {
        val transAnim = ObjectAnimator.ofFloat(
            contentView,
            "y",
            contentView.y,
            getScreenHeight(contentView.context).toFloat()
        )
        val fadeAnim = ObjectAnimator.ofFloat(contentView, "alpha", 1f, 0f)
        transAnim.duration =
                contentView.context.resources.getInteger(android.R.integer.config_shortAnimTime)
                    .toLong()
        fadeAnim.duration =
                contentView.context.resources.getInteger(android.R.integer.config_mediumAnimTime)
                    .toLong()
        val animSet = AnimatorSet()
        animSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onDestroy()
            }
        })
        animSet.playTogether(transAnim, fadeAnim)
        animSet.start()
    }

    private fun onDestroy() {
        windowManager.removeViewImmediate(overlayView)
        presenter.onDetachView()
        presenter.onDestroy()
    }

    private fun addViewToWindowManager(view: ViewGroup) {
        val focusable =
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED.or(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                .or(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowOverlayCompat.TYPE_SYSTEM_ERROR,
            focusable,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(view, layoutParams)
    }

    protected fun getScreenHeight(context: Context): Int {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getMetrics(metrics)
        return metrics.heightPixels
    }

    protected fun autoHideAfter(millis: Long) {
        require(isAutoHide)
        autoHideHandler.postDelayed(autoHideRunnable, millis)
    }

    fun hide() {
        autoHideHandler.removeCallbacksAndMessages(null)
        overlayView.setOnClickListener(null)
        overlayView.isClickable = false
        playExitAnimation(contentView)
    }

    @MainThread
    override fun render(state: VS) {
        render(state, contentView)
    }

    abstract fun render(state: VS, view: View)

    private lateinit var intentChannel: SendChannel<I>

    protected fun send(intent: I) {
        launch {
            intentChannel.send(intent)
        }
    }

    internal object WindowOverlayCompat {
        private const val ANDROID_OREO = 26
        private const val TYPE_APPLICATION_OVERLAY = 2038

        val TYPE_SYSTEM_ERROR =
            if (Build.VERSION.SDK_INT < ANDROID_OREO) WindowManager.LayoutParams.TYPE_SYSTEM_ERROR else TYPE_APPLICATION_OVERLAY
    }
}

abstract class ReduxPopup<A : Action, VS : ViewState, out VSR : ViewStateReducer<AppState, VS>>
    (
    private val isAutoHide: Boolean = false,
    private val position: Position = Position.CENTER,
    @DrawableRes private val overlayBackground: Int? = R.color.md_dark_text_12
) :
    StateStore.StateChangeSubscriber<AppState>, Injects<Module> {

    enum class Position {

        CENTER, TOP, BOTTOM
    }

    private val stateStore by required { stateStore }

    protected abstract val reducer: VSR

    private lateinit var overlayView: PopupBackgroundLayout
    private lateinit var contentView: ViewGroup
    private lateinit var windowManager: WindowManager
    private val autoHideHandler = Handler(Looper.getMainLooper())

    private val autoHideRunnable = {
        hide()
    }

    protected open var namespace: String? = null

    @Volatile
    private var currentState: VS? = null

    abstract fun createView(inflater: LayoutInflater): View

    fun show(context: Context) {
        inject(myPoliApp.module(context))

        contentView = createView(LayoutInflater.from(context)) as ViewGroup
        contentView.visibility = View.INVISIBLE

        stateStore.dispatch(UIAction.Attach(reducer))

        overlayView = PopupBackgroundLayout(context)
        overlayView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        overlayBackground?.let {
            overlayView.setBackgroundResource(it)
        }

        val contentLp = when (position) {
            Position.CENTER -> {
                val lp = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.MATCH_PARENT
                )
                lp.marginStart = ViewUtils.dpToPx(32f, context).toInt()
                lp.marginEnd = ViewUtils.dpToPx(32f, context).toInt()
                lp.addRule(
                    RelativeLayout.CENTER_IN_PARENT,
                    RelativeLayout.TRUE
                )
                lp
            }
            Position.TOP -> {
                val lp = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginStart = ViewUtils.dpToPx(16f, context).toInt()
                lp.marginEnd = ViewUtils.dpToPx(16f, context).toInt()
                lp.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE)
                lp.topMargin = ViewUtils.dpToPx(24f, context).toInt()
                lp
            }
            else -> {
                val lp = RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.MATCH_PARENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginStart = ViewUtils.dpToPx(16f, context).toInt()
                lp.marginEnd = ViewUtils.dpToPx(16f, context).toInt()
                lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                lp.bottomMargin = ViewUtils.dpToPx(24f, context).toInt()
                lp
            }
        }

        overlayView.addView(contentView, contentLp)

        overlayView.setOnBackPressed {
            if (!isAutoHide) {
                hide()
            }
        }
        if (!isAutoHide) {
            overlayView.setOnClickListener { hide() }
        }

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        addViewToWindowManager(overlayView)
        overlayView.post {
            stateStore.subscribe(this)
            onCreateLoadAction()?.let {
                dispatch(it)
            }
            playEnterAnimation(contentView)
        }
    }

    protected open fun onCreateLoadAction(): A? {
        return null
    }

    protected open fun playEnterAnimation(contentView: View) {
        val transAnim = ObjectAnimator.ofFloat(
            contentView,
            "y",
            getScreenHeight(contentView.context).toFloat(),
            contentView.y
        )
        val fadeAnim = ObjectAnimator.ofFloat(contentView, "alpha", 0f, 1f)
        transAnim.duration =
                contentView.context.resources.getInteger(android.R.integer.config_mediumAnimTime)
                    .toLong()
        fadeAnim.duration =
                contentView.context.resources.getInteger(android.R.integer.config_longAnimTime)
                    .toLong()
        val animSet = AnimatorSet()
        animSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                contentView.visible = true
            }

            override fun onAnimationEnd(animation: Animator) {
                onViewShown(contentView)
            }
        })
        animSet.playTogether(transAnim, fadeAnim)
        animSet.start()
    }

    protected open fun onViewShown(contentView: View) {

    }

    protected open fun playExitAnimation(contentView: View) {
        val transAnim = ObjectAnimator.ofFloat(
            contentView,
            "y",
            contentView.y,
            getScreenHeight(contentView.context).toFloat()
        )
        val fadeAnim = ObjectAnimator.ofFloat(contentView, "alpha", 1f, 0f)
        transAnim.duration =
                contentView.context.resources.getInteger(android.R.integer.config_shortAnimTime)
                    .toLong()
        fadeAnim.duration =
                contentView.context.resources.getInteger(android.R.integer.config_mediumAnimTime)
                    .toLong()
        val animSet = AnimatorSet()
        animSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onDestroy()
            }
        })
        animSet.playTogether(transAnim, fadeAnim)
        animSet.start()
    }

    private fun onDestroy() {
        windowManager.removeViewImmediate(overlayView)
        stateStore.unsubscribe(this)
        stateStore.dispatch(UIAction.Detach(reducer))
        currentState = null
    }

    private fun addViewToWindowManager(view: ViewGroup) {
        val focusable =
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED.or(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
                .or(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowOverlayCompat.TYPE_SYSTEM_ERROR,
            focusable,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(view, layoutParams)
    }

    protected fun getScreenHeight(context: Context): Int {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm.defaultDisplay.getMetrics(metrics)
        return metrics.heightPixels
    }

    protected fun autoHideAfter(millis: Long) {
        require(isAutoHide)
        autoHideHandler.postDelayed(autoHideRunnable, millis)
    }

    fun hide() {
        autoHideHandler.removeCallbacksAndMessages(null)
        overlayView.setOnClickListener(null)
        overlayView.isClickable = false
        playExitAnimation(contentView)
    }

    override suspend fun onStateChanged(newState: AppState) {
        if (newState.hasState(reducer.stateKey)) {
            val viewState = newState.stateFor<VS>(reducer.stateKey)
            if (viewState != currentState) {
                currentState = viewState
                withContext(UI) {
                    onRenderViewState(viewState)
                }
            }
        }
    }

    protected open fun onRenderViewState(state: VS) {
        render(state, contentView)
    }

    abstract fun render(state: VS, view: View)

    fun dispatch(action: A) {
        val a = namespace?.let {

            NamespaceAction(action, it)
        } ?: action
        stateStore.dispatch(a)
    }

    internal object WindowOverlayCompat {
        private const val ANDROID_OREO = 26
        private const val TYPE_APPLICATION_OVERLAY = 2038

        val TYPE_SYSTEM_ERROR =
            if (Build.VERSION.SDK_INT < ANDROID_OREO) WindowManager.LayoutParams.TYPE_SYSTEM_ERROR else TYPE_APPLICATION_OVERLAY
    }
}