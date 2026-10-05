package totah.lab.athena.system.rules.research;

import java.io.IOException;

/** Canonical new research payloads only; never changes historical evidence/state encodings. */
public final class ResearchDocuments {
    private ResearchDocuments() { }
    public static byte[] encode(Object value) { return ResearchCodec.bytes(value); }
    public static <T> T decode(byte[] bytes,Class<T> type)throws IOException { return ResearchCodec.decode(bytes.clone(),type); }
}
