package id.dlms.audio

import android.app.Activity
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import android.content.Context

class MainActivity : Activity() {
    private val bg = 0xFF0B1018.toInt()
    private val panel = 0xFF172333.toInt()
    private val accent = 0xFF45E0C1.toInt()
    private val white = 0xFFF1F5FA.toInt()
    private val muted = 0xFFB7C4D6.toInt()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var section = "INPUT"
    private var channel = 0
    private val prefs by lazy { getSharedPreferences("dlms_dsp", Context.MODE_PRIVATE) }
    private val gains = Array(12) { FloatArray(8) }
    private val delays = FloatArray(12)
    private val outputGain = FloatArray(8)
    private val mutedChannels = BooleanArray(12)
    private val polarity = BooleanArray(8)
    private val presetNames = listOf("Flat", "Live", "DJ", "Bass", "Vocal")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadState()
        buildUi()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 18, 16, 16)
            setBackgroundColor(bg)
        }
        root.addView(TextView(this).apply {
            text = "DLMS  •  DSP STUDIO"
            textSize = 25f; setTextColor(accent); typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "XTA-style audio processor · Simulasi lokal Android"
            textSize = 12f; setTextColor(muted); setPadding(0, 4, 0, 12)
        })
        status = TextView(this).apply {
            text = "SIMULATION MODE  •  Tidak perlu perangkat eksternal"
            textSize = 12f; setTextColor(accent); setPadding(12, 12, 12, 12)
            setBackgroundColor(panel)
        }
        root.addView(status)
        val sections = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 10, 0, 4) }
        listOf("INPUT", "OUTPUT", "MATRIX", "PRESET").forEach { name ->
            val b = Button(this).apply { text = name; textSize = 10f; isAllCaps = false }
            b.setOnClickListener {
                section = name
                if (name == "INPUT") channel = channel.coerceIn(0, 3)
                if (name == "OUTPUT") channel = channel.coerceIn(4, 11)
                render()
            }
            sections.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(sections)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 6, 0, 16) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
    }

    private fun render() {
        content.removeAllViews()
        when (section) {
            "INPUT" -> renderChannel(false)
            "OUTPUT" -> renderChannel(true)
            "MATRIX" -> renderMatrix()
            else -> renderPresets()
        }
    }

    private fun header(text: String) {
        content.addView(TextView(this).apply {
            this.text = text; textSize = 18f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(white); setPadding(0, 12, 0, 6)
        })
    }

    private fun info(text: String) {
        content.addView(TextView(this).apply {
            this.text = text; textSize = 12f; setTextColor(muted); setPadding(0, 2, 0, 8)
        })
    }

    private fun channelPicker(output: Boolean) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val count = if (output) 8 else 4
        repeat(count) { idx ->
            val actual = if (output) idx + 4 else idx
            val b = Button(this).apply {
                text = if (output) "OUT ${idx + 1}" else "IN ${('A'.code + idx).toChar()}"
                textSize = 9f
                alpha = if (channel == actual) 1f else 0.65f
            }
            b.setOnClickListener { channel = actual; render() }
            row.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        content.addView(row)
    }

    private fun renderChannel(output: Boolean) {
        channelPicker(output)
        val title = if (output) "OUTPUT ${channel - 3}" else "INPUT ${('A'.code + channel).toChar()}"
        header(title)
        info(if (output) "Gain · delay · crossover · PEQ · polarity · compressor · limiter" else "Gain · delay · 28-band GEQ · 8-band PEQ · dynamic EQ")
        toggleRow(if (mutedChannels[channel]) "Channel MUTE: ON" else "Channel MUTE: OFF", mutedChannels[channel]) {
            mutedChannels[channel] = it; saveState(); render()
        }
        slider("Gain", if (output) outputGain[channel - 4] else gains[channel][0], -24f, 12f, "dB") { v ->
            if (output) outputGain[channel - 4] = v else gains[channel][0] = v
            saveState()
        }
        slider("Delay", delays[channel], 0f, 650f, "ms") { v -> delays[channel] = v; saveState() }
        if (output) {
            toggleRow(if (polarity[channel - 4]) "Polarity: INVERTED" else "Polarity: NORMAL", polarity[channel - 4]) {
                polarity[channel - 4] = it; saveState(); render()
            }
            header("CROSSOVER FILTERS")
            slider("High-pass frequency", gains[channel][1].let { if (it == 0f) 35f else it }, 20f, 2000f, "Hz") { gains[channel][1] = it; saveState() }
            slider("Low-pass frequency", gains[channel][2].let { if (it == 0f) 18000f else it }, 100f, 20000f, "Hz") { gains[channel][2] = it; saveState() }
            choiceRow("HP/LP slope", listOf("6", "12", "18", "24", "48 dB/oct"))
            header("PARAMETRIC EQ · 9 BANDS")
            repeat(9) { band -> eqBand(band, channel) }
            header("COMPRESSOR")
            slider("Threshold", gains[channel][3], -60f, 0f, "dB") { gains[channel][3] = it; saveState() }
            slider("Ratio", if (gains[channel][4] == 0f) 4f else gains[channel][4], 1f, 16f, ":1") { gains[channel][4] = it; saveState() }
            slider("Attack", if (gains[channel][5] == 0f) 10f else gains[channel][5], 0.1f, 100f, "ms") { gains[channel][5] = it; saveState() }
            slider("Release", if (gains[channel][6] == 0f) 100f else gains[channel][6], 10f, 1000f, "ms") { gains[channel][6] = it; saveState() }
            header("LIMITER")
            slider("Limiter threshold", gains[channel][7], -24f, 0f, "dB") { gains[channel][7] = it; saveState() }
        } else {
            header("GRAPHIC EQ · 28 BANDS (SIMULASI)")
            val freqs = listOf("31.5", "40", "50", "63", "80", "100", "125", "160", "200", "250", "315", "400", "500", "630", "800", "1k", "1.25k", "1.6k", "2k", "2.5k", "3.15k", "4k", "5k", "6.3k", "8k", "10k", "12.5k", "16k")
            freqs.forEachIndexed { i, f ->
                val band = i % 8
                slider("GEQ $f Hz", gains[channel][band], -12f, 12f, "dB") { gains[channel][band] = it; saveState() }
            }
            header("DYNAMIC EQ · 3 BANDS")
            repeat(3) { i ->
                slider("DEQ ${i + 1} threshold", gains[channel][i + 1], -60f, 0f, "dB") { gains[channel][i + 1] = it; saveState() }
            }
            header("PARAMETRIC EQ · 8 BANDS")
            repeat(8) { band -> eqBand(band, channel) }
        }
        info("Catatan: ini antarmuka simulasi parameter. Kontrol tidak tersambung ke perangkat DSP eksternal.")
    }

    private fun eqBand(band: Int, ch: Int) {
        header("Band ${band + 1}")
        slider("Frequency", 1000f, 20f, 20000f, "Hz") { }
        slider("Gain", gains[ch][band % 8], -12f, 12f, "dB") { gains[ch][band % 8] = it; saveState() }
        slider("Q", 1f, 0.1f, 10f, "") { }
        choiceRow("Filter type", listOf("PEQ", "Low shelf", "High shelf", "Notch", "Band-pass", "All-pass", "Phase"))
    }

    private fun renderMatrix() {
        header("INPUT → OUTPUT MATRIX")
        info("Atur simulasi routing dari 4 input ke 8 output. Status disimpan lokal.")
        repeat(4) { input ->
            header("INPUT ${('A'.code + input).toChar()}")
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(8) { output ->
                val key = "route_${input}_$output"
                val cb = CheckBox(this).apply {
                    text = "${output + 1}"; textSize = 10f; setTextColor(white)
                    isChecked = prefs.getBoolean(key, input == output % 4)
                    setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean(key, checked).apply() }
                }
                row.addView(cb, LinearLayout.LayoutParams(0, -2, 1f))
            }
            content.addView(row)
        }
        info("Matrix ini menyimpan pilihan routing untuk simulasi; tidak mengubah output audio sistem.")
    }

    private fun renderPresets() {
        header("PRESET MANAGER")
        info("Simpan dan muat konfigurasi DSP di penyimpanan lokal aplikasi.")
        presetNames.forEachIndexed { index, name ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = name; textSize = 15f; setTextColor(white) }, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                text = "LOAD"
                setOnClickListener { applyPreset(index); status.text = "Preset '$name' dimuat"; render() }
            })
            row.addView(Button(this).apply {
                text = "SAVE"
                setOnClickListener { savePreset(index); status.text = "Preset '$name' disimpan di HP"; Toast.makeText(this@MainActivity, "Preset disimpan", Toast.LENGTH_SHORT).show() }
            })
            content.addView(row)
        }
        header("SIMULATED METERS")
        info("Input level  ▰▰▰▰▰▰▱▱  -12 dBFS")
        info("Output level ▰▰▰▰▰▱▱▱  -18 dBFS")
        info("Gain reduction ▰▰▱▱▱▱▱▱  -3 dB")
        info("Meter ditampilkan sebagai contoh visual, bukan pengukuran audio real-time.")
        content.addView(Button(this).apply {
            text = "RESET ALL TO FLAT"
            setOnClickListener {
                for (a in gains) a.fill(0f)
                delays.fill(0f); outputGain.fill(0f); mutedChannels.fill(false); polarity.fill(false)
                saveState(); status.text = "Semua parameter direset"; render()
            }
        })
    }

    private fun slider(labelText: String, value: Float, min: Float, max: Float, unit: String, onChange: (Float) -> Unit) {
        val label = TextView(this).apply {
            textSize = 12f; setTextColor(white); setPadding(0, 4, 0, 0)
        }
        val seek = SeekBar(this).apply { this.max = 1000; progress = (((value - min) / (max - min)).coerceIn(0f, 1f) * 1000).toInt() }
        fun update(p: Int) {
            val v = min + (max - min) * p / 1000f
            label.text = "$labelText: ${"%.1f".format(v)} $unit"
            onChange(v)
        }
        update(seek.progress)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) update(p) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        content.addView(label); content.addView(seek)
    }

    private fun toggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val cb = CheckBox(this).apply { text = label; isChecked = checked; setTextColor(white) }
        cb.setOnCheckedChangeListener { _, value -> onChange(value) }
        content.addView(cb)
    }

    private fun choiceRow(label: String, choices: List<String>) {
        content.addView(TextView(this).apply { text = label; textSize = 12f; setTextColor(muted) })
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices)
        content.addView(spinner)
    }

    private fun applyPreset(index: Int) {
        val base = when (index) {
            1 -> floatArrayOf(2f, 1f, 0f, 2f, 3f, 1f, 0f, 0f)
            2 -> floatArrayOf(5f, 3f, -2f, 3f, 5f, 0f, 0f, 0f)
            3 -> floatArrayOf(8f, 4f, 0f, -2f, 1f, 0f, 0f, 0f)
            4 -> floatArrayOf(0f, 1f, 3f, 2f, 0f, 0f, 0f, 0f)
            else -> FloatArray(8)
        }
        gains.forEach { row -> for (i in row.indices) row[i] = base[i] }
        saveState()
    }

    private fun savePreset(index: Int) {
        val editor = prefs.edit()
        gains.forEachIndexed { i, row -> row.forEachIndexed { j, v -> editor.putFloat("preset_${index}_g_${i}_$j", v) } }
        delays.forEachIndexed { i, v -> editor.putFloat("preset_${index}_d_$i", v) }
        editor.apply()
    }

    private fun loadState() {
        gains.forEachIndexed { i, row -> row.indices.forEach { j -> row[j] = prefs.getFloat("g_${i}_$j", 0f) } }
        delays.indices.forEach { delays[it] = prefs.getFloat("d_$it", 0f) }
        outputGain.indices.forEach { outputGain[it] = prefs.getFloat("og_$it", 0f) }
        mutedChannels.indices.forEach { mutedChannels[it] = prefs.getBoolean("mute_$it", false) }
        polarity.indices.forEach { polarity[it] = prefs.getBoolean("pol_$it", false) }
    }

    private fun saveState() {
        val e = prefs.edit()
        gains.forEachIndexed { i, row -> row.forEachIndexed { j, v -> e.putFloat("g_${i}_$j", v) } }
        delays.forEachIndexed { i, v -> e.putFloat("d_$i", v) }
        outputGain.forEachIndexed { i, v -> e.putFloat("og_$i", v) }
        mutedChannels.forEachIndexed { i, v -> e.putBoolean("mute_$i", v) }
        polarity.forEachIndexed { i, v -> e.putBoolean("pol_$i", v) }
        e.apply()
    }
}
