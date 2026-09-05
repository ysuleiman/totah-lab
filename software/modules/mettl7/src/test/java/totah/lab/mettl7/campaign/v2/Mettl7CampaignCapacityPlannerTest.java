package totah.lab.mettl7.campaign.v2;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Mettl7CampaignCapacityPlannerTest {
    @Test
    void usesOnlyReadyLedgerAndMeasuredSmokeRuntime() {
        var receptorA = new Mettl7CartesianLedgerGenerator.Receptor(
                "A0", "METTL7A", List.of(), "a", "a".repeat(64), "A", true);
        var receptorB = new Mettl7CartesianLedgerGenerator.Receptor(
                "B0", "METTL7B", List.of(), "b", "b".repeat(64), "B", true);
        var species = new Mettl7CartesianLedgerGenerator.Species(
                "TSL", "TSL", "", "RSH", "", "S", "l", "c".repeat(64), true);
        var plan = new Mettl7CartesianLedgerGenerator.LedgerPlan(
                List.of(receptorA, receptorB), List.of(species), List.of(1, 7, 42), List.of());

        var estimate = Mettl7CampaignCapacityPlanner.estimate(plan, 12, 3, 60.0);

        assertEquals(6, estimate.expectedRuns());
        assertEquals(4, estimate.configuredConcurrency());
        assertEquals(0.3, estimate.estimatedCpuHours(), 1.0e-12);
        assertEquals(2.0 / 60.0, estimate.estimatedWallHours(), 1.0e-12);
        assertEquals(2, Mettl7SmokeTestPlanner.matchedWildTypePair(plan).size());
    }
}
