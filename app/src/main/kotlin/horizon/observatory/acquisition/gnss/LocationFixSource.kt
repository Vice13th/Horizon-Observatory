package horizon.observatory.acquisition.gnss

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import horizon.observatory.acquisition.base.ObservationSource
import horizon.observatory.core.time.TimestampEngine
import horizon.observatory.domain.model.ObservationType
import horizon.observatory.domain.model.RawObservation
import horizon.observatory.domain.model.CapabilityState
import horizon.observatory.domain.model.EvidenceStatus
import horizon.observatory.domain.model.ObservationProvenance
import horizon.observatory.domain.model.SourceStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject

/**
 * Phase 1 implementation (roadmap Section 22). This is genuinely new code, not a fix -- the
 * supplied source had only a design comment here.
 *
 * Deliberate choices, per Section 22's own constraints:
 *  - Uses the framework android.location.LocationManager / GPS_PROVIDER directly, not Google
 *    Play services FusedLocationProviderClient. No new Gradle dependency, and it keeps this a
 *    "public/documented Android API" source per Section 19 -- nothing proprietary or requiring
 *    Play services availability on the target SM-A075F.
 *  - "Missing optional values remain missing. Never substitute 0 / -1 / false / 'unknown'
 *    unless supplied by Android": every optional Location field (altitude, bearing, speed) is
 *    only written to the payload if Location.hasX() reports it was actually supplied, using
 *    JSONObject.NULL rather than 0.0/-1 for the absent case.
 *  - elapsedRealtimeNanos and provider are always present on a Location object (not optional),
 *    so they're always included.
 *
 * UNVERIFIED: nothing about this class's runtime behavior has been confirmed on real hardware.
 * It has not been built, launched, or exercised against a physical GPS receiver anywhere in
 * this pipeline -- only reviewed by inspection. The GNSS test procedure (roadmap Section 23:
 * a 10-30 minute physical session, verifying coordinates/accuracy/provider/timestamps/sequence/
 * nullability/observation count) has NOT been run. Status stays UNVERIFIED until you run it on
 * the SM-A075F and report back, the same evidence discipline as the build gate.
 */
class LocationFixSource(private val context: Context) : ObservationSource {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _status = MutableStateFlow(computeStatus())
    val statusFlow: StateFlow<SourceStatus> = _status

    override val status: SourceStatus
        get() = _status.value

    private var listener: LocationListener? = null
    private var closeFlow: (() -> Unit)? = null

    override val observationFlow: Flow<RawObservation> = callbackFlow {
        if (computeStatus() != SourceStatus.AVAILABLE) {
            // Section 6/I12: permission or provider not available -- close the flow rather
            // than emit anything. No fabricated observation is produced here.
            close()
            return@callbackFlow
        }

        val gnssListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toRawObservation())
            }

            @Deprecated("Deprecated in Java", ReplaceWith(""))
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                // Deprecated callback, intentionally not used for data -- provider-level
                // availability is tracked via computeStatus()/LocationManager instead.
            }

            override fun onProviderEnabled(provider: String) {
                _status.value = computeStatus()
            }

            override fun onProviderDisabled(provider: String) {
                _status.value = computeStatus()
            }
        }
        listener = gnssListener
        closeFlow = { close() }

        try {
            // Acquisition runs on Dispatchers.IO. The listener-only overload creates its
            // Handler on the calling thread and therefore fails without a Looper. Bind the
            // callback explicitly to the main application Looper.
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                /* minTimeMs = */ 1000L,
                /* minDistanceM = */ 0f,
                gnssListener,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Permission was revoked between the checkSelfPermission above and this call
            // (Section 6: "never assume a permission granted during installation remains
            // granted"). Close rather than crash the collecting coroutine.
            close(e)
        }

        awaitClose {
            locationManager.removeUpdates(gnssListener)
            listener = null
            closeFlow = null
        }
    }

    override fun start() {
        _status.value = computeStatus()
    }

    override fun stop() {
        listener?.let { locationManager.removeUpdates(it) }
        listener = null
        closeFlow?.invoke()
    }

    private fun computeStatus(): SourceStatus {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return SourceStatus.PERMISSION_DENIED

        val providerEnabled = try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            false
        }
        return if (providerEnabled) SourceStatus.AVAILABLE else SourceStatus.UNAVAILABLE
    }

    private fun Location.toRawObservation(): RawObservation {
        val payload = JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
            put("altitude", if (hasAltitude()) altitude else JSONObject.NULL)
            put("accuracyMeters", if (hasAccuracy()) accuracy else JSONObject.NULL)
            put("bearingDegrees", if (hasBearing()) bearing else JSONObject.NULL)
            put("speedMetersPerSecond", if (hasSpeed()) speed else JSONObject.NULL)
            put("provider", provider ?: JSONObject.NULL)
            put("elapsedRealtimeNanos", elapsedRealtimeNanos)
        }
        return RawObservation(
            type = ObservationType.GNSS_FIX,
            utcTimestampMs = TimestampEngine.elapsedNanosToUtcMillis(elapsedRealtimeNanos),
            monotonicTimestampNs = elapsedRealtimeNanos,
            provider = provider ?: "UNKNOWN",
            payloadJson = payload.toString(),
            technology = "GNSS",
            capabilityState = CapabilityState.AVAILABLE_NOW,
            evidenceStatus = EvidenceStatus.MEASURED,
            provenance = ObservationProvenance.RAW_ACQUISITION
        )
    }
}
