package com.oro.hearingboost

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.oro.hearingboost.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val processor = AudioProcessor()

    companion object {
        private const val REQUEST_AUDIO   = 101
        private const val GAIN_MAX        = 500
        private const val GAIN_DEFAULT    = 100
        private const val HP_MIN_HZ       = 20.0
        private const val HP_MAX_HZ       = 2000.0
        private const val LP_MIN_HZ       = 500.0
        private const val LP_MAX_HZ       = 20000.0
        private const val GATE_DB_DEFAULT = 40
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupGainSliders()
        setupHardwareEffects()
        setupVoiceEqToggle()
        setupHpFilter()
        setupLpFilter()
        setupPreEmphasis()
        setupCompressor()
        setupNoiseSlider()
        setupGate()
        setupToggleButton()
    }

    // ── Gain L/R ──────────────────────────────────────────────────────────────

    private fun setupGainSliders() {
        binding.seekGainL.max      = GAIN_MAX
        binding.seekGainL.progress = GAIN_DEFAULT
        binding.seekGainR.max      = GAIN_MAX
        binding.seekGainR.progress = GAIN_DEFAULT
        updateGainLabel(GAIN_DEFAULT, true)
        updateGainLabel(GAIN_DEFAULT, false)

        binding.seekGainL.onProgress { p ->
            processor.gainLeft = p / 100f
            updateGainLabel(p, true)
        }
        binding.seekGainR.onProgress { p ->
            processor.gainRight = p / 100f
            updateGainLabel(p, false)
        }
    }

    private fun updateGainLabel(p: Int, isLeft: Boolean) {
        if (isLeft) binding.tvGainLValue.text = "${p}%"
        else        binding.tvGainRValue.text = "${p}%"
    }

    // ── Hardware effects: AEC + AGC ───────────────────────────────────────────

    private fun setupHardwareEffects() {
        // AEC
        if (processor.aecAvailable) {
            binding.switchAec.isChecked = processor.aecEnabled
            binding.switchAec.setOnCheckedChangeListener { _, checked ->
                processor.aecEnabled = checked
            }
        } else {
            binding.rowAec.visibility = View.GONE
        }

        // AGC
        if (processor.agcAvailable) {
            binding.switchAgc.isChecked = processor.agcEnabled
            binding.switchAgc.setOnCheckedChangeListener { _, checked ->
                processor.agcEnabled = checked
            }
        } else {
            binding.rowAgc.visibility = View.GONE
        }
    }

    // ── Voice EQ ──────────────────────────────────────────────────────────────

    private fun setupVoiceEqToggle() {
        binding.switchVoiceEq.isChecked = processor.voiceEqEnabled
        binding.switchVoiceEq.setOnCheckedChangeListener { _, checked ->
            processor.voiceEqEnabled = checked
        }
    }

    // ── High-Pass ─────────────────────────────────────────────────────────────

    private fun setupHpFilter() {
        binding.switchHp.isChecked = processor.hpEnabled
        binding.seekHp.max         = 100
        binding.seekHp.progress    = freqToSlider(processor.hpFreqHz, HP_MIN_HZ, HP_MAX_HZ)
        updateHpLabel(binding.seekHp.progress)
        binding.seekHp.isEnabled   = processor.hpEnabled

        binding.switchHp.setOnCheckedChangeListener { _, checked ->
            processor.hpEnabled      = checked
            binding.seekHp.isEnabled = checked
        }
        binding.seekHp.onProgress { p ->
            processor.hpFreqHz = sliderToFreq(p, HP_MIN_HZ, HP_MAX_HZ)
            updateHpLabel(p)
        }
    }

    private fun updateHpLabel(p: Int) {
        binding.tvHpValue.text = formatHz(sliderToFreq(p, HP_MIN_HZ, HP_MAX_HZ))
    }

    // ── Low-Pass ──────────────────────────────────────────────────────────────

    private fun setupLpFilter() {
        binding.switchLp.isChecked = processor.lpEnabled
        binding.seekLp.max         = 100
        binding.seekLp.progress    = freqToSlider(processor.lpFreqHz, LP_MIN_HZ, LP_MAX_HZ)
        updateLpLabel(binding.seekLp.progress)
        binding.seekLp.isEnabled   = processor.lpEnabled

        binding.switchLp.setOnCheckedChangeListener { _, checked ->
            processor.lpEnabled      = checked
            binding.seekLp.isEnabled = checked
        }
        binding.seekLp.onProgress { p ->
            processor.lpFreqHz = sliderToFreq(p, LP_MIN_HZ, LP_MAX_HZ)
            updateLpLabel(p)
        }
    }

    private fun updateLpLabel(p: Int) {
        binding.tvLpValue.text = formatHz(sliderToFreq(p, LP_MIN_HZ, LP_MAX_HZ))
    }

    // ── Pre-enfasi ────────────────────────────────────────────────────────────

    private fun setupPreEmphasis() {
        binding.switchPreEmph.isChecked = processor.preEmphasisEnabled
        binding.seekPreEmph.max         = 100
        binding.seekPreEmph.progress    = (processor.preEmphasisStrength * 100).toInt()
        updatePreEmphLabel(binding.seekPreEmph.progress)
        binding.seekPreEmph.isEnabled   = processor.preEmphasisEnabled

        binding.switchPreEmph.setOnCheckedChangeListener { _, checked ->
            processor.preEmphasisEnabled   = checked
            binding.seekPreEmph.isEnabled  = checked
        }
        binding.seekPreEmph.onProgress { p ->
            processor.preEmphasisStrength = p / 100f
            updatePreEmphLabel(p)
        }
    }

    private fun updatePreEmphLabel(p: Int) {
        val db = (p / 100f * 12f).toInt()
        binding.tvPreEmphValue.text = "+${db} dB"
    }

    // ── Compressore ───────────────────────────────────────────────────────────

    private fun setupCompressor() {
        binding.switchComp.isChecked = processor.compressorEnabled
        // Soglia: slider 0–100 → 0.05–0.8 lineare
        binding.seekCompThresh.max      = 100
        binding.seekCompThresh.progress = 40   // default ~0.3
        updateCompThreshLabel(40)
        // Ratio: slider 0–100 → 1:1 – 10:1
        binding.seekCompRatio.max       = 100
        binding.seekCompRatio.progress  = 33   // default ~4:1
        updateCompRatioLabel(33)

        binding.switchComp.setOnCheckedChangeListener { _, checked ->
            processor.compressorEnabled      = checked
            binding.seekCompThresh.isEnabled = checked
            binding.seekCompRatio.isEnabled  = checked
        }
        binding.seekCompThresh.isEnabled = processor.compressorEnabled
        binding.seekCompRatio.isEnabled  = processor.compressorEnabled

        binding.seekCompThresh.onProgress { p ->
            processor.compressorThreshold = 0.05f + (p / 100f) * 0.75f
            updateCompThreshLabel(p)
        }
        binding.seekCompRatio.onProgress { p ->
            processor.compressorRatio = 1f + (p / 100f) * 9f
            updateCompRatioLabel(p)
        }
    }

    private fun updateCompThreshLabel(p: Int) {
        val thresh = 0.05f + (p / 100f) * 0.75f
        val db = (20 * Math.log10(thresh.toDouble())).toInt()
        binding.tvCompThreshValue.text = "${db} dB"
    }

    private fun updateCompRatioLabel(p: Int) {
        val ratio = 1f + (p / 100f) * 9f
        binding.tvCompRatioValue.text = "${String.format("%.1f", ratio)}:1"
    }

    // ── Noise reduction ───────────────────────────────────────────────────────

    private fun setupNoiseSlider() {
        binding.seekNoise.max      = 100
        binding.seekNoise.progress = (processor.noiseLevel * 100).toInt()
        updateNoiseLabel(binding.seekNoise.progress)

        binding.seekNoise.onProgress { p ->
            processor.noiseLevel = p / 100f
            updateNoiseLabel(p)
        }
    }

    private fun updateNoiseLabel(p: Int) {
        val text = when {
            p == 0 -> "Off"
            p < 34 -> "Leggera"
            p < 67 -> "Media"
            else   -> "Alta"
        }
        binding.tvNoiseValue.text = "$text ($p%)"
    }

    // ── dB Gate ───────────────────────────────────────────────────────────────

    private fun setupGate() {
        binding.switchGate.isChecked  = processor.gateEnabled
        binding.seekGate.max          = 80
        binding.seekGate.progress     = GATE_DB_DEFAULT
        updateGateLabel(GATE_DB_DEFAULT)
        binding.seekGate.isEnabled    = processor.gateEnabled

        binding.switchGate.setOnCheckedChangeListener { _, checked ->
            processor.gateEnabled      = checked
            binding.seekGate.isEnabled = checked
        }
        binding.seekGate.onProgress { p ->
            processor.gateThresholdDb = -p.toFloat()
            updateGateLabel(p)
        }
    }

    private fun updateGateLabel(p: Int) {
        binding.tvGateValue.text = "-${p} dB"
    }

    // ── Start/Stop ────────────────────────────────────────────────────────────

    private fun setupToggleButton() {
        binding.btnToggle.setOnClickListener {
            if (processor.isRunning) stopProcessing() else requestAudioAndStart()
        }
    }

    private fun requestAudioAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
            startProcessing()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO
            )
        }
    }

    private fun startProcessing() {
        processor.start()
        binding.btnToggle.text = "⏹ Stop"
        binding.btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.stop_red))
        binding.statusDot.setImageResource(R.drawable.dot_active)
        binding.tvStatus.text = "In ascolto…"
    }

    private fun stopProcessing() {
        processor.stop()
        binding.btnToggle.text = "▶ Avvia"
        binding.btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.start_green))
        binding.statusDot.setImageResource(R.drawable.dot_idle)
        binding.tvStatus.text = "In pausa"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startProcessing()
        } else {
            Toast.makeText(this, "Permesso microfono necessario", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        processor.stop()
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private fun sliderToFreq(p: Int, minHz: Double, maxHz: Double): Double {
        val t = p / 100.0
        return minHz * Math.pow(maxHz / minHz, t)
    }

    private fun freqToSlider(hz: Double, minHz: Double, maxHz: Double): Int {
        val t = Math.log(hz / minHz) / Math.log(maxHz / minHz)
        return (t * 100).toInt().coerceIn(0, 100)
    }

    private fun formatHz(hz: Double) = if (hz >= 1000) {
        String.format("%.1f kHz", hz / 1000.0)
    } else {
        String.format("%.0f Hz", hz)
    }

    private fun SeekBar.onProgress(block: (Int) -> Unit) {
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) = block(p)
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }
}
