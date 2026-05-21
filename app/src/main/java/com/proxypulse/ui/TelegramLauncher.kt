package com.proxypulse.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.proxypulse.R
import com.proxypulse.data.TelegramLinkBuilder
import com.proxypulse.domain.ProxyEntry

object TelegramLauncher {
    fun openProxy(context: Context, entry: ProxyEntry) {
        val tg = TelegramLinkBuilder.buildTg(entry)
        val https = TelegramLinkBuilder.buildHttps(entry)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tg)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(https)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.no_telegram, Toast.LENGTH_LONG).show()
            }
        }
    }
}
