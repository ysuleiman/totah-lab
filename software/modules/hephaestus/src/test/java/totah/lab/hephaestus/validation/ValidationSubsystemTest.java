package totah.lab.hephaestus.validation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.flexibility.*;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.topology.ProteinTopology;
import totah.lab.hephaestus.validation.internal.CanonicalAtomResolver;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationSubsystemTest {
    @Test
    void preparedProteinValidatorAggregatesIndependentFailures() {
        PreparedProtein protein=PreparedProtein.of(new Protein("p",null,"protein",null,null,null,new Structure(List.of())));
        ValidationReport report=new PreparedProteinValidator().validate(protein);
        assertTrue(report.hasErrors()); assertTrue(report.hasWarnings());
        assertTrue(report.issues().stream().anyMatch(i->i.code()==ValidationCode.EMPTY_STRUCTURE));
        assertTrue(report.issues().stream().anyMatch(i->i.code()==ValidationCode.MISSING_TOPOLOGY));
        assertTrue(report.issues().stream().anyMatch(i->i.code()==ValidationCode.MISSING_CHARGE_ASSIGNMENT));
        assertTrue(report.issues().stream().anyMatch(i->i.code()==ValidationCode.MISSING_ATOM_TYPE_ASSIGNMENT));
    }

    @Test
    void canonicalResolverRejectsStaleAndMismatchedIndicesWithoutSearching() {
        Structure structure=structure(); CanonicalAtomResolver resolver=new CanonicalAtomResolver(structure);
        ResidueId identity=new ResidueId("A",1,null);
        var stale=resolver.resolve(new AtomReference(identity,"CA",9));
        var mismatch=resolver.resolve(new AtomReference(identity,"CB",0));
        assertFalse(stale.resolved()); assertEquals(ValidationCode.STALE_ATOM_INDEX,stale.issue().code());
        assertFalse(mismatch.resolved()); assertEquals(ValidationCode.ATOM_REFERENCE_MISMATCH,mismatch.issue().code());
    }

    @Test
    void flexibilityValidatorAggregatesReferenceAndGraphFailures() {
        ResidueId identity=new ResidueId("A",1,null);
        AtomReference stale=new AtomReference(identity,"CA",5);
        RigidFragment fragment=new RigidFragment("root",List.of(stale),stale,null);
        FlexibilityModel model=new FlexibilityModel(List.of(
                new FlexibleResidue(identity,stale,List.of(fragment),List.of(
                        new RotatableBond(stale,stale,"missing-a","missing-b")))));
        ValidationReport report=new FlexibilityModelValidator().validate(
                structure(),new ProteinTopology(1,List.of()),model);
        assertTrue(report.hasErrors());
        assertTrue(report.issues().stream().filter(i->i.code()==ValidationCode.STALE_ATOM_INDEX).count()>=2);
        assertTrue(report.issues().stream().anyMatch(i->i.code()==ValidationCode.FLEXIBILITY_BOND_INVALID));
    }

    private Structure structure(){
        var atom=totah.lab.gaia.structure.Atom.builder().name("CA")
                .element(totah.lab.gaia.chemistry.Element.C)
                .position(new totah.lab.gaia.geometry.Point3D(0,0,0)).charge(0)
                .autoDockType("C").occupancy(1).build();
        return new Structure(List.of(new Chain("A",List.of(new Residue("ALA",1,List.of(atom))))));
    }
}
