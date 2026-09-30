package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class EriReceiptReplay {
    private EriReceiptReplay() {}

    public static void main(String[] args) throws IOException {
        if (Runtime.version().feature() != 21) throw new IllegalStateException("Replay requires Java 21");
        System.out.write(EriTestCases.calculate(KineticTestCases.h2()).receipt().toString().getBytes(StandardCharsets.UTF_8));
    }
}
