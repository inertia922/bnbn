@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package blbl.cat3399.feature.player

import android.net.Uri
import android.os.SystemClock
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import blbl.cat3399.core.log.AppLog
import java.io.IOException

internal enum class DebugStreamKind { VIDEO, AUDIO, MAIN }

internal class CdnFailoverState(
    val kind: DebugStreamKind,
    val candidates: List<Uri>,
) {
    @Volatile
    private var preferredIndex: Int = 0

    @Synchronized
    fun getPreferredIndex(): Int {
        val last = candidates.lastIndex
        return preferredIndex.coerceIn(0, last.coerceAtLeast(0))
    }

    @Synchronized
    fun prefer(index: Int) {
        val last = candidates.lastIndex
        preferredIndex = index.coerceIn(0, last.coerceAtLeast(0))
    }

    @Synchronized
    fun preferNextAfter(index: Int) {
        if (candidates.size <= 1) return
        val safeIndex = index.coerceIn(0, candidates.lastIndex)
        preferredIndex = (safeIndex + 1) % candidates.size
    }
}

/**
 * Watches the throughput of the currently opened CDN. A CDN that accepts the connection but
 * serves bytes slowly is worse than a connection error, because ExoPlayer would otherwise keep
 * reading from it forever. Two consecutive low-throughput windows ask the data source to rotate
 * to the next candidate URL.
 */
internal class CdnThroughputMonitor(
    private val thresholdBytesPerSecond: Long,
    private val windowMs: Long = 4_000L,
    private val idleResetMs: Long = 8_000L,
    private val slowWindowsToSwitch: Int = 2,
) {
    private var windowStartMs: Long = 0L
    private var windowBytes: Long = 0L
    private var lastReadMs: Long = 0L
    private var slowWindows: Int = 0

    fun reset() {
        windowStartMs = 0L
        windowBytes = 0L
        lastReadMs = 0L
        slowWindows = 0
    }

    /** Returns true when the caller should abandon the current CDN candidate. */
    fun record(
        bytes: Int,
        nowMs: Long,
    ): Boolean {
        if (bytes <= 0) return false
        if (lastReadMs > 0L && nowMs - lastReadMs > idleResetMs) {
            // Playback was paused / no data was needed; don't count the idle gap as slow.
            windowStartMs = nowMs
            windowBytes = 0L
            slowWindows = 0
        }
        lastReadMs = nowMs
        if (windowStartMs == 0L) windowStartMs = nowMs
        windowBytes += bytes.toLong()

        val elapsedMs = nowMs - windowStartMs
        if (elapsedMs < windowMs) return false

        val bytesPerSecond = windowBytes * 1000L / elapsedMs.coerceAtLeast(1L)
        windowStartMs = nowMs
        windowBytes = 0L
        if (bytesPerSecond < thresholdBytesPerSecond) {
            slowWindows++
            if (slowWindows >= slowWindowsToSwitch) {
                slowWindows = 0
                return true
            }
        } else {
            slowWindows = 0
        }
        return false
    }
}

internal class CdnFailoverDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val state: CdnFailoverState,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = CdnFailoverDataSource(upstreamFactory, state)
}

internal class CdnFailoverDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val state: CdnFailoverState,
) : DataSource {
    private var upstream: DataSource? = null
    private var currentCandidateIndex: Int = 0
    private val slowMonitor =
        CdnThroughputMonitor(
            thresholdBytesPerSecond =
                when (state.kind) {
                    DebugStreamKind.AUDIO -> 4_000L
                    else -> 16_000L
                },
        )
    private val transferListeners = ArrayList<TransferListener>(2)

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        upstream?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        closeQuietly()
        slowMonitor.reset()
        val candidates = state.candidates
        if (candidates.isEmpty()) throw IOException("No CDN candidates (kind=${state.kind})")

        val start = state.getPreferredIndex()
        var lastException: IOException? = null
        for (attempt in candidates.indices) {
            val idx = (start + attempt) % candidates.size
            val uri = candidates[idx]
            val ds = upstreamFactory.createDataSource()
            transferListeners.forEach { ds.addTransferListener(it) }
            val spec = dataSpec.buildUpon().setUri(uri).build()
            try {
                val openedLength = ds.open(spec)
                upstream = ds
                currentCandidateIndex = idx
                state.prefer(idx)
                return openedLength
            } catch (e: IOException) {
                runCatching { ds.close() }
                lastException = e
            }
        }
        throw lastException ?: IOException("Failed to open any CDN candidate (kind=${state.kind})")
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val ds = upstream ?: throw IllegalStateException("read() before open() (kind=${state.kind})")
        val read = try {
            ds.read(buffer, offset, length)
        } catch (e: IOException) {
            // A CDN can accept the connection and then die mid-stream. Rotate the preferred
            // candidate so ExoPlayer's retry (which creates a new data source) uses another CDN
            // instead of hitting the same broken host again.
            state.preferNextAfter(currentCandidateIndex)
            throw e
        }
        if (read > 0 && slowMonitor.record(read, SystemClock.elapsedRealtime())) {
            state.preferNextAfter(currentCandidateIndex)
            AppLog.w(
                "CdnFailover",
                "slow cdn kind=${state.kind} index=$currentCandidateIndex; switching to next candidate",
            )
            throw IOException("CDN throughput below threshold (kind=${state.kind})")
        }
        return read
    }

    override fun getUri(): Uri? = upstream?.uri

    override fun close() {
        closeQuietly()
    }

    private fun closeQuietly() {
        runCatching { upstream?.close() }
        upstream = null
    }
}
