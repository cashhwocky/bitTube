package com.bitdroid.tube.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Settings, mirrored into Compose state so screens update live. */
object Prefs {
    private lateinit var sp: SharedPreferences

    var qualityWifi by mutableIntStateOf(1080)
        private set
    var qualityData by mutableIntStateOf(480)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var autoplay by mutableStateOf(true)
        private set
    var showDislikes by mutableStateOf(true)
        private set
    var keepHistory by mutableStateOf(true)
        private set
    var loopClips by mutableStateOf(true)
        private set
    var seekStep by mutableIntStateOf(10)
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("bittube", Context.MODE_PRIVATE)
        qualityWifi = sp.getInt("q_wifi", 1080)
        qualityData = sp.getInt("q_data", 480)
        speed = sp.getFloat("speed", 1f)
        autoplay = sp.getBoolean("autoplay", true)
        showDislikes = sp.getBoolean("dislikes", true)
        keepHistory = sp.getBoolean("history", true)
        loopClips = sp.getBoolean("loop_clips", true)
        seekStep = sp.getInt("seek_step", 10)
    }

    fun raw(): SharedPreferences = sp

    fun setQualityWifi(v: Int) { qualityWifi = v; sp.edit().putInt("q_wifi", v).apply() }
    fun setQualityData(v: Int) { qualityData = v; sp.edit().putInt("q_data", v).apply() }
    fun setSpeed(v: Float) { speed = v; sp.edit().putFloat("speed", v).apply() }
    fun setAutoplay(v: Boolean) { autoplay = v; sp.edit().putBoolean("autoplay", v).apply() }
    fun setShowDislikes(v: Boolean) { showDislikes = v; sp.edit().putBoolean("dislikes", v).apply() }
    fun setKeepHistory(v: Boolean) { keepHistory = v; sp.edit().putBoolean("history", v).apply() }
    fun setLoopClips(v: Boolean) { loopClips = v; sp.edit().putBoolean("loop_clips", v).apply() }
    fun setSeekStep(v: Int) { seekStep = v; sp.edit().putInt("seek_step", v).apply() }

    /** Quality cap right now: the mobile-data one on metered networks. 0 means "best available". */
    fun qualityNow(context: Context): Int {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        return if (cm?.isActiveNetworkMetered == true) qualityData else qualityWifi
    }
}

/** Launcher icon choices; each one is an activity-alias in the manifest. */
object AppIcons {
    data class Choice(val key: String, val label: String, val bg: Long, val fg: Int)

    val all = listOf(
        Choice("bloom", "Bloom", 0xFFFFFFFF, com.bitdroid.tube.R.drawable.ic_fg_tube_violet),
        Choice("lilac", "Lilac", 0xFFEADDFF, com.bitdroid.tube.R.drawable.ic_fg_tube_lilac),
        Choice("cookie", "Cookie", 0xFF2B2541, com.bitdroid.tube.R.drawable.ic_fg_cookie),
        Choice("mint", "Mint", 0xFFB4F2D6, com.bitdroid.tube.R.drawable.ic_fg_tube_mint),
        Choice("peach", "Peach", 0xFFFFDBC9, com.bitdroid.tube.R.drawable.ic_fg_tube_peach),
        Choice("ocean", "Ocean", 0xFFD1E4FF, com.bitdroid.tube.R.drawable.ic_fg_tube_ocean),
        Choice("night", "Night", 0xFF000000, com.bitdroid.tube.R.drawable.ic_fg_tube_night),
    )

    fun current(context: Context): String = Prefs.raw().getString("app_icon", "bloom") ?: "bloom"

    fun set(context: Context, key: String) {
        val chosen = all.firstOrNull { it.key == key } ?: return
        val pm = context.packageManager
        // Turn the new one on first so the app never has zero launcher entries.
        for (c in listOf(chosen) + all.filter { it != chosen }) {
            pm.setComponentEnabledSetting(
                android.content.ComponentName(context, "com.bitdroid.tube.Icon_${c.key}"),
                if (c == chosen) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        Prefs.raw().edit().putString("app_icon", key).apply()
    }
}
