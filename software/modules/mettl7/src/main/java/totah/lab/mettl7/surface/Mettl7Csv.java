package totah.lab.mettl7.surface;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Strict single-line CSV codec with quoting and rectangular-schema validation. */
final class Mettl7Csv {
    private Mettl7Csv() {
    }

    static List<Map<String, String>> read(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) throw new IOException("empty CSV: " + path);
        List<String> headers = parseLine(lines.getFirst());
        if (headers.isEmpty() || headers.stream().anyMatch(String::isBlank)
                || headers.stream().distinct().count() != headers.size()) {
            throw new IOException("invalid CSV header: " + path);
        }
        List<Map<String, String>> result = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) continue;
            List<String> values = parseLine(lines.get(index));
            if (values.size() != headers.size()) {
                throw new IOException("CSV field count mismatch at " + path + ":" + (index + 1));
            }
            LinkedHashMap<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                row.put(headers.get(column), values.get(column));
            }
            result.add(Map.copyOf(row));
        }
        return List.copyOf(result);
    }

    static String row(Object... values) {
        return java.util.Arrays.stream(values)
                .map(value -> escape(value == null ? "" : value.toString()))
                .collect(Collectors.joining(",")) + System.lineSeparator();
    }

    private static String escape(String value) {
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("multiline CSV fields are not supported");
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static List<String> parseLine(String line) throws IOException {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean quoteClosed = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else if (quoted || current.isEmpty()) {
                    quoted = !quoted;
                    if (!quoted) quoteClosed = true;
                } else {
                    throw new IOException("quote inside unquoted CSV field");
                }
            } else if (character == ',' && !quoted) {
                fields.add(current.toString());
                current.setLength(0);
                quoteClosed = false;
            } else {
                if (quoteClosed) throw new IOException(
                        "characters after closing CSV quote");
                current.append(character);
            }
        }
        if (quoted) throw new IOException("unterminated quoted CSV field");
        fields.add(current.toString());
        return List.copyOf(fields);
    }
}
