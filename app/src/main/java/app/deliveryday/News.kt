package app.deliveryday

/** A news item gets attention for at most 24 hours, until it has been seen in the app. */
object News {
    fun highlighted(at: Long, seenAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        at > 0 && at > seenAt && now - at in 0 until 86_400_000L
}
