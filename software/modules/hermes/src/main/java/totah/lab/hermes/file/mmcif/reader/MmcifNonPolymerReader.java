package totah.lab.hermes.file.mmcif.reader;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.hermes.file.mmcif.BoundComponentAtom;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reads non-polymer occurrences and bound coordinates from an mmCIF atom-site loop. */
public final class MmcifNonPolymerReader {

    public List<BoundComponentOccurrence> read(
            Path path,
            String pdbId,
            BoundComponentOccurrence.SourceKind sourceKind,
            String assemblyId
    ) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(pdbId, "pdbId");
        Objects.requireNonNull(sourceKind, "sourceKind");
        if (sourceKind == BoundComponentOccurrence.SourceKind.ENTRY
                && assemblyId != null) {
            throw new IllegalArgumentException("Entry input cannot have an assembly ID");
        }
        if (sourceKind == BoundComponentOccurrence.SourceKind.ASSEMBLY
                && (assemblyId == null || assemblyId.isBlank())) {
            throw new IllegalArgumentException("Assembly input requires an assembly ID");
        }

        Map<OccurrenceKey, OccurrenceBuilder> occurrences = new LinkedHashMap<>();
        parseAtomSite(path, pdbId, sourceKind, assemblyId, occurrences);
        return occurrences.values().stream().map(OccurrenceBuilder::build).toList();
    }

    private void parseAtomSite(
            Path path,
            String pdbId,
            BoundComponentOccurrence.SourceKind sourceKind,
            String assemblyId,
            Map<OccurrenceKey, OccurrenceBuilder> occurrences
    ) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            boolean afterLoop = false;
            List<String> columns = null;
            List<String> tokens = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (columns == null) {
                    if ("loop_".equals(trimmed)) {
                        afterLoop = true;
                    } else if (afterLoop && trimmed.startsWith("_atom_site.")) {
                        columns = new ArrayList<>();
                        columns.add(tag(trimmed));
                        afterLoop = false;
                    } else if (afterLoop && !trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        afterLoop = false;
                    }
                    continue;
                }
                if (trimmed.startsWith("_atom_site.")) {
                    columns.add(tag(trimmed));
                    continue;
                }
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (isLoopBoundary(trimmed)) {
                    emitRows(columns, tokens, occurrences, pdbId, sourceKind, assemblyId, path);
                    if (!tokens.isEmpty()) {
                        throw new IOException("Incomplete _atom_site row in " + path);
                    }
                    return;
                }
                if (trimmed.startsWith(";")) {
                    throw new IOException("Multiline value inside _atom_site loop in " + path);
                }
                tokens.addAll(tokenize(trimmed));
                emitRows(columns, tokens, occurrences, pdbId, sourceKind, assemblyId, path);
            }
            if (columns != null) {
                emitRows(columns, tokens, occurrences, pdbId, sourceKind, assemblyId, path);
                if (!tokens.isEmpty()) {
                    throw new IOException("Incomplete _atom_site row in " + path);
                }
            }
        }
    }

    private static boolean isLoopBoundary(String value) {
        return value.startsWith("#") || value.startsWith("_")
                || value.equals("loop_") || value.startsWith("data_")
                || value.startsWith("save_");
    }

    private static String tag(String line) {
        return line.split("\\s+", 2)[0];
    }

    private static void emitRows(
            List<String> columns,
            List<String> tokens,
            Map<OccurrenceKey, OccurrenceBuilder> occurrences,
            String pdbId,
            BoundComponentOccurrence.SourceKind sourceKind,
            String assemblyId,
            Path path
    ) throws IOException {
        while (tokens.size() >= columns.size()) {
            List<String> row = new ArrayList<>(tokens.subList(0, columns.size()));
            tokens.subList(0, columns.size()).clear();
            emitRow(columns, row, occurrences, pdbId, sourceKind, assemblyId, path);
        }
    }

    private static void emitRow(
            List<String> columns,
            List<String> row,
            Map<OccurrenceKey, OccurrenceBuilder> occurrences,
            String pdbId,
            BoundComponentOccurrence.SourceKind sourceKind,
            String assemblyId,
            Path path
    ) throws IOException {
        if (!"HETATM".equals(value(columns, row, "_atom_site.group_PDB"))) {
            return;
        }
        String component = required(columns, row, "_atom_site.label_comp_id", path);
        String asym = required(columns, row, "_atom_site.label_asym_id", path);
        String sequence = value(columns, row, "_atom_site.label_seq_id");
        String authAsym = value(columns, row, "_atom_site.auth_asym_id");
        String authSequence = value(columns, row, "_atom_site.auth_seq_id");
        String insertionCode = value(columns, row, "_atom_site.pdbx_PDB_ins_code");
        int model = integer(value(columns, row, "_atom_site.pdbx_PDB_model_num"), 1,
                "model number", path);
        OccurrenceKey key = new OccurrenceKey(component, asym, sequence,
                authAsym, authSequence, insertionCode, model);
        OccurrenceBuilder occurrence = occurrences.computeIfAbsent(key,
                ignored -> new OccurrenceBuilder(pdbId, sourceKind, assemblyId, key));

        String atomName = required(columns, row, "_atom_site.label_atom_id", path);
        String element = required(columns, row, "_atom_site.type_symbol", path);
        Point3D position = new Point3D(
                decimal(required(columns, row, "_atom_site.Cartn_x", path), "x", path),
                decimal(required(columns, row, "_atom_site.Cartn_y", path), "y", path),
                decimal(required(columns, row, "_atom_site.Cartn_z", path), "z", path));
        occurrence.atoms.add(new BoundComponentAtom(
                atomName,
                value(columns, row, "_atom_site.auth_atom_id"),
                element,
                position,
                decimalOrNull(value(columns, row, "_atom_site.occupancy"), path),
                decimalOrNull(value(columns, row, "_atom_site.B_iso_or_equiv"), path),
                integerOrNull(value(columns, row, "_atom_site.pdbx_formal_charge"), path),
                value(columns, row, "_atom_site.label_alt_id"),
                value(columns, row, "_atom_site.id")));
    }

    private static String required(
            List<String> columns, List<String> row, String name, Path path)
            throws IOException {
        String result = value(columns, row, name);
        if (result == null) {
            throw new IOException("Missing " + name + " in " + path);
        }
        return result;
    }

    private static String value(List<String> columns, List<String> row, String name) {
        int index = columns.indexOf(name);
        if (index < 0 || index >= row.size()) {
            return null;
        }
        String result = row.get(index);
        return ".".equals(result) || "?".equals(result) ? null : result;
    }

    private static double decimal(String value, String field, Path path) throws IOException {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid " + field + " in " + path + ": " + value,
                    exception);
        }
    }

    private static Double decimalOrNull(String value, Path path) throws IOException {
        return value == null ? null : decimal(value, "decimal value", path);
    }

    private static int integer(String value, int fallback, String field, Path path)
            throws IOException {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid " + field + " in " + path + ": " + value,
                    exception);
        }
    }

    private static Integer integerOrNull(String value, Path path) throws IOException {
        return value == null ? null : integer(value, 0, "integer value", path);
    }

    private static List<String> tokenize(String line) throws IOException {
        List<String> tokens = new ArrayList<>();
        int index = 0;
        while (index < line.length()) {
            while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
                index++;
            }
            if (index >= line.length() || line.charAt(index) == '#') {
                break;
            }
            char quote = line.charAt(index);
            if (quote == '\'' || quote == '"') {
                int end = line.indexOf(quote, index + 1);
                if (end < 0) {
                    throw new IOException("Unterminated quoted mmCIF value: " + line);
                }
                tokens.add(line.substring(index + 1, end));
                index = end + 1;
            } else {
                int end = index;
                while (end < line.length() && !Character.isWhitespace(line.charAt(end))) {
                    end++;
                }
                tokens.add(line.substring(index, end));
                index = end;
            }
        }
        return tokens;
    }

    private record OccurrenceKey(
            String componentId,
            String asymId,
            String sequenceId,
            String authAsymId,
            String authSequenceId,
            String insertionCode,
            int modelNumber) {
    }

    private static final class OccurrenceBuilder {
        private final String pdbId;
        private final BoundComponentOccurrence.SourceKind sourceKind;
        private final String assemblyId;
        private final OccurrenceKey key;
        private final List<BoundComponentAtom> atoms = new ArrayList<>();

        private OccurrenceBuilder(String pdbId,
                BoundComponentOccurrence.SourceKind sourceKind,
                String assemblyId, OccurrenceKey key) {
            this.pdbId = pdbId;
            this.sourceKind = sourceKind;
            this.assemblyId = assemblyId;
            this.key = key;
        }

        private BoundComponentOccurrence build() {
            return new BoundComponentOccurrence(pdbId, sourceKind, assemblyId,
                    key.modelNumber(), key.componentId(), key.asymId(),
                    key.sequenceId(), key.authAsymId(), key.authSequenceId(),
                    key.insertionCode(), atoms);
        }
    }
}
