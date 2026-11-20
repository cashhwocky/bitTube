package com.bitdroid.tube

import android.app.Application
import com.bitdroid.tube.data.Library
import com.bitdroid.tube.data.Prefs
import com.bitdroid.tube.data.Repo
import com.bitdroid.tube.player.ClipPlayback
import com.bitdroid.tube.player.Playback

class BitTubeApp : Application() {
    /** For work that should outlive a screen (e.g. finishing sign-in). */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Library.init(this)
        com.bitdroid.tube.data.Account.init(this)
        Repo.init()
        Playback.init(this)
        ClipPlayback.init(this)
        com.bitdroid.tube.data.Sync.init(this)
    }
}
