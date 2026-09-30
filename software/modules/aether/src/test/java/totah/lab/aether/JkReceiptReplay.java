package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class JkReceiptReplay {
    private JkReceiptReplay() {}

    public static void main(String[] args) throws IOException {
        if (Runtime.version().feature() != 21) throw new IllegalStateException("Replay requires Java 21");
        var density = JkTestCases.densities().get("h2_rhf");
        System.out.write(JkTestCases.calculate(density).receipt().toString().getBytes(StandardCharsets.UTF_8));
    }
}
