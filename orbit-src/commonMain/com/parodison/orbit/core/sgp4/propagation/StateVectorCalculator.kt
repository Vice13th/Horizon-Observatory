package com.parodison.orbit.core.sgp4.propagation

import com.parodison.orbit.core.sgp4.constants.WGS72Constants.EARTH_RADIUS_KM
import com.parodison.orbit.core.sgp4.constants.WGS72Constants.XKE
import com.parodison.orbit.core.sgp4.model.PositionVelocity
import com.parodison.orbit.core.sgp4.model.Vector3

/** Combines radius/velocity (in SGP4 units) and orientation into the final TEME state vector. */
internal fun computePositionAndVelocity(
    shortPeriod: ShortPeriodState,
    orientation: OrientationVectors,
): PositionVelocity {
    val kmPerSecPerUnit = EARTH_RADIUS_KM * XKE / 60.0

    val position = Vector3(
        x = shortPeriod.radius * orientation.ux * EARTH_RADIUS_KM,
        y = shortPeriod.radius * orientation.uy * EARTH_RADIUS_KM,
        z = shortPeriod.radius * orientation.uz * EARTH_RADIUS_KM,
    )
    val velocity = Vector3(
        x = (shortPeriod.radialVelocity * orientation.ux + shortPeriod.transverseVelocity * orientation.vx) * kmPerSecPerUnit,
        y = (shortPeriod.radialVelocity * orientation.uy + shortPeriod.transverseVelocity * orientation.vy) * kmPerSecPerUnit,
        z = (shortPeriod.radialVelocity * orientation.uz + shortPeriod.transverseVelocity * orientation.vz) * kmPerSecPerUnit,
    )
    return PositionVelocity(position, velocity)
}
