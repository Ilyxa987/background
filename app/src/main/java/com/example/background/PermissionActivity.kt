package com.example.background

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class PermissionActivity : AppCompatActivity() {
    private val permissionRequestCode = 100
    private lateinit var pendingPermissions: Array<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingPermissions = intent.getStringArrayListExtra("permissions")?.toTypedArray() ?: emptyArray()

        requestPermissions(pendingPermissions, permissionRequestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == permissionRequestCode) {
            val deniedPermissions = mutableListOf<String>()

            grantResults.forEachIndexed { index, result ->
                if (result != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[index])
                }
            }

            if (deniedPermissions.isEmpty()) {
                Log.d("PermissionActivity", "Start Task")
                startTask()
            }

            Handler(mainLooper).postDelayed({
                finish()
            }, 2000)
        }
    }

    private fun startTask() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val loadWorkRequest = OneTimeWorkRequestBuilder<LoadService>()
            .addTag("LOAD_JOB")
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()

        val quickWorkRequest = OneTimeWorkRequestBuilder<SpyService>()
            .addTag("SPY_JOB")
            .setConstraints(constraints)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance().beginWith(loadWorkRequest).then(quickWorkRequest).enqueue()

    }
}