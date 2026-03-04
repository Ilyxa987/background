package com.example.background

import android.content.ContentResolver

interface InfoProvider {
    suspend fun sendInfo(resolver: ContentResolver): Boolean
}