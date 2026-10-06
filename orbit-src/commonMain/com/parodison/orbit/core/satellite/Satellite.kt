package com.parodison.orbit.core.satellite

import com.parodison.orbit.core.geojson.Feature
import com.parodison.orbit.core.geojson.LineString
import com.parodison.orbit.core.geojson.toLineStringGeometry
import com.parodison.orbit.core.geojson.toMultiLineStringGeometry
import com.parodison.orbit.core.geojson.toPointGeometry
import com.parodison.orbit.core.geojson.toPolygonGeometry
import com.parodison.orbit.core.satellite.model.DEGREES_TO_RADIANS
import com.parodison.orbit.core.satellite.model.MINUTES_PER_DAY
import com.parodison.orbit.core.satellite.model.OrbitMeanElementsMessage
import com.parodison.orbit.core.sgp4.SGP4Engine
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.EARTH_RADIUS_KM
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.TWO_PI
import com.parodison.orbit.core.sgp4.model.GeodeticCoordinates
import com.parodison.orbit.core.sgp4.model.PositionVelocity
import kotlin.math.*
import kotlin.time.*
import kotlin.time.Duration.Companion.seconds

/** Result of [Satellite.nextPassesFrom]: a visible pass over an [ObserverCoordinates]. */
data class PassPrediction(
    val aos: Instant,
    val los: Instant,
    val tca: Instant,
    val maxElevationDeg: Double,
)

/**
 * High-level facade over [SGP4Engine] + positional astronomy ([PositionalAstronomy],
 * [subPointOf], [SolarPosition]): exposes what a UI needs (map, HUD, pass prediction) in
 * lat/lon/altitude, look angles, illumination and GeoJSON, without the consumer needing to
 * know anything about TEME/ECEF or the propagator itself.
 */
open class Satellite(
    val omm: OrbitMeanElementsMessage,
) {
    val engine = SGP4Engine(
        epoch = omm.epoch,
        meanMotionRadPerMin = omm.meanMotion * 2.0 * PI / MINUTES_PER_DAY,
        eccentricity = omm.eccentricity,
        inclinationRad = omm.inclination * DEGREES_TO_RADIANS,
        raanRad = omm.raOfAscNode * DEGREES_TO_RADIANS,
        argPerigeeRad = omm.argOfPericenter * DEGREES_TO_RADIANS,
        meanAnomalyRad = omm.meanAnomaly * DEGREES_TO_RADIANS,
        bstar = omm.bstar,
    )

    private var cachedInstant: Instant? = null
    private var cachedState: PositionVelocity? = null

    private fun stateAt(at: Instant): PositionVelocity {
        cachedState?.let { if (cachedInstant == at) return it }
        val computed = engine.propagate(at)
        cachedInstant = at
        cachedState = computed
        return computed
    }

    /** Raw position/velocity in TEME. For most uses, [subPointAt] or [lookAnglesFrom] is more convenient. */
    fun positionAt(at: Instant): PositionVelocity = stateAt(at)

    /** Sub-satellite point (lat/lon/altitude) at instant [at]. */
    fun subPointAt(at: Instant): GeodeticCoordinates = subPointOf(stateAt(at), at)

    /** Height above the ellipsoid (km) at instant [at]. */
    fun altitudeAt(at: Instant): Double = subPointAt(at).altitudeKm

    /** Orbital speed (km/s) at instant [at]. */
    fun speedAt(at: Instant): Double {
        val velocity = stateAt(at).velocity
        return sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z)
    }

    /** Orbital period (minutes). Doesn't depend on [at]: it's the same for the whole element set. */
    val periodMinutes: Double get() = engine.periodMinutes

    /** Azimuth/elevation/range/range-rate of the satellite as seen from [observer] at instant [at]. */
    fun lookAnglesFrom(observer: ObserverCoordinates, at: Instant): LookAngles =
        PositionalAstronomy(observer).computeLookAngles(stateAt(at), at)

    /** True if the satellite is above [minElevationDeg] as seen from [observer]. */
    fun isVisibleFrom(observer: ObserverCoordinates, at: Instant, minElevationDeg: Double = 0.0): Boolean =
        lookAnglesFrom(observer, at).elevationDeg >= minElevationDeg

    /** True if the satellite is in direct sunlight (not inside Earth's cylindrical shadow). */
    fun isSunlitAt(at: Instant): Boolean = !SolarPosition.isInEarthShadow(stateAt(at).position, at)

    /**
     * True if the satellite is potentially visible to the naked eye from [observer]: above the
     * horizon, lit by the Sun, and with the observer in twilight/night (Sun below
     * [twilightSunElevationDeg], -6° = civil twilight by default). This is the actual
     * "can be seen with the naked eye" condition, different from [isVisibleFrom].
     */
    fun isVisuallyVisibleFrom(
        observer: ObserverCoordinates,
        at: Instant,
        minElevationDeg: Double = 10.0,
        twilightSunElevationDeg: Double = -6.0,
    ): Boolean {
        if (!isVisibleFrom(observer, at, minElevationDeg)) return false
        if (!isSunlitAt(at)) return false
        return SolarPosition.solarElevationDeg(observer, at) <= twilightSunElevationDeg
    }

    /**
     * Upcoming visible passes over [observer] between [from] and [from] + [searchWindow], with
     * maximum elevation >= [minElevationDeg]. Scans in [stepSeconds] steps and refines AOS/LOS
     * by bisection and TCA by ternary search around the detected crossing.
     *
     * If a pass is already in progress when [from] is reached, it's reported with
     * `aos = from` (the real AOS, before the start of the window, isn't known). If a pass is
     * still in progress at the end of the window, it's reported with `los` at that limit.
     */
    fun nextPassesFrom(
        observer: ObserverCoordinates,
        from: Instant,
        searchWindow: Duration,
        minElevationDeg: Double = 10.0,
        stepSeconds: Int = 30,
    ): List<PassPrediction> {
        require(searchWindow > Duration.ZERO) { "searchWindow debe ser positivo" }
        require(stepSeconds > 0) { "stepSeconds debe ser positivo" }

        val step = stepSeconds.seconds
        val until = from + searchWindow

        val passes = mutableListOf<PassPrediction>()
        var previousInstant = from
        val initialElevation = lookAnglesFrom(observer, previousInstant).elevationDeg

        var currentAos = previousInstant.takeIf { initialElevation >= minElevationDeg }
        var maxElevationInstant = previousInstant
        var maxElevationDeg = initialElevation

        var t = from + step
        while (t <= until) {
            val elevation = lookAnglesFrom(observer, t).elevationDeg

            if (currentAos == null && elevation >= minElevationDeg) {
                currentAos = bisectCrossing(observer, previousInstant, t, minElevationDeg, risingEdge = true)
                maxElevationInstant = t
                maxElevationDeg = elevation
            } else if (currentAos != null) {
                if (elevation > maxElevationDeg) {
                    maxElevationInstant = t
                    maxElevationDeg = elevation
                }
                if (elevation < minElevationDeg) {
                    val los = bisectCrossing(observer, previousInstant, t, minElevationDeg, risingEdge = false)
                    passes += buildPassPrediction(observer, currentAos, los, maxElevationInstant, step)
                    currentAos = null
                }
            }

            previousInstant = t
            t += step
        }

        if (currentAos != null) {
            passes += buildPassPrediction(observer, currentAos, previousInstant, maxElevationInstant, step)
        }

        return passes
    }

    private fun buildPassPrediction(
        observer: ObserverCoordinates,
        aos: Instant,
        los: Instant,
        approximateTca: Instant,
        step: Duration,
    ): PassPrediction {
        val tca = refineMaxElevation(observer, approximateTca, step)
        return PassPrediction(
            aos = aos,
            los = los,
            tca = tca,
            maxElevationDeg = lookAnglesFrom(observer, tca).elevationDeg,
        )
    }

    private fun bisectCrossing(
        observer: ObserverCoordinates,
        before: Instant,
        after: Instant,
        thresholdDeg: Double,
        risingEdge: Boolean,
    ): Instant {
        var lo = before
        var hi = after
        repeat(BISECTION_ITERATIONS) {
            val mid = lo + (hi - lo) / 2
            val isAboveThreshold = lookAnglesFrom(observer, mid).elevationDeg >= thresholdDeg
            if (isAboveThreshold == risingEdge) hi = mid else lo = mid
        }
        return hi
    }

    private fun refineMaxElevation(observer: ObserverCoordinates, aroundInstant: Instant, step: Duration): Instant {
        var lo = aroundInstant - step
        var hi = aroundInstant + step
        repeat(TERNARY_SEARCH_ITERATIONS) {
            val m1 = lo + (hi - lo) / 3
            val m2 = hi - (hi - lo) / 3
            val e1 = lookAnglesFrom(observer, m1).elevationDeg
            val e2 = lookAnglesFrom(observer, m2).elevationDeg
            if (e1 < e2) lo = m1 else hi = m2
        }
        return lo + (hi - lo) / 2
    }

    /** Ground track as a list of points, one every [step], between [from] and [to] (inclusive). */
    fun groundTrack(from: Instant, to: Instant, step: Duration): List<GeodeticCoordinates> {
        require(step > Duration.ZERO) { "step debe ser positivo" }
        require(to >= from) { "to debe ser posterior o igual a from" }

        val points = mutableListOf<GeodeticCoordinates>()
        var t = from
        while (t <= to) {
            points += subPointAt(t)
            t += step
        }
        return points
    }

    /**
     * Ground track as a GeoJSON `Feature`. If the path crosses the antimeridian (±180°), a
     * `MultiLineString` split at each crossing is built instead of a continuous `LineString` —
     * otherwise a map renderer would draw a spurious line across the entire map.
     */
    fun groundTrackFeature(from: Instant, to: Instant, step: Duration): Feature {
        val segments = splitAtAntimeridian(groundTrack(from, to, step))
        val geometry = if (segments.size <= 1) {
            segments.firstOrNull()?.toLineStringGeometry() ?: LineString(coordinates = emptyList())
        } else {
            segments.toMultiLineStringGeometry()
        }
        return Feature(geometry = geometry)
    }

    /** Sub-satellite point as a GeoJSON `Feature`, for the satellite's marker on the map. */
    fun subPointFeature(at: Instant): Feature = Feature(geometry = subPointAt(at).toPointGeometry())

    /**
     * Radius (km) of the ground coverage circle: the point where the satellite is exactly at
     * [minElevationDeg] above the horizon (0° = geometric horizon).
     */
    fun footprintRadiusKm(at: Instant, minElevationDeg: Double = 0.0): Double =
        EARTH_RADIUS_KM * footprintAngularRadiusRad(at, minElevationDeg)

    /** Coverage circle as a list of points (polygonal approximation, spherical Earth). */
    fun footprintPolygon(at: Instant, minElevationDeg: Double = 0.0, points: Int = 64): List<GeodeticCoordinates> {
        require(points >= 3) { "un polígono necesita al menos 3 puntos" }

        val center = subPointAt(at)
        val angularRadiusRad = footprintAngularRadiusRad(at, minElevationDeg)
        val centerLatRad = center.latitudeDeg.toRadians()
        val centerLonRad = center.longitudeDeg.toRadians()

        return (0 until points).map { i ->
            val bearingRad = TWO_PI * i / points
            destinationPoint(centerLatRad, centerLonRad, angularRadiusRad, bearingRad)
        }
    }

    /** Coverage circle as a GeoJSON `Feature` (`Polygon`). */
    fun footprintFeature(at: Instant, minElevationDeg: Double = 0.0, points: Int = 64): Feature =
        Feature(geometry = footprintPolygon(at, minElevationDeg, points).toPolygonGeometry())

    private fun footprintAngularRadiusRad(at: Instant, minElevationDeg: Double): Double {
        val elevationRad = minElevationDeg.toRadians()
        val horizonAngleRad = asin((EARTH_RADIUS_KM / (EARTH_RADIUS_KM + altitudeAt(at))) * cos(elevationRad))
        return PI / 2.0 - elevationRad - horizonAngleRad
    }

    private fun destinationPoint(
        latRad: Double,
        lonRad: Double,
        angularDistanceRad: Double,
        bearingRad: Double,
    ): GeodeticCoordinates {
        val destLatRad = asin(
            sin(latRad) * cos(angularDistanceRad) + cos(latRad) * sin(angularDistanceRad) * cos(bearingRad),
        )
        val destLonRad = lonRad + atan2(
            sin(bearingRad) * sin(angularDistanceRad) * cos(latRad),
            cos(angularDistanceRad) - sin(latRad) * sin(destLatRad),
        )
        return GeodeticCoordinates(
            latitudeDeg = destLatRad.toDegrees(),
            longitudeDeg = normalizeLongitudeDeg(destLonRad.toDegrees()),
            altitudeKm = 0.0,
        )
    }

    /** Splits the ground track into new segments every time it jumps more than 180° in longitude. */
    private fun splitAtAntimeridian(points: List<GeodeticCoordinates>): List<List<GeodeticCoordinates>> {
        if (points.isEmpty()) return emptyList()

        val segments = mutableListOf(mutableListOf(points.first()))
        for (i in 1 until points.size) {
            val jumpDeg = abs(points[i].longitudeDeg - points[i - 1].longitudeDeg)
            if (jumpDeg > ANTIMERIDIAN_JUMP_THRESHOLD_DEG) segments.add(mutableListOf())
            segments.last().add(points[i])
        }
        return segments
    }

    private fun normalizeLongitudeDeg(lonDeg: Double): Double {
        var normalized = lonDeg
        while (normalized > 180.0) normalized -= 360.0
        while (normalized < -180.0) normalized += 360.0
        return normalized
    }

    private companion object {
        const val BISECTION_ITERATIONS = 20
        const val TERNARY_SEARCH_ITERATIONS = 20
        const val ANTIMERIDIAN_JUMP_THRESHOLD_DEG = 180.0
    }
}
