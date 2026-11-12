package com.github.andreyasadchy.xtra.custom

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Xtra5 custom: points the built-in updater at Xtra5 releases (official Xtra + custom
 * features, auto-built by .github/workflows/xtra5.yml) and shows the official Xtra changelog.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hooks: MainActivity.onCreate and SettingsActivity.SettingsFragment.onCreatePreferences.
 */
object UpstreamUpdates {

    private const val REPO = "crackededed/Xtra"
    private const val BRANCH = "master"
    private const val MAX_LISTED = 50

    /** Xtra5 builds (official Xtra + custom features) are published here by .github/workflows/xtra5.yml */
    const val XTRA5_UPDATE_URL = "https://api.github.com/repos/prime385/Xtra5/releases/latest"
    private const val OFFICIAL_UPDATE_URL = "https://api.github.com/repos/crackededed/xtra/releases/tags/latest"
    private const val PREF_MIGRATED = "xtra5_updater_configured"

    /** Called from MainActivity.onCreate, before the built-in update check runs. */
    fun onMainActivityCreate(activity: AppCompatActivity) {
        FixedTopBar.register(activity) // Xtra5: fixed top bar (Settings > UI)
        RefreshButton.register(activity) // Xtra5: refresh button in the top bar
        (activity as? com.github.andreyasadchy.xtra.ui.main.MainActivity)?.let {
            AutoRefresh.register(it) // Xtra5: auto refresh / auto play
            PlayerExtras.register(it) // Xtra5: Settings button in the player controls
        }
        val prefs = activity.prefs()
        // Xtra5: don't pause when headphones / Bluetooth audio disconnect (see AudioGuard).
        if (prefs.getBoolean(C.PLAYER_HANDLE_AUDIO_BECOMING_NOISY, true)) {
            prefs.edit { putBoolean(C.PLAYER_HANDLE_AUDIO_BECOMING_NOISY, false) }
        }
        // Point the built-in updater at Xtra5 releases, never at official Xtra builds.
        val url = prefs.getString(C.UPDATE_URL, null)
        if (url.isNullOrBlank() || url.equals(OFFICIAL_UPDATE_URL, true)) {
            prefs.edit { putString(C.UPDATE_URL, XTRA5_UPDATE_URL) }
        }
        // First run: turn on daily update checks (the user can change this in Update settings).
        if (!prefs.getBoolean(PREF_MIGRATED, false)) {
            prefs.edit {
                putBoolean(C.UPDATE_CHECK_ENABLED, true)
                putString(C.UPDATE_CHECK_FREQUENCY, "1")
                putBoolean(PREF_MIGRATED, true)
            }
        }
    }

    /** Called at the end of SettingsFragment.onCreatePreferences. */
    fun setupSettings(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        val checkPref = fragment.findPreference<Preference>("check_updates") ?: return
        checkPref.summary = "Xtra5 ${BuildConfig.VERSION_NAME}, based on official Xtra ${BuildConfig.XTRA5_UPSTREAM_SHA.take(7)}"
        val parent = checkPref.parent ?: return
        parent.addPreference(Preference(context).apply {
            key = "xtra5_official_changelog"
            title = "Official Xtra changelog"
            summary = "Recent changes to the official app. Changes not in this build are marked NEW"
            isIconSpaceReserved = false
            isSingleLineTitle = false
            order = checkPref.order
            setOnPreferenceClickListener {
                Toast.makeText(context, "Loading…", Toast.LENGTH_SHORT).show()
                fragment.lifecycleScope.launch {
                    val entries = fetchChangelog()
                    if (!fragment.isAdded) return@launch
                    if (entries == null) {
                        Toast.makeText(context, "Couldn't reach GitHub", Toast.LENGTH_LONG).show()
                    } else {
                        showChangelog(context, entries)
                    }
                }
                true
            }
        })
    }

    class ChangelogEntry(val date: String, val message: String, val isNew: Boolean)

    private fun showChangelog(context: Context, entries: List<ChangelogEntry>) {
        val newCount = entries.count { it.isNew }
        val header = when {
            BuildConfig.XTRA5_UPSTREAM_SHA.isBlank() -> ""
            newCount == 0 -> "This build is up to date with official Xtra.\n\n"
            else -> "$newCount change${if (newCount == 1) " is" else "s are"} not in this build yet (marked NEW).\n\n"
        }
        val text = header + entries.joinToString("\n") { "${if (it.isNew) "NEW  " else ""}${it.date}  ${it.message}" }
        context.getAlertDialogBuilder()
            .setTitle("Official Xtra changelog")
            .setMessage(text)
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton("Open on GitHub") { _, _ ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/$REPO/commits/$BRANCH".toUri()))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.no_browser_found, Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    /** Recent upstream commits, newest first. Entries until this build's base commit are marked new. */
    suspend fun fetchChangelog(): List<ChangelogEntry>? = withContext(Dispatchers.IO) {
        val json = get("https://api.github.com/repos/$REPO/commits?sha=$BRANCH&per_page=$MAX_LISTED") ?: return@withContext null
        try {
            val commits = org.json.JSONArray(json)
            val base = BuildConfig.XTRA5_UPSTREAM_SHA
            var reachedBase = base.isBlank()
            (0 until commits.length()).mapNotNull { i ->
                val commit = commits.getJSONObject(i)
                if (commit.optString("sha") == base) reachedBase = true
                val details = commit.optJSONObject("commit") ?: return@mapNotNull null
                val message = details.optString("message").lineSequence().firstOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val date = details.optJSONObject("committer")?.optString("date")?.take(10) ?: ""
                ChangelogEntry(date, message, !reachedBase)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun get(url: String): String? = try {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "Xtra5")
        try {
            if (connection.responseCode == 200) connection.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        null
    }
}
