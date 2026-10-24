package horizon.observatory.resilience

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Architecture fitness functions: the layering, determinism and manifest non-negotiables of the
 * resilience pipeline, enforced as tests so a later change cannot erode them silently.
 *
 * Layers: the pure core (everything in this package except the declared glue) takes time only from the
 * observations it is fed, so replay is deterministic and JVM-testable. Android types live only in the
 * declared glue files. A new glue file must be registered here on purpose.
 */
class ResilienceArchitectureFitnessTest {
    private val androidGlue = setOf("ResilientLocationProvider.kt", "ResilienceModeControllers.kt")
    private val coroutineGlue = setOf("ContinuityStatusHub.kt")

    private fun resilienceDir(): File {
        var d: File? = File(System.getProperty("user.dir")).absoluteFile
        while (d != null) {
            for (rel in listOf("app/src/main/kotlin", "src/main/kotlin")) {
                val f = File(d, "$rel/horizon/observatory/resilience")
                if (f.isDirectory) return f
            }
            d = d.parentFile
        }
        throw AssertionError("resilience source directory not found from ${System.getProperty("user.dir")}")
    }

    private fun files(): List<File> = resilienceDir().listFiles { f -> f.extension == "kt" }!!.sortedBy { it.name }

    private fun code(f: File): String = f.readText().removePrefix("﻿")
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "").replace(Regex("//[^\\n]*"), "")

    private fun manifestFile(): File =
        File(resilienceDir().parentFile.parentFile.parentFile.parentFile, "AndroidManifest.xml")

    private fun manifest(): String = manifestFile().readText()

    @Test fun scanActuallyFindsTheCore() = assertTrue(files().size >= 14)

    @Test fun androidImportsStayInDeclaredGlue() {
        val offenders = files().filter { Regex("^import androidx?\\.", RegexOption.MULTILINE).containsMatchIn(code(it)) }
            .map { it.name }.filterNot { it in androidGlue }
        assertTrue("Android imports outside declared glue: $offenders", offenders.isEmpty())
    }

    @Test fun coroutineImportsStayInDeclaredGlue() {
        val offenders = files().filter { Regex("^import kotlinx\\.coroutines", RegexOption.MULTILINE).containsMatchIn(code(it)) }
            .map { it.name }.filterNot { it in coroutineGlue }
        assertTrue("Coroutine imports outside declared glue: $offenders", offenders.isEmpty())
    }

    @Test fun pureCoreReadsNoClockAndNoRandomness() {
        val forbidden = listOf(
            "System.currentTimeMillis", "System.nanoTime", "SystemClock", "Instant.now", "LocalDateTime.now",
            "Math.random", "kotlin.random", "java.util.Random", "TimestampEngine", "Date()"
        )
        val hits = files().filter { it.name !in androidGlue && it.name !in coroutineGlue }
            .flatMap { f -> forbidden.filter { code(f).contains(it) }.map { "${f.name}: $it" } }
        assertTrue("Pure core must take time only from observations: $hits", hits.isEmpty())
    }

    @Test fun noBackgroundLocationPermission() = assertFalse(manifest().contains("ACCESS_BACKGROUND_LOCATION"))

    @Test fun observatoryServiceIsNotExported() {
        val service = Regex("<service[\\s\\S]*?/>").find(manifest())!!.value
        assertTrue(service.contains("ObservatoryService"))
        assertTrue(service.contains("android:exported=\"false\""))
    }

    @Test fun providerIsPermissionGated_readOnly_andNotGrantable() {
        val provider = Regex("<provider[\\s\\S]*?/>").find(manifest())!!.value
        assertTrue(provider.contains("android:readPermission=\"\${resilientLocationReadPermission}\""))
        assertFalse(provider.contains("writePermission"))
        assertTrue(provider.contains("android:grantUriPermissions=\"false\""))
    }

    @Test fun onlyLauncherActivityAndProviderAreExported() =
        assertEquals(2, Regex("android:exported=\"true\"").findAll(manifest()).count())

    @Test fun everyInstallFlavorDeclaresUniqueProviderIdentity() {
        val project = manifestFile().parentFile.parentFile.parentFile.parentFile
        val build = File(project, "app/build.gradle.kts").readText()
        val identities = listOf(
            "horizon.observatory.resilient-location" to "horizon.observatory.permission.READ_RESILIENT_LOCATION",
            "horizon.observatory.ts.resilient-location" to "horizon.observatory.ts.permission.READ_RESILIENT_LOCATION",
            "horizon.observatory.t1.resilient-location" to "horizon.observatory.t1.permission.READ_RESILIENT_LOCATION",
            "horizon.observatory.parallel.resilient-location" to "horizon.observatory.parallel.permission.READ_RESILIENT_LOCATION"
        )
        identities.forEach { (authority, permission) ->
            assertTrue("Authority missing from build config: $authority", build.contains(authority))
            assertTrue("Read permission missing from build config: $permission", build.contains(permission))
        }
        assertEquals("Every installation flavor must have a unique authority", identities.size, identities.map { it.first }.toSet().size)
        assertEquals("Every installation flavor must have a unique permission", identities.size, identities.map { it.second }.toSet().size)
    }

    @Test fun providerIdentitySelectionDoesNotCollideAcrossPackages() {
        assertEquals("horizon.observatory.resilient-location", ResilientLocationStore.authorityForPackage("horizon.observatory"))
        assertEquals("horizon.observatory.resilient-location", ResilientLocationStore.authorityForPackage("horizon.observatory.debug"))
        assertEquals("horizon.observatory.ts.resilient-location", ResilientLocationStore.authorityForPackage("horizon.observatory.ts"))
        assertEquals("horizon.observatory.ts.resilient-location", ResilientLocationStore.authorityForPackage("horizon.observatory.ts.debug"))
        assertEquals("horizon.observatory.t1.resilient-location", ResilientLocationStore.authorityForPackage("horizon.observatory.t1"))
        assertEquals("horizon.observatory.t1.resilient-location", ResilientLocationStore.authorityForPackage("horizon.observatory.t1.debug"))
        assertEquals("horizon.observatory.parallel.resilient-location", ResilientLocationStore.authorityForPackage("horizon.observatory.parallel"))
        assertEquals("horizon.observatory.parallel.resilient-location", ResilientLocationStore.authorityForPackage("horizon.observatory.parallel.debug"))
        assertEquals("horizon.observatory.permission.READ_RESILIENT_LOCATION", ResilientLocationStore.readPermissionForPackage("horizon.observatory"))
        assertEquals("horizon.observatory.ts.permission.READ_RESILIENT_LOCATION", ResilientLocationStore.readPermissionForPackage("horizon.observatory.ts.debug"))
        assertEquals("horizon.observatory.t1.permission.READ_RESILIENT_LOCATION", ResilientLocationStore.readPermissionForPackage("horizon.observatory.t1.debug"))
        assertEquals("horizon.observatory.parallel.permission.READ_RESILIENT_LOCATION", ResilientLocationStore.readPermissionForPackage("horizon.observatory.parallel.debug"))
    }

    @Test fun parallelFlavorHasDistinctInstallIdentityAndProviderPermission() {
        val project = manifestFile().parentFile.parentFile.parentFile.parentFile
        val build = File(project, "app/build.gradle.kts").readText()
        val text = build.replace(" ", "")
        assertTrue("Parallel applicationId suffix missing", text.contains("applicationIdSuffix=\".parallel\""))
        assertTrue("Parallel visible app label missing", text.contains("manifestPlaceholders[\"appLabel\"]=\"HorizonParallel\""))
        assertTrue("Parallel launcher icon must be variant-specific", text.contains("manifestPlaceholders[\"appIcon\"]=\"@mipmap/ic_launcher_parallel\""))
        assertTrue("Parallel round launcher icon must be variant-specific", text.contains("manifestPlaceholders[\"appRoundIcon\"]=\"@mipmap/ic_launcher_parallel_round\""))
        assertTrue("Parallel splash theme must be variant-specific", text.contains("manifestPlaceholders[\"appTheme\"]=\"@style/Theme.Horizon.ParallelStarting\""))
        assertTrue("Parallel notification channel must be isolated", File(project, "app/src/main/kotlin/horizon/observatory/acquisition/service/ObservatoryService.kt").readText().contains("horizon_parallel_observatory_channel"))
        assertTrue("Parallel authority placeholder missing", text.contains("horizon.observatory.parallel.resilient-location"))
        assertTrue("Parallel permission placeholder missing", text.contains("horizon.observatory.parallel.permission.READ_RESILIENT_LOCATION"))
        val manifestText = manifest()
        assertTrue("Manifest must use a variant-specific authority", manifestText.contains("\${resilientLocationAuthority}"))
        assertTrue("Manifest must use a variant-specific read permission", manifestText.contains("\${resilientLocationReadPermission}"))
        val provider = File(resilienceDir(), "ResilientLocationProvider.kt").readText()
        assertTrue(provider.contains("horizon.observatory.parallel.resilient-location"))
        assertTrue(provider.contains("horizon.observatory.parallel.permission.READ_RESILIENT_LOCATION"))
        assertTrue(provider.contains("horizon.observatory.parallel"))
        assertTrue(build.contains("horizon.observatory.resilient-location"))
        assertTrue(build.contains("horizon.observatory.ts.resilient-location"))
        assertTrue(build.contains("horizon.observatory.t1.resilient-location"))
    }

    @Test fun serviceHasASinglePublicationPath_throughTheGate() {
        val service = code(File(resilienceDir().parentFile, "acquisition/service/ObservatoryService.kt"))
        assertEquals("exactly one store.publish call", 1, Regex("resilientLocationStore\\.publish\\(").findAll(service).count())
        assertTrue(service.contains("publishGate.decide("))
    }
}
