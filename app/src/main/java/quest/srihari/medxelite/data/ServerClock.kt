package quest.srihari.medxelite.data

/**
 * The Firestore server's clock, as seen from this phone. Duels schedule every question as an
 * absolute time ("question 3 opens at T"), so two phones must agree on what T is even when their
 * own clocks are seconds apart. Each commit returns the server's commit time; the offset is the
 * median of the last few (server − midpoint of the request), which shrugs off one slow round trip.
 */
object ServerClock {
    private val samples = ArrayDeque<Long>()
    @Volatile var offsetMs: Long = 0L
        private set

    @Synchronized
    fun observe(serverMs: Long, sentMs: Long, receivedMs: Long) {
        val rtt = receivedMs - sentMs
        if (rtt < 0 || rtt > 10_000) return
        samples.addLast(serverMs - (sentMs + rtt / 2))
        while (samples.size > 7) samples.removeFirst()
        offsetMs = samples.sorted()[samples.size / 2]
    }

    fun now(): Long = System.currentTimeMillis() + offsetMs

    @Synchronized
    fun reset() { samples.clear(); offsetMs = 0 }
}
