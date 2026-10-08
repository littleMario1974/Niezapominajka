package com.mario.niezapominajka

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class VoiceRecorder(
    private val context: Context
) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File {

        val file = File(
            context.filesDir,
            "voice_${System.currentTimeMillis()}.m4a"
        )

        outputFile = file

        recorder =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

        recorder?.apply {

            setAudioSource(
                MediaRecorder.AudioSource.MIC
            )

            setOutputFormat(
                MediaRecorder.OutputFormat.MPEG_4
            )

            setAudioEncoder(
                MediaRecorder.AudioEncoder.AAC
            )

            setOutputFile(
                file.absolutePath
            )

            prepare()
            start()
        }

        return file
    }

    fun stop(): File? {

        return try {

            recorder?.stop()
            recorder?.release()
            recorder = null

            outputFile

        } catch (e: Exception) {

            recorder?.release()
            recorder = null

            null
        }
    }

    fun cancel() {

        recorder?.release()
        recorder = null

        outputFile?.delete()
        outputFile = null
    }
}