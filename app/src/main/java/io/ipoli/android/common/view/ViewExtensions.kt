package io.ipoli.android.common.view

import android.support.annotation.LayoutRes
import android.support.constraint.Group
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.delay

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 8/27/17.
 */
var View.visible: Boolean
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.INVISIBLE
    }

fun View.setScale(scale: Float) {
    scaleX = scale
    scaleY = scale
}

fun Group.views() =
    referencedIds.map { id ->
        rootView.findViewById<View>(id)
    }

fun Group.goneViews() {
    this.visibility = View.GONE
}

fun Group.hideViews() {
    this.visibility = View.INVISIBLE
}

fun Group.showViews() {
    this.visibility = View.VISIBLE
}

fun View.gone() {
    visibility = View.GONE
}

fun View.invisible() {
    visibility = View.INVISIBLE
}

fun View.visible() {
    visibility = View.VISIBLE
}

fun View.enableClick() {
    isClickable = true
}

fun View.disableClick() {
    isClickable = false
}

fun View.visibleOrGone(isVisible: Boolean) {
    if (isVisible) visible()
    else gone()
}

val ViewGroup.children: List<View>
    get() = 0.until(childCount).map { getChildAt(it) }

fun ViewGroup.inflate(@LayoutRes layout: Int, attachToRoot: Boolean = false): View =
    LayoutInflater.from(context).inflate(layout, this, attachToRoot)

object Debounce {

    fun clickListener(action: suspend (View) -> Unit): View.OnClickListener {
        val eventActor = actor<View>(UI) {
            for (event in channel) {
                action(event)
                delay(400)
            }
            this.cancel()
        }

        return View.OnClickListener {
            eventActor.offer(it)
        }
    }
}