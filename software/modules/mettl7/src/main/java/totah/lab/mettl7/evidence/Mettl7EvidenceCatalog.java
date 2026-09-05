package totah.lab.mettl7.evidence;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Versioned evidence catalog kept separate from triage rules and Athena algorithms. */
public final class Mettl7EvidenceCatalog {
    public static final String VERSION = "METTL7_COMPOUND_EVIDENCE_V1_2026_09_04";
    public static final String RUN_KEY = "METTL7_MANUSCRIPT_NETARSUDIL_EVIDENCE_UPDATE_V1_2026_09_04";

    private final Map<String, Mettl7CompoundEvidence> byIdentity;

    public Mettl7EvidenceCatalog() {
        this.byIdentity = evidence().stream().collect(Collectors.toUnmodifiableMap(
                value -> normalize(value.canonicalIdentity()), Function.identity()));
    }

    public Optional<Mettl7CompoundEvidence> find(String canonicalIdentity) {
        return Optional.ofNullable(byIdentity.get(normalize(canonicalIdentity)));
    }

    public List<Mettl7CompoundEvidence> entries() {
        return byIdentity.values().stream().sorted(
                java.util.Comparator.comparing(Mettl7CompoundEvidence::canonicalIdentity)).toList();
    }

    private static List<Mettl7CompoundEvidence> evidence() {
        String manuscript = "Manuscript_Final.docx sha256=59cf114c47fc1d7f7f43b45d25c1b8a0deb7209b5ed5ee027df054d6fe3daf59";
        return List.of(
                new Mettl7CompoundEvidence("7alpha-thiospironolactone (TSL)",
                        "robust S-methylation at 125 uM in TMT1A-overexpressing HeLa cells",
                        "robust S-methylation at 125 uM in TMT1B-overexpressing HeLa cells",
                        "shared productive substrate", "productive in both paralogs", "thiol sulfur",
                        "SAM methyl donor; SAH product state not tested in this assay", "none added",
                        "direct LC-MS/MS product readout", "none", "SHARED_PRODUCTIVE",
                        List.of(), manuscript, List.of(RUN_KEY), "matched purified-protein kinetics"),
                new Mettl7CompoundEvidence("captopril",
                        "robust S-methylation at 500 uM in TMT1A-overexpressing HeLa cells",
                        "robust S-methylation at 500 uM in TMT1B-overexpressing HeLa cells",
                        "shared productive substrate", "productive in both paralogs", "thiol sulfur",
                        "SAM methyl donor; SAH product state not tested in this assay", "none added",
                        "direct LC-MS/MS product readout", "none", "SHARED_PRODUCTIVE",
                        List.of(), manuscript, List.of(RUN_KEY), "matched purified-protein kinetics"),
                new Mettl7CompoundEvidence("2,3-dichloro-alpha-methylbenzylamine (DCMB)",
                        "strong inhibition at 25 uM with TSL/captopril assays",
                        "activity retained at 25 uM under matched conditions", "nonproductive inhibitor",
                        "no methyl product established", "none established",
                        "tested with SAM-dependent turnover; apo/SAH binding not established",
                        "historical manuscript docking: amine-to-SAM methyl 3.6 A (7A), 7.6 A (7B); historical MD/metadynamics retained with limitations",
                        "nonproductive inhibitor; newer controlled calculations are not overwritten",
                        "7A Y47S and F199G reduce sensitivity; 7A F43L retains partial sensitivity; 7B S47Y does not confer sensitivity",
                        "EXPERIMENTALLY_A_SELECTIVE_INHIBITOR", List.of(), manuscript,
                        List.of(RUN_KEY), "matched A/B direct binding in apo/SAM/SAH states"),
                new Mettl7CompoundEvidence("netarsudil",
                        "no effect observed in matched TSL assay", "IC50 approximately 20 uM in TSL assay",
                        "nonproductive inhibitor", "no methyl product established",
                        "primary amine and isoquinoline nitrogens tested; neither productive in accepted poses",
                        "cofactor dependence unresolved",
                        "accepted 7B distributed 196-207 topology; C203 causal support not supported; 7A poor/migrating or strained",
                        "productive primary-amine or isoquinoline-N geometry not observed",
                        "none", "EXPERIMENTALLY_B_SELECTIVE_INHIBITOR", List.of("B_COMPATIBLE_ONLY"),
                        "direct Rheem lab result supplied 2026-09-04", List.of(RUN_KEY),
                        "improve B potency from approximately 20 uM while preserving METTL7A sparing")
        );
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
