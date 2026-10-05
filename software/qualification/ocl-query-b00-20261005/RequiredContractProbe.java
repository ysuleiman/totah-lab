package totah.lab.athena.design.backend.ocl;

import org.junit.jupiter.api.Test;
import totah.lab.athena.design.backend.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Normative B00 checks, kept outside the normal suite while the unchanged adapter fails them. */
public class RequiredContractProbe {
    private final OclMolecularBackend backend = new OclMolecularBackend();
    @Test void allDistinctTargetAtomsMustSurviveSymmetry() throws Exception {
        assertEquals(6, backend.match("[c]",OclQuerySemanticsQualificationTest.fixture("benzene"))
                .queryToTargetAtomIds().size(),"Six distinct stable target atoms; not six automorphisms of one atom set");
    }
    @Test void unsupportedQueryMustNotBeAnEmptyNegative() {
        assertThrows(MolecularBackendException.class,()->backend.match("[z2]",OclQuerySemanticsQualificationTest.fixture("ethanol")));
    }
    @Test void suppliedHydrogenConstraintCannotBeSilentlyIgnored() {
        var atom=new MolecularGraph.Atom("c","C",null,0,1,false,"UNSPECIFIED",null,Map.of());
        var graph=new MolecularGraph(List.of(atom),List.of(),Map.of());
        assertThrows(MolecularBackendException.class,()->backend.match("[CH4]",graph));
    }
    @Test void invalidUnclosedBranchMustNotBecomeAPartialQuery() {
        assertThrows(MolecularBackendException.class,()->backend.match("C(",OclQuerySemanticsQualificationTest.fixture("ethanol")));
    }
}
