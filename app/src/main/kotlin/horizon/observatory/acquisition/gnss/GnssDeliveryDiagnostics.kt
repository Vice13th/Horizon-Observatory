package horizon.observatory.acquisition.gnss

import horizon.observatory.domain.model.ObservationType
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Counts source-side callback activity and channel-delivery results. Each row is accounted independently;
 * a single measurements callback can produce one clock row and N raw-measurement rows.
 */
internal class GnssDeliveryDiagnostics {
    enum class FailureReason { CHANNEL_FULL, CHANNEL_CLOSED }

    data class TypeSnapshot(
        val attempted: Long,
        val accepted: Long,
        val rejected: Long,
        val channelFull: Long,
        val channelClosed: Long
    ) {
        val invariantHolds: Boolean
            get() = attempted == accepted + rejected &&
                rejected == channelFull + channelClosed
    }

    private class MutableType {
        val attempted = AtomicLong()
        val accepted = AtomicLong()
        val rejected = AtomicLong()
        val full = AtomicLong()
        val closed = AtomicLong()

        fun snapshot() = TypeSnapshot(
            attempted = attempted.get(),
            accepted = accepted.get(),
            rejected = rejected.get(),
            channelFull = full.get(),
            channelClosed = closed.get()
        )
    }

    private val types = ObservationType.entries.associateWith { MutableType() }
    private val measurementCallbacks = AtomicLong()
    private val measurementCount = AtomicLong()
    private val statusCallbacks = AtomicLong()
    private val navigationCallbacks = AtomicLong()
    private val antennaCallbacks = AtomicLong()
    private val initialAntennaRows = AtomicLong()
    private val state = AtomicReference("RECORDING")

    /** Called only after the prior session's GNSS source jobs have stopped. */
    @Synchronized
    fun resetForSession() {
        types.values.forEach { counter ->
            counter.attempted.set(0L)
            counter.accepted.set(0L)
            counter.rejected.set(0L)
            counter.full.set(0L)
            counter.closed.set(0L)
        }
        measurementCallbacks.set(0L)
        measurementCount.set(0L)
        statusCallbacks.set(0L)
        navigationCallbacks.set(0L)
        antennaCallbacks.set(0L)
        initialAntennaRows.set(0L)
        state.set("RECORDING")
    }

    fun onMeasurementsCallback(measurements: Int) {
        measurementCallbacks.incrementAndGet()
        measurementCount.addAndGet(measurements.toLong())
    }

    fun onStatusCallback() { statusCallbacks.incrementAndGet() }
    fun onNavigationCallback() { navigationCallbacks.incrementAndGet() }
    fun onAntennaCallback(rows: Int) { antennaCallbacks.incrementAndGet() }
    fun onInitialAntennaRows(rows: Int) { initialAntennaRows.addAndGet(rows.toLong()) }

    fun recordDelivery(type: ObservationType, success: Boolean, reason: FailureReason? = null) {
        val counter = types.getValue(type)
        counter.attempted.incrementAndGet()
        if (success) {
            counter.accepted.incrementAndGet()
        } else {
            counter.rejected.incrementAndGet()
            when (reason ?: FailureReason.CHANNEL_CLOSED) {
                FailureReason.CHANNEL_FULL -> counter.full.incrementAndGet()
                FailureReason.CHANNEL_CLOSED -> counter.closed.incrementAndGet()
            }
        }
    }

    /** Idempotent terminal transition. Call only after source callbacks have been unregistered. */
    fun finalizeSession(finalState: String = "FINALIZED") {
        state.compareAndSet("RECORDING", finalState)
    }

    fun snapshot(): JSONObject {
        val perType = JSONObject()
        val valid = types.all { (_, counters) -> counters.snapshot().invariantHolds }
        types.toSortedMap(compareBy { it.name }).forEach { (type, counters) ->
            val s = counters.snapshot()
            perType.put(type.name, JSONObject()
                .put("attempted", s.attempted)
                .put("accepted", s.accepted)
                .put("rejected", s.rejected)
                .put("channelFull", s.channelFull)
                .put("channelClosed", s.channelClosed)
                .put("invariantHolds", s.invariantHolds))
        }
        val currentState = state.get()
        val countsComplete = currentState == "FINALIZED"
        return JSONObject()
            .put("schemaVersion", 1)
            .put("state", currentState)
            .put("countsComplete", countsComplete)
            .put("callbackCounts", JSONObject()
                .put("gnssMeasurements", measurementCallbacks.get())
                .put("gnssMeasurementRows", measurementCount.get())
                .put("gnssStatus", statusCallbacks.get())
                .put("gnssNavigationMessage", navigationCallbacks.get())
                .put("gnssAntennaCallbacks", antennaCallbacks.get())
                .put("initialAntennaRows", initialAntennaRows.get()))
            .put("deliveryByObservationType", perType)
            .put("invariantsHold", valid)
    }

    companion object {
        fun classifyFailure(isClosed: Boolean): FailureReason =
            if (isClosed) FailureReason.CHANNEL_CLOSED else FailureReason.CHANNEL_FULL
    }
}

/** Checks the finalized source-side channel accepts against authoritative persisted rows. */
internal fun reconcileAcceptedDeliveriesWithPersistedRows(
    diagnostics: JSONObject,
    persistedCountsByType: Map<String, Int>
): List<String> {
    if (diagnostics.optString("state") != "FINALIZED") return emptyList()
    val perType = diagnostics.optJSONObject("deliveryByObservationType")
        ?: return listOf("finalized GNSS diagnostics are missing deliveryByObservationType")
    val mismatches = mutableListOf<String>()
    val keys = perType.keys()
    while (keys.hasNext()) {
        val type = keys.next()
        val counters = perType.optJSONObject(type) ?: continue
        // GNSS_FIX and other sources do not use GnssObservationSource's callback counters.
        if (counters.optLong("attempted", 0L) == 0L) continue
        val accepted = counters.optLong("accepted", -1L)
        val persisted = (persistedCountsByType[type] ?: 0).toLong()
        if (accepted != persisted) {
            mismatches += "$type accepted=$accepted persisted=$persisted"
        }
    }
    return mismatches
}
