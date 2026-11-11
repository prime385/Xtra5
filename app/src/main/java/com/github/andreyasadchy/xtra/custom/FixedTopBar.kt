package com.github.andreyasadchy.xtra.custom

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.prefs
import com.google.android.material.appbar.AppBarLayout

/**
 * Xtra5 custom: keep the top bar (title bar, section tabs, sort bar) fixed instead of scrolling
 * away with the list, without the lift-on-scroll color change. On/off in Settings > UI > Top bar. Applies to every screen of the main
 * activity; the Settings screen is separate and unchanged.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Started from UpstreamUpdates.onMainActivityCreate (no extra upstream hook).
 */
object FixedTopBar {

    const val PREF_ENABLED = "xtra5_fixed_top_bar"

    fun register(activity: AppCompatActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                if (!activity.prefs().getBoolean(PREF_ENABLED, true)) return
                findAppBars(v).forEach { fix(it) }
            }
        }, true)
    }

    private fun findAppBars(view: View, out: MutableList<AppBarLayout> = mutableListOf()): List<AppBarLayout> {
        if (view is AppBarLayout) out.add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) findAppBars(view.getChildAt(i), out)
        return out
    }

    private fun fix(appBar: AppBarLayout) {
        clearScrollFlags(appBar)
        // A fixed bar shouldn't change color while the list scrolls ("lift on scroll").
        appBar.setLiftable(false)
        if (appBar.getTag(R.id.xtra5_fixed_top_bar) == null) {
            appBar.setTag(R.id.xtra5_fixed_top_bar, true)
            // Some screens (a channel's pages) turn scrolling back on when switching tabs.
            appBar.addOnOffsetChangedListener { bar, offset ->
                if (offset != 0) {
                    clearScrollFlags(bar)
                    bar.setExpanded(true, false)
                }
            }
        }
    }

    private fun clearScrollFlags(appBar: AppBarLayout) {
        for (i in 0 until appBar.childCount) {
            val child = appBar.getChildAt(i)
            val params = child.layoutParams as? AppBarLayout.LayoutParams ?: continue
            if (params.scrollFlags != 0) {
                params.scrollFlags = 0
                child.layoutParams = params
            }
        }
    }
}
