package com.github.andreyasadchy.xtra.custom

import android.os.Handler
import android.os.Looper
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.following.FollowPagerFragment
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.tabs.TabLayout

/**
 * Xtra5 custom: Following > Live is the only place Back exits the app.
 *
 * With no video open, a Back that would otherwise leave the app (a main section, or a Following
 * tab other than Live) switches to Following > Live and refreshes it instead. Back still steps back
 * through pages normally first (navigation handles those before this callback is reached), and the
 * player's own Back handling (minimize, close) comes first too. Works for the remote, the back
 * button and the back gesture.
 *
 * Also: tapping the Live tab (not swiping to it) refreshes that tab only and scrolls it to the top.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Started from UpstreamUpdates.onMainActivityCreate (no extra upstream hook). Registered before
 * the navigation host, so navigation's own Back (popping pages) runs before it.
 */
object BackToLive {

    private val handler = Handler(Looper.getMainLooper())

    fun register(activity: MainActivity) {
        // Tapping the Live tab refreshes that tab only (and scrolls it to the top).
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: androidx.fragment.app.FragmentManager, f: Fragment, v: android.view.View, savedInstanceState: android.os.Bundle?) {
                if (f !is FollowPagerFragment) return
                val tabs = v.findViewById<TabLayout>(R.id.tabLayout) ?: return
                // Tabs can be (re)created after the view: keep the Live tab's tap listener in place.
                tabs.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> watchLiveTab(activity, f, tabs) }
                tabs.post { watchLiveTab(activity, f, tabs) }
            }
        }, true)
        activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (goToLive(activity)) return
                // Not our case: let the normal Back happen (exit, or whatever comes next).
                isEnabled = false
                activity.onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    /** Switches to Following > Live and refreshes it. False when Back should behave normally. */
    private fun goToLive(activity: MainActivity): Boolean {
        // A video is open (full-size or minimized): leave Back to the player.
        if ((activity.playerFragment as? PlayerFragment)?.isAdded == true) return false
        val navBar = activity.findViewById<BottomNavigationView>(R.id.navBar) ?: return false
        if (navBar.menu.findItem(R.id.followPagerFragment) == null) return false // no Following section
        val screen = currentScreen(activity)
        if (screen is FollowPagerFragment) {
            val tabs = screen.view?.findViewById<TabLayout>(R.id.tabLayout) ?: return false
            val live = liveTabIndex(activity, tabs) ?: return false
            if (tabs.selectedTabPosition == live) return false // on Live: exit as normal
            tabs.getTabAt(live)?.select()
        } else {
            navBar.selectedItemId = R.id.followPagerFragment
            // Pick the Live tab once Following has been shown.
            handler.postDelayed({
                val following = currentScreen(activity) as? FollowPagerFragment
                val tabs = following?.view?.findViewById<TabLayout>(R.id.tabLayout)
                if (tabs != null) liveTabIndex(activity, tabs)?.let { tabs.getTabAt(it)?.select() }
            }, 150L)
        }
        handler.postDelayed({ RefreshButton.refreshCurrentScreen(activity, showMessage = false) }, 400L)
        return true
    }

    /** Taps (and remote OK) on the Live tab only - swiping to it doesn't count. */
    private fun watchLiveTab(activity: MainActivity, pager: FollowPagerFragment, tabs: TabLayout) {
        val index = liveTabIndex(activity, tabs) ?: return
        val tabView = tabs.getTabAt(index)?.view ?: return
        if (tabView.getTag(R.id.xtra5_live_tab) == true) return
        tabView.setTag(R.id.xtra5_live_tab, true)
        // TabView still selects the tab after calling this listener.
        tabView.setOnClickListener { handler.postDelayed({ refreshLive(pager, index) }, 250L) }
    }

    /** Reloads only the Live list and scrolls it to the top. */
    private fun refreshLive(pager: FollowPagerFragment, index: Int) {
        if (!pager.isAdded) return
        val live = pager.childFragmentManager.findFragmentByTag("f$index") ?: return
        val list = live.view?.let { findList(it) } ?: return
        (list.adapter as? androidx.paging.PagingDataAdapter<*, *>)?.refresh()
        list.scrollToPosition(0)
    }

    private fun findList(view: android.view.View): androidx.recyclerview.widget.RecyclerView? {
        if (view is androidx.recyclerview.widget.RecyclerView) return view
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) findList(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun currentScreen(activity: MainActivity): Fragment? {
        val host = activity.supportFragmentManager.findFragmentById(R.id.navHostFragment) ?: return null
        return host.childFragmentManager.primaryNavigationFragment ?: host.childFragmentManager.fragments.lastOrNull()
    }

    private fun liveTabIndex(activity: MainActivity, tabs: TabLayout): Int? {
        val live = activity.getString(R.string.live)
        return (0 until tabs.tabCount).firstOrNull { tabs.getTabAt(it)?.text?.toString() == live }
    }
}
