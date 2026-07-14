package willykez.gitflowmobile.sync

import android.content.Context

/**
 * Deliberately plain SharedPreferences rather than DataStore — this is exactly two
 * settings (on/off, interval), and DataStore's Flow-based API would be pure overhead here.
 */
object SyncPrefs {
    private const val PREFS_NAME = "gitflowmobile_sync_prefs"
    private const val KEY_ENABLED = "background_sync_enabled"
    private const val KEY_INTERVAL_HOURS = "background_sync_interval_hours"

    const val DEFAULT_INTERVAL_HOURS = 3L
    /** WorkManager's PeriodicWorkRequest hard-floors at 15 minutes; anything shorter is
     * silently clamped up to this by the platform anyway. */
    const val MIN_INTERVAL_HOURS = 1L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun intervalHours(context: Context): Long =
        prefs(context).getLong(KEY_INTERVAL_HOURS, DEFAULT_INTERVAL_HOURS)

    fun setIntervalHours(context: Context, hours: Long) {
        prefs(context).edit().putLong(KEY_INTERVAL_HOURS, hours.coerceAtLeast(MIN_INTERVAL_HOURS)).apply()
    }
}
