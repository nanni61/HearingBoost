package com.oro.hearingboost

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
        private const val GAIN_MAX        = 500   // slider 0–500 → 0.0–5.0f
        private const val GAIN_DEFAULT    = 100

        private const val HP_MIN_HZ       = 20.0
        private const val HP_MAX_HZ       = 2000.0

        private const val LP_MIN_HZ       = 500.0
        private const val LP_MAX_HZ       = 20000.0

        private const val GATE_DB_DEFAULT = 40    // → -40 dB
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupGainSliders()
        setupVoiceEqToggle()
        setupHpFilter()
        setupLpFilter()
        setupGate()
        setupNoiseSlider()
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

    // ── Voice EQ toggle ───────────────────────────────────────────────────────

    private fun setupVoiceEqToggle() {
        binding.switchVoiceEq.isChecked = processor.voiceEqEnabled
        binding.switchVoiceEq.setOnCheckedChangeListener { _, checked ->
            processor.voiceEqEnabled = checked
        }
    }

    // ── High-Pass filter ──────────────────────────────────────────────────────

    private fun setupHpFilter() {
        binding.switchHp.isChecked = processor.hpEnabled
        binding.seekHp.max         = 100
        binding.seekHp.progress    = freqToSlider(processor.hpFreqHz, HP_MIN_HZ, HP_MAX_HZ)
        updateHpLabel(binding.seekHp.progress)

        binding.switchHp.setOnCheckedChangeListener { _, checked ->
            processor.hpEnabled      = checked
            binding.seekHp.isEnabled = checked
        }
        binding.seekHp.isEnabled = processor.hpEnabled
        binding.seekHp.onProgress { p ->
            processor.hpFreqHz = sliderToFreq(p, HP_MIN_HZ, HP_MAX_HZ)
            updateHpLabel(p)
        }
    }

    private fun updateHpLabel(p: Int) {
        binding.tvHpValue.text = formatHz(sliderToFreq(p, HP_MIN_HZ, HP_MAX_HZ))
    }

    // ── Low-Pass filter ───────────────────────────────────────────────────────

    private fun setupLpFilter() {
        binding.switchLp.isChecked = processor.lpEnabled
        binding.seekLp.max         = 100
        binding.seekLp.progress    = freqToSlider(processor.lpFreqHz, LP_MIN_HZ, LP_MAX_HZ)
        updateLpLabel(binding.seekLp.progress)

        binding.switchLp.setOnCheckedChangeListener { _, checked ->
            processor.lpEnabled      = checked
            binding.seekLp.isEnabled = checked
        }
        binding.seekLp.isEnabled = processor.lpEnabled
        binding.seekLp.onProgress { p ->
            processor.lpFreqHz = sliderToFreq(p, LP_MIN_HZ, LP_MAX_HZ)
            updateLpLabel(p)
        }
    }

    private fun updateLpLabel(p: Int) {
        binding.tvLpValue.text = formatHz(sliderToFreq(p, LP_MIN_HZ, LP_MAX_HZ))
    }

    // ── dB Gate ───────────────────────────────────────────────────────────────

    private fun setupGate() {
        binding.switchGate.isChecked = processor.gateEnabled
        binding.seekGate.max         = 80
        binding.seekGate.progress    = GATE_DB_DEFAULT
        updateGateLabel(GATE_DB_DEFAULT)

        binding.switchGate.setOnCheckedChangeListener { _, checked ->
            processor.gateEnabled      = checked
            binding.seekGate.isEnabled = checked
        }
        binding.seekGate.isEnabled = processor.gateEnabled
        binding.seekGate.onProgress { p ->
            processor.gateThresholdDb = -p.toFloat()
            updateGateLabel(p)
        }
    }

    private fun updateGateLabel(p: Int) {
        binding.tvGateValue.text = "-${p} dB"
    }

    // ── Noise reduction ───────────────────────────────────────────────────────

    private fun setupNoiseSlider() {
        binding.seekNoise.max      = 100
        binding.seekNoise.progress = 50
        updateNoiseLabel(50)

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
