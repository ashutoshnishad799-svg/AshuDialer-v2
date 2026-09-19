// Adapted from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
package com.ashudialer.app.appcalls

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages binding to Shizuku and the [ShellService] it hosts, hiding the permission-request and
 * service-lifecycle complexity behind [getShellService].
 *
 * Terminology: the "Service" is our own privileged code ([ShellService]); the "Server" is the
 * Shizuku ADB server process (UID 2000/0) that hosts it.
 *
 * @param context The application context (not an Activity context, to avoid leaks).
 * @param onBinderDied Called if the Shizuku server / ShellService binder connection is lost unexpectedly, after a successful connection.
 */
class ShizukuConnectionManager(
    private val context: Context,
    private val onBinderDied: () -> Unit = {}
) {
    companion object {
        private const val TAG = "AppCalls:ShizukuConn"
        private const val PERMISSION_REQUEST_CODE = 204847

        fun isAvailable(): Boolean = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            AppCallsLogger.w(TAG, "Shizuku unavailable: ${e.message}", e)
            false
        }

        /**
         * @param context Optional; enables a fallback Android-permission check when Shizuku itself
         * isn't running yet. That fallback can be out of sync with Shizuku's own internal state
         * until Shizuku is restarted, so it's a secondary signal, not authoritative.
         */
        fun hasPermission(context: Context? = null): Boolean = try {
            if (isAvailable()) {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else if (context == null) {
                false
            } else {
                context.checkSelfPermission(ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            AppCallsLogger.e(TAG, "Error while checking Shizuku permission", e)
            false
        }

        /** Checks whether the Shizuku server itself (running as shell) holds [permissionName] - relevant for e.g. CAPTURE_AUDIO_OUTPUT. */
        fun checkServerPermission(permissionName: String): Boolean = try {
            if (isAvailable()) {
                Shizuku.checkRemotePermission(permissionName) == PackageManager.PERMISSION_GRANTED
            } else {
                false
            }
        } catch (e: Exception) {
            AppCallsLogger.e(TAG, "Error while checking remote Shizuku server permission", e)
            false
        }

        fun requestPermission() {
            if (!hasPermission()) Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }

        /** Resolves the Shizuku manager app's package name via its declared permission, so it's found even if the user hid the app. */
        fun getPackageName(context: Context): String? = runCatching {
            context.packageManager.getPermissionInfo(ShizukuProvider.PERMISSION, 0)
        }.getOrNull()?.packageName

        /**
         * Starts the Shizuku server via Shizuku's own broadcast intent (needs Shizuku's
         * "Intents" auth key, copied from Shizuku > Settings > "Start via intent").
         * Safe to call when it is already running.
         */
        fun startServer(context: Context, authKey: String) {
            try {
                if (isAvailable()) {
                    AppCallsLogger.i(TAG, "Shizuku already running, no start broadcast needed")
                    return
                }
                val packageName = getPackageName(context)
                    ?: throw IllegalStateException("Shizuku manager package not found, cannot start server")
                val intent = Intent("moe.shizuku.privileged.api.START").apply {
                    setPackage(packageName)
                    putExtra("auth", authKey)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                context.sendBroadcast(intent)
                AppCallsLogger.i(TAG, "Sent start broadcast to $packageName")
            } catch (e: Exception) {
                AppCallsLogger.e(TAG, "Failed to send start broadcast to Shizuku", e)
            }
        }

        /** Stops the Shizuku server via broadcast intent (same auth key as [startServer]). */
        fun stopServer(context: Context, authKey: String) {
            try {
                if (!isAvailable()) return
                val packageName = getPackageName(context)
                    ?: throw IllegalStateException("Shizuku manager package not found, cannot stop server")
                val intent = Intent("moe.shizuku.privileged.api.STOP").apply {
                    setPackage(packageName)
                    putExtra("auth", authKey)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                }
                context.sendBroadcast(intent)
                AppCallsLogger.i(TAG, "Sent stop broadcast to $packageName")
            } catch (e: Exception) {
                AppCallsLogger.e(TAG, "Failed to send stop broadcast to Shizuku", e)
            }
        }

        suspend fun waitForServer(timeoutMillis: Long = 30000, pollIntervalMillis: Long = 250): Boolean {
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMillis) {
                if (isAvailable()) return true
                delay(pollIntervalMillis)
            }
            AppCallsLogger.w(TAG, "Timed out waiting for Shizuku server after ${timeoutMillis}ms")
            return false
        }
    }

    /**
     * Configuration for binding the Shizuku user service.
     *
     * daemon(false) is deliberate: the ShellService process should exit when this app process
     * dies, since even if it kept running, the app side would no longer hold the pipe read-end
     * to write the output file to - a lingering daemon here would serve no purpose.
     */
    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        val isDebug = BuildConfig.DEBUG
        val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()

        Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("AppCallsShellService")
            .debuggable(isDebug)
            .version(version)
    }

    private var serviceConnection: ServiceConnection? = null

    /**
     * Binds (if needed) and returns the [IShellService] proxy, requesting Shizuku permission
     * first if it hasn't been granted yet.
     *
     * @throws IllegalStateException if Shizuku is not running or binding fails.
     * @throws SecurityException if the user denies the Shizuku permission prompt.
     */
    suspend fun getShellService(): IShellService = suspendCancellableCoroutine { continuation ->
        if (!isAvailable()) {
            continuation.resumeWithException(IllegalStateException("Shizuku is not running"))
            return@suspendCancellableCoroutine
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
                if (binder != null) {
                    val proxy = IShellService.Stub.asInterface(binder)
                    AppCallsLogger.i(TAG, "ShellService connected successfully")
                    if (continuation.isActive) continuation.resume(proxy)
                } else {
                    val e = IllegalStateException("Shizuku returned a null binder")
                    AppCallsLogger.e(TAG, "Service connected with null binder", e)
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }

            /** Fires only on an unplanned loss (Shizuku server crashed/killed, or the user stopped Shizuku). */
            override fun onServiceDisconnected(name: ComponentName?) {
                AppCallsLogger.d(TAG, "ShellService disconnected prematurely")
                unbind()
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("Shizuku service disconnected prematurely"))
                } else {
                    onBinderDied()
                }
            }
        }
        this.serviceConnection = connection

        fun bindServiceInternal() {
            try {
                AppCallsLogger.i(TAG, "Binding ShellService...")
                Shizuku.bindUserService(userServiceArgs, connection)
            } catch (e: Exception) {
                AppCallsLogger.e(TAG, "Failed to bind service", e)
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }

        val permissionListener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == PERMISSION_REQUEST_CODE) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        AppCallsLogger.d(TAG, "Permission granted, proceeding to bind")
                        bindServiceInternal()
                    } else {
                        AppCallsLogger.w(TAG, "Shizuku permission denied, cannot continue with binding")
                        if (continuation.isActive) {
                            continuation.resumeWithException(SecurityException("Shizuku permission denied by user"))
                        }
                    }
                }
            }
        }

        if (hasPermission()) {
            bindServiceInternal()
        } else {
            AppCallsLogger.w(TAG, "Cannot bind yet, missing permission, requesting Shizuku permission...")
            Shizuku.addRequestPermissionResultListener(permissionListener)
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
        }

        continuation.invokeOnCancellation {
            Shizuku.removeRequestPermissionResultListener(permissionListener)
        }
    }

    /** Unbinds from Shizuku. This alone does not trigger ShellService.destroy() (daemon=false already handles cleanup on app-process death); Shizuku itself calls destroy() only if the user manually stops Shizuku. */
    fun unbind() {
        val serviceConn = serviceConnection
        if (serviceConn != null) {
            try {
                if (isAvailable()) {
                    Shizuku.unbindUserService(userServiceArgs, serviceConn, false)
                    AppCallsLogger.i(TAG, "ShellService was unbound")
                } else {
                    AppCallsLogger.d(TAG, "ShellService binder already gone (killed by Shizuku server) - nothing to unbind")
                }
            } catch (e: Exception) {
                AppCallsLogger.e(TAG, "Unexpected error while unbinding ShellService", e)
            }
        }
        serviceConnection = null
    }
}
