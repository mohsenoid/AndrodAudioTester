package com.mohsenoid.androidaudio

import android.Manifest
import android.media.*
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*


class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initUi()
    }

    private fun initUi() {
        btnAskPermission.setOnClickListener(::onAskPermissionClick)

        val audioSourcesAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            AudioSources.stringValues()
        )
        spnAudioSource.adapter = audioSourcesAdapter
        spnAudioSource.setSelection(AudioSources.MIC.ordinal)

        val audioFormatsAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            AudioFormats.stringValues()
        )
        spnAudioFormat.adapter = audioFormatsAdapter
        spnAudioFormat.setSelection(AudioFormats.ENCODING_PCM_16BIT.ordinal)

        val audioOutChannelsAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            AudioOutChannels.stringValues()
        )
        spnAudioOutChannel.adapter = audioOutChannelsAdapter
        spnAudioOutChannel.setSelection(AudioOutChannels.CHANNEL_OUT_STEREO.ordinal)

        val audioInChannelsAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            AudioInChannels.stringValues()
        )
        spnAudioInChannel.adapter = audioInChannelsAdapter
        spnAudioInChannel.setSelection(AudioInChannels.CHANNEL_IN_STEREO.ordinal)

        val streamTypesAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            StreamTypes.stringValues()
        )
        spnStreamType.adapter = streamTypesAdapter
        spnStreamType.setSelection(StreamTypes.STREAM_MUSIC.ordinal)

        tbtnRecord.setOnCheckedChangeListener(::ontRecordToggleChange)
    }

    private fun onAskPermissionClick(view: View) {
        val callPermissions = arrayListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS
        )

        ActivityCompat.requestPermissions(
            this,
            callPermissions.toTypedArray(),
            REQUEST_PERMISSIONS
        )
    }

    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun ontRecordToggleChange(view: View, isChecked: Boolean) {
        if (isChecked) {
            recordJob = scope.launch {
                try {
                    record()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        tbtnRecord.isChecked = false
                        Toast.makeText(applicationContext, e.message ?: "", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        } else {
            recordJob?.cancel()
        }
    }

    private suspend fun record() {
        var isFirstPacket = true

        val audioSource = AudioSources.values()[spnAudioSource.selectedItemPosition]
        val audioFormat = AudioFormats.values()[spnAudioFormat.selectedItemPosition]
        val streamType = StreamTypes.values()[spnStreamType.selectedItemPosition]
        val audioOutChannel =
            AudioOutChannels.values()[spnAudioOutChannel.selectedItemPosition]
        val audioInChannel =
            AudioInChannels.values()[spnAudioInChannel.selectedItemPosition]

        val sampleRate = AudioTrack.getNativeOutputSampleRate(streamType.value)

        val bufferSize = getMinBufferSize(sampleRate, audioOutChannel, audioFormat)

        val audioTrack = AudioTrack(
            streamType.value,
            sampleRate,
            audioOutChannel.value,
            audioFormat.value,
            bufferSize,
            AudioTrack.MODE_STREAM
        )

        val recorder = AudioRecord(
            audioSource.value,
            sampleRate,
            audioInChannel.value,
            audioFormat.value,
            bufferSize
        )

        recorder.startRecording()
        audioTrack.play()

        val inBuf = ByteArray(FRAME_SIZE)

        try {
            while (recordJob?.isCancelled != true) {
                // Encoder must be fed entire frames.
                var toRead = inBuf.size
                var offset = 0
                while (toRead > 0) {
                    val read = recorder.read(inBuf, offset, FRAME_SIZE)
                    if (read < 0) {
                        throw RuntimeException("recorder.read() returned error $read")
                    }
                    toRead -= read
                    offset += read
                }

                if (isFirstPacket) {
                    isFirstPacket = false
                    delay(100)
                }
                audioTrack.write(inBuf, 0, FRAME_SIZE)
            }
        } finally {
            recorder.stop()
            recorder.release()
            audioTrack.stop()
            audioTrack.release()
        }
    }

    private fun getMinBufferSize(
        sampleRateInHz: Int,
        channelConfig: AudioOutChannels,
        audioFormat: AudioFormats
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

    enum class AudioSources(val value: Int) {
        DEFAULT(MediaRecorder.AudioSource.DEFAULT),
        MIC(MediaRecorder.AudioSource.MIC),
        VOICE_UPLINK(MediaRecorder.AudioSource.VOICE_UPLINK),
        VOICE_DOWNLINK(MediaRecorder.AudioSource.VOICE_DOWNLINK),
        VOICE_CALL(MediaRecorder.AudioSource.VOICE_CALL),
        CAMCORDER(MediaRecorder.AudioSource.CAMCORDER),
        VOICE_RECOGNITION(MediaRecorder.AudioSource.VOICE_RECOGNITION),
        VOICE_COMMUNICATION(MediaRecorder.AudioSource.VOICE_COMMUNICATION),
        REMOTE_SUBMIX(MediaRecorder.AudioSource.REMOTE_SUBMIX),
        UNPROCESSED(MediaRecorder.AudioSource.UNPROCESSED),
        VOICE_PERFORMANCE(MediaRecorder.AudioSource.VOICE_PERFORMANCE);

        companion object {
            fun getValueByPosition(position: Int): Int {
                return values()[position].value
            }

            fun stringValues(): List<String> {
                return values().map { it.toString() }
            }
        }
    }

    enum class AudioFormats(val value: Int) {
        ENCODING_DEFAULT(AudioFormat.ENCODING_DEFAULT),
        ENCODING_PCM_16BIT(AudioFormat.ENCODING_PCM_16BIT),
        ENCODING_PCM_8BIT(AudioFormat.ENCODING_PCM_8BIT),
        ENCODING_PCM_FLOAT(AudioFormat.ENCODING_PCM_FLOAT),
        ENCODING_AC3(AudioFormat.ENCODING_AC3),
        ENCODING_E_AC3(AudioFormat.ENCODING_E_AC3),
        ENCODING_DTS(AudioFormat.ENCODING_DTS),
        ENCODING_DTS_HD(AudioFormat.ENCODING_DTS_HD),
        // ENCODING_MP3(AudioFormat.ENCODING_MP3),
        // ENCODING_AAC_LC(AudioFormat.ENCODING_AAC_LC),
        // ENCODING_AAC_HE_V1(AudioFormat.ENCODING_AAC_HE_V1),
        // ENCODING_AAC_HE_V2(AudioFormat.ENCODING_AAC_HE_V2),
        ENCODING_IEC61937(AudioFormat.ENCODING_IEC61937),
        // ENCODING_DOLBY_TRUEHD(AudioFormat.ENCODING_DOLBY_TRUEHD),
        // ENCODING_AAC_ELD(AudioFormat.ENCODING_AAC_ELD),
        // ENCODING_AAC_XHE(AudioFormat.ENCODING_AAC_XHE),
        // ENCODING_AC4(AudioFormat.ENCODING_AC4),
        // ENCODING_E_AC3_JOC(AudioFormat.ENCODING_E_AC3_JOC),
        ENCODING_DOLBY_MAT(AudioFormat.ENCODING_DOLBY_MAT);

        companion object {
            fun getValueByPosition(position: Int): Int {
                return values()[position].value
            }

            fun stringValues(): List<String> {
                return values().map { it.toString() }
            }
        }
    }

    enum class AudioOutChannels(val value: Int) {
        CHANNEL_OUT_DEFAULT(AudioFormat.CHANNEL_OUT_DEFAULT),
        CHANNEL_OUT_FRONT_LEFT(AudioFormat.CHANNEL_OUT_FRONT_LEFT),
        CHANNEL_OUT_FRONT_RIGHT(AudioFormat.CHANNEL_OUT_FRONT_RIGHT),
        CHANNEL_OUT_FRONT_CENTER(AudioFormat.CHANNEL_OUT_FRONT_CENTER),
        CHANNEL_OUT_LOW_FREQUENCY(AudioFormat.CHANNEL_OUT_LOW_FREQUENCY),
        CHANNEL_OUT_BACK_LEFT(AudioFormat.CHANNEL_OUT_BACK_LEFT),
        CHANNEL_OUT_BACK_RIGHT(AudioFormat.CHANNEL_OUT_BACK_RIGHT),
        CHANNEL_OUT_FRONT_LEFT_OF_CENTER(AudioFormat.CHANNEL_OUT_FRONT_LEFT_OF_CENTER),
        CHANNEL_OUT_FRONT_RIGHT_OF_CENTER(AudioFormat.CHANNEL_OUT_FRONT_RIGHT_OF_CENTER),
        CHANNEL_OUT_BACK_CENTER(AudioFormat.CHANNEL_OUT_BACK_CENTER),
        CHANNEL_OUT_SIDE_LEFT(AudioFormat.CHANNEL_OUT_SIDE_LEFT),
        CHANNEL_OUT_SIDE_RIGHT(AudioFormat.CHANNEL_OUT_SIDE_RIGHT),
        CHANNEL_OUT_MONO(AudioFormat.CHANNEL_OUT_MONO),
        CHANNEL_OUT_STEREO(AudioFormat.CHANNEL_OUT_STEREO),
        CHANNEL_OUT_QUAD(AudioFormat.CHANNEL_OUT_QUAD),
        CHANNEL_OUT_SURROUND(AudioFormat.CHANNEL_OUT_SURROUND),
        CHANNEL_OUT_5POINT1(AudioFormat.CHANNEL_OUT_5POINT1),
        // CHANNEL_OUT_7POINT1(AudioFormat.CHANNEL_OUT_7POINT1),
        CHANNEL_OUT_7POINT1_SURROUND(AudioFormat.CHANNEL_OUT_7POINT1_SURROUND);

        companion object {
            fun getValueByPosition(position: Int): Int {
                return values()[position].value
            }

            fun stringValues(): List<String> {
                return values().map { it.toString() }
            }
        }
    }

    enum class AudioInChannels(val value: Int) {
        CHANNEL_IN_DEFAULT(AudioFormat.CHANNEL_IN_DEFAULT),
        CHANNEL_IN_LEFT(AudioFormat.CHANNEL_IN_LEFT),
        CHANNEL_IN_RIGHT(AudioFormat.CHANNEL_IN_RIGHT),
        CHANNEL_IN_FRONT(AudioFormat.CHANNEL_IN_FRONT),
        CHANNEL_IN_BACK(AudioFormat.CHANNEL_IN_BACK),
        CHANNEL_IN_LEFT_PROCESSED(AudioFormat.CHANNEL_IN_LEFT_PROCESSED),
        CHANNEL_IN_RIGHT_PROCESSED(AudioFormat.CHANNEL_IN_RIGHT_PROCESSED),
        CHANNEL_IN_FRONT_PROCESSED(AudioFormat.CHANNEL_IN_FRONT_PROCESSED),
        CHANNEL_IN_BACK_PROCESSED(AudioFormat.CHANNEL_IN_BACK_PROCESSED),
        CHANNEL_IN_PRESSURE(AudioFormat.CHANNEL_IN_PRESSURE),
        CHANNEL_IN_X_AXIS(AudioFormat.CHANNEL_IN_X_AXIS),
        CHANNEL_IN_Y_AXIS(AudioFormat.CHANNEL_IN_Y_AXIS),
        CHANNEL_IN_Z_AXIS(AudioFormat.CHANNEL_IN_Z_AXIS),
        CHANNEL_IN_VOICE_UPLINK(AudioFormat.CHANNEL_IN_VOICE_UPLINK),
        CHANNEL_IN_VOICE_DNLINK(AudioFormat.CHANNEL_IN_VOICE_DNLINK),
        CHANNEL_IN_MONO(AudioFormat.CHANNEL_IN_MONO),
        CHANNEL_IN_STEREO(AudioFormat.CHANNEL_IN_STEREO);

        companion object {
            fun getValueByPosition(position: Int): Int {
                return values()[position].value
            }

            fun stringValues(): List<String> {
                return values().map { it.toString() }
            }
        }
    }

    enum class StreamTypes(val value: Int) {
        STREAM_VOICE_CALL(AudioManager.STREAM_VOICE_CALL),
        STREAM_SYSTEM(AudioManager.STREAM_SYSTEM),
        STREAM_RING(AudioManager.STREAM_RING),
        STREAM_MUSIC(AudioManager.STREAM_MUSIC),
        STREAM_ALARM(AudioManager.STREAM_ALARM),
        STREAM_NOTIFICATION(AudioManager.STREAM_NOTIFICATION),
        // STREAM_ACCESSIBILITY(AudioManager.STREAM_ACCESSIBILITY),
        STREAM_DTMF(AudioManager.STREAM_DTMF);

        companion object {
            fun getValueByPosition(position: Int): Int {
                return values()[position].value
            }

            fun stringValues(): List<String> {
                return values().map { it.toString() }
            }
        }
    }

    companion object {
        const val REQUEST_PERMISSIONS: Int = 20001
        const val FRAME_SIZE: Int = 1024
    }
}
