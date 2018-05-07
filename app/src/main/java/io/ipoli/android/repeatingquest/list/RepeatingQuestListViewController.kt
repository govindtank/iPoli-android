package io.ipoli.android.repeatingquest.list

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.annotation.ColorRes
import android.support.v4.widget.TextViewCompat
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.ionicons_typeface_library.Ionicons
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.text.DateFormatter
import io.ipoli.android.common.text.DurationFormatter
import io.ipoli.android.common.view.*
import io.ipoli.android.common.view.recyclerview.BaseRecyclerViewAdapter
import io.ipoli.android.common.view.recyclerview.RecyclerViewViewModel
import io.ipoli.android.common.view.recyclerview.SimpleViewHolder
import io.ipoli.android.repeatingquest.add.AddRepeatingQuestViewController
import io.ipoli.android.repeatingquest.entity.repeatType
import io.ipoli.android.repeatingquest.list.RepeatingQuestListViewState.StateType.CHANGED
import io.ipoli.android.repeatingquest.show.RepeatingQuestViewController
import kotlinx.android.synthetic.main.animation_empty_list.view.*
import kotlinx.android.synthetic.main.controller_repeating_quest_list.view.*
import kotlinx.android.synthetic.main.item_repeating_quest.view.*
import kotlinx.android.synthetic.main.view_loader.view.*

/**
 * Created by Polina Zhelyazkova <polina@mypoli.fun>
 * on 2/14/18.
 */
class RepeatingQuestListViewController(args: Bundle? = null) :
    ReduxViewController<RepeatingQuestListAction, RepeatingQuestListViewState, RepeatingQuestListReducer>(
        args
    ) {

    override val reducer = RepeatingQuestListReducer

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {

        val view = inflater.inflate(
            R.layout.controller_repeating_quest_list, container, false
        )
        view.repeatingQuestList.layoutManager =
                LinearLayoutManager(container.context, LinearLayoutManager.VERTICAL, false)
        view.repeatingQuestList.adapter = RepeatingQuestAdapter()

        view.addRepeatingQuest.setOnClickListener {
            rootRouter.pushController(AddRepeatingQuestViewController.routerTransaction)
        }
        view.emptyAnimation.setAnimation("empty_repeating_quest_list.json")
        return view
    }

    override fun onCreateLoadAction() =
        RepeatingQuestListAction.LoadData

    override fun onAttach(view: View) {
        super.onAttach(view)
        toolbarTitle = stringRes(R.string.drawer_repeating_quests)
    }

    override fun render(state: RepeatingQuestListViewState, view: View) {
        when (state.type) {
            CHANGED -> {
                view.loader.visible = false

                if (state.showEmptyView) {
                    view.repeatingQuestList.visible = false
                    view.emptyContainer.visible = true
                    view.emptyAnimation.playAnimation()
                    view.emptyTitle.setText(R.string.empty_repeating_quests_title)
                    view.emptyText.setText(R.string.empty_repeating_quests_text)
                } else {
                    view.repeatingQuestList.visible = true
                    view.emptyContainer.visible = false
                    view.emptyAnimation.pauseAnimation()
                }

                (view.repeatingQuestList.adapter as RepeatingQuestAdapter).updateAll(
                    state.toViewModels(
                        view.context
                    )
                )
            }
        }
    }

    data class TagViewModel(val name: String, @ColorRes val color: Int)

    data class RepeatingQuestViewModel(
        override val id: String,
        val name: String,
        val tags: List<TagViewModel>,
        val icon: IIcon,
        @ColorRes val color: Int,
        val next: String,
        val completedCount: Int,
        val allCount: Int,
        val isCompleted: Boolean,
        val frequency: String
    ) : RecyclerViewViewModel

    inner class RepeatingQuestAdapter :
        BaseRecyclerViewAdapter<RepeatingQuestViewModel>(R.layout.item_repeating_quest) {

        override fun onBindViewModel(
            vm: RepeatingQuestViewModel,
            view: View,
            holder: SimpleViewHolder
        ) {
            view.rqName.text = vm.name

            if (vm.tags.isNotEmpty()) {
                view.rqTagName.visible()
                renderTag(view, vm.tags.first())
            } else {
                view.rqTagName.gone()
            }

            view.rqIcon.backgroundTintList =
                    ColorStateList.valueOf(colorRes(vm.color))
            view.rqIcon.setImageDrawable(
                IconicsDrawable(view.context)
                    .icon(vm.icon)
                    .colorRes(R.color.md_white)
                    .paddingDp(3)
                    .sizeDp(24)
            )
            view.rqNext.text = vm.next
            view.rqFrequency.text = vm.frequency

            val progressBar = view.rqProgressBar
            val progress = view.rqProgress
            if (vm.isCompleted) {
                ViewUtils.hideViews(progressBar, progress)
            } else {
                ViewUtils.showViews(progressBar, progress)
                progressBar.max = vm.allCount
                progressBar.progress = vm.completedCount
                progressBar.progressTintList = ColorStateList.valueOf(colorRes(vm.color))
                progress.text = "${vm.completedCount}/${vm.allCount}"
            }

            view.onDebounceClick {
                rootRouter.pushController(
                    RepeatingQuestViewController.routerTransaction(vm.id)
                )
            }
        }

        private fun renderTag(view: View, tag: TagViewModel) {
            view.rqTagName.text = tag.name
            TextViewCompat.setTextAppearance(
                view.rqTagName,
                R.style.TextAppearance_AppCompat_Caption
            )

            val indicator = view.rqTagName.compoundDrawablesRelative[0] as GradientDrawable
            indicator.mutate()
            val size = ViewUtils.dpToPx(8f, view.context).toInt()
            indicator.setSize(size, size)
            indicator.setColor(colorRes(tag.color))
            view.rqTagName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                indicator,
                null,
                null,
                null
            )
        }
    }

    private fun RepeatingQuestListViewState.toViewModels(context: Context): List<RepeatingQuestViewModel> {
        val frequencies = stringsRes(R.array.repeating_quest_frequencies)
        return repeatingQuests.map {
            val next = when {
                it.isCompleted -> stringRes(R.string.completed)
                it.nextDate != null -> {
                    var res = stringRes(
                        R.string.repeating_quest_next,
                        DateFormatter.formatWithoutYear(context, it.nextDate)
                    )
                    res += if (it.startTime != null) {
                        " ${it.startTime} - ${it.endTime}"
                    } else {
                        " " + stringRes(
                            R.string.quest_for_time,
                            DurationFormatter.formatShort(view!!.context, it.duration)
                        )
                    }
                    res
                }
                else -> stringRes(
                    R.string.repeating_quest_next,
                    stringRes(R.string.unscheduled)
                )
            }
            RepeatingQuestListViewController.RepeatingQuestViewModel(
                id = it.id,
                name = it.name,
                tags = it.tags.map { TagViewModel(it.name, it.color.androidColor.color500) },
                icon = it.icon?.let { AndroidIcon.valueOf(it.name).icon }
                        ?: Ionicons.Icon.ion_android_clipboard,
                color = AndroidColor.valueOf(it.color.name).color500,
                next = next,
                completedCount = it.periodProgress!!.completedCount,
                allCount = it.periodProgress.allCount,
                isCompleted = it.isCompleted,
                frequency = frequencies[it.repeatPattern.repeatType.ordinal]
            )
        }
    }
}