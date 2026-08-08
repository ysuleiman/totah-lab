package totah.lab.hermes.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.hermes.file.mmcif.BoundComponentAtom;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LigandInventoryJsonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void inventoryRoundTripsThroughJson() throws Exception {
        BoundComponentAtom atom = new BoundComponentAtom(
                "C1", "C1", "C", new Point3D(1.234, 2.345, 3.456),
                0.85, 12.0, null, null, "1");
        BoundComponentOccurrence occurrence = new BoundComponentOccurrence(
                "1EH6",
                BoundComponentOccurrence.SourceKind.ENTRY,
                null,
                1,
                "SAM",
                "B",
                null,
                "A",
                "501",
                null,
                List.of(atom));
        ComponentInventory component = new ComponentInventory(
                "SAM",
                LigandClassification.COFACTOR,
                List.of(occurrence),
                Path.of("ligands/SAM/SAM.cif").toAbsolutePath(),
                Path.of("ligands/SAM/SAM_ideal.sdf").toAbsolutePath());
        LigandInventory inventory = new LigandInventory(
                Map.of("SAM", component),
                1,
                Map.of(LigandClassification.COFACTOR, 1));

        String json = mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(inventory);
        LigandInventory restored =
                mapper.readValue(json, LigandInventory.class);

        assertThat(restored).isEqualTo(inventory);
        assertThat(restored.components().get("SAM").occurrences().getFirst()
                .atoms().getFirst().position())
                .isEqualTo(new Point3D(1.234, 2.345, 3.456));
        assertThat(json).contains("ligands/SAM/SAM.cif");
    }

    @Test
    void nullableDownloadPathsRoundTrip() throws Exception {
        ComponentInventory component = new ComponentInventory(
                "HOH",
                LigandClassification.SOLVENT,
                List.of(),
                null,
                null);
        LigandInventory inventory = new LigandInventory(
                Map.of("HOH", component), 0, Map.of());

        LigandInventory restored = mapper.readValue(
                mapper.writeValueAsString(inventory), LigandInventory.class);

        assertThat(restored).isEqualTo(inventory);
        assertThat(restored.components().get("HOH").ccdCif()).isNull();
    }
}
