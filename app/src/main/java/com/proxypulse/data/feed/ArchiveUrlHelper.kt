package com.proxypulse.data.feed

object ArchiveUrlHelper {
    private val beforeInUrlRegex = Regex("""before=(\d+)""", RegexOption.IGNORE_CASE)

    fun parseBeforeId(url: String?): Long? {
        if (url.isNullOrEmpty()) return null
        val m = beforeInUrlRegex.find(url) ?: return null
        return m.groupValues[1].toLongOrNull()
    }

    fun getFirstPageCandidates(entryUrl: String): List<String> {
        val list = mutableListOf<String>()
        if (!entryUrl.isNullOrEmpty()) list.add(entryUrl)
        list.add("https://web.archive.org/web/2/https://t.me/s/ProxyMTProto")
        return list
    }

    fun firstPageForSnapshot(snapshotId: String): String =
        "https://web.archive.org/web/${snapshotId}if_/https://t.me/s/ProxyMTProto"
}
