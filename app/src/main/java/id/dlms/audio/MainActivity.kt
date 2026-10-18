package id.dlms.audio

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Typeface
import android.view.Gravity
import android.widget.*
import android.view.ViewGroup
import java.util.Locale

class MainActivity : Activity() {
    private val bg = 0xFF0B1018.toInt()
    private val panel = 0xFF172333.toInt()
    private val accent = 0xFF45E0C1.toInt()
    private val white = 0xFFF1F5FA.toInt()
    private val muted = 0xFFB7C4D6.toInt()

    private lateinit var root: LinearLayout
    private lateinit var fileName: TextView
    private lateinit var status: TextView
    private lateinit var timeLabel: TextView
    private lateinit var seek: SeekBar
    private lateinit var playButton: Button
    private lateinit var leftLabel: TextView
    private lateinit var rightLabel: TextView
    private var player: MediaPlayer? = null
    private var selectedUri: Uri? = null
    private var durationMs = 0
    private var draggingSeek = false
    private var leftLevel = 1f
    private var rightLevel = 1f
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            val p = player
            if (p != null) {
                try {
                    if (!draggingSeek && durationMs > 0) seek.progress = (p.currentPosition * 1000L / durationMs).toInt().coerceIn(0, 1000)
                    timeLabel.text = "${formatTime(p.currentPosition)} / ${formatTime(durationMs)}"
                    if (p.isPlaying) status.text = "SEDANG MEMUTAR • L/R DSP AKTIF"
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
            text = "STEREO L / R • LOCAL PLAYER"
            textSize = 12f; setTextColor(muted); setPadding(0, 3, 0, 14)
        })
        status = TextView(this).apply {
            text = "Pilih file MP3 atau MP4 untuk mulai"
            textSize = 12f; setTextColor(accent); setPadding(12, 12, 12, 12)
            setBackgroundColor(panel)
        }
        root.addView(status)
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 8, 0, 20) }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        body.addView(TextView(this).apply {
            text = "PEMUTARAN AUDIO"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(white); setPadding(0, 12, 0, 8)
        })
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
                        if (durationMs > 0) {
                            try { p.seekTo((s!!.progress * durationMs / 1000L).toInt()) } catch (_: IllegalStateException) {}
                        }
                    }
                    draggingSeek = false
                }
            })
        }
        body.addView(seek)

        body.addView(TextView(this).apply {
            text = "KONTROL STEREO L / R"; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(white); setPadding(0, 20, 0, 4)
        })
        body.addView(TextView(this).apply {
            text = "Atur level kanal kiri dan kanan secara terpisah saat file diputar."
            textSize = 12f; setTextColor(muted); setPadding(0, 0, 0, 8)
        })
        leftLabel = TextView(this).apply { setTextColor(white); textSize = 13f }
        body.addView(leftLabel)
        addLevelSlider(body, "LEFT / KIRI", 0.0f) { value -> leftLevel = value; applyStereo() }
        rightLabel = TextView(this).apply { setTextColor(white); textSize = 13f }
        body.addView(rightLabel)
        addLevelSlider(body, "RIGHT / KANAN", 0.0f) { value -> rightLevel = value; applyStereo() }
        addLevelSlider(body, "MASTER VOLUME", 1.0f) { value ->
            leftLevel = leftLevel.coerceIn(0f, 1f)
            rightLevel = rightLevel.coerceIn(0f, 1f)
            masterVolume = value
            applyStereo()
        }
        updateLevelLabels()
        body.addView(TextView(this).apply {
            text = "Catatan: MP4 diputar sebagai audio saja. Kontrol L/R bekerja pada audio yang diputar di aplikasi ini, bukan audio dari aplikasi lain."
            textSize = 12f; setTextColor(muted); setPadding(0, 18, 0, 4)
        })
        setContentView(root)
    }

    private var masterVolume = 1f

    private fun matchWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun addLevelSlider(parent: LinearLayout, label: String, initial: Float, onValue: (Float) -> Unit) {
        val text = TextView(this).apply { text = "$label: ${(initial * 100).toInt()}%"; textSize = 12f; setTextColor(white); setPadding(0, 6, 0, 0) }
        parent.addView(text)
        val bar = SeekBar(this).apply {
            max = 100
            progress = (initial * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    text.text = "$label: $p%"
                    if (fromUser) onValue(p / 100f)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        parent.addView(bar)
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
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "File audio"
            fileName.text = name
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
                prepared.start()
                playButton.text = "Ⅱ PAUSE"
                status.text = "AUDIO SIAP • L/R DSP AKTIF"
                timeLabel.text = "00:00 / ${formatTime(durationMs)}"
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

    private fun togglePlayback() {
        val p = player
        if (p == null) {
            if (selectedUri == null) chooseFile() else prepareTrack(selectedUri!!)
            return
        }
        try {
            if (p.isPlaying) {
                p.pause()
                playButton.text = "▶ PLAY"
                status.text = "DIJEDA"
            } else {
                p.start()
                playButton.text = "Ⅱ PAUSE"
                status.text = "SEDANG MEMUTAR • L/R DSP AKTIF"
            }
        } catch (_: IllegalStateException) {
            selectedUri?.let { prepareTrack(it) }
        }
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
    }

    private fun formatTime(ms: Int): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        return String.format(Locale.getDefault(), "%02d:%02d", total / 60, total % 60)
    }

    private fun releasePlayer() {
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
