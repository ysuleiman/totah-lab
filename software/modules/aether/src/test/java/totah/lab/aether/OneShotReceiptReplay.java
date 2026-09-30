package totah.lab.aether;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import totah.lab.aether.matrix.CoreHamiltonianCalculator;
import totah.lab.aether.matrix.DensityMatrix;
import totah.lab.aether.matrix.OneShotRhfCalculator;
import totah.lab.aether.matrix.OneShotRhfResult;
import totah.lab.aether.matrix.OverlapMatrix;

public final class OneShotReceiptReplay {
    private OneShotReceiptReplay() {}

    static OneShotRhfResult solve(DensityMatrix density) {
        return OneShotRhfCalculator.solve(density, OverlapMatrix.compute(density.functions()),
                new CoreHamiltonianCalculator(density.system(), density.functions()).calculate(), JkTestCases.calculate(density));
    }

    public static void main(String[] args) throws IOException {
        if (Runtime.version().feature() != 21) throw new IllegalStateException("Replay requires Java 21");
        String name = args.length == 0 ? "h2_rhf" : args[0];
        System.out.write(solve(JkTestCases.densities().get(name)).receipt().toString().getBytes(StandardCharsets.UTF_8));
    }
}
