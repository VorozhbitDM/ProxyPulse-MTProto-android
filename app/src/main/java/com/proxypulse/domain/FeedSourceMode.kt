package com.proxypulse.domain

/** Откуда загружать ленту @ProxyMTProto. */
enum class FeedSourceMode {
    /** Прямой доступ: t.me / telegram.me. */
    Direct,

    /** Обходной: TGStat (актуальная лента) + web.archive.org. */
    Bypass
}
