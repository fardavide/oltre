package dev.fardavide.oltre.client

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.notifications.data.GameNotifications
import dev.fardavide.oltre.client.notifications.data.defaultNotificationScheduler
import dev.fardavide.oltre.client.save.data.GameStore
import dev.fardavide.oltre.client.save.data.defaultSaveFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Clock

// Android forgets every alarm across a reboot; iOS keeps its notification requests, so this is
// the one piece of the check-in loop that has no counterpart there. Without it the game goes
// quiet after a restart and stays quiet until the player next opens it — which is exactly the
// player the alerts exist to reach.
//
// It reads the save and recomputes, rather than remembering a schedule: the schedule is derived
// from state everywhere else in this game, and a stored copy of it would be the one thing that
// could disagree with the colony. That makes this composition — save plus notifications — which
// is why it is in the shell and not in either module it uses.
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // The broadcast has to be kept alive across the file read, and a receiver that has
        // returned may have its process killed mid-coroutine.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // **A device with no save has nothing to re-book**, and since 0.21 that is a state
                // the app can genuinely be in: `resume` no longer founds a colony out of a null
                // save, because founding is the server's. A phone that rebooted before its owner
                // ever signed in gets no alarms, which is right — there is nothing in flight.
                val saved = GameStore(defaultSaveFile()).load() ?: return@launch
                // `resume` advances the saved colony to now, so what is left in flight is
                // genuinely still in flight. Syncing the raw snapshot instead would book alerts
                // for builds that finished while the phone was off.
                val session = resume(saved, now = Clock.System.now())
                // **`English` by name, and it is the honest reading rather than a shortcut.** There
                // is one language, and at boot there is no composition and no shell to have chosen
                // one — so the receiver names the table the app would have named. #87 is what turns
                // this into a device-locale read, in the one place that will need it.
                GameNotifications(defaultNotificationScheduler(), English)
                    .sync(session.state, now = session.lastUpdatedAt)
            } catch (error: Throwable) {
                // Nothing to report to and nowhere to report it: there is no UI at boot, and the
                // next time the game is opened it schedules the whole set again. What must not
                // happen is a crash dialog on somebody's phone every time it starts up.
                error.printStackTrace()
            } finally {
                pending.finish()
            }
        }
    }
}
