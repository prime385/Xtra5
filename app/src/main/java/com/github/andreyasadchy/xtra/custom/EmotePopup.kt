package com.github.andreyasadchy.xtra.custom

import android.view.Gravity
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.github.andreyasadchy.xtra.ui.chat.ChatFragment
import com.github.andreyasadchy.xtra.ui.chat.ImageClickedDialog
import com.github.andreyasadchy.xtra.ui.chat.MessageClickedDialog
import com.github.andreyasadchy.xtra.ui.chat.ReplyClickedDialog
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Xtra5 custom: chat popups beside the chat. With the chat beside the video (landscape), the
 * message popup (conversation history) and the reply-thread popup open with their right edge a
 * few pixels left of the chat; the emote details popup opens centred on that same area. Without
 * room left of the chat (e.g. portrait) they open normally.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Started from UpstreamUpdates.onMainActivityCreate (no extra upstream hook).
 */
object EmotePopup {

    private const val GAP_DP = 4
    private const val MIN_WIDTH_DP = 200
    private const val PANEL_MAX_WIDTH_DP = 640 // Material's bottom sheet max width

    fun register(activity: MainActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                val chat = f.parentFragment as? ChatFragment ?: return
                when (f) {
                    is MessageClickedDialog, is ReplyClickedDialog -> f.view?.post { place(f as BottomSheetDialogFragment, chat, centred = false) }
                    is ImageClickedDialog -> f.view?.post { place(f, chat, centred = true) }
                }
            }
        }, true)
    }

    /**
     * The panel area is [chatLeft - gap - panelWidth, chatLeft - gap]. Panels fill it (right edge
     * at the chat); the emote popup is centred in it.
     */
    private fun place(dialog: BottomSheetDialogFragment, chat: ChatFragment, centred: Boolean) {
        val chatView = chat.view ?: return
        val sheet = dialog.dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val sheetParent = sheet.parent as? View ?: return
        val density = sheet.resources.displayMetrics.density
        val chatLeft = IntArray(2).also { chatView.getLocationOnScreen(it) }[0]
        val parentLeft = IntArray(2).also { sheetParent.getLocationOnScreen(it) }[0]
        val available = chatLeft - parentLeft - (GAP_DP * density).toInt()
        // Only when the chat is beside the video with room to its left.
        if (chatLeft <= parentLeft || available < MIN_WIDTH_DP * density) return
        val panelWidth = minOf((PANEL_MAX_WIDTH_DP * density).toInt(), available)
        val panelLeft = available - panelWidth
        val width = if (centred) minOf(if (sheet.width > 0) sheet.width else panelWidth, panelWidth) else panelWidth
        val left = if (centred) panelLeft + (panelWidth - width) / 2 else panelLeft
        BottomSheetBehavior.from(sheet).maxWidth = width
        val params = sheet.layoutParams as? CoordinatorLayout.LayoutParams ?: return
        params.width = width
        // TOP, as the bottom sheet expects: it positions from the top and then slides up.
        // (BOTTOM pushed the popups off the bottom of the screen in 1.27.2.)
        params.gravity = Gravity.START or Gravity.TOP
        params.leftMargin = left
        sheet.layoutParams = params
    }
}
