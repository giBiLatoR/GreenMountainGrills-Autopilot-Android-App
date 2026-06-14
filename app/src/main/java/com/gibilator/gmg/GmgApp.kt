package com.gibilator.gmg

import android.app.Application
import com.gibilator.gmg.data.GrillRepository
import com.gibilator.gmg.service.AndroidCookNotifier
import com.gibilator.gmg.service.Channels

/** Application singleton — owns the one [GrillRepository]. */
class GmgApp : Application() {
    lateinit var repository: GrillRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        Channels.ensure(this)
        repository = GrillRepository(this, AndroidCookNotifier(this))
    }

    companion object {
        lateinit var instance: GmgApp
            private set

        val repo: GrillRepository get() = instance.repository
    }
}
