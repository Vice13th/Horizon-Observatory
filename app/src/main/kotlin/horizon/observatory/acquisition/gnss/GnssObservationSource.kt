package horizon.observatory.acquisition.gnss

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssAntennaInfo
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.GnssNavigationMessage
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.location.LocationManagerCompat
import androidx.core.content.ContextCompat
import horizon.observatory.acquisition.base.ObservationSource
import horizon.observatory.core.time.TimestampEngine
import horizon.observatory.domain.model.CapabilityState
import horizon.observatory.domain.model.EvidenceStatus
import horizon.observatory.domain.model.ObservationProvenance
import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.RawObservation
import horizon.observatory.domain.model.SourceStatus
import horizon.observatory.domain.model.TimestampDomain
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.Executor

class GnssObservationSource(private val context: Context) : ObservationSource {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val executor: Executor = Executor { it.run() }

    override val status: SourceStatus
        get() = when {
            locationManager == null -> SourceStatus.UNAVAILABLE
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED -> SourceStatus.PERMISSION_DENIED
            !runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) -> SourceStatus.UNAVAILABLE
            else -> SourceStatus.AVAILABLE
        }

    override val observationFlow: Flow<RawObservation> = callbackFlow {
        val manager = locationManager ?: run { close(); return@callbackFlow }
        if (status != SourceStatus.AVAILABLE) { close(); return@callbackFlow }

        val measurementCallback = object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
                eventArgs.measurements.forEach { measurement ->
                    trySend(measurement.toRawObservation(eventArgs.clock.elapsedRealtimeNanos))
                }
            }
        }
        val statusCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val elapsed = android.os.SystemClock.elapsedRealtimeNanos()
                val satellites = JSONArray()
                for (i in 0 until status.satelliteCount) {
                    val row = JSONObject()
                        .put("svid", status.getSvid(i))
                        .put("constellationType", status.getConstellationType(i))
                        .put("azimuthDegrees", status.getAzimuthDegrees(i).toDouble())
                        .put("elevationDegrees", status.getElevationDegrees(i).toDouble())
                        .put("cn0DbHz", status.getCn0DbHz(i).toDouble())
                        .put("usedInFix", status.usedInFix(i))
                        .put("hasAlmanacData", status.hasAlmanacData(i))
                        .put("hasEphemerisData", status.hasEphemerisData(i))
                    if (Build.VERSION.SDK_INT >= 26 && status.hasCarrierFrequencyHz(i)) {
                        row.put("carrierFrequencyHz", status.getCarrierFrequencyHz(i).toDouble())
                    } else {
                        row.put("carrierFrequencyHz", JSONObject.NULL)
                    }
                    if (Build.VERSION.SDK_INT >= 30 && status.hasBasebandCn0DbHz(i)) {
                        row.put("basebandCn0DbHz", status.getBasebandCn0DbHz(i).toDouble())
                    } else {
                        row.put("basebandCn0DbHz", JSONObject.NULL)
                    }
                    satellites.put(row)
                }
                val payload = JSONObject()
                    .put("satelliteCount", status.satelliteCount)
                    .put("satellites", satellites)
                trySend(RawObservation(
                    type = ObservationType.GNSS_STATUS,
                    utcTimestampMs = TimestampEngine.elapsedNanosToUtcMillis(elapsed),
                    monotonicTimestampNs = elapsed,
                    provider = "GnssStatus",
                    payloadJson = payload.toString(),
                    technology = "GNSS",
                    capabilityState = CapabilityState.AVAILABLE_NOW,
                    evidenceStatus = EvidenceStatus.MEASURED,
                    provenance = ObservationProvenance.RAW_ACQUISITION,
                    timestampDomain = TimestampDomain.ANDROID_ELAPSED_REALTIME_NANOS
                ))
            }
        }
        fun antennaObservation(info: GnssAntennaInfo): RawObservation {
            val elapsed = android.os.SystemClock.elapsedRealtimeNanos()
            val pco = info.phaseCenterOffset.toString()
            val payload = JSONObject()
                .put("carrierFrequencyMHz", info.carrierFrequencyMHz)
                .put("phaseCenterOffset", pco)
                .put("phaseCenterVariationCorrections", info.phaseCenterVariationCorrections?.toString() ?: JSONObject.NULL)
                .put("signalGainCorrections", info.signalGainCorrections?.toString() ?: JSONObject.NULL)
            return RawObservation(
                type = ObservationType.GNSS_ANTENNA_INFO,
                utcTimestampMs = TimestampEngine.elapsedNanosToUtcMillis(elapsed),
                monotonicTimestampNs = elapsed,
                provider = "GnssAntennaInfo",
                payloadJson = payload.toString(),
                technology = "GNSS",
                capabilityState = CapabilityState.AVAILABLE_NOW,
                evidenceStatus = EvidenceStatus.OBSERVED,
                provenance = ObservationProvenance.RAW_ACQUISITION,
                timestampDomain = TimestampDomain.ANDROID_ELAPSED_REALTIME_NANOS
            )
        }

        val antennaListener = if (Build.VERSION.SDK_INT >= 30) object : GnssAntennaInfo.Listener {
            override fun onGnssAntennaInfoReceived(antennaInfos: MutableList<GnssAntennaInfo>) {
                antennaInfos.forEach { trySend(antennaObservation(it)) }
            }
        } else null

        val navCallback = object : GnssNavigationMessage.Callback() {
            override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage) {
                val elapsed = android.os.SystemClock.elapsedRealtimeNanos()
                val payload = JSONObject()
                    .put("type", event.type)
                    .put("svid", event.svid)
                    .put("messageId", event.messageId)
                    .put("submessageId", event.submessageId)
                    .put("status", event.status)
                    .put("dataLength", event.data.size)
                    .put("dataBase64", Base64.getEncoder().encodeToString(event.data))
                trySend(RawObservation(
                    type = ObservationType.GNSS_NAVIGATION_MESSAGE,
                    utcTimestampMs = TimestampEngine.elapsedNanosToUtcMillis(elapsed),
                    monotonicTimestampNs = elapsed,
                    provider = "GnssNavigationMessage",
                    payloadJson = payload.toString(),
                    technology = "GNSS",
                    capabilityState = CapabilityState.AVAILABLE_NOW,
                    evidenceStatus = EvidenceStatus.MEASURED,
                    provenance = ObservationProvenance.RAW_ACQUISITION,
                    timestampDomain = TimestampDomain.ANDROID_ELAPSED_REALTIME_NANOS
                ))
            }
        }

        val measurementRegistered = runCatching {
            if (Build.VERSION.SDK_INT >= 30) manager.registerGnssMeasurementsCallback(executor, measurementCallback)
            else LocationManagerCompat.registerGnssMeasurementsCallback(manager, executor, measurementCallback)
        }.getOrDefault(false)

        val statusRegistered = runCatching {
            manager.registerGnssStatusCallback(statusCallback, Handler(Looper.getMainLooper()))
        }.getOrDefault(false)

        val navRegistered = runCatching {
            if (Build.VERSION.SDK_INT >= 30) manager.registerGnssNavigationMessageCallback(executor, navCallback)
            else manager.registerGnssNavigationMessageCallback(navCallback, Handler(Looper.getMainLooper()))
        }.getOrDefault(false)

        var antennaRegistered = false
        if (Build.VERSION.SDK_INT >= 30 && antennaListener != null) {
            antennaRegistered = runCatching { manager.registerAntennaInfoListener(executor, antennaListener) }.getOrDefault(false)
            if (Build.VERSION.SDK_INT >= 31) {
                runCatching { manager.getGnssAntennaInfos() }.getOrNull().orEmpty().forEach { trySend(antennaObservation(it)) }
            }
        }

        if (!measurementRegistered && !statusRegistered && !navRegistered) {
            close()
        }

        closeFlow = { close() }
        awaitClose {
            closeFlow = null
            runCatching {
                if (Build.VERSION.SDK_INT >= 30) manager.unregisterGnssMeasurementsCallback(measurementCallback)
                else if (Build.VERSION.SDK_INT >= 24) LocationManagerCompat.unregisterGnssMeasurementsCallback(manager, measurementCallback)
            }
            runCatching { manager.unregisterGnssStatusCallback(statusCallback) }
            runCatching {
                if (Build.VERSION.SDK_INT >= 30) manager.unregisterGnssNavigationMessageCallback(navCallback)
                else manager.unregisterGnssNavigationMessageCallback(navCallback)
            }
            if (Build.VERSION.SDK_INT >= 30 && antennaRegistered && antennaListener != null) {
                runCatching { manager.unregisterAntennaInfoListener(antennaListener) }
            }
        }
    }

    override fun start() = Unit

    private var closeFlow: (() -> Unit)? = null

    override fun stop() {
        closeFlow?.invoke()
    }

    private fun GnssMeasurement.toRawObservation(eventElapsedNs: Long): RawObservation {
        val payload = JSONObject().apply {
            put("constellationType", constellationType)
            put("svid", svid)
            put("state", state)
            put("cn0DbHz", cn0DbHz)
            put("receivedSvTimeNanos", receivedSvTimeNanos)
            put("receivedSvTimeUncertaintyNanos", receivedSvTimeUncertaintyNanos)
            put("timeOffsetNanos", timeOffsetNanos)
            put("pseudorangeRateMetersPerSecond", pseudorangeRateMetersPerSecond)
            put("pseudorangeRateUncertaintyMetersPerSecond", pseudorangeRateUncertaintyMetersPerSecond)
            put("multipathIndicator", multipathIndicator)
            if (Build.VERSION.SDK_INT >= 26 && hasCarrierFrequencyHz()) put("carrierFrequencyHz", carrierFrequencyHz) else put("carrierFrequencyHz", JSONObject.NULL)
            put("accumulatedDeltaRangeMeters", accumulatedDeltaRangeMeters)
            put("accumulatedDeltaRangeUncertaintyMeters", accumulatedDeltaRangeUncertaintyMeters)
            put("accumulatedDeltaRangeState", accumulatedDeltaRangeState)
            if (Build.VERSION.SDK_INT >= 26 && hasAutomaticGainControlLevelDb()) put("automaticGainControlLevelDb", automaticGainControlLevelDb) else put("automaticGainControlLevelDb", JSONObject.NULL)
            if (Build.VERSION.SDK_INT >= 29 && hasCodeType()) put("codeType", codeType) else put("codeType", JSONObject.NULL)
            if (hasCarrierPhase()) put("carrierPhase", carrierPhase) else put("carrierPhase", JSONObject.NULL)
            if (hasCarrierPhaseUncertainty()) put("carrierPhaseUncertainty", carrierPhaseUncertainty) else put("carrierPhaseUncertainty", JSONObject.NULL)
            if (Build.VERSION.SDK_INT >= 30 && hasFullInterSignalBiasNanos()) put("fullInterSignalBiasNanos", fullInterSignalBiasNanos) else put("fullInterSignalBiasNanos", JSONObject.NULL)
            if (Build.VERSION.SDK_INT >= 30 && hasFullInterSignalBiasUncertaintyNanos()) put("fullInterSignalBiasUncertaintyNanos", fullInterSignalBiasUncertaintyNanos) else put("fullInterSignalBiasUncertaintyNanos", JSONObject.NULL)
            if (Build.VERSION.SDK_INT >= 30 && hasBasebandCn0DbHz()) put("basebandCn0DbHz", basebandCn0DbHz) else put("basebandCn0DbHz", JSONObject.NULL)
            put("eventElapsedRealtimeNanos", eventElapsedNs)
        }
        return RawObservation(
            type = ObservationType.GNSS_RAW_MEASUREMENT,
            utcTimestampMs = TimestampEngine.elapsedNanosToUtcMillis(eventElapsedNs),
            monotonicTimestampNs = eventElapsedNs,
            provider = "GnssMeasurement",
            payloadJson = payload.toString(),
            technology = "GNSS",
            capabilityState = CapabilityState.AVAILABLE_NOW,
            evidenceStatus = EvidenceStatus.MEASURED,
            provenance = ObservationProvenance.RAW_ACQUISITION,
            timestampDomain = TimestampDomain.ANDROID_ELAPSED_REALTIME_NANOS
        )
    }
}
