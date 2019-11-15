package com.mohsenoid.androidaudio

import android.Manifest
import android.media.*
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
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

        spnAudioSource.setupSpinnerAdapter(
            data = AudioSources.stringValues(),
            defaultPosition = AudioSources.MIC.ordinal
        )
        spnAudioFormat.setupSpinnerAdapter(
            data = AudioFormats.stringValues(),
            defaultPosition = AudioFormats.ENCODING_PCM_16BIT.ordinal
        )
        spnAudioInChannel.setupSpinnerAdapter(
            data = AudioInChannels.stringValues(),
            defaultPosition = AudioInChannels.CHANNEL_IN_STEREO.ordinal
        )
        spnAudioOutChannel.setupSpinnerAdapter(
            data = AudioOutChannels.stringValues(),
            defaultPosition = AudioOutChannels.CHANNEL_OUT_STEREO.ordinal
        )
        spnStreamType.setupSpinnerAdapter(
            data = StreamTypes.stringValues(),
            defaultPosition = StreamTypes.STREAM_MUSIC.ordinal
        )

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

    private fun Spinner.setupSpinnerAdapter(
        data: List<String>,
        defaultPosition: Int
    ) {
        adapter =
            ArrayAdapter<String>(this@MainActivity, android.R.layout.simple_spinner_item, data)
        setSelection(defaultPosition)
    }

    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private fun ontRecordToggleChange(view: View, isChecked: Boolean) {
        runBlocking {
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
                recordJob?.cancelAndJoin()
            }
        }
    }

    private suspend fun record() {
        val audioSource = AudioSources.getValueByPosition(spnAudioSource.selectedItemPosition)
        val audioFormat = AudioFormats.getValueByPosition(spnAudioFormat.selectedItemPosition)
        val streamType = StreamTypes.getValueByPosition(spnStreamType.selectedItemPosition)
        val audioOutChannel =
            AudioOutChannels.getValueByPosition(spnAudioOutChannel.selectedItemPosition)
        val audioInChannel =
            AudioInChannels.getValueByPosition(spnAudioInChannel.selectedItemPosition)

        val sampleRate = AudioTrack.getNativeOutputSampleRate(streamType.value)

        val bufferSize = Utils.getMinBufferSize(sampleRate, audioOutChannel, audioFormat)

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

        val inputBuffer = ByteArray(FRAME_SIZE)

        try {
            while (recordJob?.isCancelled != true) {
                val numberOfBytes = recorder.read(inputBuffer, 0, FRAME_SIZE)
                if (numberOfBytes < 0) {
                    throw RuntimeException("Unable to read AudioRecorder.")
                }

                audioTrack.write(inputBuffer, 0, numberOfBytes)
            }
        } finally {
            recorder.stop()
            recorder.release()
            audioTrack.stop()
            audioTrack.release()
        }
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
            fun getValueByPosition(position: Int): AudioSources {
                return values()[position]
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
            fun getValueByPosition(position: Int): AudioFormats {
                return values()[position]
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
            fun getValueByPosition(position: Int): AudioOutChannels {
                return values()[position]
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
            fun getValueByPosition(position: Int): AudioInChannels {
                return values()[position]
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
            fun getValueByPosition(position: Int): StreamTypes {
                return values()[position]
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
