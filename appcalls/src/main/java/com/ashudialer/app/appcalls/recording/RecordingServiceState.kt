package com.ashudialer.app.appcalls.recording

import com.ashudialer.app.appcalls.AppCallRecordingEngine

/** What [RecordingForegroundService] is doing right now. */
sealed class RecordingServiceState {
    abstract val session: RecordingSession?

    /** A call is going on but nothing is being recorded yet (waiting for answer, or auto-record is off). */
    data class Standby(override val session: RecordingSession?) : RecordingServiceState()

    /** Connecting to Shizuku / starting the capture pipeline. */
    data class Starting(override val session: RecordingSession) : RecordingServiceState()

    /** Audio is being captured. */
    data class Active(
        val engine: AppCallRecordingEngine,
        val isPaused: Boolean,
        override val session: RecordingSession
    ) : RecordingServiceState()
}
