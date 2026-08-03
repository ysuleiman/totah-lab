package totah.lab.proteus.protein.mutation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityMetadata;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.proteus.validation.ValidationCode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MutationValidatorTest {

    @Test
    void blocksCysteineRemovalWithExplicitSulfurBond() {
        Residue cysteine = residue("CYS", 203,
                atom("N", 0, 0, 0, Element.N), atom("CA", 1, 0, 0, Element.C),
                atom("C", 2, 0, 0, Element.C), atom("SG", 1, 2, 0, Element.S));
        Residue partner = residue("CYS", 210,
                atom("N", 3, 0, 0, Element.N), atom("CA", 4, 0, 0, Element.C),
                atom("C", 5, 0, 0, Element.C), atom("SG", 1, 4, 0, Element.S));
        AtomReference first = reference(203, "SG");
        AtomReference second = reference(210, "SG");
        Structure structure = new Structure(
                List.of(new Chain("A", List.of(cysteine, partner))),
                List.of(new Bond(first, second, BondOrder.SINGLE)),
                new ConnectivityMetadata(ConnectivityProvenance.EXPLICIT, List.of()));

        var report = new MutationValidator().validate(
                structure, mutation("CYS", "ASN"), MutationContext.defaults());

        assertTrue(report.hasErrors());
        assertTrue(report.issues().stream().anyMatch(issue ->
                issue.code() == ValidationCode.MUTATION_EXPLICIT_COVALENT_BOND));
    }

    @Test
    void requiresOverrideWhenCysteineTopologyIsAbsent() {
        Structure structure = structure(ConnectivityProvenance.ABSENT);
        var validator = new MutationValidator();

        assertTrue(validator.validate(
                structure, mutation("CYS", "ASN"), MutationContext.defaults()).hasErrors());

        MutationContext override = new MutationContext(
                AmbiguousCovalentTopologyPolicy.WARN_AND_PROCEED,
                false, false, 0.0);
        var report = validator.validate(structure, mutation("CYS", "ASN"), override);
        assertFalse(report.hasErrors());
        assertTrue(report.hasWarnings());
    }

    @Test
    void permitsAsparagineToUnbondedCysteineWithoutTopologyGuessing() {
        Residue asparagine = residue("ASN", 203,
                atom("N", 0, 0, 0, Element.N), atom("CA", 1, 0, 0, Element.C),
                atom("C", 2, 0, 0, Element.C), atom("CB", 1, 1, 0, Element.C));
        Structure structure = new Structure(
                List.of(new Chain("A", List.of(asparagine))),
                List.of(),
                new ConnectivityMetadata(ConnectivityProvenance.EXPLICIT, List.of()));

        var report = new MutationValidator().validate(
                structure, mutation("ASN", "CYS"), MutationContext.defaults());

        assertFalse(report.hasErrors());
    }

    private static Structure structure(ConnectivityProvenance provenance) {
        Residue cysteine = residue("CYS", 203,
                atom("N", 0, 0, 0, Element.N), atom("CA", 1, 0, 0, Element.C),
                atom("C", 2, 0, 0, Element.C), atom("SG", 1, 2, 0, Element.S));
        return new Structure(
                List.of(new Chain("A", List.of(cysteine))),
                List.of(), new ConnectivityMetadata(provenance, List.of()));
    }

    private static Mutation mutation(String from, String to) {
        return new Mutation(new ResidueId("A", 203, null), from, to);
    }

    private static AtomReference reference(int number, String name) {
        return new AtomReference("A", number, ' ', name);
    }

    private static Residue residue(String name, int number, Atom... atoms) {
        return new Residue(name, number, null, List.of(atoms));
    }

    private static Atom atom(
            String name, double x, double y, double z, Element element) {
        return Atom.builder().name(name).position(new Point3D(x, y, z))
                .element(element).build();
    }
}
