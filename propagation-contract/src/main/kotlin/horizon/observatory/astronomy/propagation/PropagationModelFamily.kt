package horizon.observatory.astronomy.propagation

/** Which analytical model produced a state. Chosen by the propagation implementation, never by the UI. */
enum class PropagationModelFamily {
    SGP4_NEAR_EARTH,
    SDP4_DEEP_SPACE
}
