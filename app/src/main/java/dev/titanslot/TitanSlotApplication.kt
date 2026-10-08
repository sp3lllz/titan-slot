package dev.titanslot

import android.app.Application
import android.content.Context
import dev.titanslot.core.Delivery

/**
 * Runs in both processes (the shelf and :game), so each one can see cores Google Play
 * delivered after the app was installed.
 */
class TitanSlotApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        Delivery.attach(this)
    }
}
