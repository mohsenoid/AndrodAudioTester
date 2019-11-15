package com.mohsenoid.androidaudio

import android.media.AudioTrack

object Utils {

    fun getMinBufferSize(
        sampleRateInHz: Int,
        channelConfig: MainActivity.AudioOutChannels,
        audioFormat: MainActivity.AudioFormats
    ): Int {
        val bufferSize =
            AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig.value, audioFormat.value)

        if (bufferSize < 0) {
            val errorMessage = when (bufferSize) {
                AudioTrack.ERROR -> "Denotes a generic operation failure."
                AudioTrack.ERROR_BAD_VALUE -> "Denotes a failure due to the use of an invalid value."
                AudioTrack.ERROR_DEAD_OBJECT -> "An error code indicating that the object reporting it is no longer valid and needs to be recreated."
                else -> "Unable to get Audio Track min buffer size"
            }

            throw Exception(
                "$errorMessage\n" +
                        "sampleRateInHz = $sampleRateInHz\n" +
                        "channelConfig = $channelConfig\n" +
                        "audioFormat = $audioFormat}"
            )
        }

        return bufferSize
    }
}