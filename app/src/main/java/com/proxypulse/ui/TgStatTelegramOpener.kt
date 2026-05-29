package com.proxypulse.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.proxypulse.data.tgstat.TgStatAuthService

object TgStatTelegramOpener {
    private val telegramPackages = listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.messenger.beta"
    )

    /** Открывает @tg_analytics_bot с start=authKey. Возвращает false, если Telegram не найден. */
    fun openBot(context: Context, authKey: String): Boolean {
        if (authKey.isBlank()) return false

        val links = listOf(
            TgStatAuthService.buildTelegramDeepLink(authKey),
            TgStatAuthService.buildTelegramHttpsLink(authKey)
        )

        for (link in links) {
            val base = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            for (pkg in telegramPackages) {
                try {
                    context.startActivity(Intent(base).setPackage(pkg))
                    return true
                } catch (_: ActivityNotFoundException) {
                }
            }
            try {
                context.startActivity(base)
                return true
            } catch (_: ActivityNotFoundException) {
            }
        }
        return false
    }
}
