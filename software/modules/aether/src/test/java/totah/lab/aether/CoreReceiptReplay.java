package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import totah.lab.aether.matrix.CoreHamiltonianCalculator;
import totah.lab.aether.matrix.CoreHamiltonianMatrix;
import totah.lab.aether.model.QuantumSystem;

public final class CoreReceiptReplay {
    private CoreReceiptReplay() {}

    static CoreHamiltonianMatrix h2Matrix() throws IOException {
        return new CoreHamiltonianCalculator(new QuantumSystem(NuclearTestCases.h2Nuclei(), 0, 1),
                KineticTestCases.h2()).calculate();
    }

    public static void main(String[] args) throws IOException {
        if (Runtime.version().feature() != 21) throw new IllegalStateException("Replay requires Java 21");
        System.out.write(h2Matrix().receipt().toString().getBytes(StandardCharsets.UTF_8));
    }
}
