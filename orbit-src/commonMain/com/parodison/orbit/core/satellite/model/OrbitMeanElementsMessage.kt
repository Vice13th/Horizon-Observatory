package com.parodison.orbit.core.satellite.model

import com.parodison.orbit.core.satellite.Satellite
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.math.PI
import kotlin.time.Instant

const val MINUTES_PER_DAY = 1440.0
const val DEGREES_TO_RADIANS = PI / 180.0

/**
 * Celestrak's EPOCH comes without a timezone offset (e.g. "2026-07-17T13:44:50.076384"), but
 * it's always UTC. kotlin.time's `Instant.parse` requires the offset, so it's added if
 * missing before parsing.
 */
object CelestrakEpochSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("CelestrakEpoch", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString()
        val hasOffset = raw.endsWith("Z") || Regex("[+-]\\d{2}:?\\d{2}$").containsMatchIn(raw)
        return Instant.parse(if (hasOffset) raw else "${raw}Z")
    }

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
}

enum class OrbitType {
    LEO,
    MEO,
    GEO,
    HEO
}

@Serializable
data class OrbitMeanElementsMessage(
    @SerialName("OBJECT_NAME")
    val objectName: String,
    @SerialName("OBJECT_ID")
    val objectId: String,
    @SerialName("EPOCH")
    @Serializable(with = CelestrakEpochSerializer::class)
    val epoch: Instant,
    @SerialName("MEAN_MOTION")
    val meanMotion: Double,
    @SerialName("ECCENTRICITY")
    val eccentricity: Double,
    @SerialName("INCLINATION")
    val inclination: Double,
    @SerialName("RA_OF_ASC_NODE")
    val raOfAscNode: Double,
    @SerialName("ARG_OF_PERICENTER")
    val argOfPericenter: Double,
    @SerialName("MEAN_ANOMALY")
    val meanAnomaly: Double,
    @SerialName("EPHEMERIS_TYPE")
    val ephemerisType: Long,
    @SerialName("CLASSIFICATION_TYPE")
    val classificationType: String,
    @SerialName("NORAD_CAT_ID")
    val noradCatId: Long,
    @SerialName("ELEMENT_SET_NO")
    val elementSetNo: Long,
    @SerialName("REV_AT_EPOCH")
    val revAtEpoch: Long,
    @SerialName("BSTAR")
    val bstar: Double,
    @SerialName("MEAN_MOTION_DOT")
    val meanMotionDot: Double,
    @SerialName("MEAN_MOTION_DDOT")
    val meanMotionDdot: Double,
) {
    val orbitType: OrbitType
        get() = when {
            meanMotion >= 11.25 -> OrbitType.LEO
            meanMotion >= 2.0 -> OrbitType.MEO
            meanMotion in 0.95..1.05 -> OrbitType.GEO
            else -> OrbitType.HEO
        }
}

fun OrbitMeanElementsMessage.toSatellite(): Satellite {
    return Satellite(
        this
    )
}