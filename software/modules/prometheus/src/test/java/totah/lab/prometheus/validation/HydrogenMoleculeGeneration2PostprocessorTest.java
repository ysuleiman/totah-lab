package totah.lab.prometheus.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class HydrogenMoleculeGeneration2PostprocessorTest {
    @Test void parsesSignedScientificExponent() {
        assertEquals(1.25e-12,
                HydrogenMoleculeGeneration2Postprocessor.number(
                        "{\"energy\":1.25E-12}", "energy"), 0.0);
        assertEquals(-2.5e-7,
                HydrogenMoleculeGeneration2Postprocessor.number(
                        "{\"energy\":-2.5e-7}", "energy"), 0.0);
    }
}
