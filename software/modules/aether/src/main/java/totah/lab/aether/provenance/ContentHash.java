package totah.lab.aether.provenance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.security.DigestOutputStream;

/** SHA-256 shared by resource verification and overlap receipts. */
public final class ContentHash {
    private ContentHash() {}

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Java runtime lacks required SHA-256", e);
        }
    }

    public static String sha256(String content) {
        return accumulator().append(content).finish();
    }

    /** Hash UTF-8 lines with one LF after each line, without retaining the complete canonical text. */
    public static String sha256Lines(Iterable<String> lines) {
        var digest=accumulator();
        for(String line:lines) digest.line(line);
        return digest.finish();
    }

    /** Incremental canonical UTF-8 hashing; no tensor-sized text or byte buffer is retained. */
    public static Accumulator accumulator() { return new Accumulator(); }

    /** Single-use, thread-confined accumulator. Chunk boundaries do not change UTF-8 encoding. */
    public static final class Accumulator {
        private final MessageDigest digest;
        private final OutputStreamWriter writer;
        private final char[] buffer=new char[4096];
        private boolean finished;

        private Accumulator() {
            try { digest=MessageDigest.getInstance("SHA-256"); }
            catch(NoSuchAlgorithmException e) { throw new IllegalStateException("Java runtime lacks required SHA-256",e); }
            writer=new OutputStreamWriter(new DigestOutputStream(OutputStream.nullOutputStream(),digest),StandardCharsets.UTF_8);
        }

        public Accumulator append(String text) {
            if(finished) throw new IllegalStateException("Hash already finished");
            java.util.Objects.requireNonNull(text);
            try {
                for(int offset=0;offset<text.length();) {
                    int length=Math.min(buffer.length,text.length()-offset);
                    text.getChars(offset,offset+length,buffer,0);
                    writer.write(buffer,0,length);
                    offset+=length;
                }
            } catch(IOException e) { throw new IllegalStateException("In-memory digest sink failed",e); }
            return this;
        }

        public Accumulator line(String text) { return append(text).append("\n"); }

        public String finish() {
            if(finished) throw new IllegalStateException("Hash already finished");
            finished=true;
            try { writer.close(); }
            catch(IOException e) { throw new IllegalStateException("In-memory digest sink failed",e); }
            return HexFormat.of().formatHex(digest.digest());
        }
    }

    public static String number(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Nonfinite identity value");
        return Double.toHexString(value == 0 ? 0 : value);
    }
}
