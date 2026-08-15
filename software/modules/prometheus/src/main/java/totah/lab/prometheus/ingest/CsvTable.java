package totah.lab.prometheus.ingest;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Minimal quote-aware CSV reader with header-name lookup. Columns are resolved
 * by name and missing columns degrade to {@link Optional#empty()} so callers
 * can tolerate schema drift between archive tables.
 */
final class CsvTable {

    private final List<String> header;
    private final Map<String, Integer> columnIndex;
    private final List<List<String>> rows;

    private CsvTable(List<String> header, List<List<String>> rows) {
        this.header = List.copyOf(header);
        this.rows = List.copyOf(rows);
        this.columnIndex = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            this.columnIndex.put(header.get(i), i);
        }
    }

    static CsvTable read(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("empty csv file: " + file);
            }
            List<String> header = splitLine(headerLine);
            List<List<String>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                rows.add(splitLine(line));
            }
            return new CsvTable(header, rows);
        }
    }

    boolean hasColumn(String name) {
        return columnIndex.containsKey(name);
    }

    List<String> header() {
        return header;
    }

    List<List<String>> rows() {
        return rows;
    }

    /** Cell value of {@code column} in {@code row}; empty when the column is absent or the cell blank. */
    Optional<String> cell(List<String> row, String column) {
        Integer index = columnIndex.get(column);
        if (index == null || index >= row.size()) {
            return Optional.empty();
        }
        String value = row.get(index);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    Optional<Double> cellAsDouble(List<String> row, String column) {
        return cell(row, column).map(text -> {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                return null;
            }
        });
    }

    /** Splits one CSV line, honoring double-quoted fields with embedded commas/quotes. */
    static List<String> splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields;
    }
}
