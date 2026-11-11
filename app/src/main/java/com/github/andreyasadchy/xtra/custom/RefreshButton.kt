package com.github.andreyasadchy.xtra.custom

import android.app.Activity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R

/**
 * Xtra5 custom: a refresh button in the top bar, between search and the ⋮ menu, on every screen
 * that has the search button. Pressing it reloads every loaded section (tab) of that screen, the
 * same as pulling down to refresh. Sections not opened yet load fresh when first opened.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Started from UpstreamUpdates.onMainActivityCreate (no extra upstream hook).
 */
object RefreshButton {

    fun register(activity: AppCompatActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                findToolbars(v).forEach { addButton(it, f) }
            }
        }, true)
    }

    private fun addButton(toolbar: Toolbar, screen: Fragment) {
        val menu = toolbar.menu
        if (menu.findItem(R.id.search) == null || menu.findItem(R.id.xtra5_refresh) != null) return
        menu.add(Menu.NONE, R.id.xtra5_refresh, Menu.NONE, "Refresh").apply {
            setIcon(R.drawable.xtra5_refresh)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            setOnMenuItemClickListener {
                refreshAll(screen)
                true
            }
        }
    }

    /** Refreshes the screen currently shown in the main activity. False if there is none. */
    fun refreshCurrentScreen(activity: Activity): Boolean {
        val main = activity as? AppCompatActivity ?: return false
        val host = main.supportFragmentManager.findFragmentById(R.id.navHostFragment) ?: return false
        val screen = host.childFragmentManager.primaryNavigationFragment
            ?: host.childFragmentManager.fragments.lastOrNull() ?: return false
        refreshAll(screen)
        Toast.makeText(activity, "Refreshing", Toast.LENGTH_SHORT).show()
        return true
    }

    /** Refreshes every paged list in [screen] and its child fragments (all of its tabs). */
    private fun refreshAll(screen: Fragment) {
        val seen = HashSet<RecyclerView.Adapter<*>>()
        fun visit(fragment: Fragment) {
            if (!fragment.isAdded) return
            fragment.view?.let { view -> findRecyclerViews(view).forEach { refresh(it.adapter, seen) } }
            fragment.childFragmentManager.fragments.forEach { visit(it) }
        }
        visit(screen)
    }

    private fun refresh(adapter: RecyclerView.Adapter<*>?, seen: MutableSet<RecyclerView.Adapter<*>>) {
        when (adapter) {
            null -> Unit
            is ConcatAdapter -> adapter.adapters.forEach { refresh(it, seen) }
            is PagingDataAdapter<*, *> -> if (seen.add(adapter)) adapter.refresh()
        }
    }

    private fun findToolbars(view: View, out: MutableList<Toolbar> = mutableListOf()): List<Toolbar> {
        if (view is Toolbar) out.add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) findToolbars(view.getChildAt(i), out)
        return out
    }

    private fun findRecyclerViews(view: View, out: MutableList<RecyclerView> = mutableListOf()): List<RecyclerView> {
        if (view is RecyclerView) out.add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) findRecyclerViews(view.getChildAt(i), out)
        return out
    }
}
