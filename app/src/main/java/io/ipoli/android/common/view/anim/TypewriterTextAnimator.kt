package io.ipoli.android.common.view.anim

import android.animation.Animator
import android.animation.TimeInterpolator
import android.widget.TextView

class TypewriterTextAnimator private constructor(
    private val textView: TextView,
    private val text: String,
    private val typeSpeed: Int = DEFAULT_TYPE_SPEED
) : Animator() {

    private var isRunning = false
    private var textIndex = 0
    private var startDelay = 0L

    private val addCharRunnable = AddCharRunnable()

    companion object {

        private const val DEFAULT_TYPE_SPEED = 40

        fun of(
            textView: TextView,
            text: String,
            typeSpeed: Int = DEFAULT_TYPE_SPEED
        ) = TypewriterTextAnimator(textView, text, typeSpeed)
    }

    override fun isRunning() = isRunning

    override fun getDuration() = (text.length * typeSpeed).toLong()

    override fun getStartDelay() = startDelay

    override fun setStartDelay(startDelay: Long) {
        this.startDelay = startDelay
    }

    override fun setInterpolator(value: TimeInterpolator) {
        // No support for interpolation
    }

    override fun setDuration(duration: Long): Animator {
        // Do not support duration setting
        return this
    }

    override fun cancel() {
        isRunning = false
        listeners?.toMutableList()?.forEach {
            it.onAnimationCancel(this)
        }
        listeners?.toMutableList()?.forEach {
            it.onAnimationEnd(this)
        }
    }

    override fun end() {
        textView.text = text
        endAnimation()
    }

    private fun endAnimation() {
        isRunning = false
        listeners?.toMutableList()?.forEach {
            it.onAnimationEnd(this)
        }
    }

    override fun start() {
        isRunning = true
        textView.post(addCharRunnable)
    }

    inner class AddCharRunnable : Runnable {
        override fun run() {
            if (!isRunning) {
                return
            }

            if (textIndex > text.length) {
                endAnimation()
                return
            }

            textView.text = text.substring(0, textIndex)
            textIndex++
            textView.postDelayed(addCharRunnable, typeSpeed.toLong())
        }
    }
}