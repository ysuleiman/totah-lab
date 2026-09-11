package totah.lab.mettl7.surface;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mettl7FrozenDifferentialSurfaceLoaderTest {
    @Test void rejectsUnknownParalogInsteadOfSilentlyLoadingB() {
        assertThatThrownBy(() -> new Mettl7FrozenDifferentialSurfaceLoader()
                .load(Path.of("."), "C"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A or B");
    }
}
