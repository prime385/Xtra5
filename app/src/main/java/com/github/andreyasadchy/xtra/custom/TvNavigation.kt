package com.github.andreyasadchy.xtra.custom

import android.animation.ValueAnimator
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragment
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragment
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragment
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.card.MaterialCardView
import java.util.WeakHashMap

/**
 * Xtra5 custom: easier browsing, mostly for remote (D-pad) navigation.
 *  - Channel and game pages: scrolling a list away from the top (touch or remote) slides the
 *    banner away, keeping the top bar and the tabs; back at the top brings it back.
 *  - Cards (streams, videos, clips, channels, games) show a clear outline while selected.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Started from UpstreamUpdates.onMainActivityCreate (no extra upstream hook).
 */
object TvNavigation {

    private const val OUTLINE_DP = 3
    private const val SELECTED = 0xFFFFC107.toInt() // gold, as the held seek bar marker
    private val headerListeners = WeakHashMap<Fragment, ViewTreeObserver.OnScrollChangedListener>()

    fun register(activity: MainActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: android.os.Bundle?) {
                outlineCards(v)
                if (f is com.github.andreyasadchy.xtra.ui.player.PlayerFragment) outlinePlayerButtons(f, v)
                if (f is ChannelPagerFragment || f is GamePagerFragment || f is GameMediaFragment) watchHeader(f, v)
            }

            override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
                val listener = headerListeners.remove(f) ?: return
                f.activity?.window?.decorView?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnScrollChangedListener(listener)
            }
        }, true)
    }

    // ---- Banner collapse ----

    private fun watchHeader(fragment: Fragment, root: View) {
        val appBar = root.findViewById<AppBarLayout>(R.id.appBar) ?: return
        val toolbar = root.findViewById<View>(R.id.toolbar) ?: return
        val bottomBar = root.findViewById<View>(R.id.toolbarContainer2) ?: return
        var animator: ValueAnimator? = null
        var collapsed = false
        fun setCollapsed(collapse: Boolean) {
            if (collapse == collapsed) return
            collapsed = collapse
            animator?.cancel()
            if (!collapse) {
                appBar.setExpanded(true, true)
                return
            }
            // Leave the top bar and the tabs showing.
            val target = -(appBar.height - toolbar.height - bottomBar.height).coerceIn(0, appBar.totalScrollRange)
            val coordinator = appBar.parent as? CoordinatorLayout ?: return
            val behavior = (appBar.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as? AppBarLayout.Behavior ?: return
            val start = behavior.topAndBottomOffset
            if (start <= target) return
            var last = start
            animator = ValueAnimator.ofInt(start, target).apply {
                duration = 200L
                addUpdateListener {
                    val value = it.animatedValue as Int
                    val dy = last - value
                    last = value
                    if (dy > 0) behavior.onNestedPreScroll(coordinator, appBar, appBar, 0, dy, IntArray(2), ViewCompat.TYPE_NON_TOUCH)
                }
                start()
            }
        }
        // Any scroll of the list on screen (touch or remote): away from the top hides the banner,
        // back at the top shows it. The header no longer scrolls with the list by itself, so the
        // tabs always stay visible.
        val listener = ViewTreeObserver.OnScrollChangedListener {
            if (!root.isAttachedToWindow || !root.isShown) return@OnScrollChangedListener
            val list = findLists(root).firstOrNull { it.isShown && !isInside(it, appBar) } ?: return@OnScrollChangedListener
            findLists(root).forEach { if (it.isNestedScrollingEnabled) it.isNestedScrollingEnabled = false }
            setCollapsed(list.computeVerticalScrollOffset() > 0)
        }
        // Xtra5: never past "banner hidden, top bar and tabs showing". A list made after this
        // screen was set up (e.g. the Videos tab) can still push the header on its first scroll,
        // which used to hide the tabs too; put it back at the tabs.
        appBar.addOnOffsetChangedListener { bar, offset ->
            val limit = -(bar.height - toolbar.height - bottomBar.height).coerceIn(0, bar.totalScrollRange)
            if (offset < limit) {
                val behavior = (bar.layoutParams as? CoordinatorLayout.LayoutParams)?.behavior as? AppBarLayout.Behavior ?: return@addOnOffsetChangedListener
                bar.post {
                    if (behavior.topAndBottomOffset < limit) {
                        behavior.setTopAndBottomOffset(limit)
                        bar.requestLayout()
                    }
                }
            }
        }
        val observer = fragment.activity?.window?.decorView?.viewTreeObserver ?: return
        observer.addOnScrollChangedListener(listener)
        root.post { findLists(root).forEach { it.isNestedScrollingEnabled = false } }
        headerListeners[fragment] = listener
    }

    private fun isInside(view: View, parent: View): Boolean =
        generateSequence(view) { it.parent as? View }.any { it === parent }

    // ---- Card outline ----

    private fun outlineCards(root: View) {
        findLists(root).forEach { list ->
            if (list.getTag(R.id.xtra5_tv_outline) == true) return@forEach
            list.setTag(R.id.xtra5_tv_outline, true)
            list.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    val card = view as? MaterialCardView ?: return
                    if (card.getTag(R.id.xtra5_tv_outline) == true) return
                    card.setTag(R.id.xtra5_tv_outline, true)
                    val width = card.strokeWidth
                    val color = card.strokeColorStateList
                    val outline = TypedValue().let { tv ->
                        card.context.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
                        if (tv.resourceId != 0) card.context.getColorStateList(tv.resourceId).defaultColor else tv.data
                    }
                    val previous = card.onFocusChangeListener
                    card.setOnFocusChangeListener { v, hasFocus ->
                        previous?.onFocusChange(v, hasFocus)
                        if (hasFocus) {
                            card.strokeWidth = (OUTLINE_DP * card.resources.displayMetrics.density).toInt()
                            card.strokeColor = outline
                        } else {
                            card.strokeWidth = width
                            color?.let { card.setStrokeColor(it) }
                        }
                    }
                }

                override fun onChildViewDetachedFromWindow(view: View) {}
            })
        }
    }

    // ---- Player buttons ----

    /** The selected player button (remote / D-pad) gets a gold box around it and a gold icon. */
    private fun outlinePlayerButtons(player: com.github.andreyasadchy.xtra.ui.player.PlayerFragment, root: View) {
        val controls = root.findViewById<ViewGroup>(R.id.playerControls) ?: return
        val density = root.resources.displayMetrics.density
        fun outline(view: View) {
            if (view is androidx.media3.ui.DefaultTimeBar) return // has its own (gold, larger marker)
            if (view.isFocusable && view.isClickable && view.foreground == null) {
                view.foreground = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 8 * density
                        setColor(0x33FFC107)
                        setStroke((2 * density).toInt(), SELECTED)
                    })
                }
                // The selected button's icon turns gold too (same gold as the held seek bar marker).
                if (view is android.widget.ImageView) {
                    val normal = view.imageTintList?.defaultColor ?: android.graphics.Color.WHITE
                    view.imageTintList = android.content.res.ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                        intArrayOf(SELECTED, normal)
                    )
                }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) outline(view.getChildAt(i))
        }
        outline(controls)
        // Selecting the seek bar with the remote takes hold of it (TV mode shortcuts).
        root.findViewById<View>(R.id.progressBar)?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && player.view === root) TvShortcuts.onSeekBarFocused(player)
        }
    }

    private fun findLists(view: View): List<RecyclerView> {
        if (view is RecyclerView) return listOf(view)
        if (view !is ViewGroup) return emptyList()
        return (0 until view.childCount).flatMap { findLists(view.getChildAt(it)) }
    }
}
