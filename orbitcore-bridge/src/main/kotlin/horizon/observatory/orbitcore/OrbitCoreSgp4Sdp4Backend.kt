package horizon.observatory.orbitcore

import horizon.observatory.astronomy.propagation.BackendResult
import horizon.observatory.astronomy.propagation.BackendState
import horizon.observatory.astronomy.propagation.PropagationModelFamily
import horizon.observatory.astronomy.propagation.Sgp4Sdp4Backend
import horizon.observatory.astronomy.propagation.Sgp4Sdp4Input
import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * OrbitCore adapter that intentionally has no compile-time reference to OrbitCore classes.
 *
 * OrbitCore 0.1.0 is compiled with Kotlin 2.4 metadata while HORIZON T1 stays on Kotlin 1.9.24
 * and Room/KAPT has a metadata reader boundary. Reflection is therefore used as the narrowest
 * containment mechanism: OrbitCore is runtime-only and its types cannot become part of this
 * module's Kotlin ABI or compile classpath.
 */
class OrbitCoreSgp4Sdp4Backend : Sgp4Sdp4Backend {
    override val name: String = "OrbitCore"
    override val version: String = "0.1.0"
    override val supportsDeepSpace: Boolean = true

    override fun propagate(input: Sgp4Sdp4Input, minutesSinceEpoch: Double): BackendResult {
        return try {
            val api = Api.load()
            val omm = api.createOmm(input)
            val satellite = api.satelliteCtor.newInstance(omm)
            val targetEpochMillis = input.epochUtc.toEpochMilli() + Math.round(minutesSinceEpoch * 60_000.0)
            val instant = api.kotlinInstantFromEpochMillis(targetEpochMillis)
            val pv = api.positionAt.invoke(satellite, instant)
            val position = api.positionField.invoke(pv)
            val velocity = api.velocityField.invoke(pv)
            val state = BackendState(
                xKm = (api.vectorX.invoke(position) as Number).toDouble(),
                yKm = (api.vectorY.invoke(position) as Number).toDouble(),
                zKm = (api.vectorZ.invoke(position) as Number).toDouble(),
                vxKmS = (api.vectorX.invoke(velocity) as Number).toDouble(),
                vyKmS = (api.vectorY.invoke(velocity) as Number).toDouble(),
                vzKmS = (api.vectorZ.invoke(velocity) as Number).toDouble(),
            )
            val periodMinutes = 2.0 * Math.PI / input.meanMotionRadPerMin
            val model = if (periodMinutes >= 225.0) PropagationModelFamily.SDP4_DEEP_SPACE else PropagationModelFamily.SGP4_NEAR_EARTH
            BackendResult.Ok(state, model)
        } catch (t: Throwable) {
            BackendResult.Error(null, "OrbitCore reflection failure: ${t.rootCauseMessage()}")
        }
    }

    private class Api private constructor(
        val satelliteCtor: Constructor<*>,
        val positionAt: Method,
        val positionField: Method,
        val velocityField: Method,
        val vectorX: Method,
        val vectorY: Method,
        val vectorZ: Method,
        private val ommCtor: Constructor<*>,
        private val instantFactory: Method,
        private val instantCompanion: Any,
    ) {
        fun createOmm(input: Sgp4Sdp4Input): Any {
            val args = arrayOfNulls<Any>(17)
            args[0] = input.objectName ?: "NORAD-${input.noradCatId}"
            args[1] = input.objectId ?: "NORAD-${input.noradCatId}"
            args[2] = kotlinInstantFromEpochMillis(input.epochUtc.toEpochMilli())
            args[3] = input.meanMotionRadPerMin * 1440.0 / (2.0 * Math.PI)
            args[4] = input.eccentricity
            args[5] = Math.toDegrees(input.inclinationRad)
            args[6] = Math.toDegrees(input.raanRad)
            args[7] = Math.toDegrees(input.argOfPericenterRad)
            args[8] = Math.toDegrees(input.meanAnomalyRad)
            args[9] = input.ephemerisType
            args[10] = input.classification ?: "U"
            args[11] = input.noradCatId.toInt()
            args[12] = input.elementSetNo
            args[13] = input.revAtEpoch?.toInt()
            args[14] = input.bstar
            args[15] = input.meanMotionDotRevPerDay2
            args[16] = input.meanMotionDdotRevPerDay3
            return ommCtor.newInstance(*args)
        }

        fun kotlinInstantFromEpochMillis(epochMillis: Long): Any =
            instantFactory.invoke(instantCompanion, epochMillis)

        companion object {
            fun load(): Api {
                val ommClass = Class.forName("com.parodison.orbit.core.satellite.model.OrbitMeanElementsMessage")
                val satelliteClass = Class.forName("com.parodison.orbit.core.satellite.Satellite")
                val pvClass = Class.forName("com.parodison.orbit.core.sgp4.model.PositionVelocity")
                val vectorClass = Class.forName("com.parodison.orbit.core.sgp4.model.Vector3")
                val instantClass = Class.forName("kotlin.time.Instant")
                val instantCompanionClass = Class.forName("kotlin.time.Instant\$Companion")

                val ommCtor = ommClass.constructors.singleOrNull { it.parameterCount == 17 }
                    ?: error("OrbitCore OMM constructor with 17 parameters not found")
                val satelliteCtor = satelliteClass.constructors.singleOrNull { it.parameterCount == 1 }
                    ?: error("OrbitCore Satellite(OMM) constructor not found")

                val positionAt = satelliteClass.methods.singleOrNull { it.name == "positionAt" && it.parameterCount == 1 }
                    ?: error("OrbitCore Satellite.positionAt(Instant) not found")
                val positionField = pvClass.methods.singleOrNull { it.name == "getPosition" && it.parameterCount == 0 }
                    ?: error("OrbitCore PositionVelocity.position not found")
                val velocityField = pvClass.methods.singleOrNull { it.name == "getVelocity" && it.parameterCount == 0 }
                    ?: error("OrbitCore PositionVelocity.velocity not found")
                val vectorX = vectorClass.methods.singleOrNull { it.name == "getX" && it.parameterCount == 0 }
                    ?: error("OrbitCore Vector3.x not found")
                val vectorY = vectorClass.methods.singleOrNull { it.name == "getY" && it.parameterCount == 0 }
                    ?: error("OrbitCore Vector3.y not found")
                val vectorZ = vectorClass.methods.singleOrNull { it.name == "getZ" && it.parameterCount == 0 }
                    ?: error("OrbitCore Vector3.z not found")

                val instantFactory = instantCompanionClass.methods.singleOrNull {
                    it.name == "fromEpochMilliseconds" && it.parameterCount == 1
                } ?: error("kotlin.time.Instant.fromEpochMilliseconds(Long) not found")

                // Force the class to resolve now so a missing runtime artifact fails deterministically.
                Class.forName("com.parodison.orbit.core.satellite.Satellite", true, OrbitCoreSgp4Sdp4Backend::class.java.classLoader)
                check(instantClass.isAssignableFrom(instantFactory.returnType)) { "OrbitCore Instant factory mismatch" }

                val instantCompanion = instantClass.getField("Companion").get(null)
                check(instantCompanionClass.isInstance(instantCompanion)) { "kotlin.time.Instant.Companion missing" }

                return Api(
                    satelliteCtor, positionAt, positionField, velocityField,
                    vectorX, vectorY, vectorZ, ommCtor, instantFactory, instantCompanion
                )
            }
        }
    }
}

private val Number.asDouble: Double get() = toDouble()

private fun Throwable.rootCauseMessage(): String {
    var t: Throwable = this
    while (t.cause != null) t = t.cause!!
    return t.message ?: t::class.java.name
}
