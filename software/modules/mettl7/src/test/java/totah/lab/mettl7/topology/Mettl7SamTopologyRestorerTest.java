package totah.lab.mettl7.topology;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mettl7SamTopologyRestorerTest {
    @TempDir Path temporary;

    @Test
    void restoresBothParalogsWithMatchedSamSemanticsAndZeroCoordinateMovement() throws Exception {
        var restorer = new Mettl7SamTopologyRestorer();
        var a = restorer.restore(canonical(), campaign("prepared/7A_SAM.sdf"),
                netarsudil("prepared/METTL7A_SAM_rigid.pdbqt"),
                netarsudil("results/topology_complete_athena/METTL7A_SAM_TOPOLOGY_COMPLETE_EXACT_AD4_COORDS.pdb"));
        var b = restorer.restore(canonical(), campaign("prepared/7B_SAM.sdf"),
                netarsudil("prepared/METTL7B_SAM_rigid.pdbqt"),
                root().resolve("software/modules/athena/src/test/resources/mettl7-v2-regression/netarsudil/"
                        + "METTL7B_SAM_TOPOLOGY_COMPLETE_EXACT_COORDS.pdb"));

        assertThat(a.receipt().canonicalSamGraphHash()).isEqualTo(b.receipt().canonicalSamGraphHash());
        assertThat(a.receipt().samBondCount()).isEqualTo(51);
        assertThat(b.receipt().samBondCount()).isEqualTo(51);
        assertThat(a.receipt().proteinSamCrossBondCount()).isZero();
        assertThat(a.receipt().preCoordinateHash()).isEqualTo(a.receipt().postCoordinateHash());
        assertThat(a.receipt().rmsdAngstrom()).isZero();
        assertThat(a.receipt().maximumDisplacementAngstrom()).isZero();
        assertThat(a.receipt().movedAtomCount()).isZero();
        assertThat(a.receipt().malformedSamDirectUseStatus())
                .isEqualTo(Mettl7SamTopologyRestorer.REJECTED_TOPOLOGY_SOURCE_DIRECT_USE);
        assertThat(a.receipt().cofactorStatus()).isEqualTo("SAM_COFACTOR_SEPARATE_FROM_PROTEIN");
        assertThat(a.receipt().surfDiffScope()).isEqualTo("SURFDIFF_PROTEIN_ONLY_REQUIRED");
    }

    @Test
    void chargeOrProtonationMismatchFailsClosed() throws Exception {
        Path changed = temporary.resolve("charge-mismatch.sdf");
        Files.writeString(changed, Files.readString(canonical())
                .replace("M  CHG  1   8   1", "M  CHG  1   8   0"));
        assertThatThrownBy(() -> restoreA(changed, campaign("prepared/7A_SAM.sdf")))
                .isInstanceOf(IOException.class).hasMessageContaining("formal charge");
    }

    @Test
    void elementMismatchFailsClosed() throws Exception {
        Path changed = temporary.resolve("element-mismatch.sdf");
        String text = Files.readString(campaign("prepared/7A_SAM.sdf"));
        Files.writeString(changed, text.replaceFirst(" S   0  0", " P   0  0"));
        assertThatThrownBy(() -> restoreA(canonical(), changed))
                .isInstanceOf(IOException.class).hasMessageContaining("chemical identity mismatch");
    }

    @Test
    void missingSamAtomFailsClosed() throws Exception {
        Path truncated = temporary.resolve("missing-sam.pdbqt");
        String text = Files.readString(netarsudil("prepared/METTL7A_SAM_rigid.pdbqt"));
        int firstSam = text.indexOf("ATOM   4001");
        int endLine = text.indexOf('\n', firstSam);
        Files.writeString(truncated, text.substring(0, firstSam) + text.substring(endLine + 1));
        assertThatThrownBy(() -> new Mettl7SamTopologyRestorer().restore(
                canonical(), campaign("prepared/7A_SAM.sdf"), truncated,
                netarsudil("results/topology_complete_athena/METTL7A_SAM_TOPOLOGY_COMPLETE_EXACT_AD4_COORDS.pdb")))
                .isInstanceOf(IOException.class).hasMessageContaining("Missing SAM atom");
    }

    @Test
    void corruptedOrdinalSamNamesCannotControlMapping() throws Exception {
        var restored = restoreA(canonical(), campaign("prepared/7A_SAM.sdf"));
        assertThat(restored.receipt().samMappingStatus()).isIn("MAPPED_UNIQUE", "SYMMETRY_EQUIVALENT");
        assertThat(restored.receipt().canonicalToPreparedAtomMapping()).hasSize(49);
    }

    @Test
    void preparedAtomReorderingDoesNotChangeChemicalCorrespondence() throws Exception {
        Path reordered = temporary.resolve("reordered.sdf");
        Files.writeString(reordered, swapAtomsAndBondReferences(
                Files.readString(campaign("prepared/7A_SAM.sdf")), 28, 29));

        var original = restoreA(canonical(), campaign("prepared/7A_SAM.sdf"));
        var changed = restoreA(canonical(), reordered);

        assertThat(changed.receipt().canonicalSamGraphHash())
                .isEqualTo(original.receipt().canonicalSamGraphHash());
        assertThat(changed.receipt().samMappingStatus()).isEqualTo("SYMMETRY_EQUIVALENT");
        assertThat(changed.receipt().movedAtomCount()).isZero();
    }

    @Test
    void connectivityMismatchFailsClosed() throws Exception {
        Path changed = temporary.resolve("connectivity-mismatch.sdf");
        Files.writeString(changed, Files.readString(campaign("prepared/7A_SAM.sdf"))
                .replace("  1  2  1  0", "  1  3  1  0"));
        assertThatThrownBy(() -> restoreA(canonical(), changed))
                .isInstanceOf(IOException.class)
                .hasMessageMatching(".*(chemical identity mismatch|connectivity).*" );
    }

    @Test
    void canonicalSulfoniumIdentityIsRequired() throws Exception {
        Path changed = temporary.resolve("sulfur-charge-mismatch.sdf");
        Files.writeString(changed, Files.readString(canonical())
                .replace("M  CHG  1   8   1", "M  CHG  1   8   0"));
        assertThatThrownBy(() -> restoreA(changed, campaign("prepared/7A_SAM.sdf")))
                .isInstanceOf(IOException.class).hasMessageContaining("formal charge");
    }

    @Test
    void canonicalSMethylConnectivityIsRequired() throws Exception {
        Path changed = temporary.resolve("s-methyl-mismatch.sdf");
        Files.writeString(changed, Files.readString(canonical())
                .replace("  8  9  1  0", "  7  9  1  0"));
        assertThatThrownBy(() -> restoreA(changed, campaign("prepared/7A_SAM.sdf")))
                .isInstanceOf(IOException.class).hasMessageContaining("sulfonium");
    }

    private static String swapAtomsAndBondReferences(String sdf, int firstOneBased, int secondOneBased) {
        String[] lines = sdf.split("\\R", -1);
        int atomStart = 4;
        String atom = lines[atomStart + firstOneBased - 1];
        lines[atomStart + firstOneBased - 1] = lines[atomStart + secondOneBased - 1];
        lines[atomStart + secondOneBased - 1] = atom;
        int bondStart = atomStart + 49;
        for (int i = bondStart; i < bondStart + 51; i++) {
            int left = Integer.parseInt(lines[i].substring(0, 3).trim());
            int right = Integer.parseInt(lines[i].substring(3, 6).trim());
            left = swapped(left, firstOneBased, secondOneBased);
            right = swapped(right, firstOneBased, secondOneBased);
            lines[i] = String.format("%3d%3d%s", left, right, lines[i].substring(6));
        }
        return String.join(System.lineSeparator(), lines);
    }

    private static int swapped(int value, int first, int second) {
        return value == first ? second : value == second ? first : value;
    }

    private Mettl7SamTopologyRestorer.RestoredReceptor restoreA(Path canonical, Path prepared)
            throws IOException {
        return new Mettl7SamTopologyRestorer().restore(canonical, prepared,
                netarsudil("prepared/METTL7A_SAM_rigid.pdbqt"),
                netarsudil("results/topology_complete_athena/METTL7A_SAM_TOPOLOGY_COMPLETE_EXACT_AD4_COORDS.pdb"));
    }

    private static Path canonical() { return root().resolve("software/modules/daedalus/src/test/resources/ligand/SAM.sdf"); }
    private static Path campaign(String path) { return root().resolve("analysis/dcmb/controlled_campaign").resolve(path); }
    private static Path netarsudil(String path) { return root().resolve("analysis/mettl7-netarsudil-autodock4-matched-rigid-2026-09-10").resolve(path); }
    private static Path root() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null && !Files.isDirectory(current.resolve("software/modules"))) current = current.getParent();
        if (current == null) throw new IllegalStateException("repository root not found");
        return current;
    }
}
