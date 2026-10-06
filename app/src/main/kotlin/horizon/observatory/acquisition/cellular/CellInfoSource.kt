package horizon.observatory.acquisition.cellular

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellIdentityNr
import android.telephony.CellSignalStrengthNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import java.util.concurrent.Executor

class CellInfoSource(private val context: Context) : ObservationSource {
    private val telephonyManager = context.getSystemService(TelephonyManager::class.java)
    private val executor: Executor = Executor { it.run() }
    private var closeFlow: (() -> Unit)? = null

    override val status: SourceStatus
        get() = when {
            telephonyManager == null -> SourceStatus.UNAVAILABLE
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) -> SourceStatus.UNSUPPORTED
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED -> SourceStatus.PERMISSION_DENIED
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS) -> SourceStatus.UNSUPPORTED
            else -> SourceStatus.AVAILABLE
        }

    override val observationFlow: Flow<RawObservation> = callbackFlow {
        val tm = telephonyManager ?: run { close(); return@callbackFlow }
        if (status != SourceStatus.AVAILABLE) { close(); return@callbackFlow }

        fun emitAll(items: List<CellInfo>?, trigger: String) {
            items.orEmpty().forEach { info -> trySend(info.toRawObservation(trigger)) }
        }

        fun requestFreshSnapshot() {
            if (Build.VERSION.SDK_INT >= 29) {
                runCatching {
                    tm.requestCellInfoUpdate(executor, object : TelephonyManager.CellInfoCallback() {
                        override fun onCellInfo(cellInfo: MutableList<CellInfo>) = emitAll(cellInfo, "REQUEST_CELL_INFO_UPDATE")
                        override fun onError(errorCode: Int, detail: Throwable?) {
                            // The request is rate-limited by Android and can fail transiently.
                            // Do not terminate the source; the live callback may still deliver data.
                        }
                    })
                }
            } else {
                runCatching { emitAll(tm.allCellInfo, "CACHED_ALL_CELL_INFO") }
            }
        }

        requestFreshSnapshot()

        val refreshJob = launch {
            while (isActive) {
                delay(CELL_INFO_REFRESH_MS)
                requestFreshSnapshot()
            }
        }

        closeFlow = { close() }

        val canRegisterLiveCellInfo = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canRegisterLiveCellInfo) {
            val callback = object : TelephonyCallback(), TelephonyCallback.CellInfoListener {
                override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) = emitAll(cellInfo, "TELEPHONY_CALLBACK")
            }
            runCatching { tm.registerTelephonyCallback(executor, callback) }
                .onFailure { close(it) }
            awaitClose { refreshJob.cancel(); runCatching { tm.unregisterTelephonyCallback(callback) }; closeFlow = null }
        } else if (canRegisterLiveCellInfo) {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Suppress("DEPRECATION")
                override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) = emitAll(cellInfo, "PHONE_STATE_CALLBACK")
            }
            @Suppress("DEPRECATION")
            runCatching { tm.listen(listener, PhoneStateListener.LISTEN_CELL_INFO) }
                .onFailure { close(it) }
            @Suppress("DEPRECATION")
            awaitClose { refreshJob.cancel(); tm.listen(listener, PhoneStateListener.LISTEN_NONE); closeFlow = null }
        } else {
            awaitClose { refreshJob.cancel(); closeFlow = null }
        }
    }

    override fun start() = Unit
    override fun stop() { closeFlow?.invoke() }

    private fun Int.unavailableToNull(): Any =
        if (this == CellInfo.UNAVAILABLE || this == Int.MAX_VALUE) JSONObject.NULL else this

    companion object {
        private const val CELL_INFO_REFRESH_MS = 5_000L
    }

    private fun Long.unavailableToNull(): Any =
        if (this == CellInfo.UNAVAILABLE_LONG || this == Long.MAX_VALUE) JSONObject.NULL else this

    private fun CellInfo.toRawObservation(trigger: String): RawObservation {
        val sourceTimeNs: Long? = if (Build.VERSION.SDK_INT >= 30) {
            val millis = timestampMillis
            if (millis >= 0L) millis * 1_000_000L else null
        } else {
            val nanos = timeStamp
            if (nanos >= 0L) nanos else null
        }
        val ingestionNs = android.os.SystemClock.elapsedRealtimeNanos()
        val timestampNs = sourceTimeNs ?: ingestionNs
        val timestampDomain = if (sourceTimeNs != null) TimestampDomain.SOURCE_TIMESTAMP else TimestampDomain.ANDROID_ELAPSED_REALTIME_NANOS
        val technology: String
        val payload = JSONObject()

        payload.put("registered", isRegistered)
        if (Build.VERSION.SDK_INT >= 31) payload.put("connectionStatus", getCellConnectionStatus())
        payload.put("sourceTimestampMillis", if (Build.VERSION.SDK_INT >= 30) timestampMillis else JSONObject.NULL)
        payload.put("ingestionElapsedRealtimeNanos", ingestionNs)
        payload.put("acquisitionTrigger", trigger)
        payload.put("freshness", if (sourceTimeNs != null) "TIMESTAMPED_CELL_INFO" else "INGESTION_TIMESTAMP_ONLY")
        if (sourceTimeNs != null) {
            payload.put("sourceAgeMs", ((ingestionNs - sourceTimeNs).coerceAtLeast(0L)) / 1_000_000L)
        } else {
            payload.put("sourceAgeMs", JSONObject.NULL)
        }

        when (this) {
            is CellInfoLte -> {
                technology = "LTE"
                val id = cellIdentity
                payload.put("mcc", id.mccString ?: JSONObject.NULL)
                payload.put("mnc", id.mncString ?: JSONObject.NULL)
                payload.put("tac", id.tac.unavailableToNull())
                payload.put("ci", id.ci.unavailableToNull())
                payload.put("pci", id.pci.unavailableToNull())
                payload.put("earfcn", id.earfcn.unavailableToNull())
                val ss = cellSignalStrength
                payload.put("rsrp", ss.rsrp.unavailableToNull())
                payload.put("rsrq", ss.rsrq.unavailableToNull())
                payload.put("rssnr", ss.rssnr.unavailableToNull())
                payload.put("signalLevel", ss.level)
            }
            is CellInfoWcdma -> {
                technology = "WCDMA"
                val id = cellIdentity
                payload.put("mcc", id.mccString ?: JSONObject.NULL)
                payload.put("mnc", id.mncString ?: JSONObject.NULL)
                payload.put("lac", id.lac.unavailableToNull())
                payload.put("cid", id.cid.unavailableToNull())
                payload.put("psc", id.psc.unavailableToNull())
                payload.put("uarfcn", id.uarfcn.unavailableToNull())
                payload.put("rscp", cellSignalStrength.dbm.unavailableToNull())
                payload.put("signalLevel", cellSignalStrength.level)
            }
            is CellInfoGsm -> {
                technology = "GSM"
                val id = cellIdentity
                payload.put("mcc", id.mccString ?: JSONObject.NULL)
                payload.put("mnc", id.mncString ?: JSONObject.NULL)
                payload.put("lac", id.lac.unavailableToNull())
                payload.put("cid", id.cid.unavailableToNull())
                payload.put("arfcn", id.arfcn.unavailableToNull())
                payload.put("rssi", cellSignalStrength.dbm.unavailableToNull())
                payload.put("signalLevel", cellSignalStrength.level)
            }
            is CellInfoNr -> {
                technology = "NR"
                val id = cellIdentity as CellIdentityNr
                payload.put("mcc", id.mccString ?: JSONObject.NULL)
                payload.put("mnc", id.mncString ?: JSONObject.NULL)
                payload.put("tac", id.tac.unavailableToNull())
                payload.put("nci", id.nci.unavailableToNull())
                payload.put("pci", id.pci.unavailableToNull())
                payload.put("nrarfcn", id.nrarfcn.unavailableToNull())
                val ss = cellSignalStrength as CellSignalStrengthNr
                payload.put("ssRsrp", ss.ssRsrp.unavailableToNull())
                payload.put("ssRsrq", ss.ssRsrq.unavailableToNull())
                payload.put("ssSinr", ss.ssSinr.unavailableToNull())
                payload.put("signalLevel", ss.level)
            }
            is CellInfoTdscdma -> {
                technology = "TDSCDMA"
                payload.put("raw", toString())
                payload.put("mapped", false)
            }
            else -> {
                technology = javaClass.simpleName.removePrefix("CellInfo")
                payload.put("raw", toString())
                payload.put("mapped", false)
            }
        }

        return RawObservation(
            type = ObservationType.CELLULAR_INFO,
            utcTimestampMs = TimestampEngine.elapsedNanosToUtcMillis(timestampNs),
            provider = "TelephonyManager",
            payloadJson = payload.toString(),
            monotonicTimestampNs = timestampNs,
            technology = technology,
            capabilityState = CapabilityState.AVAILABLE_NOW,
            evidenceStatus = EvidenceStatus.MEASURED,
            provenance = ObservationProvenance.RAW_ACQUISITION,
            timestampDomain = timestampDomain,
            timestampUncertaintyNs = if (sourceTimeNs != null) 1_000_000L else null
        )
    }
}
