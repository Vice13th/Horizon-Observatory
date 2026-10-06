package com.parodison.orbit.core.geojson

import com.parodison.orbit.core.sgp4.model.GeodeticCoordinates
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * A subset of GeoJSON (RFC 7946) with what [com.parodison.orbit.core.satellite.Satellite]
 * needs to feed a map: point (sub-satellite position), lines (ground track) and polygon
 * (coverage footprint). These types are portable to every Kotlin Multiplatform target, unlike
 * framework-specific GeoJSON types that are usually only available on a single platform.
 *
 * The "type" discriminator for each variant comes for free from kotlinx.serialization's
 * sealed-class polymorphism (its default key is already "type"); it's enough to fix the value
 * with @SerialName on each subtype to match the spec.
 */
@Serializable
sealed class Geometry

@Serializable
@SerialName("Point")
data class Point(val coordinates: List<Double>) : Geometry()

@Serializable
@SerialName("LineString")
data class LineString(val coordinates: List<List<Double>>) : Geometry()

@Serializable
@SerialName("MultiLineString")
data class MultiLineString(val coordinates: List<List<List<Double>>>) : Geometry()

@Serializable
@SerialName("Polygon")
data class Polygon(val coordinates: List<List<List<Double>>>) : Geometry()

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Feature(
    val geometry: Geometry,
    val properties: Map<String, JsonElement> = emptyMap(),
) {
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "Feature"
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class FeatureCollection(
    val features: List<Feature>,
) {
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: String = "FeatureCollection"
}

/**
 * [longitude, latitude, altitude in meters] — GeoJSON fixes the lon/lat order; altitude is in
 * meters, not km.
 */
private fun GeodeticCoordinates.toPosition(): List<Double> =
    listOf(longitudeDeg, latitudeDeg, altitudeKm * 1000.0)

fun GeodeticCoordinates.toPointGeometry(): Point = Point(coordinates = toPosition())

fun List<GeodeticCoordinates>.toLineStringGeometry(): LineString =
    LineString(coordinates = map { it.toPosition() })

fun List<List<GeodeticCoordinates>>.toMultiLineStringGeometry(): MultiLineString =
    MultiLineString(coordinates = map { segment -> segment.map { it.toPosition() } })

/** Closes the ring (repeats the first point at the end) if the caller hasn't already. */
fun List<GeodeticCoordinates>.toPolygonGeometry(): Polygon {
    val ring = if (isNotEmpty() && first() != last()) this + first() else this
    return Polygon(coordinates = listOf(ring.map { it.toPosition() }))
}

private val geoJsonFormat = Json { encodeDefaults = true }

/** Serializes to GeoJSON text — useful for passing it as-is to a `map.addSource(...)` call in JS. */
fun Feature.toGeoJsonString(): String = geoJsonFormat.encodeToString(this)

fun FeatureCollection.toGeoJsonString(): String = geoJsonFormat.encodeToString(this)
