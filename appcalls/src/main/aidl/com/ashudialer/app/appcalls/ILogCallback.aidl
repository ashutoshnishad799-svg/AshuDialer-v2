// Ported from ShizuCallRecorder (github.com/kitsumed/ShizuCallRecorder), GPLv3+.
// See ShellService.kt in this module for the adaptation notes.
package com.ashudialer.app.appcalls;

/** Lets the privileged shell process forward log lines back to the app process's own logger. */
interface ILogCallback {
    void onLogEvent(String level, String tag, String message, String throwableStackTrace);
}
