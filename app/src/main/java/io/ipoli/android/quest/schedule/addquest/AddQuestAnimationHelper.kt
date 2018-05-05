package io.ipoli.android.quest.schedule.addquest

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.support.design.widget.FloatingActionButton
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import com.bluelinelabs.conductor.RestoreViewOnCreateController
import com.bluelinelabs.conductor.RouterTransaction
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.changehandler.CircularRevealChangeHandler
import org.threeten.bp.LocalDate

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 4/25/18.
 */
class AddQuestAnimationHelper(
    private val controller: RestoreViewOnCreateController,
    private val addContainer: ViewGroup,
    private val fab: FloatingActionButton,
    private val background: View
) {

    fun openAddContainer(currentDate: LocalDate? = LocalDate.now()) {
        fab.isClickable = false
        val halfWidth = addContainer.width / 2

        val fabSet = createFabAnimator(fab, halfWidth.toFloat() - fab.width / 2)
        fabSet.start()

        fabSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                addContainer.visible()
                fab.invisible()

                animateShowAddContainer(background)

                val handler = CircularRevealChangeHandler(
                    addContainer,
                    addContainer,
                    duration = controller.shortAnimTime
                )
                val childRouter = controller.getChildRouter(addContainer, "add-quest")
                val addQuestViewController = AddQuestViewController({
                    childRouter.popCurrentController()
                    closeAddContainer()
                }, currentDate)

                childRouter.setRoot(
                    RouterTransaction.with(addQuestViewController)
                        .pushChangeHandler(handler)
                        .popChangeHandler(handler)
                )
            }
        })
    }

    fun closeAddContainer() {
        background.gone()
        val duration =
            background.resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

        val revealAnim = RevealAnimator().createWithEndRadius(
            view = addContainer,
            endRadius = (fab.width / 2).toFloat(),
            reverse = true
        )
        revealAnim.duration = duration
        revealAnim.startDelay = 300
        revealAnim.interpolator = AccelerateDecelerateInterpolator()
        revealAnim.addListener(object : AnimatorListenerAdapter() {

            override fun onAnimationEnd(animation: Animator?) {
                if (controller.view == null) {
                    return
                }
                addContainer.invisible()
                addContainer.requestFocus()
                fab.visible()

                val fabSet = createFabAnimator(
                    fab,
                    (addContainer.width - fab.width - ViewUtils.dpToPx(16f, fab.context)),
                    reverse = true
                )
                fabSet.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator?) {
                        fab.isClickable = true
                    }
                })
                fabSet.start()

            }

        })
        revealAnim.start()
    }

    private fun createFabAnimator(
        fab: FloatingActionButton,
        x: Float,
        reverse: Boolean = false
    ): AnimatorSet {
        val duration =
            fab.resources.getInteger(android.R.integer.config_shortAnimTime)
                .toLong()
        val fabTranslation = ObjectAnimator.ofFloat(fab, "x", x)

        val fabColor = controller.attrData(R.attr.colorAccent)
        val whiteColor = controller.colorRes(R.color.md_white)

        val startColor = if (reverse) whiteColor else fabColor
        val endColor = if (reverse) fabColor else whiteColor

        val rgbAnim = ObjectAnimator.ofArgb(
            fab,
            "backgroundTint",
            startColor, endColor
        )
        rgbAnim.addUpdateListener({ animation ->
            val value = animation.animatedValue as Int
            fab.backgroundTintList = ColorStateList.valueOf(value)
        })

        return AnimatorSet().also {
            it.playTogether(fabTranslation, rgbAnim)
            it.interpolator = AccelerateDecelerateInterpolator()
            it.duration = duration
        }
    }

    private fun animateShowAddContainer(background: View) {
        background.alpha = 0f
        background.visible()
        background.animate().alpha(1f).setDuration(controller.longAnimTime).start()
    }

}