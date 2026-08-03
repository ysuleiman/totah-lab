package totah.lab.hephaestus.receptor.operation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.flexibility.FlexibilityPreparationConfig;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.protonation.ProtonationConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlphaFoldFilterOperationTest {

    @Test
    void filteringPreservesProteinIdentityAndMetadata() {
        Structure structure = new Structure(List.of(
                new Chain("A", List.of(
                        residue("ALA", 1, 95.0),
                        residue("GLY", 2, 10.0)))));

        Protein protein = new Protein(
                "receptor-1", "P12345", "My receptor", "GENE",
                "organism", "function", structure);

        PreparedProtein output = new AlphaFoldFilterOperation()
                .apply(PreparedProtein.of(protein), options(50.0))
                .value();

        assertEquals("receptor-1", output.protein().id());
        assertEquals("My receptor", output.protein().name());
        assertEquals("P12345", output.protein().uniProtId().orElse(null));
        assertEquals(1, output.protein().structure().getResidueCount());
        assertEquals(1, output.protein().structure()
                .getChains().getFirst().residues().getFirst().getNumber());
        assertTrue(output.attributes().containsKey(
                AlphaFoldFilterOperation.REPORT_ATTRIBUTE));
    }

    private ReceptorPreparationOptions options(double cutoff) {
        return new ReceptorPreparationOptions(
                true,
                false,
                Set.of("MSE", "TYS"),
                cutoff,
                true,
                true,
                true,
                true,
                true,
                ProtonationConfig.defaults(),
                Map.of(),
                FlexibilityPreparationConfig.none(),
                null);
    }

    private Residue residue(String name, int number, double plddt) {
        return new Residue(name, number, List.of(
                atom("N", Element.N, 0.0, plddt),
                atom("CA", Element.C, 1.45, plddt),
                atom("C", Element.C, 2.45, plddt),
                atom("O", Element.O, 3.05, plddt)));
    }

    private Atom atom(String name, Element element, double x, double plddt) {
        return Atom.builder()
                .name(name)
                .element(element)
                .position(new Point3D(x, 0.0, 0.0))
                .occupancy(1.0)
                .bFactor(plddt)
                .build();
    }
}
