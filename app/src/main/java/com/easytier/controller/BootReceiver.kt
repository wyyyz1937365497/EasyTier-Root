package com.easytier.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i("BootReceiver", "Boot completed, EasyTier Pro module handles startup via Magisk service.sh")
            // Magisk 的 service.sh 会在开机时自动启动 easytier-core
            // 这里不需要额外操作
        }
    }
}