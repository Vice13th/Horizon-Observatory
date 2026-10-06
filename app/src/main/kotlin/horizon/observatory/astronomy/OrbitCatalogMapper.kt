package horizon.observatory.astronomy

import horizon.observatory.astronomy.time.RetrievalTimeUtcMs
import java.time.Instant

fun CatalogedOrbit.toEntity(): OrbitCatalogEntity =
    OrbitCatalogEntity(
        noradCatId = record.noradCatId,
        objectName = record.objectName,
        objectId = record.objectId,
        classification = record.classification,
        epochRaw = record.epochRaw,
        epochUtcMillis = record.epochUtc.toEpochMilli(),
        meanMotionRevPerDay = record.meanMotionRevPerDay,
        eccentricity = record.eccentricity,
        inclinationDeg = record.inclinationDeg,
        raanDeg = record.raanDeg,
        argOfPericenterDeg = record.argOfPericenterDeg,
        meanAnomalyDeg = record.meanAnomalyDeg,
        ephemerisType = record.ephemerisType,
        elementSetNo = record.elementSetNo,
        revAtEpoch = record.revAtEpoch,
        bstar = record.bstar,
        meanMotionDot = record.meanMotionDot,
        meanMotionDdot = record.meanMotionDdot,
        catalogSource = provenance.source,
        catalogSourceIdentifier = provenance.sourceIdentifier,
        catalogFormat = provenance.format,
        retrievedAtUtcMillis = provenance.retrievedAt.value
    )

/**
 * Entity -> domain. Provenance and retrieval time are copied verbatim (never reset on read). The
 * epoch is re-parsed from [OrbitCatalogEntity.epochRaw] to keep full source precision; if that text
 * were ever unparseable the stored millisecond epoch is used, which loses only sub-millisecond
 * precision and invents nothing.
 */
fun OrbitCatalogEntity.toCatalogedOrbit(): CatalogedOrbit {
    val epochUtc = OmmEpoch.parse(epochRaw) ?: Instant.ofEpochMilli(epochUtcMillis)
    return CatalogedOrbit(
        record = OmmRecord(
            noradCatId = noradCatId,
            objectName = objectName,
            objectId = objectId,
            classification = classification,
            epochRaw = epochRaw,
            epochUtc = epochUtc,
            meanMotionRevPerDay = meanMotionRevPerDay,
            eccentricity = eccentricity,
            inclinationDeg = inclinationDeg,
            raanDeg = raanDeg,
            argOfPericenterDeg = argOfPericenterDeg,
            meanAnomalyDeg = meanAnomalyDeg,
            ephemerisType = ephemerisType,
            elementSetNo = elementSetNo,
            revAtEpoch = revAtEpoch,
            bstar = bstar,
            meanMotionDot = meanMotionDot,
            meanMotionDdot = meanMotionDdot
        ),
        provenance = OrbitProvenance(
            source = catalogSource,
            sourceIdentifier = catalogSourceIdentifier,
            format = catalogFormat,
            retrievedAt = RetrievalTimeUtcMs(retrievedAtUtcMillis)
        )
    )
}
