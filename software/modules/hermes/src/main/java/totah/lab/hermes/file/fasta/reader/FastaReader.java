package totah.lab.hermes.file.fasta.reader;

import totah.lab.hermes.file.fasta.FastaRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Parses standard single- or multi-record FASTA documents. */
public final class FastaReader {

    public List<FastaRecord> parse(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return parse(reader);
        }
    }

    /** Parses without closing the caller-owned reader. */
    public List<FastaRecord> parse(Reader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        BufferedReader buffered = reader instanceof BufferedReader bufferedReader
                ? bufferedReader
                : new BufferedReader(reader);
        List<FastaRecord> records = new ArrayList<>();
        String header = null;
        StringBuilder sequence = new StringBuilder();
        int lineNumber = 0;

        String line;
        while ((line = buffered.readLine()) != null) {
            lineNumber++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith(";")) {
                continue;
            }
            if (trimmed.startsWith(">")) {
                if (header != null) {
                    records.add(toRecord(header, sequence, lineNumber - 1));
                }
                header = trimmed.substring(1).trim();
                if (header.isEmpty()) {
                    throw malformed(lineNumber, "header must contain an identifier");
                }
                sequence.setLength(0);
            } else {
                if (header == null) {
                    throw malformed(lineNumber, "sequence appears before the first header");
                }
                if (trimmed.chars().anyMatch(Character::isWhitespace)) {
                    throw malformed(lineNumber, "sequence lines must not contain whitespace");
                }
                sequence.append(trimmed);
            }
        }

        if (header != null) {
            records.add(toRecord(header, sequence, lineNumber));
        }
        return List.copyOf(records);
    }

    private static FastaRecord toRecord(
            String header,
            StringBuilder sequence,
            int lineNumber
    ) throws IOException {
        if (sequence.isEmpty()) {
            throw malformed(lineNumber, "record has no sequence");
        }
        int separator = firstWhitespace(header);
        String identifier = separator < 0 ? header : header.substring(0, separator);
        String description = separator < 0 ? null : header.substring(separator).trim();
        return new FastaRecord(identifier, description, sequence.toString());
    }

    private static int firstWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isWhitespace(value.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    private static IOException malformed(int lineNumber, String reason) {
        return new IOException("Malformed FASTA at line " + lineNumber + ": " + reason);
    }
}
