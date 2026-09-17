package com.ashudialer.app.telecom

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.MediaRecorder
import android.util.Log
import java.io.File

class CallRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    var isRecording = false
        private set

    // YAHI HAI MAIN LOGIC: Check agar app Magisk se system app bani hai (Module flashed)
    fun isSystemApp(): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }

    // Normal APK mein false return karega -> UI mein recording button hide kar dena
    fun canRecord(): Boolean {
        return isSystemApp()
    }

    fun startRecording(outputFile: File) {
        if (!canRecord()) {
            Log.e("AshuDialer", "Call Recording disabled in Normal APK. Flash Magisk module.")
            return
        }

        try {
            mediaRecorder = MediaRecorder().apply {
                // System app privileges hone ki wajah se VOICE_CALL bypass ho jayega
                setAudioSource(MediaRecorder.AudioSource.VOICE_CALL)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Log.d("AshuDialer", "Recording started successfully with VOICE_CALL source.")
        } catch (e: Exception) {
            Log.e("AshuDialer", "Failed to start recording: ${e.message}")
        }
    }

    fun stopRecording() {
        if (isRecording) {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                Log.e("AshuDialer", "Stop recording error: ${e.message}")
            } finally {
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
            }
        }
    }
}
