package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import totah.lab.aether.matrix.KineticMatrix;

/** Separate-process test entry point: emits the entire receipt as UTF-8, without a timestamp. */
public final class KineticReceiptReplay {
    private KineticReceiptReplay() {}

    public static void main(String[] args) throws IOException {
        if (Runtime.version().feature() != 21) throw new IllegalStateException("Replay requires Java 21");
        var receipt = KineticMatrix.compute(KineticTestCases.h2()).receipt();
        System.out.write(receipt.toString().getBytes(StandardCharsets.UTF_8));
    }
}
