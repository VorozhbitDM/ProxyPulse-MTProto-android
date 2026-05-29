package com.proxypulse.data.feed

import com.proxypulse.domain.FeedSourceMode

object TelegramMirrorSources {
    private val directFeedUrls = listOf(
        "https://t.me/s/ProxyMTProto",
        "https://telegram.me/s/ProxyMTProto"
    )

    private val mirrorFeedUrls = listOf(
        "https://tgstat.com/channel/@ProxyMTProto",
        "https://tgstat.ru/channel/@ProxyMTProto"
    )

    fun getUrls(mode: FeedSourceMode): List<String> = when (mode) {
        FeedSourceMode.Direct -> directFeedUrls
        FeedSourceMode.Bypass -> mirrorFeedUrls
    }
}
