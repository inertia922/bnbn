package blbl.cat3399.core.api

/** Explicit follow wins over the viewing status; absent fields stay unknown. */
internal fun pgcFollowStateOf(follow: String?, followStatus: Int?): Boolean? {
    val normalized = follow?.trim()?.lowercase()
    when (normalized) {
        "true" -> return true
        "false" -> return false
        else -> {
            // The web API has used both 0/1 booleans and relation codes (2 = following).
            // Negative values mean "unknown" and must fall through to follow_status.
            normalized?.toIntOrNull()?.takeIf { it >= 0 }?.let { return it > 0 }
        }
    }
    return followStatus?.takeIf { it in 0..3 }?.let { it > 0 }
}
