import com.actelion.research.chem.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CorrespondenceCertificationTest {
    @TempDir Path temporary;
    StereoMolecule parent() throws Exception { return CorrespondenceCertification.heavy(CorrespondenceCertification.sdf(CorrespondenceCertification.PARENT,true)); }
    StereoMolecule ar() throws Exception { return CorrespondenceCertification.heavy(CorrespondenceCertification.sdf(CorrespondenceCertification.AR,true)); }
    @Test void rawSdfAbsoluteIdentityIsRejectedButDeclaredSourceBridgeIsExplicitlyEquivalent() throws Exception {
        for(String name:List.of("netarsudil","AR-13503")) {
            var source=CorrespondenceCertification.smiles(CorrespondenceCertification.json(CorrespondenceCertification.IDENTITIES+name+".json").at("/PropertyTable/Properties/0/SMILES").asText());
            var path=name.equals("netarsudil")?CorrespondenceCertification.PARENT:CorrespondenceCertification.AR;
            assertThrows(IllegalArgumentException.class,()->CorrespondenceCertification.sameIdentity(source,CorrespondenceCertification.sdf(path,false)));
            assertDoesNotThrow(()->CorrespondenceCertification.sameIdentity(source,CorrespondenceCertification.sdf(path,true)));
        }
    }
    @Test void rigorousCorrespondenceRetainsAll24AtomsAndExposesBothRingAlternatives() throws Exception {
        var maps=CorrespondenceCertification.mappings(parent(),ar());assertEquals(2,maps.size());
        for(var map:maps){assertEquals(24,map.size());assertEquals(24,new HashSet<>(map.values()).size());assertEquals("O1",map.get("O8"));assertEquals("C7",map.get("C14"));assertEquals("N18",map.get("N25"));assertEquals("N9",map.get("N16"));}
        assertEquals(Set.of("C4","C24"),maps.stream().map(m->m.get("C11")).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void internalAtomPermutationCannotChangeStableIdMapping() throws Exception {
        var p=parent();var a=ar();var expected=CorrespondenceCertification.mappings(p,a);
        p.swapAtoms(0,5);a.swapAtoms(0,2);
        assertEquals(expected,CorrespondenceCertification.mappings(p,a));
    }
    @Test void invertedAbsoluteStereoCannotMap() throws Exception {
        String text=CorrespondenceCertification.json(CorrespondenceCertification.IDENTITIES+"AR-13503.json").at("/PropertyTable/Properties/0/SMILES").asText();
        var inverted=CorrespondenceCertification.smiles(text.replace("@@","@"));
        for(int i=0;i<inverted.getAllAtoms();i++)inverted.setAtomMapNo(i,i+1,false);
        assertTrue(CorrespondenceCertification.mappings(parent(),inverted).isEmpty());
    }
    @Test void changedChargeCannotMap() throws Exception {
        var a=ar();int n=CorrespondenceCertification.byId(a).get("N9");a.setAtomCharge(n,1);
        assertTrue(CorrespondenceCertification.mappings(parent(),a).isEmpty());
    }
    @Test void changedCarbonylBondCannotMap() throws Exception {
        var a=ar();var ids=CorrespondenceCertification.byId(a);a.setBondOrder(a.getBond(ids.get("C10"),ids.get("O11")),1);
        assertTrue(CorrespondenceCertification.mappings(parent(),a).isEmpty());
    }
    @Test void missingTopologyIsRejectedRatherThanReconstructedFromCoordinates() throws Exception {
        var f=temporary.resolve("missing.sdf");Files.writeString(f,"no connection table");
        assertThrows(IllegalArgumentException.class,()->CorrespondenceCertification.sdf(f.toString(),true));
    }
    @Test void isotopicDifferenceCannotMap() throws Exception {
        var a=ar();a.setAtomMass(CorrespondenceCertification.byId(a).get("O1"),18);
        assertTrue(CorrespondenceCertification.mappings(parent(),a).isEmpty());
    }
    @Test void partialEsterSurvivalCannotBeMisreportedAsWholeFeaturePreservationOrRemoval() throws Exception {
        for(var map:CorrespondenceCertification.mappings(parent(),ar())) {
            assertEquals("PARTIALLY_RETAINED_CHEMICALLY_CHANGED",CorrespondenceCertification.featureStatus(List.of("C6","O7","O8"),map));
            assertEquals("REMOVED",CorrespondenceCertification.featureStatus(List.of("C1"),map));
            assertEquals("PRESERVED_ATOM_SET_GEOMETRY_UNKNOWN",CorrespondenceCertification.featureStatus(List.of("N25"),map));
        }
    }
    @Test void completeCertificateAccountsForHydrogensAndValidatesAllFrozenPoseNames() throws Exception {
        var r=CorrespondenceCertification.J.valueToTree(CorrespondenceCertification.certify());
        assertEquals(200,r.get("matched_parent_poses_name_element_bridge_checked").asInt());
        assertEquals(10,r.get("deleted_parent_heavy_atoms").size());assertEquals(9,r.get("deleted_parent_explicit_H").size());
        assertEquals(1,r.get("added_AR_explicit_H").size());assertTrue(r.get("retained_heavy_bond_order_changes").isEmpty());
        assertEquals(2,r.get("frozen_vector_feature_bridge").size());assertFalse(r.get("physical_atom_lineage_established").asBoolean());
    }
}
