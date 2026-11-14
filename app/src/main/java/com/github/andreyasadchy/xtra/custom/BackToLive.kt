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
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Started from UpstreamUpdates.onMainActivityCreate (no extra upstream hook). Registered before
 * the navigation host, so navigation's own Back (popping pages) runs before it.
 */
object BackToLive {

    private val handler = Handler(Looper.getMainLooper())

    fun register(activity: MainActivity) {
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

    private fun currentScreen(activity: MainActivity): Fragment? {
        val host = activity.supportFragmentManager.findFragmentById(R.id.navHostFragment) ?: return null
        return host.childFragmentManager.primaryNavigationFragment ?: host.childFragmentManager.fragments.lastOrNull()
    }

    private fun liveTabIndex(activity: MainActivity, tabs: TabLayout): Int? {
        val live = activity.getString(R.string.live)
        return (0 until tabs.tabCount).firstOrNull { tabs.getTabAt(it)?.text?.toString() == live }
    }
}
