package totah.lab.mettl7.campaign.v2;

import java.util.List;

/** Selects a deterministic matched WT A/B smoke pair without executing it. */
public final class Mettl7SmokeTestPlanner {
    private Mettl7SmokeTestPlanner() {}

    public static List<Mettl7CartesianLedgerGenerator.Row> matchedWildTypePair(
            Mettl7CartesianLedgerGenerator.LedgerPlan plan) {
        if (!plan.ready()) throw new IllegalStateException("Final ledger is not ready");
        var species = plan.species().getFirst();
        var a0 = receptor(plan, "A0");
        var b0 = receptor(plan, "B0");
        int seed = Mettl7MechanisticMatrixV2Protocol.SEEDS.getFirst();
        return List.of(Mettl7CartesianLedgerGenerator.Row.of(a0, species, seed),
                Mettl7CartesianLedgerGenerator.Row.of(b0, species, seed));
    }

    private static Mettl7CartesianLedgerGenerator.Receptor receptor(
            Mettl7CartesianLedgerGenerator.LedgerPlan plan, String id) {
        return plan.receptors().stream().filter(value -> value.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Required smoke receptor absent: " + id));
    }
}
