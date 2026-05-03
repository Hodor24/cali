package dev.tabml.box

import android.app.Application

class TabMlApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.syncWakeFlagsToDeviceProtectedStorage(this)
        CaliWakePowerReceiver.register(this)
        WorkflowDueScheduler.sync(this)
    }
}
