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
        private const val REQUEST_AUDIO = 101
        private const val GAIN_MAX      = 500   // slider 0–500 → 0.0–5.0f
        private const val GAIN_DEFAULT  = 100   // = 100% = 1.0f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Keep screen on while amplifying
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupSliders()
        setupToggle()
    }

    // ── UI Setup ─────────────────────────────────────────────────────────────

    private fun setupSliders() {

        // ── Gain Left ────────────────────────────────────────────────────────
        binding.seekGainL.max      = GAIN_MAX
        binding.seekGainL.progress = GAIN_DEFAULT
        updateGainLabel(binding.seekGainL.progress, isLeft = true)

        binding.seekGainL.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                processor.gainLeft = p / 100f
                updateGainLabel(p, isLeft = true)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // ── Gain Right ───────────────────────────────────────────────────────
        binding.seekGainR.max      = GAIN_MAX
        binding.seekGainR.progress = GAIN_DEFAULT
        updateGainLabel(binding.seekGainR.progress, isLeft = false)

        binding.seekGainR.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                processor.gainRight = p / 100f
                updateGainLabel(p, isLeft = false)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // ── Noise Reduction ──────────────────────────────────────────────────
        binding.seekNoise.max      = 100
        binding.seekNoise.progress = 50
        updateNoiseLabel(50)

        binding.seekNoise.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                processor.noiseLevel = p / 100f
                updateNoiseLabel(p)
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun setupToggle() {
        binding.btnToggle.setOnClickListener {
            if (processor.isRunning) {
                stopProcessing()
            } else {
                requestAudioAndStart()
            }
        }
    }

    // ── Label helpers ─────────────────────────────────────────────────────────

    private fun updateGainLabel(progress: Int, isLeft: Boolean) {
        val label = "${progress}%"
        if (isLeft) binding.tvGainLValue.text = label
        else        binding.tvGainRValue.text = label
    }

    private fun updateNoiseLabel(progress: Int) {
        val text = when {
            progress == 0        -> "Off"
            progress < 34        -> "Leggera"
            progress < 67        -> "Media"
            else                 -> "Alta"
        }
        binding.tvNoiseValue.text = "$text ($progress%)"
    }

    // ── Audio control ─────────────────────────────────────────────────────────

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
        binding.btnToggle.setBackgroundColor(
            ContextCompat.getColor(this, R.color.stop_red)
        )
        binding.statusDot.setImageResource(R.drawable.dot_active)
        binding.tvStatus.text = "In ascolto…"
    }

    private fun stopProcessing() {
        processor.stop()
        binding.btnToggle.text = "▶ Avvia"
        binding.btnToggle.setBackgroundColor(
            ContextCompat.getColor(this, R.color.start_green)
        )
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
            Toast.makeText(this,
                "Permesso microfono necessario", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        processor.stop()
    }
}
