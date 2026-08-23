package totah.lab.prometheus.ingest.authoritative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adversarial acceptance tests C1-C4 of docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md
 * against {@link AmberPrmtopReader}. Fixtures are hand-written minimal prmtop
 * fragments with fixed-width fields; the oracle is byte-exact string
 * round-trip and hand-computed numbers, independent of the implementation.
 */
class AdversarialPrmtopAcceptanceTest {

    @TempDir Path directory;

    /**
     * TEST_ID: C1 — Atom identity strings pass through the reader byte-exact.
     * {@code CD1}, {@code HD11}, {@code SD}, {@code OD2} are names, not numbers;
     * Fortran D→E exponent normalization must not touch string sections.
     */
    @Test void c1_atomNamesContainingDRoundTripByteExact() throws IOException {
        Path topology = write("c1.prmtop", """
                %FLAG POINTERS
                %FORMAT(10I8)
                       4       1       0       0       0       0       0       0       0       0
                %FLAG ATOM_NAME
                %FORMAT(20a4)
                CD1 HD11SD  OD2\s
                %FLAG AMBER_ATOM_TYPE
                %FORMAT(20a4)
                CT  HC  SD  OS\s
                %FLAG CHARGE
                %FORMAT(5E16.8)
                  1.00000000E+00 -2.50000000E-01  3.00000000E+00  0.00000000E+00
                """);
        AmberTopologyResult result = new AmberPrmtopReader().read(topology);
        assertThat(result.atomNames().value()).contains(List.of("CD1", "HD11", "SD", "OD2"));
        assertThat(result.atomTypes().value()).contains(List.of("CT", "HC", "SD", "OS"));
    }

    /**
     * TEST_ID: C2 — {@code 1.0D+00} in CHARGE is the number 1.0. One CHARGE
     * line mixing {@code 1.0D+00}, {@code -2.5D-01}, {@code 3.0E+00} parses to
     * 1.0, -0.25, 3.0 (the result carries them divided by AMBER_CHARGE_SCALE
     * 18.2223, so the assertions rescale). The same file carries D-containing
     * atom names so C1 must still hold on it: D→E applies to numerics only.
     */
    @Test void c2_fortranDExponentsParseInNumericSectionsOnly() throws IOException {
        Path topology = write("c2.prmtop", """
                %FLAG POINTERS
                %FORMAT(10I8)
                       3       1       0       0       0       0       0       0       0       0
                %FLAG ATOM_NAME
                %FORMAT(20a4)
                CD1 SD  OD2\s
                %FLAG AMBER_ATOM_TYPE
                %FORMAT(20a4)
                CT  SD  O2\s
                %FLAG CHARGE
                %FORMAT(5E16.8)
                  1.00000000D+00 -2.50000000D-01  3.00000000E+00
                """);
        AmberTopologyResult result = new AmberPrmtopReader().read(topology);
        List<Double> charges = result.charges().value().orElseThrow();
        assertThat(charges).hasSize(3);
        assertThat(charges.get(0) * 18.2223).isCloseTo(1.0, within(1e-9));
        assertThat(charges.get(1) * 18.2223).isCloseTo(-0.25, within(1e-9));
        assertThat(charges.get(2) * 18.2223).isCloseTo(3.0, within(1e-9));
        assertThat(result.atomNames().value()).contains(List.of("CD1", "SD", "OD2"));
        assertThat(result.atomTypes().value()).contains(List.of("CT", "SD", "O2"));
    }

    /**
     * TEST_ID: C3 — name and type are separate channels; neither is derived
     * from, swapped with, or deduplicated against the other. Two atoms share
     * type {@code CT}; one name ({@code CD1}) and one type ({@code SD})
     * contain D. Per-atom (name, type) pairs must survive exactly as authored,
     * in file order, with duplicate type values preserved.
     */
    @Test void c3_nameAndTypeChannelsDistinctOrderPreservingWithDuplicateTypes() throws IOException {
        Path topology = write("c3.prmtop", """
                %FLAG POINTERS
                %FORMAT(10I8)
                       3       1       0       0       0       0       0       0       0       0
                %FLAG ATOM_NAME
                %FORMAT(20a4)
                CD1 CD2 SD\s\s
                %FLAG AMBER_ATOM_TYPE
                %FORMAT(20a4)
                CT  CT  SD\s\s
                %FLAG CHARGE
                %FORMAT(5E16.8)
                  1.00000000E+00  2.00000000E+00 -3.00000000E+00
                """);
        AmberTopologyResult result = new AmberPrmtopReader().read(topology);
        List<String> names = result.atomNames().value().orElseThrow();
        List<String> types = result.atomTypes().value().orElseThrow();
        assertThat(names).containsExactly("CD1", "CD2", "SD");
        assertThat(types).containsExactly("CT", "CT", "SD");
    }

    /**
     * TEST_ID: C4 — NATOM is the contract. (a) NATOM=3 with a 2-entry
     * ATOM_NAME section is corrupt and must be rejected with IOException.
     * (b) Repeated names are legitimate: {@code H H O} is accepted with two
     * distinct H entries in order, no dedup.
     */
    @Test void c4_shortSectionRejectedAndDuplicateNamesSurvive() throws IOException {
        Path corrupt = write("c4a.prmtop", """
                %FLAG POINTERS
                %FORMAT(10I8)
                       3       1       0       0       0       0       0       0       0       0
                %FLAG ATOM_NAME
                %FORMAT(20a4)
                H   O\s\s\s
                %FLAG AMBER_ATOM_TYPE
                %FORMAT(20a4)
                HC  HC  O2\s\s
                %FLAG CHARGE
                %FORMAT(5E16.8)
                  1.00000000E+00  1.00000000E+00 -2.00000000E+00
                """);
        assertThatIOException().isThrownBy(() -> new AmberPrmtopReader().read(corrupt));

        Path water = write("c4b.prmtop", """
                %FLAG POINTERS
                %FORMAT(10I8)
                       3       1       0       0       0       0       0       0       0       0
                %FLAG ATOM_NAME
                %FORMAT(20a4)
                H   H   O\s\s\s
                %FLAG AMBER_ATOM_TYPE
                %FORMAT(20a4)
                HC  HC  O2\s\s
                %FLAG CHARGE
                %FORMAT(5E16.8)
                  1.00000000E+00  1.00000000E+00 -2.00000000E+00
                """);
        AmberTopologyResult result = new AmberPrmtopReader().read(water);
        assertThat(result.atomCount().value()).contains(3);
        assertThat(result.atomNames().value()).contains(List.of("H", "H", "O"));
    }

    private Path write(String name, String content) throws IOException {
        Path path = directory.resolve(name);
        Files.writeString(path, content);
        return path;
    }
}
