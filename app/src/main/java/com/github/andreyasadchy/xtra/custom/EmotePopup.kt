package com.github.andreyasadchy.xtra.custom

import android.content.res.Configuration
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.ui.chat.ImageClickedDialog
import com.github.andreyasadchy.xtra.ui.chat.MessageClickedDialog
import com.github.andreyasadchy.xtra.ui.chat.ReplyClickedDialog
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Xtra5 custom: chat popups beside the chat. With the chat beside the video (landscape), the
 * message popup (conversation history), reply-thread popup and emote details popup keep their
 * normal size and are slid sideways so their right edge is a few pixels left of the chat (they
 * are the same width, so the emote popup is centred on the same area). Portrait: unchanged.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Started from UpstreamUpdates.onMainActivityCreate (no extra upstream hook).
 */
object EmotePopup {

    private const val GAP_DP = 4

    fun register(activity: MainActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                if (f is ImageClickedDialog) enlargeImage(f)
                val chat = f.parentFragment as? ChatFragment ?: return
                if (f is MessageClickedDialog || f is ReplyClickedDialog || f is ImageClickedDialog) {
                    f.view?.post { attach(f as BottomSheetDialogFragment, chat) }
                }
            }
        }, true)
    }

    /** The emote/badge in its popup at twice the original 70dp (the largest it stays sharp). */
    private const val IMAGE_HEIGHT_DP = 140

    private fun enlargeImage(dialog: ImageClickedDialog) {
        val image = dialog.view?.findViewById<View>(R.id.image) ?: return
        val height = (IMAGE_HEIGHT_DP * image.resources.displayMetrics.density).toInt()
        if (image.layoutParams.height != height) image.layoutParams = image.layoutParams.apply { this.height = height }
    }

    /**
     * Keeps the popup at its normal size and position rules, and only slides it sideways so its
     * right edge is a few pixels left of the chat. Re-applied whenever the popup lays itself out.
     */
    private fun attach(dialog: BottomSheetDialogFragment, chat: ChatFragment) {
        val sheet = dialog.dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        if (sheet.getTag(R.id.xtra5_chat_popup) == true) return
        sheet.setTag(R.id.xtra5_chat_popup, true)
        sheet.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> slide(sheet, chat) }
        slide(sheet, chat)
    }

    private fun slide(sheet: View, chat: ChatFragment) {
        val chatView = chat.view
        val landscape = sheet.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (chatView == null || !chatView.isShown || !landscape || sheet.width == 0) {
            if (sheet.translationX != 0f) sheet.translationX = 0f
            return
        }
        val chatPanel = generateSequence(chatView) { it.parent as? View }.firstOrNull { it.id == R.id.chatLayout } ?: chatView
        val chatLeft = IntArray(2).also { chatPanel.getLocationOnScreen(it) }[0]
        val sheetLeft = IntArray(2).also { sheet.getLocationOnScreen(it) }[0] - sheet.translationX.toInt() // without our slide
        val gap = (GAP_DP * sheet.resources.displayMetrics.density).toInt()
        val target = (chatLeft - gap - (sheetLeft + sheet.width)).toFloat()
        // Slide either way so the right edge is just left of the chat (1.28.1 only slid left, so
        // with a narrow chat the popup stayed centred).
        val translation = if (chatLeft > gap) target else 0f
        if (sheet.translationX != translation) sheet.translationX = translation
    }
}
