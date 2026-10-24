package horizon.observatory.resilience

import horizon.observatory.domain.model.ObservationType
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/** H8: the service used to publish every GNSS_FIX, so an out-of-order fix overwrote a newer position. */
class SnapshotPublishGateTest {
    private fun obs(ts: Long) = ResilientLocationSnapshot(50.0, 8.0, null, 4.0, null, ts, NavigationState.FULL_GNSS, "GNSS_OBSERVED", fixKind = ResilientFixKind.OBSERVED_GNSS)
    private fun est(ts: Long) = ResilientLocationSnapshot(50.0, 8.0, null, 20.0, null, ts, NavigationState.INERTIAL_BRIDGING,
        "DEAD_RECKONED_FROM_LAST_TRUSTED_PVT", fixKind = ResilientFixKind.SHORT_HORIZON_ESTIMATE, estimator = CoastingPolicy.ESTIMATOR_ID)
    private fun skip(d: PublishDecision) = (d as PublishDecision.Skip).reason

    @Test fun firstIsPublished_olderAndDuplicateAreSkipped_newerIsPublished() {
        val g = SnapshotPublishGate()
        assertEquals(PublishDecision.Publish, g.decide(obs(10 * S)))
        assertEquals("OLDER_THAN_PUBLISHED", skip(g.decide(obs(9 * S))))
        assertEquals("DUPLICATE_TIMESTAMP", skip(g.decide(obs(10 * S))))
        assertEquals(PublishDecision.Publish, g.decide(obs(11 * S)))
        assertEquals("OLDER_THAN_PUBLISHED", skip(g.decide(est(10 * S))))
    }

    @Test fun estimateNeverReplacesObservedAtTheSameInstant_butObservedReplacesEstimate() {
        val g = SnapshotPublishGate()
        assertEquals(PublishDecision.Publish, g.decide(obs(10 * S)))
        assertEquals("ESTIMATE_DOES_NOT_REPLACE_OBSERVED", skip(g.decide(est(10 * S))))
        assertEquals(PublishDecision.Publish, g.decide(est(11 * S)))
        assertEquals(PublishDecision.Publish, g.decide(obs(11 * S)))
    }

    @Test fun invalidSnapshotIsNeverPublished_andDoesNotAdvanceTheGate() {
        val g = SnapshotPublishGate()
        assertTrue(skip(g.decide(obs(10 * S).copy(latitudeDeg = 91.0))).startsWith("INVALID_"))
        assertEquals(PublishDecision.Publish, g.decide(obs(5 * S)))
    }

    @Test fun outOfOrderFixes_regressThePublishedTimeWithoutAGate_andNeverWithOne() {
        val sources = listOf(10.0, 11.0, 9.5, 12.0, 11.5, 12.0, 13.0, 12.5, 14.0)
        val stream = sources.mapIndexed { i, s -> rawObs(ObservationType.GNSS_FIX, fixJson(acc = 5.0), (s * S).toLong(), (i + 11) * S) }
        val snapshots = stream.map { (ObservedFixMapper.map(it, NavigationState.FULL_GNSS) as ObservedFixMapping.Mapped).snapshot }
        fun regressions(ts: List<Long>) = ts.zipWithNext().count { (a, b) -> b < a }
        assertTrue("baseline (publish everything) must regress", regressions(snapshots.map { it.timestampMonotonicNs }) > 0)
        val gate = SnapshotPublishGate()
        val published = snapshots.filter { gate.decide(it) == PublishDecision.Publish }
        assertEquals(0, regressions(published.map { it.timestampMonotonicNs }))
        assertEquals(listOf(10.0, 11.0, 12.0, 13.0, 14.0).map { (it * S).toLong() }, published.map { it.timestampMonotonicNs })
    }

    @Test fun randomValidSequences_publishedTimeIsNonDecreasing_andObservedWinsTies() {
        val r = Random(5)
        repeat(500) {
            val g = SnapshotPublishGate()
            var last = Long.MIN_VALUE
            var lastEst = false
            repeat(100) {
                val ts = (1 + r.nextInt(30)).toLong() * S
                val s = if (r.nextBoolean()) obs(ts) else est(ts)
                if (g.decide(s) == PublishDecision.Publish) {
                    assertTrue("time regressed", s.timestampMonotonicNs >= last)
                    if (s.timestampMonotonicNs == last) assertTrue("tie must go estimate -> observed only", lastEst && s.fixKind == ResilientFixKind.OBSERVED_GNSS)
                    last = s.timestampMonotonicNs; lastEst = s.fixKind == ResilientFixKind.SHORT_HORIZON_ESTIMATE
                }
            }
        }
    }
}
