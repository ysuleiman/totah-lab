package totah.lab.prometheus.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.identity.CanonicalAtomMap;

/**
 * Exercises the famous TSL mapping trap: the number inside a label is NOT the
 * canonical serial. Serial 10 is labeled C9, serial 11 is labeled C10.
 */
class LegacyCanonicalAtomLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsLabelsByCanonicalIndexNotByLabelNumber() throws Exception {
        Path unit02 = Files.createDirectories(tempDir.resolve("execution-unit-02"));
        Files.writeString(unit02.resolve("canonical_atom_inventory.csv"), """
                chemical_id,canonical_atom_index,canonical_atom_id,mol2_atom_name,element,formal_charge,chiral_tag,is_explicit_hydrogen,role
                SAM,1,SAM_C001,C1,C,0,CHI_UNSPECIFIED,false,atom
                TSL_RSH,6,TSH_O006,O1,O,0,CHI_UNSPECIFIED,false,atom
                TSL_RSH,9,TSH_C009,C8,C,0,CHI_UNSPECIFIED,false,atom
                TSL_RSH,10,TSH_C010,C9,C,0,CHI_TETRAHEDRAL_CCW,false,atom
                TSL_RSH,11,TSH_C011,C10,C,0,CHI_TETRAHEDRAL_CCW,false,atom
                TSL_RSH,26,TSH_S026,S26,S,0,CHI_UNSPECIFIED,false,reactive_sulfur
                TSL_RSH,30,TSH_N030,N1,N,0,CHI_UNSPECIFIED,false,atom
                TSL_RSH,56,TSH_H056,H56,H,0,CHI_UNSPECIFIED,true,atom
                TSL_RS_MINUS,26,TSM_S026,S26,S,-1,CHI_UNSPECIFIED,false,reactive_sulfur
                """);

        CanonicalAtomMap map = LegacyCanonicalAtomLoader.load(unit02);

        assertThat(map.size()).isEqualTo(7);
        // the regression: label number != canonical serial
        assertThat(map.byIndex(9).orElseThrow().label()).isEqualTo("C8");
        assertThat(map.byIndex(10).orElseThrow().label()).isEqualTo("C9");
        assertThat(map.byIndex(11).orElseThrow().label()).isEqualTo("C10");
        assertThat(map.byIndex(26).orElseThrow().label()).isEqualTo("S26");
        assertThat(map.byIndex(26).orElseThrow().elementSymbol()).isEqualTo("S");
        assertThat(map.byIndex(56).orElseThrow().label()).isEqualTo("H56");
        // stable molecule identity, formula derived from parsed elements
        assertThat(map.molecule().moleculeId()).isEqualTo("TSL-RSH");
        assertThat(map.molecule().displayName()).isEqualTo("neutral TSL thiol");
        assertThat(map.molecule().molecularFormula()).isEqualTo("C3HNOS");
    }

    @Test
    void atomsAreSortedByCanonicalIndex() throws Exception {
        Path unit02 = Files.createDirectories(tempDir.resolve("execution-unit-02"));
        Files.writeString(unit02.resolve("canonical_atom_inventory.csv"), """
                chemical_id,canonical_atom_index,canonical_atom_id,mol2_atom_name,element,formal_charge,chiral_tag,is_explicit_hydrogen,role
                TSL_RSH,56,TSH_H056,H56,H,0,CHI_UNSPECIFIED,true,atom
                TSL_RSH,9,TSH_C009,C8,C,0,CHI_UNSPECIFIED,false,atom
                """);

        CanonicalAtomMap map = LegacyCanonicalAtomLoader.load(unit02);

        assertThat(map.atoms()).extracting("canonicalIndex").containsExactly(9, 56);
    }
}
