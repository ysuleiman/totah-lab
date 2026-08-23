package totah.lab.prometheus.ingest.authoritative;

import totah.lab.prometheus.recovery.FieldSourceProvenance;
import totah.lab.prometheus.recovery.RecoveredField;
import totah.lab.prometheus.recovery.RecoveryClassification;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Minimal authoritative Amber topology reader for POINTERS, names, types and scaled charges. */
public final class AmberPrmtopReader {
    private static final double AMBER_CHARGE_SCALE = 18.2223;
    private static final Pattern FORMAT = Pattern.compile("%FORMAT\\((\\d+)([a-zA-Z])(\\d+)(?:\\.\\d+)?\\)");

    public AmberTopologyResult read(Path topology) throws IOException {
        ArtifactLines file = ArtifactLines.read(topology);
        Map<String, Section> sections = sections(file.lines());
        List<String> pointers = numericTokens(file.lines(), sections.get("POINTERS"));
        if (pointers.isEmpty()) throw new IOException("Missing POINTERS in " + topology);
        int atoms = Integer.parseInt(pointers.getFirst());
        List<String> names = fixedStrings(file.lines(), sections.get("ATOM_NAME"), atoms);
        List<String> types = fixedStrings(file.lines(), sections.get("AMBER_ATOM_TYPE"), atoms);
        List<String> scaledTokens = numericTokens(file.lines(), sections.get("CHARGE"));
        if (scaledTokens.size() < atoms) throw new IOException("CHARGE shorter than NATOM");
        List<Double> charges = new ArrayList<>(atoms);
        for (int i = 0; i < atoms; i++) charges.add(Double.parseDouble(scaledTokens.get(i)) / AMBER_CHARGE_SCALE);
        double total = charges.stream().mapToDouble(Double::doubleValue).sum();
        FieldSourceProvenance chargeSource = source(file, sections.get("CHARGE"), "%FLAG CHARGE[1:" + atoms + "]");
        return new AmberTopologyResult(raw("prmtop.atom_count", atoms, source(file, sections.get("POINTERS"), "%FLAG POINTERS.NATOM")),
                raw("prmtop.atom_names", names, source(file, sections.get("ATOM_NAME"), "%FLAG ATOM_NAME")),
                raw("prmtop.atom_types", types, source(file, sections.get("AMBER_ATOM_TYPE"), "%FLAG AMBER_ATOM_TYPE")),
                raw("prmtop.charges", List.copyOf(charges), chargeSource), derived("prmtop.total_charge", total, chargeSource));
    }

    private static Map<String, Section> sections(List<String> lines) throws IOException {
        Map<String, Section> result = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!lines.get(i).startsWith("%FLAG ")) continue;
            String name = lines.get(i).substring(6).trim();
            if (i + 1 >= lines.size()) throw new IOException("Missing format for " + name);
            Matcher format = FORMAT.matcher(lines.get(++i));
            if (!format.matches()) throw new IOException("Unsupported format for " + name);
            int start = i + 1, end = start;
            while (end < lines.size() && !lines.get(end).startsWith("%FLAG ")) end++;
            result.put(name, new Section(start, end, Integer.parseInt(format.group(3))));
            i = end - 1;
        }
        return result;
    }

    private static List<String> fixedStrings(List<String> lines, Section section, int count) throws IOException {
        List<String> values = tokens(lines, section, false);
        if (values.size() < count) throw new IOException("Topology section shorter than NATOM");
        return List.copyOf(values.subList(0, count));
    }

    private static List<String> numericTokens(List<String> lines, Section section) throws IOException {
        return tokens(lines, section, true);
    }

    private static List<String> tokens(List<String> lines, Section section,
            boolean normalizeFortranExponent) throws IOException {
        if (section == null) throw new IOException("Required prmtop section missing");
        List<String> values = new ArrayList<>();
        for (String line : lines.subList(section.start(), section.end())) {
            for (int i = 0; i < line.length(); i += section.width()) {
                String value = line.substring(i, Math.min(i + section.width(), line.length())).trim();
                if (!value.isEmpty()) {
                    values.add(normalizeFortranExponent
                            ? value.replace('D', 'E').replace('d', 'e') : value);
                }
            }
        }
        return values;
    }

    private static FieldSourceProvenance source(ArtifactLines file, Section section, String label) {
        return file.field(label + ";lines:" + (section.start() + 1) + "-" + section.end());
    }

    private static <T> RecoveredField<T> raw(String name, T value, FieldSourceProvenance source) {
        return new RecoveredField<>(name, Optional.of(value), RecoveryClassification.RECOVERABLE_FROM_RAW_ARTIFACT,
                List.of(source), "parsed directly from the Amber topology section");
    }

    private static <T> RecoveredField<T> derived(String name, T value, FieldSourceProvenance source) {
        return new RecoveredField<>(name, Optional.of(value), RecoveryClassification.DERIVABLE, List.of(source),
                "sum of topology charges after applying Amber 18.2223 charge scaling");
    }

    private record Section(int start, int end, int width) {}
}
