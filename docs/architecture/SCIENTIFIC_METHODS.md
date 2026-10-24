# Horizon Observatory â€” Scientific Methods & Mathematical Reference

Compact reference of every physical model and formula implemented in the code base.
Units: km, km/s, rad internally for astronomy; metres/seconds for navigation. Status tags:
**[V]** verified by test vectors Â· **[P]** provisional (engineering placeholder, uncalibrated) Â· **[U]** unverified on device.

## 1. Time systems â€” `astronomy/time/OrbitTime.kt`
- UTC is the single time scale. Catalog `EPOCH` (OMM) is UTC by definition.
- Julian Date from Unix ms: `JD = ms / 86 400 000 + 2 440 587.5`; `T = (JD âˆ’ 2 451 545.0) / 36 525`.
- UT1 â‰ˆ UTC (|UT1âˆ’UTC| < 0.9 s â‡’ â‰¤ ~0.4 km along-track-equivalent Earth-rotation error in sidereal angle, negligible for visual az/el). No leap-second, GPS/TAI conversion is performed.
- Sensor/monotonic clocks (`elapsedRealtimeNanos`) are never mixed with UTC; source time and ingestion time are stored separately.

## 2. Orbit propagation (primary) â€” SGP4/SDP4 via `propagation-contract` + `orbitcore-bridge`
- Input: CCSDS **OMM** mean elements (Kozai mean motion, e, i, Î©, Ï‰, M, B*, á¹…, nÌˆ) â€” parsed in `OmmParser`.
- Unit conversion: `n [rad/min] = n[rev/day]Â·2Ï€ / 1440`; angles degâ†’rad.
- Model: Brouwer/Kozai mean-element theory â€” secular J2, J3, J4 zonal harmonics, atmospheric drag via B*, near-Earth SGP4 (period < 225 min) and deep-space SDP4 (lunarâ€“solar periodics, 12 h / 24 h resonances). Output frame: **TEME**, position/velocity at epoch + Î”t.
- Validation status: per the uploaded OMEGA cycle-1 receipt (`docs/verification/RECEIPT_2026-10-16_JVM_HARNESS_AND_SGP4.md`; **reported, not re-executed by me**), a JVM harness ran the real OrbitCore (vendored 0.1.0 sources) against the published Vallado SGP4 verification set through the production bridge: 629 compared rows agree within the adapter bound; satellite 23599 deviates 0.96 km (OrbitCore floored-`mod` defect, pinned by a canary test). Status: **PARTIAL** (harness only; not the published binary, no Gradle/device run). The engine descriptor still defaults to `EngineVerificationStatus.UNVERIFIED`; wiring code may raise it only with executed evidence.
- Honest limits: mean-element accuracy â‰ˆ 1 km at epoch, degrading ~1â€“3 km/day (worse for LEO/high drag); no solar-radiation pressure or manoeuvres.

## 3. Keplerian fallback â€” `astronomy/OrbitPropagator.kt` (tests only, never labelled as prediction)
- Mean motion: `n = âˆš(Î¼ / aÂ³)`, `Î¼ = 398 600.4418 kmÂ³/sÂ²`.
- Mean anomaly: `M = Mâ‚€ + nÂ·Î”t` (mod 2Ï€).
- Kepler's equation `M = E âˆ’ eÂ·sin E` solved by **Newtonâ€“Raphson**: `E â† E âˆ’ (E âˆ’ e sin E âˆ’ M)/(1 âˆ’ e cos E)`, tol 1e-10, â‰¤ 50 iter, seed `Eâ‚€ = M` (e<0.8) else Ï€.
- True anomaly: `Î½ = 2Â·atan2(âˆš(1+e)Â·sin(E/2), âˆš(1âˆ’e)Â·cos(E/2))`; radius `r = a(1 âˆ’ e cos E)`; `p = a(1âˆ’eÂ²)`; `h = âˆš(Î¼p)`.
- Perifocal state: `r_pf = (r cos Î½, r sin Î½, 0)`, `v_pf = (âˆ’Î¼/h Â· sin Î½, Î¼/h Â· (e + cos Î½), 0)`.
- PQWâ†’ECI: `Râ‚ƒ(âˆ’Î©)Â·Râ‚(âˆ’i)Â·Râ‚ƒ(âˆ’Ï‰)` applied as explicit sequential rotations.

## 4. Reference frames & coordinate transforms â€” `CoordinateTransforms.kt`
- **GMST (IAU-1982 polynomial)**: `Î¸ = 280.46061837Â° + 360.98564736629Â°Â·(JDâˆ’2451545) + 0.000387933Â·TÂ² âˆ’ TÂ³/38 710 000` (mod 360Â°).
- **TEME/ECI â†’ ECEF** (GMST rotation only; no polar motion / nutation / equation of equinoxes): `r_ecef = Râ‚ƒ(Î¸)Â·r_eci`.
- Velocity: `v_ecef = Râ‚ƒ(Î¸)Â·v_eci âˆ’ Ï‰âŠ• Ã— r_ecef`, `Ï‰âŠ• = 7.2921150Ã—10â»âµ rad/s`.
- **WGS-84 geodeticâ†’ECEF**: `a = 6378.137 km`, `f = 1/298.257223563`, `eÂ² = 2f âˆ’ fÂ²`, `N = a/âˆš(1 âˆ’ eÂ² sinÂ²Ï†)`:
  `X=(N+h)cosÏ†cosÎ»`, `Y=(N+h)cosÏ†sinÎ»`, `Z=(N(1âˆ’eÂ²)+h)sinÏ†`.
- **ECEFâ†’ENU** (observer Ï†, Î»):
  `E = âˆ’sinÎ»Â·Î”x + cosÎ»Â·Î”y`
  `N = âˆ’sinÏ†cosÎ»Â·Î”x âˆ’ sinÏ†sinÎ»Â·Î”y + cosÏ†Â·Î”z`
  `U = cosÏ†cosÎ»Â·Î”x + cosÏ†sinÎ»Â·Î”y + sinÏ†Â·Î”z`
- Declared chain: `SGP4 TEME â†’ ECEF (GMST) â†’ observer ENU â†’ azimuth/elevation`.

## 5. Topocentric observables â€” `TopocentricCalculator.kt`
- Slant range `Ï = âˆš(EÂ²+NÂ²+UÂ²)`; elevation `el = asin(U/Ï)` (clamped); azimuth `az = atan2(E, N)` normalised to [0, 2Ï€).
- Range-rate (Doppler basis): `ÏÌ‡ = (Î”r Â· v_ecef) / Ï`.
- Visibility: purely geometric `el â‰¥ el_min` (no atmosphere, refraction, eclipse or illumination model).
- Batch path computes observer ECEF once per frame for N satellites (AR use).

## 6. Device pose & sky projection â€” `live/PoseMath.kt`, `SkyProjection.kt`
- Input: Android rotation-vector quaternion `q = (x sin(Î±/2), y sin(Î±/2), z sin(Î±/2), cos(Î±/2))`; missing `w = âˆš(1 âˆ’ xÂ² âˆ’ yÂ² âˆ’ zÂ²)`; sign canonicalisation (`w<0 â‡’ qâ†’âˆ’q`); renormalisation.
- Rotation matrix R (deviceâ†’ENU world) from unit quaternion; top-edge direction `(Râ‚, Râ‚„, Râ‚‡)`; **heading** `Ïˆ = atan2(east, north)`; **tilt** `Î¸ = acos(Râ‚ˆ)`; heading withheld beyond 60Â° tilt (policy, not physics).
- Magneticâ†’true north: `Ïˆ_true = Ïˆ_mag + D` (D = declination, positive east) from Android's World Magnetic Model **[U]**.
- Sky plot: polar projection, radius `Ï = (90Â° âˆ’ el)/90Â°` (0 = zenith, 1 = horizon), rotation by heading only for LIVE sessions; otherwise North-up with an explicit reason code.

## 7. Great-circle geometry â€” `PositionConsistency.kt`, `DeadReckoningBridge.kt`
- **Haversine** distance: `h = sinÂ²(Î”Ï†/2) + cosÏ†â‚cosÏ†â‚‚ sinÂ²(Î”Î»/2)`, `d = 2RÂ·asin(âˆšh)`, `R = 6 371 008.8 m` (IUGG mean radius; â‰¤0.5 % ellipsoidal error, acceptable at the â‰¤ km scales used).
- **Direct geodesic (sphere)**: `Î´ = d/R`, `Ï†â‚‚ = asin(sinÏ†â‚cosÎ´ + cosÏ†â‚sinÎ´ cosÎ¸)`, `Î»â‚‚ = Î»â‚ + atan2(sinÎ¸ sinÎ´ cosÏ†â‚, cosÎ´ âˆ’ sinÏ†â‚ sinÏ†â‚‚)` â€” valid across antimeridian and poles.

## 8. Resilient location (continuity) â€” `resilience/`
**Coasting / dead-reckoning bridge [P]** â€” constant-velocity projection from last trusted PVT:
- Along-track distance `s = vÂ·t + Â½Â·aÂ·tÂ²`; if decelerating to rest, `t_eff = v/|a|` and `s â‰¥ 0`.
- Uncertainty growth: `Ïƒ_h(t) = Ïƒâ‚€ + k_hÂ·t + Â½Â·a_unmodÂ·tÂ²` (defaults `k_h=1.5 m/s`, `a_unmod=0.5 m/sÂ²`); vertical `Ïƒ_v = Ïƒ_v0 + 1.0Â·t`; unknown heading while moving widens by `vÂ·t`.
- Hard ceilings: age â‰¤ 20 s, Ïƒ_h â‰¤ 150 m. Never invents heading or altitude; anchor timestamp never reset. Not validated inertial navigation (no IMU integration).

**Multi-source consistency check**
- Keep freshest/most-precise item per *independence group* (a fix and a projection from it are not independent).
- Time skew gate `Î”t â‰¤ 2 s`, age gate â‰¤ 5 s.
- Pairwise agreement test: `d_ij â‰¤ kÂ·âˆš(Ïƒ_iÂ² + Ïƒ_jÂ²)`, `k = 3` (â‰ˆ 3-sigma; provisional **[P]**) â€” outcomes Agree / Disagree / Insufficient.

**Freshness, publish gate, contract v2** â€” monotonic-clock ageing, state machine (GNSS â†’ COASTING â†’ DEGRADED â†’ LOST), schema-versioned snapshot for external providers (permission-guarded ContentProvider).

## 9. Session analysis & GNSS correlation â€” `analysis/`
- Vector magnitudes `â€–aâ€– = âˆš(xÂ²+yÂ²+zÂ²)` for sensor channels (NaN-safe).
- Raw measurements â†” status/assistance events correlated by sequence/time windows; derived values stay separate from raw evidence and carry provenance.

## 10. Data-integrity mathematics
- Monotonic ingestion sequence (1..N, contiguous) and non-decreasing ingestion time; source-time regressions are preserved as evidence, not repaired.
- Export manifest with cryptographic hashes for verified ZIP packages; Room migrations are non-destructive.

## 11. Constants & references
| Symbol | Value | Source |
|---|---|---|
| Î¼âŠ• | 398 600.4418 kmÂ³/sÂ² | WGS-84 / EGM96 |
| a (WGS-84) | 6378.137 km | NIMA TR8350.2 |
| 1/f | 298.257223563 | NIMA TR8350.2 |
| Ï‰âŠ• | 7.2921150Ã—10â»âµ rad/s | IERS |
| R mean | 6 371 008.8 m | IUGG |

References: Hoots & Roehrich, *Spacetrack Report No. 3* (1980); Vallado et al., *Revisiting Spacetrack Report #3* (AIAA 2006-6753); Vallado, *Fundamentals of Astrodynamics and Applications*; IAU 1982 GMST (Aoki et al.); NIMA TR8350.2 (WGS-84); CCSDS 502.0-B (OMM); Sinnott, "Virtues of the Haversine" (1984).

## 12. Known error budget & open gates
OMM mean-element error (~1â€“3 km/day) Â· GMST-only frame (no polar motion/nutation, sub-km) Â· UTCâ‰ˆUT1 Â· spherical Earth in haversine Â· coasting constants uncalibrated Â· declination accuracy unverified. See `docs/verification/VERIFICATION_MATRIX.md`.

## 13. Visualisation geometry (presentation only)
- **Sky dome** (`live/SkyDomeMath.kt`): ENU direction `(cos elÂ·sin b, cos elÂ·cos b, sin el)`; camera yaw Ïˆ, pitch Ï†: `sx = xâ‚`, `sy = UÂ·cosÏ† + yâ‚Â·sinÏ†`, depth `= yâ‚Â·cosÏ† âˆ’ UÂ·sinÏ†`, perspective scale `1/(1+0.3Â·depth)`. C/N0 colour map: `f = clamp((C/N0 âˆ’ 20)/30, 0, 1)`; missing C/N0 stays unmapped (grey).
- **Ground track** (`GroundTrackSampler`, `Geodetic`): sub-satellite point = WGS-84 geodetic of the predicted ECEF; iteration `Ï† â† atan2(z, p(1 âˆ’ eÂ²N/(N+h)))`, `h = p/cosÏ† âˆ’ N`. Longitude inherits the GMST-only frame error (Â§4) and OMM mean-element error (Â§2); tracks are DERIVED_PREDICTED, never merged with observed evidence.
- **Globe** (`live/GlobeMath.kt`): orthographic projection `R_y(Ï†c)Â·R_z(âˆ’Î»c)Â·(cosÏ†cosÎ», cosÏ†sinÎ», sinÏ†)`; screen `(yâ‚, zâ‚‚)`, visible iff `xâ‚‚ > 0`.
- **Pass prediction** (`PassFinder`): elevation oracle `el(t)` from the SGP4/SDP4 â†’ ENU chain; coarse scan (5 min), mask crossings `el(t) = el_min` refined by bisection to 1 s (error â‰¤ tolerance), culmination by ternary search (elevation unimodal within a pass). Passes open at window edges report unknown rise/set instead of guessing. Verified against `60Â°Â·sin(2Ï€t/T)` (rise/set Â±0.5 s, culmination Â±0.05 s). Geometry only: no refraction, illumination or obstruction.

## 14. Report export integrity
Per-section reports (`export/ReportExporter.kt`): Markdown + CSV, SHA-256 manifest (`sha256sum` format) over every file; missing data â†’ "NOT AVAILABLE" (never 0); CSV cells starting with `= + - @` (non-numeric) are prefixed with `'` to prevent spreadsheet formula injection; source, device-wall and ingestion clocks are exported as separate columns and never merged.
The `predicted/` report section is the only place predicted values are exported; it is labelled DERIVED_PREDICTED, built by the same samplers as the on-screen panels, and states why it is empty when position, identity, catalog or engine is missing.

## 15. Geometric identity hint (prototype)
Separation between two sky directions `Î¸ = atan2(|aÃ—b|, aÂ·b)` (unit ENU vectors `(cos elÂ·sin az, cos elÂ·cos az, sin el)`); a hint is CONSISTENT iff best Î¸ â‰¤ 3Â°, runner-up â‰¥ 4Â° farther and the match is mutual. Thresholds are provisional; method, weaknesses and calibration plan: `docs/research/GEOMETRIC_IDENTITY_HINT.md`.

## 16. GNSS integrity indicators (diagnostic, not a verdict)
Common test: `z = (median(last W epochs) âˆ’ median(preceding â‰¤B epochs)) / max(1.4826Â·MAD(baseline), floor)` with W = 10, 30 â‰¤ B â‰¤ 120, gate |z| â‰¥ 6 (all provisional, `IntegrityProfile`), evaluated per continuity segment (clock discontinuity / full-tracking transition / gap > 5 s starts a new one). Self-baselined: an anomaly present from the start is invisible.
- **C/N0**: per epoch and per constellation/band, level = median over satellites, dispersion = 1.4826Â·MAD over satellites; both series are scanned (level shift, dispersion collapse/expansion).
- **AGC**: event-level AGC preferred, else measurement-level (deprecated); same scan; the sign of the step is not interpreted; concurrency with a C/N0 shift uses the first window over the gate (onset), not the maximum.
- **Clock**: receiver-reported discontinuity-count changes; drift acceleration `a_k = (d_k âˆ’ d_{kâˆ’1})/Î”t` [ns/sÂ²] inside segments, outlier when `|a âˆ’ median(a)| / max(1.4826Â·MAD(a), 0.05) â‰¥ 6`.
- **ADR vs range-rate**: for consecutive valid samples `r = Î”ADR âˆ’ sÂ·Â½(á¹™_a+á¹™_b)Î”t`, `s = sgn(Î£ Î”ADRÂ·x)` estimated from the data (reported); intervals with reset / cycle-slip / unresolved half-cycle / gap are excluded; outlier when `|r âˆ’ median(r)| / max(1.4826Â·MAD(r), 0.02 m) â‰¥ 6`; â‰¥ 2 outlier intervals â‡’ DEVIATES.
- **Fusion**: UNAVAILABLE if no indicator has data; SUSPECT if any indicator DEVIATES; otherwise NOT_ESTABLISHED (never NOMINAL). Indicators are not independent evidence.
