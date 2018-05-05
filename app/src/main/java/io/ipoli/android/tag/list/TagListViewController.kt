package io.ipoli.android.tag.list

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.support.v4.widget.TextViewCompat
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.tag.Tag
import io.ipoli.android.tag.edit.EditTagViewController
import io.ipoli.android.tag.show.TagViewController
import kotlinx.android.synthetic.main.animation_empty_list.view.*
import kotlinx.android.synthetic.main.controller_tag_list.view.*
import kotlinx.android.synthetic.main.item_tag_list.view.*
import kotlinx.android.synthetic.main.view_loader.view.*

/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 04/03/2018.
 */
class TagListViewController(args: Bundle? = null) :
    ReduxViewController<TagListAction, TagListViewState, TagListReducer>(
        args = args
    ) {
    override val reducer = TagListReducer

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.controller_tag_list, container, false)

        view.tagAdd.dispatchOnClick { TagListAction.AddTag }

        initEmptyView(view)
        return view
    }

    private fun initEmptyView(view: View) {
        view.emptyAnimation.setAnimation("empty_tag_list.json")
        view.emptyTitle.setText(R.string.empty_tag_list_title)
        view.emptyText.setText(R.string.empty_tag_list_message)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        toolbarTitle = stringRes(R.string.tags)
    }

    override fun onCreateLoadAction() = TagListAction.Load

    override fun render(state: TagListViewState, view: View) {
        when (state) {
            is TagListViewState.Loading -> {
                view.loader.visible()
                view.emptyContainer.invisible()
            }

            is TagListViewState.Changed -> {
                view.loader.invisible()
                view.emptyContainer.invisible()
                view.emptyAnimation.pauseAnimation()

                view.tagList.removeAllViews()

                state.tags.forEach {
                    val item = view.tagList.inflate(R.layout.item_tag_list)
                    renderTag(it, item)
                    view.tagList.addView(item)
                }
            }

            is TagListViewState.Empty -> {
                view.loader.invisible()
                view.tagList.removeAllViews()
                view.emptyContainer.visible()
                view.emptyAnimation.playAnimation()
            }

            TagListViewState.ShowAdd -> {
                pushWithRootRouter(
                    RouterTransaction.with(
                        EditTagViewController()
                    )
                        .pushChangeHandler(VerticalChangeHandler())
                        .popChangeHandler(VerticalChangeHandler())
                )
            }
        }
    }

    private fun renderTag(
        tag: Tag,
        view: View
    ) {

        view.tagBackground.setOnClickListener {
            pushWithRootRouter(
                TagViewController.routerTransaction(tag.id)
            )
        }

        val color = tag.color.androidColor
        val background = view.tagBackground.background as GradientDrawable
        background.setColor(colorRes(color.color500))

        val countBackground = view.questCount.background as GradientDrawable
        countBackground.setStroke(
            ViewUtils.dpToPx(2f, view.context).toInt(),
            colorRes(R.color.md_white)
        )
        countBackground.setColor(colorRes(color.color500))

        val i = tag.icon?.let { AndroidIcon.valueOf(it.name).icon }
                ?: MaterialDesignIconic.Icon.gmi_label

        view.tagIcon.setImageDrawable(
            IconicsDrawable(activity!!)
                .icon(i)
                .paddingDp(3)
                .colorRes(R.color.md_white)
                .sizeDp(24)
        )
        view.tagName.text = tag.name

        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            view.questCount,
            8,
            12,
            1,
            TypedValue.COMPLEX_UNIT_SP
        )

        view.tagFavorite.setImageResource(
            if (tag.isFavorite) R.drawable.ic_favorite_white_24dp
            else R.drawable.ic_favorite_outline_white_24dp
        )

        view.tagFavorite.dispatchOnClick {
            if (tag.isFavorite) TagListAction.Unfavorite(tag)
            else TagListAction.Favorite(tag)
        }

        view.questCount.text = tag.questCount.toString()
    }

}