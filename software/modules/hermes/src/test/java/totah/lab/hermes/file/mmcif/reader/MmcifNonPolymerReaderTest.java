package totah.lab.hermes.file.mmcif.reader;

import totah.lab.hermes.file.mmcif.BoundComponentAtom;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MmcifNonPolymerReaderTest {

    private static final String CIF = """
            data_9XYZ
            #
            loop_
            _atom_site.group_PDB
            _atom_site.id
            _atom_site.type_symbol
            _atom_site.label_atom_id
            _atom_site.label_alt_id
            _atom_site.label_comp_id
            _atom_site.label_asym_id
            _atom_site.label_entity_id
            _atom_site.label_seq_id
            _atom_site.pdbx_PDB_ins_code
            _atom_site.Cartn_x
            _atom_site.Cartn_y
            _atom_site.Cartn_z
            _atom_site.occupancy
            _atom_site.B_iso_or_equiv
            _atom_site.pdbx_formal_charge
            _atom_site.auth_seq_id
            _atom_site.auth_comp_id
            _atom_site.auth_asym_id
            _atom_site.auth_atom_id
            _atom_site.pdbx_PDB_model_num
            ATOM   1 N N  . ALA A 1 1 ? 10.0 11.0 12.0 1.00 20.0 ? 1   ALA A N  1
            ATOM   2 C CA . ALA A 1 1 ? 11.0 12.0 13.0 1.00 20.0 ? 1   ALA A CA 1
            HETATM 3 C C1 . LIG B 2 . A 1.234 2.345 3.456 0.85 15.0 ? 100 LIG X C1 1
            HETATM 4 C C2 . LIG B 2 . A 2.234 3.345 4.456 0.85 15.0 ? 100 LIG X C2 1
            HETATM 5 O O1 . LIG C 3 . ? 9.999 8.888 7.777 1.00 18.0 ? 101 LIG Y O1 1
            HETATM 6 O O  . HOH D 4 . ? 5.000 6.000 7.000 1.00 25.0 ? 200 HOH Z O 1
            HETATM 7 O O  . HOH D 4 . ? 6.000 7.000 8.000 0.50 26.0 ? 201 HOH Z O 1
            #
            """;

    @TempDir
    Path tempDir;

    private final MmcifNonPolymerReader reader = new MmcifNonPolymerReader();

    @Test
    void extractsHetatmOccurrencesWithVerbatimCoordinates() throws IOException {
        Path cif = writeCif(CIF);

        List<BoundComponentOccurrence> occurrences = reader.read(
                cif, "9XYZ", BoundComponentOccurrence.SourceKind.ENTRY, null);

        // Two LIG instances plus two separately identified water residues.
        assertThat(occurrences).hasSize(4);

        BoundComponentOccurrence ligB = occurrences.get(0);
        assertThat(ligB.pdbId()).isEqualTo("9XYZ");
        assertThat(ligB.sourceKind())
                .isEqualTo(BoundComponentOccurrence.SourceKind.ENTRY);
        assertThat(ligB.componentId()).isEqualTo("LIG");
        assertThat(ligB.asymId()).isEqualTo("B");
        assertThat(ligB.authAsymId()).isEqualTo("X");
        assertThat(ligB.insertionCode()).isEqualTo("A");
        assertThat(ligB.atoms()).hasSize(2);

        BoundComponentAtom first = ligB.atoms().getFirst();
        assertThat(first.name()).isEqualTo("C1");
        assertThat(first.element()).isEqualTo("C");
        assertThat(first.position().x()).isEqualTo(1.234);
        assertThat(first.position().y()).isEqualTo(2.345);
        assertThat(first.position().z()).isEqualTo(3.456);
        assertThat(first.occupancy()).isEqualTo(0.85);
    }

    @Test
    void keepsWatersWithDifferentAuthorResidueIdsSeparate() throws IOException {
        Path cif = writeCif(CIF);

        List<BoundComponentOccurrence> occurrences = reader.read(
                cif, "9XYZ", BoundComponentOccurrence.SourceKind.ENTRY, null);

        List<BoundComponentOccurrence> waters = occurrences.stream()
                .filter(occurrence -> occurrence.componentId().equals("HOH"))
                .toList();
        assertThat(waters).hasSize(2);
        assertThat(waters).extracting(BoundComponentOccurrence::authSequenceId)
                .containsExactly("200", "201");
        assertThat(waters.get(1).atoms().getFirst().occupancy()).isEqualTo(0.5);
    }

    @Test
    void toleratesAssemblyStyleExtraColumns() throws IOException {
        String assemblyCif = """
                data_9XYZ
                #
                loop_
                _atom_site.group_PDB
                _atom_site.id
                _atom_site.type_symbol
                _atom_site.label_atom_id
                _atom_site.label_alt_id
                _atom_site.label_comp_id
                _atom_site.label_asym_id
                _atom_site.label_seq_id
                _atom_site.Cartn_x
                _atom_site.Cartn_y
                _atom_site.Cartn_z
                _atom_site.occupancy
                _atom_site.pdbx_PDB_oper_id
                _atom_site.auth_asym_id
                HETATM 1 ZN ZN . ZN B . -0.840 22.307 15.551 0.70 1 A
                #
                """;
        Path cif = writeCif(assemblyCif);

        List<BoundComponentOccurrence> occurrences = reader.read(
                cif, "9XYZ", BoundComponentOccurrence.SourceKind.ASSEMBLY, "1");

        assertThat(occurrences).hasSize(1);
        BoundComponentOccurrence zinc = occurrences.getFirst();
        assertThat(zinc.componentId()).isEqualTo("ZN");
        assertThat(zinc.sourceKind())
                .isEqualTo(BoundComponentOccurrence.SourceKind.ASSEMBLY);
        assertThat(zinc.assemblyId()).isEqualTo("1");
        assertThat(zinc.atoms().getFirst().position().z()).isEqualTo(15.551);
        assertThat(zinc.atoms().getFirst().occupancy()).isEqualTo(0.7);
    }

    private Path writeCif(String content) throws IOException {
        Path cif = tempDir.resolve("9XYZ.cif");
        Files.writeString(cif, content);
        return cif;
    }
}
