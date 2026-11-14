package com.github.andreyasadchy.xtra.custom

import android.content.Context
import android.content.Intent
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import kotlin.system.exitProcess

/**
 * Xtra5 custom: makes Settings > Restore settings reliable.
 *  - The settings file is always restored before the database (restoring the database restarts
 *    the app, which used to skip the settings file if it came second).
 *  - Restoring only the settings file also restarts the app, so the running app can't write its
 *    old in-memory settings back over the restored ones.
 *
 * Kept in its own package so upstream Xtra updates can be merged without conflicts.
 * Upstream hooks: SettingsViewModel.restoreSettings (start and end).
 */
object RestoreFix {

    fun settingsFirst(list: List<String>): List<String> = list.sortedBy { if (it.endsWith(".xml")) 0 else 1 }

    /** Reached only when no database was restored (that path restarts the app itself). */
    fun restartAfterSettingsOnly(context: Context, list: List<String>) {
        if (list.none { it.endsWith(".xml") }) return
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        )
        exitProcess(0)
    }
}
