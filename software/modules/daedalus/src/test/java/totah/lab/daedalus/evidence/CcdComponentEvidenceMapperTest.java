package totah.lab.daedalus.evidence;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.evidence.ComponentChemistryEvidence;
import totah.lab.athena.pocket.evidence.EvidenceChannel;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.hermes.ccd.CcdComponent;
import totah.lab.hermes.ccd.CcdComponentAtom;
import totah.lab.hermes.ccd.CcdComponentBond;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CcdComponentEvidenceMapperTest {

    @Test
    void mapsOnlySourceChemistryAndKeepsDerivedChannelsExplicit() {
        CcdComponent component = new CcdComponent("LIG", List.of(
                new CcdComponentAtom("C1", "C", 0, false, false,
                        new Point3D(1, 2, 3), new Point3D(4, 5, 6)),
                new CcdComponentAtom("O1", "O", -1, false, false,
                        null, null)),
                List.of(new CcdComponentBond(
                        "C1", "O1", BondOrder.DOUBLE, false)));

        ComponentChemistryEvidence result = new CcdComponentEvidenceMapper().map(
                component, "2026-08-08",
                EvidenceChannel.notEvaluated("Descriptor calculation was not requested"),
                EvidenceChannel.notEvaluated("Normalization was not requested"),
                EvidenceChannel.notEvaluated("Vectorization was not requested"));

        assertEquals("LIG", result.componentId());
        assertEquals("2026-08-08", result.ccdVersion());
        assertEquals(-1, result.atoms().get(1).formalCharge());
        assertEquals(BondOrder.DOUBLE, result.bonds().getFirst().order());
        assertEquals(List.of(new Point3D(4, 5, 6)),
                result.idealCoordinates().stream().map(c -> c.position()).toList());
        assertEquals(EvidenceChannel.NotEvaluated.class,
                result.molecularDescriptors().getClass());
    }
}
