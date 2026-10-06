package horizon.observatory.astronomy

/** Explicit geometric visibility only (elevation-based). No atmosphere/eclipse/optical model. */
object VisibilityCalculator {
    fun isAboveHorizon(topo: TopocentricState, minElevationDeg: Double = 0.0): Boolean =
        topo.elevationDeg >= minElevationDeg
}
