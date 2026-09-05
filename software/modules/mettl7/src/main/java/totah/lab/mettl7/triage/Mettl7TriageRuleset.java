package totah.lab.mettl7.triage;

import java.util.List;
import java.util.Set;

/** Immutable scientific policy. A changed policy requires a new version. */
public record Mettl7TriageRuleset(
        String version,
        Set<String> mettl7aCore,
        Set<String> mettl7aExtensions,
        Set<String> dcmbSpecificWall,
        Set<String> mettl7bCore,
        Set<String> mettl7bExtensions,
        int minimumCorroboratingContacts,
        int minimumRouteDimensions,
        List<String> insufficientSinglePredictors,
        List<String> provenanceSources) {
    public Mettl7TriageRuleset {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("version required");
        mettl7aCore = Set.copyOf(mettl7aCore);
        mettl7aExtensions = Set.copyOf(mettl7aExtensions);
        dcmbSpecificWall = Set.copyOf(dcmbSpecificWall);
        mettl7bCore = Set.copyOf(mettl7bCore);
        mettl7bExtensions = Set.copyOf(mettl7bExtensions);
        if (minimumCorroboratingContacts < 2 || minimumRouteDimensions < 2) {
            throw new IllegalArgumentException("distributed evidence minima must be at least two");
        }
        insufficientSinglePredictors = List.copyOf(insufficientSinglePredictors);
        provenanceSources = List.copyOf(provenanceSources);
    }

    public static Mettl7TriageRuleset version1() {
        return new Mettl7TriageRuleset(
                "METTL7_TRIAGE_RULESET_V1",
                Set.of("S149", "K151", "D200"),
                Set.of("K33", "P99", "H196"),
                Set.of("F43", "Y47", "F199"),
                Set.of("F36", "M40", "L145"),
                Set.of("L39", "T144", "W195", "G199"),
                2,
                2,
                List.of("raw Vina delta", "one pose", "one residue", "molecular size",
                        "hydrophobicity", "chlorine count", "alpha methylation", "rigidification",
                        "stereochemistry alone", "one electrophilic warhead"),
                List.of(
                        "research/mettl7-selectivity-forensics/dcmb-analog-program/METTL7_SELECTIVITY_PROGRAM_CANONICAL_HANDOFF_v1.7.md",
                        "research/mettl7-general-selectivity-v1.8/METTL7_GENERAL_SELECTIVITY_MECHANISM_AND_EXPERIMENTAL_PLAN.md",
                        "research/mettl7-general-selectivity-v1.8/METTL7_LIGAND_STATE_SELECTIVITY_MATRIX.csv",
                        "research/mettl7-general-selectivity-v1.8/B_SIDE_PROBE_SELECTION_AND_BALANCED_EXPERIMENT.md",
                        "research/mettl7-general-selectivity-v1.8/MOLECULE_SET_AUDIT.md"));
    }
}
