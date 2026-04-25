package com.scrollguard

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.scrollguard.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val timerOptions = arrayOf("5 Dakika", "15 Dakika", "30 Dakika", "1 Saat", "Özel Süre", "Sonsuz")
    
    // Uygulama içinde bir timer'ın aktif olup olmadığını takip etmek için local flag
    // (Servis her başladığında intent ile gönderdiğimiz süre 0'dan büyükse true olur)
    private var isTimerActive = false

    private val timerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ScrollBlockerService.ACTION_TIMER_UPDATE -> {
                    val time = intent.getStringExtra(ScrollBlockerService.EXTRA_REMAINING_TIME)
                    binding.tvTimerInfo.text = "Kalan Süre: $time"
                    binding.tvTimerInfo.setTextColor(getColor(R.color.active_color))
                    isTimerActive = true
                    updateStatus()
                }
                ScrollBlockerService.ACTION_TIMER_FINISHED -> {
                    isTimerActive = false
                    updateStatus()
                    binding.tvTimerInfo.text = "Süre Doldu - Koruma Kapandı"
                    binding.tvTimerInfo.setTextColor(getColor(R.color.inactive_color))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupTimerUI()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        
        val filter = IntentFilter().apply {
            addAction(ScrollBlockerService.ACTION_TIMER_UPDATE)
            addAction(ScrollBlockerService.ACTION_TIMER_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(timerReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(timerReceiver)
        } catch (_: Exception) {}
    }

    private fun setupUI() {
        binding.toggleSwitch.setOnClickListener {
            val isChecked = binding.toggleSwitch.isChecked
            if (isChecked) {
                if (!isAccessibilityEnabled()) {
                    binding.toggleSwitch.isChecked = false
                    showAccessibilityDialog()
                } else {
                    val minutes = getSelectedMinutes()
                    if (minutes > 0) {
                        // Eğer bir süre seçildiyse UYARI göster
                        showStrictWarningDialog(minutes)
                    } else {
                        startBlockerService(0)
                        updateStatus()
                    }
                }
            } else {
                // Eğer buraya düşerse (klik yapılabiliyorsa) zaten kilitli değildir
                stopBlockerService()
                updateStatus()
            }
        }

        binding.btnOpenSettings.setOnClickListener {
            openAccessibilitySettings()
        }
    }

    private fun setupTimerUI() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, timerOptions)
        binding.actvTimerMode.setAdapter(adapter)
        binding.actvTimerMode.setText(timerOptions.last(), false)

        binding.npDays.minValue = 0
        binding.npDays.maxValue = 30
        binding.npHours.minValue = 0
        binding.npHours.maxValue = 23
        binding.npMinutes.minValue = 0
        binding.npMinutes.maxValue = 59
        binding.npMinutes.value = 30

        binding.actvTimerMode.setOnItemClickListener { _, _, position, _ ->
            val selected = timerOptions[position]
            binding.llCustomPicker.visibility = if (selected == "Özel Süre") View.VISIBLE else View.GONE
            updateTimerInfoText()
        }

        val pickerListener = { _: Int, _: Int -> updateTimerInfoText() }
        binding.npDays.setOnValueChangedListener { _, _, _ -> pickerListener(0, 0) }
        binding.npHours.setOnValueChangedListener { _, _, _ -> pickerListener(0, 0) }
        binding.npMinutes.setOnValueChangedListener { _, _, _ -> pickerListener(0, 0) }
    }

    private fun updateTimerInfoText() {
        val selected = binding.actvTimerMode.text.toString()
        val info = when (selected) {
            "5 Dakika" -> "5 dakika sonra kapanacak"
            "15 Dakika" -> "15 dakika sonra kapanacak"
            "30 Dakika" -> "30 dakika sonra kapanacak"
            "1 Saat" -> "1 saat sonra kapanacak"
            "Özel Süre" -> {
                val d = binding.npDays.value
                val h = binding.npHours.value
                val m = binding.npMinutes.value
                "Özel Süre: $d gün $h saat $m dakika sonra kapanacak"
            }
            "Sonsuz" -> "Süresiz koruma"
            else -> ""
        }
        binding.tvTimerInfo.text = info
        binding.tvTimerInfo.setTextColor(getColor(R.color.text_secondary))
    }

    private fun showStrictWarningDialog(minutes: Int) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Katı Mod Uyarısı")
            .setMessage("Bu süreli korumayı başlattığında, süre dolana kadar koruma manuel olarak KAPATILAMAYACAKTIR.\n\nEmin misin?")
            .setPositiveButton("Evet, Başlat") { _, _ ->
                isTimerActive = true
                startBlockerService(minutes)
                updateStatus()
            }
            .setNegativeButton("İptal") { _, _ ->
                binding.toggleSwitch.isChecked = false
            }
            .setCancelable(false)
            .show()
    }

    private fun getSelectedMinutes(): Int {
        return when (binding.actvTimerMode.text.toString()) {
            "5 Dakika" -> 5
            "15 Dakika" -> 15
            "30 Dakika" -> 30
            "1 Saat" -> 60
            "Özel Süre" -> {
                val d = binding.npDays.value
                val h = binding.npHours.value
                val m = binding.npMinutes.value
                (d * 1440) + (h * 60) + m
            }
            else -> 0
        }
    }

    private fun updateStatus() {
        val accessibilityOn = isAccessibilityEnabled()
        val serviceRunning = ScrollBlockerService.isRunning

        binding.toggleSwitch.isChecked = serviceRunning
        
        // KRİTİK: Eğer servis çalışıyorsa VE bir timer aktifse toggle'ı kilitliyoruz.
        // Ancak accessibility kapalıysa veya servis durmuşsa kilit açılır.
        binding.toggleSwitch.isEnabled = !(serviceRunning && isTimerActive)

        if (!accessibilityOn) {
            binding.tvStatus.text = "⚠️ Erişilebilirlik İzni Gerekli"
            binding.tvStatus.setTextColor(getColor(R.color.warning_color))
            binding.tvSubStatus.text = "Aşağıdaki butona bas ve ScrollGuard'ı etkinleştir"
            binding.btnOpenSettings.visibility = View.VISIBLE
            binding.toggleSwitch.isEnabled = true 
        } else if (!serviceRunning) {
            binding.tvStatus.text = "😴 Devre Dışı"
            binding.tvStatus.setTextColor(getColor(R.color.inactive_color))
            binding.tvSubStatus.text = "Toggle ile başlat"
            binding.btnOpenSettings.visibility = View.GONE
            updateTimerInfoText() // Başlamadan önceki modu göster
        } else {
            binding.tvStatus.text = "🛡️ Aktif — Koruma Açık"
            binding.tvStatus.setTextColor(getColor(R.color.active_color))
            binding.tvSubStatus.text = "Reels, Shorts ve TikTok engelleniyor"
            binding.btnOpenSettings.visibility = View.GONE
            
            if (isTimerActive) {
                binding.tvStatus.text = "🔒 Katı Koruma Aktif"
            }
        }
    }

    private fun startBlockerService(minutes: Int) {
        val intent = Intent(this, ScrollBlockerService::class.java).apply {
            putExtra(ScrollBlockerService.EXTRA_TIMER_MINUTES, minutes)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopBlockerService() {
        val intent = Intent(this, ScrollBlockerService::class.java)
        stopService(intent)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any { service ->
            service.resolveInfo.serviceInfo.packageName == packageName &&
            service.resolveInfo.serviceInfo.name.contains("ScrollAccessibilityService")
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("İzin Gerekli")
            .setMessage("ScrollGuard'ın çalışması için Erişilebilirlik iznine ihtiyacı var...")
            .setPositiveButton("Ayarlara Git") { _, _ -> openAccessibilitySettings() }
            .setNegativeButton("İptal", null)
            .show()
    }
}
