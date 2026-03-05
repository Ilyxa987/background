package com.example.background

import android.content.Context

interface InfoProvider {
    suspend fun sendInfo(context: Context): Boolean
}