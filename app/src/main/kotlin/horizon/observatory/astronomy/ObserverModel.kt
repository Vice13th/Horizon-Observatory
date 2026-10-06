package horizon.observatory.astronomy

data class ObserverLocation(val latitudeDeg: Double, val longitudeDeg: Double, val altitudeKm: Double) {
    val latRad: Double get() = Math.toRadians(latitudeDeg)
    val lonRad: Double get() = Math.toRadians(longitudeDeg)
    fun toEcef(): EcefState = CoordinateTransforms.geodeticToEcef(latRad, lonRad, altitudeKm)
}
