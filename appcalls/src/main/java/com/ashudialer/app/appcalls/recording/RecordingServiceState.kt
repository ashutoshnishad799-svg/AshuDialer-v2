/*
 * Ashu Phone
 * Copyright (C) 2026 Ashutosh Nishad
 *
 * This file is part of Ashu Phone, licensed under the GNU General Public
 * License, version 3 or (at your option) any later version.
 * See the LICENSE and NOTICE files in the project root.
 * This program comes with ABSOLUTELY NO WARRANTY.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
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
