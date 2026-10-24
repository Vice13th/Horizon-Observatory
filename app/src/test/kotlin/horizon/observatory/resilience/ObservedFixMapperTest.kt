package horizon.observatory.resilience

import horizon.observatory.domain.model.ObservationType
import org.junit.Assert.*
import org.junit.Test

/** G1 producer side: LocationFixSource payload -> snapshot. */
class ObservedFixMapperTest {
    // exact key set LocationFixSource emitted before this change (no verticalAccuracyMeters)
    private val legacyPayload = """{"latitude":50.0,"longitude":8.0,"altitude":null,"accuracyMeters":4.0,"bearingDegrees":null,"speedMetersPerSecond":null,"provider":"gps","isMock":false,"elapsedRealtimeNanos":5000000000}"""

    private fun map(json: String, ns: Long? = 5 * S, type: ObservationType = ObservationType.GNSS_FIX) =
        ObservedFixMapper.map(rawObs(type, json, ns), NavigationState.FULL_GNSS)

    @Test fun validHorizontalFix_withoutVerticalAccuracy_isPublished_withVerticalNull() {
        val m = map(legacyPayload) as ObservedFixMapping.Mapped
        assertEquals(50.0, m.snapshot.latitudeDeg, 0.0); assertEquals(4.0, m.snapshot.horizontalUncertaintyM, 0.0)
        assertNull(m.snapshot.verticalUncertaintyM); assertNull(m.snapshot.altitudeM); assertNull(m.snapshot.speedMps); assertNull(m.snapshot.bearingDeg)
        assertEquals(ResilientFixKind.OBSERVED_GNSS, m.snapshot.fixKind); assertEquals("GNSS_OBSERVED:DIRECT_GNSS:ANDROID_PROVIDER_GPS_OR_GNSS", m.snapshot.provenance)
        assertEquals(5 * S, m.snapshot.timestampMonotonicNs); assertNull(m.snapshot.estimator)
    }
    @Test fun verticalAccuracyIsCopiedOnlyFromVerticalAccuracy_neverFromHorizontal() {
        val withVertical = map(fixJson(acc = 4.0, vertical = 9.0, alt = 12.0, speed = 3.0, bearing = 90.0)) as ObservedFixMapping.Mapped
        assertEquals(9.0, withVertical.snapshot.verticalUncertaintyM!!, 0.0); assertEquals(12.0, withVertical.snapshot.altitudeM!!, 0.0)
        assertEquals(3.0, withVertical.snapshot.speedMps!!, 0.0); assertEquals(90.0, withVertical.snapshot.bearingDeg!!, 0.0)
        val without = map(fixJson(acc = 4.0, vertical = null)) as ObservedFixMapping.Mapped
        assertNull(without.snapshot.verticalUncertaintyM)
    }
    @Test fun invalidOptionalValues_becomeNull_andDoNotSuppressTheHorizontalFix() {
        val m = map(fixJson(acc = 4.0, speed = -1.0, bearing = 400.0, vertical = -3.0)) as ObservedFixMapping.Mapped
        assertNull(m.snapshot.speedMps); assertNull(m.snapshot.bearingDeg); assertNull(m.snapshot.verticalUncertaintyM)
    }
    @Test fun missingOrInvalidRequiredFields_areRejected_withReason() {
        assertEquals("MISSING_OR_INVALID_HORIZONTAL_ACCURACY", (map(fixJson(acc = null)) as ObservedFixMapping.Rejected).reason)
        assertEquals("MISSING_OR_INVALID_HORIZONTAL_ACCURACY", (map(fixJson(acc = -1.0)) as ObservedFixMapping.Rejected).reason)
        assertEquals("LATITUDE_OUT_OF_RANGE", (map(fixJson(lat = 91.0)) as ObservedFixMapping.Rejected).reason)
        assertEquals("LONGITUDE_OUT_OF_RANGE", (map(fixJson(lon = 181.0)) as ObservedFixMapping.Rejected).reason)
        assertEquals("MISSING_LATITUDE", (map("""{"latitude":"x","longitude":8.0,"accuracyMeters":4.0,"provider":"gps","isMock":false}""") as ObservedFixMapping.Rejected).reason)
        assertEquals("MISSING_LONGITUDE", (map("""{"latitude":50.0,"accuracyMeters":4.0,"provider":"gps","isMock":false}""") as ObservedFixMapping.Rejected).reason)
        assertEquals("MALFORMED_PAYLOAD", (map("{") as ObservedFixMapping.Rejected).reason)
        assertEquals("MISSING_SOURCE_TIMESTAMP", (map(legacyPayload, ns = null) as ObservedFixMapping.Rejected).reason)
        assertEquals("INVALID_MONOTONIC_TIMESTAMP", (map(legacyPayload, ns = 0L) as ObservedFixMapping.Rejected).reason)
        assertEquals("NOT_A_GNSS_FIX", (map(legacyPayload, type = ObservationType.GNSS_STATUS) as ObservedFixMapping.Rejected).reason)
    }
    @Test fun mapping_doesNotMutateRawEvidence() {
        val raw = rawObs(ObservationType.GNSS_FIX, legacyPayload, 5 * S)
        val copy = raw.copy()
        ObservedFixMapper.map(raw, NavigationState.FULL_GNSS)
        assertEquals(copy, raw)
    }
    @Test fun mappedSnapshot_survivesTheCodecRoundTrip() {
        val m = map(legacyPayload) as ObservedFixMapping.Mapped
        assertEquals(SnapshotDecodeResult.Valid(m.snapshot), ResilientLocationCodec.decode(ResilientLocationCodec.encode(m.snapshot)))
    }
    @Test fun mockProviderFix_isNeverPublishedAsObservedGnss() {
        val mock = legacyPayload.replace("\"isMock\":false", "\"isMock\":true")
        assertEquals(ObservedFixMapping.Rejected("MOCK_LOCATION"), map(mock))
        assertTrue(map(legacyPayload) is ObservedFixMapping.Mapped)
    }

    @Test fun sourceClassification_isEvidenceBased_andUnknownProviderIsNotPromotedToGnss() {
        assertEquals(LocationSourceClass.DIRECT_GNSS, LocationSourceClassifier.classify("gps", false).sourceClass)
        assertEquals(LocationSourceClass.NETWORK_DERIVED, LocationSourceClassifier.classify("network", false).sourceClass)
        assertEquals(LocationSourceClass.FUSED_PROVIDER, LocationSourceClassifier.classify("fused", false).sourceClass)
        assertEquals(LocationSourceClass.UNKNOWN, LocationSourceClassifier.classify("unknown-provider", false).sourceClass)
        assertEquals(LocationSourceClass.UNKNOWN, LocationSourceClassifier.classify("gps", true).sourceClass)
        assertEquals(LocationSourceClass.DIRECT_GNSS, LocationSourceClassifier.classify("gps", null).sourceClass)
        assertEquals(LocationSourceClass.CACHED, LocationSourceClassifier.classify("gps", false, cached = true).sourceClass)
    }

    @Test fun gpsProvider_withoutExplicitMockStatus_isNotPromotedToObservedGnss() {
        val withoutMockStatus = legacyPayload.replace(",\"isMock\":false", "")
        assertEquals(
            ObservedFixMapping.Rejected("MOCK_STATUS_UNAVAILABLE"),
            map(withoutMockStatus)
        )
    }

    @Test fun nonGnssOrUnclassifiedProvider_isNotPublishedAsObservedGnss() {
        assertEquals(
            ObservedFixMapping.Rejected("SOURCE_NOT_CONFIRMED_GNSS:ANDROID_PROVIDER_NETWORK"),
            map(legacyPayload.replace("\"provider\":\"gps\"", "\"provider\":\"network\""))
        )
        assertTrue(map(legacyPayload.replace("\"provider\":\"gps\"", "\"provider\":\"other\"")) is ObservedFixMapping.Rejected)
    }
}
