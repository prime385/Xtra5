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
import androidx.preference.SwitchPreferenceCompat
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Xtra5 custom feature: notify-only check for new official (upstream) Xtra changes,
 * plus the official Xtra changelog. Never downloads or installs anything.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hooks: MainActivity.onCreate and SettingsActivity.SettingsFragment.onCreatePreferences.
 */
object UpstreamUpdates {

    private const val REPO = "crackededed/Xtra"
    private const val BRANCH = "master"
    private const val PREF_ENABLED = "xtra5_upstream_check_enabled"
    private const val PREF_LAST_CHECK = "xtra5_upstream_last_check"
    private const val PREF_DISMISSED_SHA = "xtra5_upstream_dismissed_sha"
    private const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    private const val MAX_LISTED = 50

    class Result(val newCommits: Int, val headSha: String, val changes: List<String>, val url: String)

    /** Called from MainActivity.onCreate. */
    fun onMainActivityCreate(activity: AppCompatActivity) {
        val prefs = activity.prefs()
        // The official updater would install official Xtra builds: keep it off.
        if (prefs.getBoolean(C.UPDATE_CHECK_ENABLED, false)) {
            prefs.edit { putBoolean(C.UPDATE_CHECK_ENABLED, false) }
        }
        if (!prefs.getBoolean(PREF_ENABLED, true)) return
        if (System.currentTimeMillis() - prefs.getLong(PREF_LAST_CHECK, 0) < CHECK_INTERVAL_MS) return
        activity.lifecycleScope.launch {
            val result = fetch() ?: return@launch
            prefs.edit { putLong(PREF_LAST_CHECK, System.currentTimeMillis()) }
            if (result.newCommits > 0 && result.headSha != prefs.getString(PREF_DISMISSED_SHA, null)) {
                showUpdateDialog(activity, result)
            }
        }
    }

    /** Called at the end of SettingsFragment.onCreatePreferences. */
    fun setupSettings(fragment: PreferenceFragmentCompat) {
        val context = fragment.requireContext()
        fragment.findPreference<Preference>("update_settings")?.isVisible = false
        val checkPref = fragment.findPreference<Preference>("check_updates") ?: return
        checkPref.title = "Check for official Xtra changes"
        checkPref.summary = "Notify only. This app is never updated automatically. Based on Xtra commit ${BuildConfig.XTRA5_UPSTREAM_SHA.take(7)}"
        checkPref.setOnPreferenceClickListener {
            Toast.makeText(context, "Checking…", Toast.LENGTH_SHORT).show()
            fragment.lifecycleScope.launch {
                val result = fetch()
                if (!fragment.isAdded) return@launch
                when {
                    result == null -> Toast.makeText(context, "Couldn't reach GitHub", Toast.LENGTH_LONG).show()
                    result.newCommits == 0 -> Toast.makeText(context, "Up to date with official Xtra", Toast.LENGTH_LONG).show()
                    else -> showUpdateDialog(fragment.requireActivity(), result)
                }
            }
            true
        }
        val parent = checkPref.parent ?: return
        parent.addPreference(SwitchPreferenceCompat(context).apply {
            key = PREF_ENABLED
            title = "Notify about official Xtra changes"
            summary = "Checks once a day when the app opens"
            setDefaultValue(true)
            isIconSpaceReserved = false
            isSingleLineTitle = false
            order = checkPref.order
        })
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

    private fun showUpdateDialog(context: Context, result: Result) {
        val list = result.changes.joinToString("\n") { "• $it" } +
                if (result.newCommits > result.changes.size) "\n…and ${result.newCommits - result.changes.size} more" else ""
        context.getAlertDialogBuilder()
            .setTitle("Official Xtra has ${result.newCommits} new change${if (result.newCommits == 1) "" else "s"}")
            .setMessage("Xtra5 won't update itself. Rebuild Xtra5 to get these changes.\n\n$list")
            .setPositiveButton("Open on GitHub") { _, _ ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, result.url.toUri()))
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.no_browser_found, Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Later", null)
            .setNegativeButton("Dismiss") { _, _ ->
                context.prefs().edit { putString(PREF_DISMISSED_SHA, result.headSha) }
            }
            .show()
    }

    /** Compares the upstream commit this build is based on with upstream master. Null on failure. */
    suspend fun fetch(): Result? = withContext(Dispatchers.IO) {
        val base = BuildConfig.XTRA5_UPSTREAM_SHA
        if (base.isBlank()) return@withContext null
        val body = get("https://api.github.com/repos/$REPO/compare/$base...$BRANCH") ?: return@withContext null
        try {
            val json = JSONObject(body)
            val commits = json.optJSONArray("commits")
            val changes = ArrayList<String>()
            var headSha = base
            if (commits != null) {
                for (i in commits.length() - 1 downTo 0) { // newest first
                    val commit = commits.getJSONObject(i)
                    if (i == commits.length() - 1) headSha = commit.optString("sha", base)
                    if (changes.size < MAX_LISTED) {
                        commit.optJSONObject("commit")?.optString("message")?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { changes.add(it) }
                    }
                }
            }
            Result(
                newCommits = json.optInt("ahead_by", commits?.length() ?: 0),
                headSha = headSha,
                changes = changes,
                url = json.optString("html_url", "https://github.com/$REPO/commits/$BRANCH")
            )
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
