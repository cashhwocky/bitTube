package com.bitdroid.tube

import android.app.Application
import com.bitdroid.tube.data.Library
import com.bitdroid.tube.data.Prefs
import com.bitdroid.tube.data.Repo
import com.bitdroid.tube.player.ClipPlayback
import com.bitdroid.tube.player.Playback

class BitTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Library.init(this)
        Repo.init()
        Playback.init(this)
        ClipPlayback.init(this)
    }
}
