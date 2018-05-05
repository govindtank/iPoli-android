package io.ipoli.android.note

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import io.ipoli.android.R
import io.ipoli.android.common.ViewUtils
import io.ipoli.android.common.redux.android.ReduxViewController
import io.ipoli.android.common.view.*
import io.ipoli.android.note.NoteViewState.Type.*
import kotlinx.android.synthetic.main.controller_note.view.*
import kotlinx.android.synthetic.main.dialog_controller_note.view.*


/**
 * Created by Venelin Valkov <venelin@mypoli.fun>
 * on 03/26/2018.
 */

class NoteViewController(args: Bundle? = null) :
    ReduxViewController<NoteAction, NoteViewState, NoteReducer>(args) {
    override val reducer = NoteReducer

    private lateinit var note: String
    private var resultListener: (String) -> Unit = {}
    private var closeListener: (() -> Unit)? = null
    private var startInEditMode: Boolean = false

    constructor(
        note: String,
        resultListener: (String) -> Unit,
        startInEditMode: Boolean = false,
        closeListener: (() -> Unit)? = null
    ) : this() {
        this.note = note
        this.resultListener = resultListener
        this.startInEditMode = startInEditMode
        this.closeListener = closeListener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup,
        savedViewState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.controller_note, null)
        view.emptyNoteHint.setMarkdown(stringRes(R.string.empty_note_hint))
        return view
    }

    override fun onCreateLoadAction() =
        NoteAction.Load(note, startInEditMode, closeListener != null)

    override fun colorLayoutBars() {

    }

    override fun render(state: NoteViewState, view: View) {
        view.emptyNoteHint.visible = state.showEmpty
        view.noteText.visible = state.showText

        when (state.type) {
            VIEW -> {
                view.noteAction.invisible()
                view.noteText.setMarkdown(state.text)
                view.editNote.dispatchOnClick { NoteAction.Edit }
                renderClose(state, view)

                if (state.isPreview) {
                    renderPreview(view, state)
                }
            }

            SAVED -> {
                resultListener(state.text)
                view.noteAction.invisible()
                if (state.isClosable) {
                    closeListener!!.invoke()
                }
            }

            EDIT -> {
                renderClose(state, view)

                view.noteAction.visible()
                view.noteAction.text = stringRes(R.string.preview)
                view.noteAction.setOnClickListener { dispatch(NoteAction.Preview(view.editNoteText.text.toString())) }

                view.editNoteText.setText(state.text)
                view.editNoteText.animate().apply {
                    alpha(1f)
                    duration = shortAnimTime
                    interpolator = AccelerateInterpolator()
                    setListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            view.editNoteText.visible()
                            view.editNoteText.post {
                                view.editNoteText.setSelection(view.editNoteText.length())
                                view.editNoteText.requestFocus()
                                ViewUtils.showKeyboard(view.context, view.editNoteText)
                            }
                        }
                    })
                }
                fadeOut(view.editNote)
                fadeOut(view.noteText)

                view.editNoteText.setOnEditTextImeBackListener(object : EditTextImeBackListener {
                    override fun onImeBack(ctrl: EditTextBackEvent, text: String) {
                        enterFullScreen()
                    }
                })
            }

            CHANGED -> {
                view.noteText.setMarkdown(state.text)
                view.editNoteText.setText(state.text)
            }
        }
    }

    private fun renderPreview(view: View, state: NoteViewState) {
        enterFullScreen()
        view.noteAction.visible()
        view.noteAction.text = stringRes(R.string.save)
        view.noteAction.setOnClickListener {
            val note = view.editNoteText.text.toString()
            dispatch(NoteAction.Save(note))
        }

        view.editNote.dispatchOnClick { NoteAction.Edit }
        ViewUtils.hideKeyboard(view)
        view.noteText.setMarkdown(state.text)
        view.editNoteText.animate().apply {
            alpha(0f)
            duration = shortAnimTime
            interpolator = AccelerateInterpolator()
            setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    view.editNoteText.gone()
                    view.editNoteText.post {
                        activity?.invalidateOptionsMenu()
                    }
                }
            })
        }

        fadeIn(view.editNote)
        fadeIn(view.noteText)
    }

    private fun renderClose(state: NoteViewState, view: View) {
        if (state.isClosable) {
            view.closeNote.visible()
            view.closeNote.setOnClickListener {
                closeListener?.invoke()
            }
        } else {
            view.closeNote.invisible()
        }
    }

    private fun fadeIn(view: View) {
        view.visible()
        view.animate().apply {
            alpha(1f)
            duration = shortAnimTime
            interpolator = AccelerateInterpolator()
            setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    view.alpha = 1f
                }
            })
        }
    }

    private fun fadeOut(view: View) {
        view.animate().apply {
            alpha(0f)
            duration = shortAnimTime
            interpolator = DecelerateInterpolator()
            setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    view.gone()
                }
            })
        }
    }
}

class NoteDialogViewController(args: Bundle? = null) : BaseFullscreenDialogController(args) {

    private lateinit var note: String
    private lateinit var resultListener: (String) -> Unit

    constructor(
        note: String,
        resultListener: (String) -> Unit
    ) : this() {
        this.note = note
        this.resultListener = resultListener
    }

    override fun onCreateContentView(inflater: LayoutInflater, savedViewState: Bundle?): View {
        val view = inflater.inflate(R.layout.dialog_controller_note, null)

        setChildController(
            view.noteContainer,
            NoteViewController(note, resultListener, true, {
                dismiss()
            })
        )


        view.closeNote.setOnClickListener {
            dismiss()
        }
        return view
    }

    override fun onDetach(view: View) {
        exitFullScreen()
        super.onDetach(view)
    }

}