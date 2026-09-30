package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class NuclearReceiptReplay {
    private NuclearReceiptReplay() {}

    public static void main(String[] args) throws IOException {
        if (Runtime.version().feature() != 21) throw new IllegalStateException("Replay requires Java 21");
        System.out.write(NuclearTestCases.h2Matrix().receipt().toString().getBytes(StandardCharsets.UTF_8));
    }
}
