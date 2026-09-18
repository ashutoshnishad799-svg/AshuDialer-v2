package com.ashudialer.app.appcalls

import kotlinx.coroutines.CoroutineScope

/**
 * What [AppCallNotificationListenerService] needs from the host application, without depending
 * on the :app module's concrete Application class.
 *
 * This interface exists specifically to avoid a circular Gradle module dependency: :app already
 * depends on :appcalls (to use this feature), so :appcalls referencing :app's AshuDialerApp
 * directly would create a cycle Gradle cannot build. Instead, :app's AshuDialerApp implements
 * this interface, and the service looks up the current implementation via [AppCallsHost.current]
 * rather than casting applicationContext to a concrete :app class.
 */
interface AppCallsHost {
    /** Process-lifetime scope for work that must survive the component that started it. */
    val applicationScope: CoroutineScope

    /** Whether recording is enabled at all, and per-app, for the given [AppCallTarget]. Suspends only as long as a DataStore read takes. */
    suspend fun isAppCallRecordingEnabled(target: AppCallTarget): Boolean

    companion object {
        @Volatile
        private var instance: AppCallsHost? = null

        /** Called once from AshuDialerApp.onCreate() to register itself as the host. */
        fun register(host: AppCallsHost) {
            instance = host
        }

        /** Null if called before AshuDialerApp.onCreate() has run, which should not happen in practice since the notification listener only binds after the app process is already up. */
        val current: AppCallsHost?
            get() = instance
    }
}
