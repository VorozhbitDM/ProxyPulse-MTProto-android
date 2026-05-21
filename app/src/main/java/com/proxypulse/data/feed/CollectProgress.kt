package com.proxypulse.data.feed

data class CollectProgress(
    val isCdxPhase: Boolean = false,
    val cdxSnapshotsDone: Int = 0,
    val cdxSnapshotsTarget: Int = 1,
    val proxiesFound: Int = 0,
    val proxiesTarget: Int = 100
)
