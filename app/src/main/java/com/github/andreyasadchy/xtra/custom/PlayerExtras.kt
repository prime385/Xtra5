package com.github.andreyasadchy.xtra.custom

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity

/**
 * Xtra5 custom: a Settings button in the player's top-right controls (just before the ⋮ menu),
 * so the app's Settings can be opened without minimizing the video. It is part of the player
 * controls, so it shows and hides with them.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Started from UpstreamUpdates.onMainActivityCreate (no extra upstream hook).
 */
object PlayerExtras {

    fun register(activity: MainActivity) {
        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                if (f is PlayerFragment) addSettingsButton(activity, v)
            }
        }, true)
    }

    private fun addSettingsButton(activity: MainActivity, view: View) {
        val row = view.findViewById<LinearLayout>(R.id.topRightLayout) ?: return
        if (row.findViewById<View>(R.id.xtra5_player_settings) != null) return
        val template = row.findViewById<ImageButton>(R.id.menu)
        val button = ImageButton(row.context).apply {
            id = R.id.xtra5_player_settings
            contentDescription = activity.getString(R.string.settings)
            setImageResource(R.drawable.xtra5_tune)
            background = template?.background?.constantState?.newDrawable()?.mutate()
            imageTintList = template?.imageTintList
            scaleType = template?.scaleType ?: scaleType
            template?.let { setPadding(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom) }
            setOnClickListener {
                activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java))
            }
        }
        val size = (40 * row.resources.displayMetrics.density).toInt()
        val params = template?.layoutParams?.let { LinearLayout.LayoutParams(it as LinearLayout.LayoutParams) }
            ?: LinearLayout.LayoutParams(size, size)
        val index = template?.let { row.indexOfChild(it) } ?: row.childCount
        row.addView(button, index.coerceAtLeast(0), params)
    }
}
