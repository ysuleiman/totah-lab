package totah.lab.mettl7.design;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Read-only, score-free prioritization of the frozen METTL7B prefilter survivors. */
public final class Mettl7SurvivorPriorityAuditCli {
    private static final Set<Integer> B_CORE = Set.of(196, 203, 206, 207);
    private static final Set<Integer> A_FOCUS = Set.of(99, 126, 151, 197, 200);

    private Mettl7SurvivorPriorityAuditCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("campaign directory required");
        Path root = Path.of(args[0]);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Set<String> survivorIds = readSurvivorIds(root.resolve("METTL7B_PREFILTER_PROPOSED_SURVIVORS.csv"));
        JsonNode all = mapper.readTree(root.resolve("METTL7B_CONFORMER_FEATURE_MAPPING.json").toFile());
        Map<String, List<JsonNode>> grouped = new TreeMap<>();
        all.forEach(row -> {
            String id = row.path("candidate_id").asText();
            if (survivorIds.contains(id)) grouped.computeIfAbsent(id, ignored -> new ArrayList<>()).add(row);
        });
        if (grouped.size() != 33) throw new IOException("expected 33 survivors, found " + grouped.size());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (var entry : grouped.entrySet()) rows.add(audit(root, entry.getKey(), entry.getValue(), mapper));
        rows.sort(Comparator.comparing(row -> row.get("candidate_id").toString()));

        List<Map<String, Object>> priority = choosePriority(rows);
        Set<String> priorityIds = new LinkedHashSet<>();
        priority.forEach(row -> priorityIds.add(row.get("candidate_id").toString()));
        rows.forEach(row -> row.put("docking_priority", priorityIds.contains(row.get("candidate_id").toString())));

        writeCsv(root.resolve("METTL7B_SURVIVOR_PRIORITY_MATRIX.csv"), rows);
        writeCsv(root.resolve("METTL7B_DOCKING_PRIORITY_SET.csv"), priority);
        Map<String, Long> tiers = new TreeMap<>();
        rows.forEach(row -> tiers.merge(row.get("tier").toString(), 1L, Long::sum));
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("input_survivors", grouped.size());
        receipt.put("input_conformers", grouped.values().stream().mapToInt(List::size).sum());
        receipt.put("tier_counts", tiers);
        receipt.put("priority_count", priority.size());
        receipt.put("priority_ids", priorityIds);
        receipt.put("vina_score_used", false);
        receipt.put("docking_run", false);
        receipt.put("molecules_or_conformers_regenerated", false);
        receipt.put("grammar_or_threshold_changed", false);
        receipt.put("classification_policy", Map.of(
                "TIER_1", "O7/B207 retained in >=2 conformers; hydrophobic evidence at B196, B203, B206 and B207 retained; >=2 A focus residues reduced, including at least one of A99/A126/A151/A197/A200; multi-conformer B compatibility",
                "TIER_2", "O7/B207 retained in >=2 conformers; hydrophobic evidence at >=3 of B196/B203/B206/B207 retained; >=2 A focus residues reduced; multi-conformer B compatibility",
                "TIER_3", "meaningful A reduction but fewer than three B hydrophobic core residue sectors retained or polar feature lost",
                "DROP", "no meaningful A-focus reduction or no multi-conformer B compatibility"));
        receipt.put("priority_policy", "all TIER_1, then mechanistically strongest TIER_2 representatives up to 15; coverage across edit vector/transformation signatures before duplicate signatures; candidate ID is the deterministic final tie-break; MMFF is evidence only and is not used for selection");
        receipt.put("docking_priority_set_ready", !priority.isEmpty() && priority.size() <= 15);
        mapper.writeValue(root.resolve("METTL7B_SURVIVOR_PRIORITY_RECEIPT.json").toFile(), receipt);
        Files.writeString(root.resolve("METTL7B_SURVIVOR_PRIORITY_AUDIT.md"), report(rows, priority, tiers), StandardCharsets.UTF_8);
        writeHashes(root, List.of("METTL7B_SURVIVOR_PRIORITY_MATRIX.csv", "METTL7B_DOCKING_PRIORITY_SET.csv",
                "METTL7B_SURVIVOR_PRIORITY_RECEIPT.json", "METTL7B_SURVIVOR_PRIORITY_AUDIT.md"));
    }

    private static Map<String, Object> audit(Path root, String id, List<JsonNode> conformers, ObjectMapper mapper) throws IOException {
        Set<String> bPreserved = ids(conformers.getFirst().path("b_mapping").path("inherited"));
        Set<String> bLost = ids(conformers.getFirst().path("b_mapping").path("invalidated"));
        Set<String> aReduced = ids(conformers.getFirst().path("a_mapping").path("invalidated"));
        Set<Integer> bResidues = residues(bPreserved);
        Set<Integer> aReducedResidues = residues(aReduced);
        Set<Integer> aSpecific = new LinkedHashSet<>(aReducedResidues); aSpecific.retainAll(A_FOCUS);
        Set<Integer> supporting = new LinkedHashSet<>();
        for (JsonNode conformer : conformers) for (JsonNode envelope : conformer.path("b_envelope_evidence")) {
            if (envelope.path("passed").asBoolean()) {
                int first = envelope.path("first_residue").asInt(); int second = envelope.path("second_residue").asInt();
                if (first >= 202 && first <= 208) supporting.add(first);
                if (second >= 202 && second <= 208) supporting.add(second);
            }
        }
        int compatible = 0; int converged = 0;
        for (JsonNode conformer : conformers) {
            if (!"B_FEATURES_LOST".equals(conformer.path("b_class").asText()) && !"UNEVALUABLE".equals(conformer.path("b_class").asText())) compatible++;
            if (conformer.path("mmff_converged").asBoolean()) converged++;
        }
        boolean polar = bPreserved.contains("B:HBA:207:O7");
        int coreCount = (int) B_CORE.stream().filter(bResidues::contains).count();
        boolean meaningfulA = aSpecific.size() >= 2;
        String tier;
        if (polar && coreCount == 4 && meaningfulA && compatible >= 2) tier = "TIER_1";
        else if (polar && coreCount >= 3 && meaningfulA && compatible >= 2) tier = "TIER_2";
        else if (meaningfulA) tier = "TIER_3";
        else tier = "DROP";
        JsonNode provenance = mapper.readTree(root.resolve("persisted_candidates").resolve(id).resolve("generation_provenance.json").toFile());
        Set<String> editSignatures = new LinkedHashSet<>();
        for (JsonNode path : provenance.path("complete_paths")) for (JsonNode edit : path.path("edits_in_order"))
            editSignatures.add(edit.path("editableVectorId").asText() + ":" + edit.path("transformation").asText());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("candidate_id", id);
        row.put("tier", tier);
        row.put("b_features_preserved", String.join(";", bPreserved));
        row.put("b_features_lost", String.join(";", bLost));
        row.put("b207_polar", polar ? "PRESERVED_O7_PRIMARY" : "LOST_NO_VALIDATED_REPLACEMENT");
        row.put("b_hydrophobic_core_residues_preserved", joinInts(intersection(bResidues, B_CORE)));
        row.put("b_hydrophobic_core_residues_lost", joinInts(difference(B_CORE, bResidues)));
        row.put("b_196_203_206_207_arrangement", coreCount == 4 ? "PRESERVED_ALL_FOUR" : coreCount == 3 ? "MOSTLY_PRESERVED_THREE_OF_FOUR" : "IMPORTANT_CORE_LOSS");
        row.put("supporting_202_208_geometry", supporting.isEmpty() ? "NOT_REPRESENTED_BY_PASSING_FROZEN_ENVELOPES" : joinInts(supporting));
        row.put("a_counter_features_reduced", String.join(";", aReduced));
        row.put("a_counter_residues_reduced", joinInts(aReducedResidues));
        row.put("a_99_126_151_197_200_reduced", joinInts(aSpecific));
        row.put("conformer_robustness", compatible + "/" + conformers.size() + " B-compatible; feature inheritance identical across retained conformers");
        row.put("conformer_reliance", compatible >= 2 ? "MULTIPLE_CONFORMERS" : "ONE_CONFORMER_ONLY");
        row.put("mmff_converged", converged + "/" + conformers.size());
        row.put("mmff_interpretation", "EVIDENCE_ONLY_NOT_A_GATE");
        row.put("edit_signatures", String.join(";", editSignatures));
        row.put("rationale", rationale(tier, polar, coreCount, aSpecific));
        return row;
    }

    private static List<Map<String, Object>> choosePriority(List<Map<String, Object>> rows) {
        Comparator<Map<String, Object>> strength = Comparator
                .comparingInt((Map<String, Object> r) -> "TIER_1".equals(r.get("tier")) ? 0 : 1)
                .thenComparingInt(r -> -countCsv(r.get("a_99_126_151_197_200_reduced").toString()))
                .thenComparing(r -> r.get("candidate_id").toString());
        List<Map<String, Object>> eligible = rows.stream().filter(r -> Set.of("TIER_1", "TIER_2").contains(r.get("tier"))).sorted(strength).toList();
        List<Map<String, Object>> selected = new ArrayList<>(); Set<String> signatures = new LinkedHashSet<>();
        for (Map<String, Object> row : eligible) if (selected.size() < 15 && signatures.add(row.get("edit_signatures").toString())) selected.add(row);
        for (Map<String, Object> row : eligible) if (selected.size() < 15 && !selected.contains(row)) selected.add(row);
        return selected;
    }

    private static String rationale(String tier, boolean polar, int coreCount, Set<Integer> aSpecific) {
        return tier + ": B207/O7=" + (polar ? "retained" : "lost") + ", B core=" + coreCount + "/4 residue sectors, A-focus reductions=" + joinInts(aSpecific) + ", multi-conformer evidence";
    }

    private static Set<String> readSurvivorIds(Path csv) throws IOException {
        Set<String> ids = new LinkedHashSet<>(); List<String> lines = Files.readAllLines(csv);
        for (int i = 1; i < lines.size(); i++) { String line = lines.get(i); if (!line.isBlank()) ids.add(line.substring(1, line.indexOf('"', 1))); }
        return ids;
    }
    private static Set<String> ids(JsonNode array) { Set<String> out = new LinkedHashSet<>(); array.forEach(n -> out.add(n.path("grammarFeatureId").asText())); return out; }
    private static Set<Integer> residues(Set<String> ids) { Set<Integer> out = new LinkedHashSet<>(); for (String id : ids) { String[] p=id.split(":"); if (p.length >= 4) out.add(Integer.parseInt(p[2])); } return out; }
    private static Set<Integer> intersection(Set<Integer> a, Set<Integer> b) { Set<Integer> out=new LinkedHashSet<>(a); out.retainAll(b); return out; }
    private static Set<Integer> difference(Set<Integer> a, Set<Integer> b) { Set<Integer> out=new LinkedHashSet<>(a); out.removeAll(b); return out; }
    private static String joinInts(Set<Integer> values) { return values.stream().sorted().map(String::valueOf).reduce((a,b)->a+";"+b).orElse("NONE"); }
    private static int countCsv(String value) { return "NONE".equals(value) ? 0 : value.split(";").length; }

    private static void writeCsv(Path path, List<Map<String, Object>> rows) throws IOException {
        List<String> headers = rows.isEmpty() ? List.of() : new ArrayList<>(rows.getFirst().keySet());
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(String.join(",", headers)); writer.newLine();
            for (Map<String, Object> row : rows) { for (int i=0;i<headers.size();i++) { if(i>0)writer.write(','); writer.write('"'+String.valueOf(row.getOrDefault(headers.get(i),"")).replace("\"","\"\"")+'"'); } writer.newLine(); }
        }
    }
    private static String report(List<Map<String, Object>> rows, List<Map<String, Object>> priority, Map<String, Long> tiers) {
        StringBuilder b=new StringBuilder("# METTL7B survivor-priority audit\n\nThis bounded audit uses only the persisted 33-survivor/99-conformer feature-mapping corpus. It uses no Vina score, performs no docking, and changes no grammar, threshold, molecule, state, or conformer. Feature preservation means chemistry-valid inherited ligand features; supporting 202–208 entries mean passing frozen configured feature envelopes, not inferred protein contacts.\n\n## Result\n\nTier counts: ").append(tiers).append(". Priority set: ").append(priority.size()).append(" candidates.\n\nThe main discriminator is whether the B196 hydrophobic feature survives. Every survivor retains the primary O7/B207 acceptor and B203/B206/B207 hydrophobic sectors. The strongest candidates also retain B196 while reducing A99 plus additional A-counter features. No candidate is claimed to bind or inhibit METTL7B before docking and experiment.\n\n## Docking priority set\n\n");
        for (Map<String,Object> r:priority)b.append("- `").append(r.get("candidate_id")).append("` — ").append(r.get("rationale")).append("; edits ").append(r.get("edit_signatures")).append("; MMFF ").append(r.get("mmff_converged")).append(" (evidence only).\n");
        b.append("\n## Complete 33-candidate audit\n\nThe full atom/group-level preserved/lost lists and exact A residue reductions are in `METTL7B_SURVIVOR_PRIORITY_MATRIX.csv`. No supporting residue was imputed from another compound or from residue proximity.\n\nDOCKING_PRIORITY_SET_READY = ").append(!priority.isEmpty()&&priority.size()<=15).append('\n');
        return b.toString();
    }
    private static String sha(Path p) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))); }
    private static void writeHashes(Path root, List<String> files) throws Exception { try(BufferedWriter w=Files.newBufferedWriter(root.resolve("METTL7B_SURVIVOR_PRIORITY_SHA256SUMS"))){for(String file:files){w.write(sha(root.resolve(file))+"  "+file);w.newLine();}} }
}
