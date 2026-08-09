package totah.lab.hermes.file.fasta.writer;

import totah.lab.hermes.file.fasta.FastaRecord;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Writes deterministic FASTA output with configurable sequence wrapping. */
public final class FastaWriter {

    public static final int DEFAULT_LINE_LENGTH = 60;

    private final int lineLength;

    public FastaWriter() {
        this(DEFAULT_LINE_LENGTH);
    }

    public FastaWriter(int lineLength) {
        if (lineLength < 1) {
            throw new IllegalArgumentException("lineLength must be positive");
        }
        this.lineLength = lineLength;
    }

    public void write(Path path, Iterable<FastaRecord> records) throws IOException {
        Objects.requireNonNull(path, "path");
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            write(writer, records);
        }
    }

    /** Writes without closing the caller-owned writer. */
    public void write(Writer writer, Iterable<FastaRecord> records) throws IOException {
        Objects.requireNonNull(writer, "writer");
        Objects.requireNonNull(records, "records");
        for (FastaRecord record : records) {
            Objects.requireNonNull(record, "records must not contain null");
            writer.write('>');
            writer.write(record.header());
            writer.write('\n');
            String sequence = record.sequence();
            for (int start = 0; start < sequence.length(); start += lineLength) {
                writer.write(sequence, start, Math.min(lineLength, sequence.length() - start));
                writer.write('\n');
            }
        }
    }
}
