package com.example.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class BootReciever : BroadcastReceiver() {

    private val TAG = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Получено событие: ${intent.action}")

        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.d(TAG, "Система загруженаб запуск приложения")

            try {
                startMainActivity(context)
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
}