package id.dlms.audio

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.EnvironmentalReverb
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private val bg = 0xFF0B1018.toInt()
    private val panel = 0xFF172333.toInt()
    private val accent = 0xFF45E0C1.toInt()
    private val white = 0xFFF1F5FA.toInt()
    private val muted = 0xFFB7C4D6.toInt()

    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var fileName: TextView
    private lateinit var status: TextView
    private lateinit var timeLabel: TextView
    private lateinit var seek: SeekBar
    private lateinit var playButton: Button
    private lateinit var leftLabel: TextView
    private lateinit var rightLabel: TextView
    private lateinit var masterLabel: TextView
    private lateinit var delayLabel: TextView

    private var player: MediaPlayer? = null
    private var selectedUri: Uri? = null
    private var durationMs = 0
    private var draggingSeek = false
    private var leftLevel = 1f
    private var rightLevel = 1f
    private var masterVolume = 1f
    private var delayMs = 80
    private var decayMs = 900
    private var equalizer: Equalizer? = null
    private var reverb: EnvironmentalReverb? = null
    private var eqControls = mutableListOf<SeekBar>()
    private var currentPreset = "Manual"
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val p = player
            if (p != null) {
                try {
                    if (!draggingSeek && durationMs > 0) seek.progress = (p.currentPosition * 1000L / durationMs).toInt().coerceIn(0, 1000)
                    timeLabel.text = "${formatTime(p.currentPosition)} / ${formatTime(durationMs)}"
                    if (p.isPlaying) status.text = "SEDANG MEMUTAR • STEREO L/R"
                } catch (_: IllegalStateException) { }
            }
            handler.postDelayed(this, 300)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        handler.post(tick)
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 16)
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "DLMS AUDIO"
            textSize = 27f; setTextColor(accent); typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "STEREO L / R • EQ • DELAY • PRESET"
            textSize = 12f; setTextColor(muted); setPadding(0, 3, 0, 14)
        })
        status = TextView(this).apply {
            text = "Pilih file MP3 atau MP4 untuk mulai"
            textSize = 12f; setTextColor(accent); setPadding(12, 12, 12, 12)
            setBackgroundColor(panel)
        }
        root.addView(status)
        val scroll = ScrollView(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 20) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        sectionHeader("PEMUTARAN AUDIO")
        fileName = TextView(this).apply {
            text = "Belum ada file dipilih"; textSize = 15f; setTextColor(white)
            setPadding(12, 16, 12, 16); setBackgroundColor(panel)
        }
        body.addView(fileName)
        body.addView(Button(this).apply {
            text = "PILIH FILE MP3 / MP4"
            setOnClickListener { chooseFile() }
        }, matchWidth())
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        playButton = Button(this).apply {
            text = "▶ PLAY"; isAllCaps = false
            setOnClickListener { togglePlayback() }
        }
        controls.addView(playButton, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(Button(this).apply {
            text = "■ STOP"; isAllCaps = false
            setOnClickListener { stopPlayback() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(controls)
        timeLabel = TextView(this).apply { text = "00:00 / 00:00"; textSize = 12f; setTextColor(muted); setPadding(0, 8, 0, 0) }
        body.addView(timeLabel)
        seek = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {}
                override fun onStartTrackingTouch(s: SeekBar?) { draggingSeek = true }
                override fun onStopTrackingTouch(s: SeekBar?) {
                    player?.let { p ->
                        if (durationMs > 0) try { p.seekTo((s!!.progress * durationMs / 1000L).toInt()) } catch (_: Exception) {}
                    }
                    draggingSeek = false
                }
            })
        }
        body.addView(seek)

        sectionHeader("STEREO LEVEL")
        leftLabel = TextView(this).apply { setTextColor(white); textSize = 13f }
        body.addView(leftLabel)
        addSlider("LEFT / KIRI", 100, 0, 100, "%") { leftLevel = it / 100f; applyStereo() }
        rightLabel = TextView(this).apply { setTextColor(white); textSize = 13f }
        body.addView(rightLabel)
        addSlider("RIGHT / KANAN", 100, 0, 100, "%") { rightLevel = it / 100f; applyStereo() }
        masterLabel = TextView(this).apply { setTextColor(white); textSize = 13f }
        body.addView(masterLabel)
        addSlider("MASTER VOLUME", 100, 0, 100, "%") { masterVolume = it / 100f; applyStereo() }

        sectionHeader("EQ STEREO")
        info("Equalizer sistem Android bekerja pada gabungan stereo L/R. EQ terpisah khusus kiri dan kanan memerlukan pemrosesan PCM khusus.")
        eqControls.clear()
        val eq = equalizer
        if (eq != null) addEqControls(eq) else info("Pilih dan mulai putar audio agar kontrol EQ tersedia.")
        body.addView(Button(this).apply {
            text = "RESET EQ"
            setOnClickListener { resetEq() }
        }, matchWidth())

        sectionHeader("DELAY / AMBIENCE")
        info("Efek ruang menggunakan Environmental Reverb Android, bukan echo berulang. Dukungan bergantung pada perangkat.")
        delayLabel = TextView(this).apply { setTextColor(white); textSize = 13f }
        body.addView(delayLabel)
        addSlider("Reflections / Delay", delayMs, 0, 250, " ms") { delayMs = it; applyReverb() }
        addSlider("Decay / Ekor efek", decayMs, 100, 3000, " ms") { decayMs = it; applyReverb() }
        body.addView(Button(this).apply {
            text = "MATIKAN EFEK RUANG"
            setOnClickListener {
                try { reverb?.enabled = false } catch (_: Exception) {}
                status.text = "Efek ruang dimatikan"
            }
        }, matchWidth())

        sectionHeader("PRESET SUARA")
        info("Preset awal yang bisa disesuaikan lagi melalui slider EQ dan L/R.")
        listOf("Dangdut", "DJ Bass Atos + Mid Trotok", "Flat").forEach { preset ->
            body.addView(Button(this).apply {
                text = preset
                setOnClickListener { applyPreset(preset) }
            }, matchWidth())
        }
        info("MP4 diputar sebagai audio saja. Semua kontrol hanya memengaruhi file yang diputar di DLMS Audio, bukan aplikasi lain.")
        updateLevelLabels()
        setContentView(root)
    }

    private fun sectionHeader(text: String) {
        body.addView(TextView(this).apply {
            this.text = text; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(white); setPadding(0, 20, 0, 8)
        })
    }

    private fun info(text: String) {
        body.addView(TextView(this).apply {
            this.text = text; textSize = 12f; setTextColor(muted); setPadding(0, 0, 0, 8)
        })
    }

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun addSlider(label: String, initial: Int, min: Int, max: Int, unit: String, onValue: (Int) -> Unit) {
        val text = TextView(this).apply { text = "$label: $initial$unit"; textSize = 12f; setTextColor(white); setPadding(0, 5, 0, 0) }
        body.addView(text)
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = (initial - min).coerceIn(0, max - min)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    val value = p + min
                    text.text = "$label: $value$unit"
                    if (fromUser) onValue(value)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        body.addView(bar)
    }

    private fun chooseFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/mp4", "video/quicktime", "video/x-m4v"))
        }
        startActivityForResult(intent, 101)
    }

    @Deprecated("Legacy activity result used for compatibility with the existing project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            selectedUri = uri
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            fileName.text = uri.lastPathSegment?.substringAfterLast('/') ?: "File audio"
            prepareTrack(uri)
        }
    }

    private fun prepareTrack(uri: Uri) {
        releasePlayer()
        status.text = "Memuat file audio..."
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setDataSource(this, uri)
            mp.setOnPreparedListener { prepared ->
                durationMs = prepared.duration.coerceAtLeast(0)
                prepared.setVolume(leftLevel * masterVolume, rightLevel * masterVolume)
                attachEffects(prepared.audioSessionId)
                prepared.start()
                playButton.text = "Ⅱ PAUSE"
                status.text = "AUDIO SIAP • L/R + EQ + EFEK"
                rebuildEqArea()
            }
            mp.setOnCompletionListener {
                playButton.text = "▶ PLAY"
                status.text = "PEMUTARAN SELESAI"
                seek.progress = 1000
            }
            mp.setOnErrorListener { _, what, extra ->
                status.text = "Gagal memutar file ($what/$extra). Coba file audio lain."
                playButton.text = "▶ PLAY"
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            status.text = "Tidak dapat membuka file: ${e.message ?: "format tidak didukung"}"
            releasePlayer()
        }
    }

    private fun attachEffects(sessionId: Int) {
        releaseEffects()
        try {
            equalizer = Equalizer(0, sessionId).apply { enabled = true }
        } catch (_: Exception) { equalizer = null }
        try {
            reverb = EnvironmentalReverb(0, sessionId).apply {
                roomLevel = (-900).toShort()
                roomHFLevel = (-500).toShort()
                reflectionsLevel = (-600).toShort()
                reverbLevel = (-700).toShort()
                reflectionsDelay = delayMs.coerceIn(0, 300)
                reverbDelay = delayMs.coerceIn(0, 100)
                decayTime = decayMs.coerceIn(100, 7000)
                decayHFRatio = 500.toShort()
                enabled = true
            }
        } catch (_: Exception) { reverb = null }
        rebuildEqArea()
        applyPreset(currentPreset)
    }

    private fun rebuildEqArea() {
        // Avoid rebuilding the whole activity: it duplicates controls and resets playback.
        if (equalizer == null && ::body.isInitialized) status.text = "EQ belum tersedia untuk sesi audio ini"
    }

    private fun addEqControls(eq: Equalizer?) {
        eqControls.clear()
        if (eq == null) {
            info("Equalizer tidak tersedia pada perangkat ini.")
            return
        }
        try {
            val count = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            for (i in 0 until count) {
                val band = i.toShort()
                val freq = eq.getCenterFreq(band) / 1000f
                val current = eq.getBandLevel(band).toInt()
                val label = TextView(this).apply {
                    text = "Band ${i + 1} • ${String.format(Locale.getDefault(), "%.1f", freq)} kHz: ${current / 100f} dB"
                    textSize = 12f; setTextColor(white); setPadding(0, 4, 0, 0)
                }
                body.addView(label)
                val minDb = range[0].toInt()
                val maxDb = range[1].toInt()
                val bar = SeekBar(this).apply {
                    max = maxDb - minDb
                    progress = (current - minDb).coerceIn(0, max)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                            val level = (p + minDb).coerceIn(minDb, maxDb)
                            label.text = "Band ${i + 1} • ${String.format(Locale.getDefault(), "%.1f", freq)} kHz: ${level / 100f} dB"
                            if (fromUser) try { eq.setBandLevel(band, level.toShort()) } catch (_: Exception) {}
                        }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })
                }
                body.addView(bar)
                eqControls.add(bar)
            }
        } catch (_: Exception) {
            info("Tidak dapat membaca band EQ pada perangkat ini.")
        }
    }

    private fun resetEq() {
        try {
            val eq = equalizer ?: return
            for (i in 0 until eq.numberOfBands.toInt()) eq.setBandLevel(i.toShort(), 0)
            status.text = "EQ direset ke Flat"
            rebuildEqArea()
        } catch (_: Exception) { status.text = "Reset EQ tidak didukung perangkat" }
    }

    private fun applyReverb() {
        try {
            reverb?.let {
                // Android applies separate valid ranges to reflections and reverb delay.
                it.reflectionsDelay = delayMs.coerceIn(0, 300)
                it.reverbDelay = delayMs.coerceIn(0, 100)
                it.decayTime = decayMs.coerceIn(100, 7000)
                it.enabled = true
            }
        } catch (_: Exception) {
            status.text = "Efek delay/ruang tidak didukung perangkat"
        }
    }

    private fun applyPreset(name: String) {
        currentPreset = name
        try {
            val eq = equalizer
            if (eq != null) {
                val count = eq.numberOfBands.toInt()
                val values = when (name) {
                    "Dangdut" -> floatArrayOf(3f, 2f, 0f, 2.5f, 4f)
                    "DJ Bass Atos + Mid Trotok" -> floatArrayOf(5f, 3f, -1f, 3.5f, 2f)
                    else -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
                }
                val range = eq.bandLevelRange
                for (i in 0 until count) {
                    val db = values[(i * values.size / count).coerceIn(0, values.lastIndex)]
                    val level = (db * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                    eq.setBandLevel(i.toShort(), level.toShort())
                }
                eq.enabled = true
            }
            when (name) {
                "Dangdut" -> { leftLevel = 1f; rightLevel = 0.78f; delayMs = 95; decayMs = 1050 }
                "DJ Bass Atos + Mid Trotok" -> { leftLevel = 0.78f; rightLevel = 1f; delayMs = 125; decayMs = 1350 }
                else -> { leftLevel = 1f; rightLevel = 1f; delayMs = 0; decayMs = 500 }
            }
            applyStereo()
            applyReverb()
            status.text = "Preset $name diterapkan"
        } catch (_: Exception) {
            status.text = "Preset diterapkan sebagian; efek tertentu tidak tersedia di perangkat"
        }
        updateLevelLabels()
        rebuildEqArea()
    }

    private fun togglePlayback() {
        val p = player
        if (p == null) {
            if (selectedUri == null) chooseFile() else prepareTrack(selectedUri!!)
            return
        }
        try {
            if (p.isPlaying) {
                p.pause(); playButton.text = "▶ PLAY"; status.text = "DIJEDA"
            } else {
                p.start(); playButton.text = "Ⅱ PAUSE"; status.text = "SEDANG MEMUTAR • STEREO L/R"
            }
        } catch (_: IllegalStateException) { selectedUri?.let { prepareTrack(it) } }
    }

    private fun stopPlayback() {
        try { player?.let { if (it.isPlaying) it.pause(); it.seekTo(0) } } catch (_: Exception) {}
        seek.progress = 0
        playButton.text = "▶ PLAY"
        status.text = "STOP • FILE SIAP DIPUTAR ULANG"
    }

    private fun applyStereo() {
        try { player?.setVolume(leftLevel * masterVolume, rightLevel * masterVolume) } catch (_: Exception) {}
        updateLevelLabels()
    }

    private fun updateLevelLabels() {
        if (::leftLabel.isInitialized) leftLabel.text = "KIRI (L): ${(leftLevel * 100).toInt()}%"
        if (::rightLabel.isInitialized) rightLabel.text = "KANAN (R): ${(rightLevel * 100).toInt()}%"
        if (::masterLabel.isInitialized) masterLabel.text = "MASTER: ${(masterVolume * 100).toInt()}%"
        if (::delayLabel.isInitialized) delayLabel.text = "Delay reflections: $delayMs ms • decay: $decayMs ms"
    }

    private fun formatTime(ms: Int): String {
        val total = ms.coerceAtLeast(0) / 1000
        return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60)
    }

    private fun releaseEffects() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { reverb?.release() } catch (_: Exception) {}
        equalizer = null
        reverb = null
    }

    private fun releasePlayer() {
        releaseEffects()
        try { player?.release() } catch (_: Exception) {}
        player = null
        durationMs = 0
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        releasePlayer()
        super.onDestroy()
    }
}
