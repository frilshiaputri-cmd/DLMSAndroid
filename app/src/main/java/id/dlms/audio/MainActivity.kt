package id.dlms.audio

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.view.View
import android.widget.*
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow

class MainActivity : Activity() {
    private val requestCapture = 2026
    private lateinit var status: TextView
    private lateinit var startButton: Button
    private lateinit var delaySeek: SeekBar
    private lateinit var delayLabel: TextView
    private val eqGains = FloatArray(5)
    @Volatile private var running = false
    private var worker: Thread? = null
    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private var delayMs = 0
    private val sampleRate = 48000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 24, 22, 20)
            setBackgroundColor(0xFF0B1018.toInt())
        }
        val title = TextView(this).apply {
            text = "DLMS AUDIO"
            textSize = 27f
            setTextColor(0xFF45E0C1.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        outer.addView(title)
        outer.addView(TextView(this).apply {
            text = "System Audio • 5-Band EQ • Delay"
            textSize = 14f
            setTextColor(0xFFB7C4D6.toInt())
            setPadding(0, 4, 0, 18)
        })
        status = TextView(this).apply {
            text = "Status: belum tersambung"
            textSize = 15f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(14, 14, 14, 14)
            setBackgroundColor(0xFF172333.toInt())
        }
        outer.addView(status)
        startButton = Button(this).apply {
            text = "SAMBUNGKAN AUDIO SISTEM"
            setOnClickListener { requestSystemAudio() }
        }
        outer.addView(startButton)
        outer.addView(TextView(this).apply {
            text = "EQ 5 Band (dB)"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 18, 0, 4)
        })
        val bands = listOf("Bass  60 Hz", "Low-mid  250 Hz", "Mid  1 kHz", "High-mid  4 kHz", "Treble  12 kHz")
        bands.forEachIndexed { i, name ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val label = TextView(this).apply {
                text = "$name: 0 dB"
                textSize = 13f
                setTextColor(0xFFE8EEF7.toInt())
                minWidth = 120
            }
            val seek = SeekBar(this).apply {
                max = 240
                progress = 120
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                        eqGains[i] = (p - 120) / 10f
                        label.text = "$name: ${"%.1f".format(eqGains[i])} dB"
                    }
                    override fun onStartTrackingTouch(s: SeekBar?) {}
                    override fun onStopTrackingTouch(s: SeekBar?) {}
                })
            }
            line.addView(label, LinearLayout.LayoutParams(120, -2))
            line.addView(seek, LinearLayout.LayoutParams(0, -2, 1f))
            outer.addView(line)
        }
        outer.addView(TextView(this).apply {
            text = "System Delay: 0 ms"
            tag = "delay"
            textSize = 17f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 18, 0, 4)
        })
        delayLabel = outer.findViewWithTag("delay")
        delaySeek = SeekBar(this).apply {
            max = 500
            progress = 0
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    delayMs = p
                    delayLabel.text = "System Delay: $p ms"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        outer.addView(delaySeek)
        outer.addView(TextView(this).apply {
            text = "Preset"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 10, 0, 4)
        })
        val presets = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Flat", "Dangdut", "DJ", "Bass Nguk", "Mid Trotok"))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) {}
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val values = when (position) {
                        1 -> floatArrayOf(5f, 2f, 0f, 2f, 3f)
                        2 -> floatArrayOf(6f, 3f, -1f, 3f, 5f)
                        3 -> floatArrayOf(9f, 4f, -2f, 1f, 2f)
                        4 -> floatArrayOf(2f, 0f, 5f, 3f, 1f)
                        else -> FloatArray(5)
                    }
                    for (i in values.indices) eqGains[i] = values[i]
                    refreshEqLabels(outer, values)
                }
            }
        }
        outer.addView(presets)
        val scroll = ScrollView(this).apply { addView(outer) }
        setContentView(scroll)
    }

    private fun refreshEqLabels(root: LinearLayout, values: FloatArray) {
        val labels = ArrayList<TextView>()
        fun walk(v: View) {
            if (v is TextView && v.text.toString().contains("Hz:")) labels.add(v)
            if (v is LinearLayout) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
        val names = listOf("Bass  60 Hz", "Low-mid  250 Hz", "Mid  1 kHz", "High-mid  4 kHz", "Treble  12 kHz")
        labels.take(5).forEachIndexed { i, t -> t.text = "${names[i]}: ${"%.1f".format(values[i])} dB" }
    }

    private fun requestSystemAudio() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), requestCapture)
    }

    @Deprecated("Deprecated by Android API; retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestCapture || resultCode != RESULT_OK || data == null) {
            status.text = "Izin audio sistem dibatalkan."
            return
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        if (projection == null) {
            status.text = "Gagal memperoleh izin capture audio."
            return
        }
        startProcessing(projection!!)
    }

    private fun startProcessing(mp: MediaProjection) {
        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mp)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            recorder = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(min * 2, 8192))
                .setAudioPlaybackCaptureConfig(config)
                .build()
            val outFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()
            player = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(outFormat)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(min * 2, 8192))
                .build()
            if (recorder?.state != AudioRecord.STATE_INITIALIZED || player?.state != AudioTrack.STATE_INITIALIZED) {
                status.text = "AudioRecord/AudioTrack gagal diinisialisasi."
                cleanup()
                return
            }
            running = true
            recorder?.startRecording()
            player?.play()
            startButton.text = "PUTUSKAN AUDIO"
            startButton.setOnClickListener { stopProcessing() }
            status.text = "Capture aktif. Putar audio di aplikasi lain."
            worker = thread(name = "DLMS-Audio-DSP") {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                val input = ShortArray(2048)
                val delayBuffer = ShortArray(sampleRate * 2 * 2)
                var writeFrame = 0
                val bandFiltersL = Array(5) { Biquad() }
                val bandFiltersR = Array(5) { Biquad() }
                val freqs = doubleArrayOf(60.0, 250.0, 1000.0, 4000.0, 12000.0)
                while (running) {
                    val count = recorder?.read(input, 0, input.size) ?: -1
                    if (count <= 0) continue
                    for (b in 0..4) {
                        bandFiltersL[b].setPeak(freqs[b], eqGains[b].toDouble(), sampleRate.toDouble())
                        bandFiltersR[b].setPeak(freqs[b], eqGains[b].toDouble(), sampleRate.toDouble())
                    }
                    for (i in 0 until count step 2) {
                        val left = input[i].toDouble() / 32768.0
                        val right = if (i + 1 < count) input[i + 1].toDouble() / 32768.0 else left
                        var l = left
                        var r = right
                        for (b in 0..4) {
                            l = bandFiltersL[b].process(l)
                            r = bandFiltersR[b].process(r)
                        }
                        val frameCapacity = delayBuffer.size / 2
                        val delayFrames = (delayMs * sampleRate / 1000).coerceIn(0, frameCapacity - 1)
                        val readFrame = (writeFrame - delayFrames + frameCapacity) % frameCapacity
                        val outIndex = (writeFrame % frameCapacity) * 2
                        delayBuffer[outIndex] = (l.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                        delayBuffer[outIndex + 1] = (r.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                        val readIndex = readFrame * 2
                        input[i] = delayBuffer[readIndex]
                        if (i + 1 < count) input[i + 1] = delayBuffer[readIndex + 1]
                        writeFrame = (writeFrame + 1) % frameCapacity
                    }
                    player?.write(input, 0, count)
                }
            }
        } catch (e: Exception) {
            status.text = "Gagal: ${e.message ?: e.javaClass.simpleName}"
            cleanup()
        }
    }

    private fun stopProcessing() {
        running = false
        worker?.join(250)
        cleanup()
        startButton.text = "SAMBUNGKAN AUDIO SISTEM"
        startButton.setOnClickListener { requestSystemAudio() }
        status.text = "Status: terputus"
    }

    private fun cleanup() {
        running = false
        try { recorder?.stop() } catch (_: Exception) {}
        try { player?.pause(); player?.flush(); player?.stop() } catch (_: Exception) {}
        recorder?.release(); recorder = null
        player?.release(); player = null
        projection?.stop(); projection = null
    }

    override fun onDestroy() {
        stopProcessing()
        super.onDestroy()
    }

    private class Biquad {
        private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
        private var a1 = 0.0; private var a2 = 0.0
        private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0
        fun setPeak(freq: Double, gainDb: Double, fs: Double) {
            val f = freq.coerceAtMost(fs * 0.45)
            val a = 10.0.pow10(gainDb / 40.0)
            val w = 2.0 * PI * f / fs
            val alpha = sin(w) / 2.0
            val c = cos(w)
            val a0 = 1.0 + alpha / a
            b0 = (1.0 + alpha * a) / a0
            b1 = (-2.0 * c) / a0
            b2 = (1.0 - alpha * a) / a0
            a1 = (-2.0 * c) / a0
            a2 = (1.0 - alpha / a) / a0
        }
        fun process(x: Double): Double {
            val y = b0*x + b1*x1 + b2*x2 - a1*y1 - a2*y2
            x2=x1; x1=x; y2=y1; y1=y
            return y
        }
    }
    private fun Double.pow10(v: Double): Double = 10.0.pow(v)
}
