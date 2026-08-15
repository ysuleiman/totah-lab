package totah.lab.prometheus.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.prometheus.ingest.XyzParser.XyzGeometry;

class XyzParserTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesThreeAtomGeometryInFileOrder() throws Exception {
        Path xyz = tempDir.resolve("water.xyz");
        Files.writeString(xyz, """
                3
                a comment line
                O   0.0000000000   0.0000000000   0.1173000000
                H   0.0000000000   0.7572000000  -0.4692000000
                H   0.0000000000  -0.7572000000  -0.4692000000
                """);

        XyzGeometry geometry = XyzParser.parse(xyz);

        assertThat(geometry.atomCount()).isEqualTo(3);
        assertThat(geometry.elementSymbols()).containsExactly("O", "H", "H");
        assertThat(geometry.coordinates().get(0)).isEqualTo(new Point3D(0.0, 0.0, 0.1173));
        assertThat(geometry.coordinates().get(2).z()).isEqualTo(-0.4692);
    }

    @Test
    void toleratesBlankLinesBetweenAtoms() throws Exception {
        Path xyz = tempDir.resolve("gappy.xyz");
        Files.writeString(xyz, "2\ncomment\nC 0.0 0.0 0.0\n\nH 1.0 0.0 0.0\n");

        XyzGeometry geometry = XyzParser.parse(xyz);

        assertThat(geometry.elementSymbols()).containsExactly("C", "H");
    }

    @Test
    void rejectsAtomCountMismatch() throws Exception {
        Path xyz = tempDir.resolve("bad.xyz");
        Files.writeString(xyz, "3\ncomment\nC 0.0 0.0 0.0\nH 1.0 0.0 0.0\n");

        assertThatIOException()
                .isThrownBy(() -> XyzParser.parse(xyz))
                .withMessageContaining("atom count mismatch");
    }

    @Test
    void readsDeclaredAtomCountWithoutParsingBody() throws Exception {
        Path xyz = tempDir.resolve("big.xyz");
        Files.writeString(xyz, "56\ncomment\nC 0.0 0.0 0.0\n");

        assertThat(XyzParser.declaredAtomCount(xyz)).isEqualTo(56);
    }
}
