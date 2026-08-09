package dev.fardavide.oltre.client

import android.app.Application
import dev.fardavide.oltre.client.debug.data.AndroidShakeHost
import dev.fardavide.oltre.client.notifications.data.AndroidNotificationHost
import dev.fardavide.oltre.client.save.data.AndroidSaveLocation

// Android is the one platform where the process can start without a screen: an alarm fires, the
// system builds the process, and a BroadcastReceiver runs with no Activity anywhere. So the two
// things Android cannot derive for itself — where the save lives, and a Context to reach the
// alarm service with — are filled here rather than in `MainActivity`, because this is the only
// component guaranteed to run before every other one.
class OltreApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AndroidSaveLocation.directory = filesDir
        // The application context, which this *is*. Holding an Activity here for the life of the
        // process would be a leaked window.
        AndroidNotificationHost.context = this
        // The third slot Android cannot derive: SensorManager needs a Context too. Filled in the
        // same place and for the same reason, even though the debug gesture — unlike the other two
        // — only ever matters while there is an Activity on screen.
        AndroidShakeHost.context = this
    }
}
