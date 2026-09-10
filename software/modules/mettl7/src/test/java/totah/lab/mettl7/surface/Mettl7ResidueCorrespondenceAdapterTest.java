package totah.lab.mettl7.surface;

import org.junit.jupiter.api.Test;
import totah.lab.athena.surface.differential.DifferentialSurfaceOptions;
import totah.lab.athena.surface.differential.ExplicitResidueCorrespondence;
import totah.lab.athena.surface.differential.SurfaceResidue;
import totah.lab.athena.surface.differential.SurfDiffCompatibleSasa;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.hermes.file.pdb.reader.PdbReader;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7ResidueCorrespondenceAdapterTest {
    private static final Path ROOT = Path.of("../../..",
            "research/mettl7-netarsudil-sam-mechanism/local-flexibility/prepared");

    @Test
    void validatesCompleteBidirectionalEstablishedMapping() throws Exception {
        List<SurfaceResidue> a = read("METTL7A_protein_only.pdb");
        List<SurfaceResidue> b = read("METTL7B_protein_only.pdb");
        Mettl7ResidueCorrespondenceAdapter adapter =
                new Mettl7ResidueCorrespondenceAdapter();
        ExplicitResidueCorrespondence aToB = adapter.correspond(a, b);
        ExplicitResidueCorrespondence bToA = adapter.correspond(b, a);
        assertThat(a).hasSize(244);
        assertThat(b).hasSize(244);
        assertThat(aToB.queryToSubject()).hasSize(244);
        assertThat(bToA.queryToSubject()).hasSize(244);
        assertThat(aToB.subjectOf(new ResidueId("A", 196, null)))
                .contains(new ResidueId("A", 196, null));
        assertThat(a).allSatisfy(row -> {
            assertThat(row.id().chainId()).isEqualTo("A");
            assertThat(row.id().insertionCode()).isNull();
            assertThat(row.residue().getAlphaCarbonPosition()).isPresent();
        });
    }

    private static List<SurfaceResidue> read(String name) throws Exception {
        return SurfDiffCompatibleSasa.calculate(
                new PdbReader().read(ROOT.resolve(name)),
                DifferentialSurfaceOptions.SURFDIFF_COMPATIBLE);
    }
}
