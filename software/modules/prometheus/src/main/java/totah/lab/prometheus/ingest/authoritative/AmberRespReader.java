package totah.lab.prometheus.ingest.authoritative;

import totah.lab.prometheus.recovery.FieldSourceProvenance;
import totah.lab.prometheus.recovery.RecoveredField;
import totah.lab.prometheus.recovery.RecoveryClassification;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reader for native AmberTools RESP cntrl/molecule blocks, qout charges and convergence output. */
public final class AmberRespReader {
    private static final Pattern INTEGER_KEY = Pattern.compile("\\b%s\\s*=\\s*(\\d+)");
    private static final Pattern DOUBLE_KEY = Pattern.compile("\\b%s\\s*=\\s*([-+0-9.Ee]+)");
    private static final Pattern VERSION = Pattern.compile("Restrained ESP Fit\\s+([^\\s]+)\\s+Amber\\s+([^\\s]+)");
    private static final Pattern ATOM = Pattern.compile("\\s*(\\d+)\\s+(-?\\d+)\\s*");

    public AmberRespResult read(Path runDirectory) throws IOException {
        ArtifactLines stage1 = ArtifactLines.read(runDirectory.resolve("resp1.in"));
        ArtifactLines stage2 = ArtifactLines.read(runDirectory.resolve("resp2.in"));
        ArtifactLines output = ArtifactLines.read(runDirectory.resolve("resp2.out"));
        ArtifactLines qout = ArtifactLines.read(runDirectory.resolve("stage2.qout"));

        Located<Integer> nmol = integer(stage2, "nmol");
        Located<Double> qwt1 = decimal(stage1, "qwt");
        Located<Double> qwt2 = decimal(stage2, "qwt");
        MoleculeBlocks molecules = moleculeBlocks(stage2, nmol.value());
        Located<String> version = version(output);
        Located<Boolean> converged = convergence(output);
        List<Double> allCharges = doubles(qout.lines());
        int atomCount = molecules.atomCount();
        if (allCharges.size() != nmol.value() * atomCount) {
            throw new IOException("stage2.qout contains " + allCharges.size() + " charges; expected "
                    + (nmol.value() * atomCount));
        }
        List<Double> common = List.copyOf(allCharges.subList(0, atomCount));
        for (int conformer = 1; conformer < nmol.value(); conformer++) {
            if (!common.equals(allCharges.subList(conformer * atomCount, (conformer + 1) * atomCount))) {
                throw new IOException("RESP output does not contain one common charge vector");
            }
        }
        double sum = common.stream().mapToDouble(Double::doubleValue).sum();
        FieldSourceProvenance chargeSource = qout.field("stage2.qout:charge-vector[1:" + atomCount + "]");
        List<FieldSourceProvenance> chargeAndInput = List.of(chargeSource, molecules.chargeSource());
        return new AmberRespResult(
                raw("resp.software", version.value(), version.source()),
                raw("resp.conformer_count", nmol.value(), nmol.source()),
                raw("resp.conformer_ids", molecules.ids(), molecules.idSource()),
                raw("resp.formal_charges", molecules.formalCharges(), molecules.chargeSource()),
                raw("resp.atom_count", atomCount, molecules.atomCountSource()),
                raw("resp.stage1.qwt", qwt1.value(), qwt1.source()),
                raw("resp.stage2.qwt", qwt2.value(), qwt2.source()),
                raw("resp.stage2.equivalence_constraints", molecules.equivalences(),
                        molecules.equivalenceSource()),
                raw("resp.serialized_charges", common, chargeSource),
                derived("resp.serialized_total_charge", sum, chargeAndInput,
                        "sum of the first common per-conformer stage2.qout vector"),
                raw("resp.converged", converged.value(), converged.source()),
                RecoveredField.unrecoverable("resp.esp_quantum_method",
                        "RESP artifacts contain ESP values but do not encode the QM method that generated them"));
    }

    private static Located<Integer> integer(ArtifactLines file, String key) throws IOException {
        Pattern p = Pattern.compile(INTEGER_KEY.pattern().formatted(Pattern.quote(key)));
        return find(file, p, m -> Integer.parseInt(m.group(1)), key);
    }

    private static Located<Double> decimal(ArtifactLines file, String key) throws IOException {
        Pattern p = Pattern.compile(DOUBLE_KEY.pattern().formatted(Pattern.quote(key)));
        return find(file, p, m -> Double.parseDouble(m.group(1)), key);
    }

    private static Located<String> version(ArtifactLines output) throws IOException {
        return find(output, VERSION, m -> "Amber RESP " + m.group(1) + " (banner Amber " + m.group(2) + ")",
                "RESP version banner");
    }

    private static Located<Boolean> convergence(ArtifactLines output) throws IOException {
        Pattern p = Pattern.compile("Convergence in\\s+(\\d+)\\s+iterations");
        return find(output, p, ignored -> true, "convergence marker");
    }

    private static <T> Located<T> find(ArtifactLines file, Pattern pattern,
                                       java.util.function.Function<Matcher, T> mapper, String label) throws IOException {
        for (int i = 0; i < file.lines().size(); i++) {
            Matcher matcher = pattern.matcher(file.lines().get(i));
            if (matcher.find()) return new Located<>(mapper.apply(matcher), file.line(i));
        }
        throw new IOException("Missing " + label);
    }

    private static MoleculeBlocks moleculeBlocks(ArtifactLines input, int expected) throws IOException {
        List<String> ids = new ArrayList<>();
        List<Integer> charges = new ArrayList<>();
        Map<Integer, Integer> equivalences = new LinkedHashMap<>();
        int atomCount = -1;
        int firstId = -1, firstCharge = -1, firstAtom = -1, lastAtom = -1;
        List<String> lines = input.lines();
        for (int i = 0; i < lines.size() && ids.size() < expected; i++) {
            if (!lines.get(i).strip().equals("1.0") || i + 2 >= lines.size()) continue;
            String id = lines.get(i + 1).trim();
            String[] header = lines.get(i + 2).trim().split("\\s+");
            if (header.length != 2) continue;
            int charge;
            int atoms;
            try {
                charge = Integer.parseInt(header[0]); atoms = Integer.parseInt(header[1]);
            } catch (NumberFormatException ignored) { continue; }
            if (firstId < 0) { firstId = i + 1; firstCharge = i + 2; firstAtom = i + 3; }
            atomCount = atomCount < 0 ? atoms : atomCount;
            if (atomCount != atoms) throw new IOException("RESP conformers have different atom counts");
            ids.add(id); charges.add(charge);
            for (int a = 0; a < atoms; a++) {
                int lineIndex = i + 3 + a;
                Matcher matcher = ATOM.matcher(lines.get(lineIndex));
                if (!matcher.matches()) throw new IOException("Malformed RESP atom row at line " + (lineIndex + 1));
                int reference = Integer.parseInt(matcher.group(2));
                if (ids.size() == 1 && reference != 0) equivalences.put(a + 1, reference);
                lastAtom = lineIndex;
            }
            i += 2 + atoms;
        }
        if (ids.size() != expected) throw new IOException("Expected " + expected + " RESP molecule blocks, found " + ids.size());
        return new MoleculeBlocks(List.copyOf(ids), List.copyOf(charges), atomCount, Map.copyOf(equivalences),
                input.line(firstId), input.line(firstCharge), input.line(firstAtom),
                input.field("lines:" + (firstAtom + 1) + "-" + (lastAtom + 1) + ":ivary"));
    }

    private static List<Double> doubles(List<String> lines) {
        List<Double> values = new ArrayList<>();
        for (String line : lines) for (String token : line.trim().split("\\s+")) {
            if (!token.isBlank()) values.add(Double.parseDouble(token));
        }
        return values;
    }

    private static <T> RecoveredField<T> raw(String name, T value, FieldSourceProvenance source) {
        return new RecoveredField<>(name, java.util.Optional.of(value),
                RecoveryClassification.RECOVERABLE_FROM_RAW_ARTIFACT, List.of(source),
                "parsed directly from an authoritative Amber RESP artifact");
    }

    private static <T> RecoveredField<T> derived(String name, T value,
                                                  List<FieldSourceProvenance> sources, String rationale) {
        return new RecoveredField<>(name, java.util.Optional.of(value), RecoveryClassification.DERIVABLE,
                sources, rationale);
    }

    private record Located<T>(T value, FieldSourceProvenance source) {}
    private record MoleculeBlocks(List<String> ids, List<Integer> formalCharges, int atomCount,
                                  Map<Integer, Integer> equivalences, FieldSourceProvenance idSource,
                                  FieldSourceProvenance chargeSource, FieldSourceProvenance atomCountSource,
                                  FieldSourceProvenance equivalenceSource) {}
}
