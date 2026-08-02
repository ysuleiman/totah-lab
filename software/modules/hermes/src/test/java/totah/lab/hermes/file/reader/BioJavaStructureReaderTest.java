package totah.lab.hermes.file.reader;

import org.biojava.nbio.structure.AminoAcidImpl;
import org.biojava.nbio.structure.AtomImpl;
import org.biojava.nbio.structure.ChainImpl;
import org.biojava.nbio.structure.Group;
import org.biojava.nbio.structure.HetatomImpl;
import org.biojava.nbio.structure.ResidueNumber;
import org.biojava.nbio.structure.StructureImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.classification.ClassificationSource;
import totah.lab.gaia.classification.ResidueClassification;
import totah.lab.gaia.classification.ResidueClassificationEvidence;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.structure.StructureReaderOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BioJavaStructureReaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void shouldRejectNullOptions() {
        assertThrows(
                NullPointerException.class,
                () -> new BioJavaStructureReader(null));
    }

    @Test
    void shouldRejectNullPath() {
        BioJavaStructureReader reader =
                new BioJavaStructureReader();

        assertThrows(
                NullPointerException.class,
                () -> reader.read(null));
    }

    @Test
    void shouldRejectMissingFile() {
        BioJavaStructureReader reader =
                new BioJavaStructureReader();

        Path missingFile =
                temporaryDirectory.resolve("missing.pdb");

        IOException exception = assertThrows(
                IOException.class,
                () -> reader.read(missingFile));

        assertTrue(
                exception.getMessage()
                        .contains("does not exist"));
    }

    @Test
    void shouldRejectDirectoryPath() {
        BioJavaStructureReader reader =
                new BioJavaStructureReader();

        IOException exception = assertThrows(
                IOException.class,
                () -> reader.read(temporaryDirectory));

        assertTrue(
                exception.getMessage()
                        .contains("not a regular file"));
    }

    @Test
    void shouldRejectUnsupportedFormat() throws IOException {
        BioJavaStructureReader reader =
                new BioJavaStructureReader();

        Path file =
                temporaryDirectory.resolve("structure.xyz");

        Files.writeString(file, "unsupported");

        IOException exception = assertThrows(
                IOException.class,
                () -> reader.read(file));

        assertTrue(
                exception.getMessage()
                        .contains("Unsupported structure format"));
    }

    @Test
    void shouldReportSupportedExtensions() {
        BioJavaStructureReader reader =
                new BioJavaStructureReader();

        assertTrue(reader.supports(Path.of("protein.pdb")));
        assertTrue(reader.supports(Path.of("protein.PDB")));
        assertTrue(reader.supports(Path.of("protein.cif")));
        assertTrue(reader.supports(Path.of("protein.mmcif")));

        assertFalse(reader.supports(Path.of("protein.sdf")));
        assertFalse(reader.supports(Path.of("protein.pdbqt")));
        assertFalse(reader.supports(null));
    }

    @Test
    void shouldReadPdbStructure() throws IOException {
        Path pdbFile = writePdb(
                "simple.pdb",
                """
                ATOM      1  N   ALA A   1      11.104  13.207   9.346  1.00 91.00           N
                ATOM      2  CA  ALA A   1      12.560  13.300   9.200  1.00 92.00           C
                ATOM      3  C   ALA A   1      13.105  11.910   8.850  1.00 93.00           C
                ATOM      4  O   ALA A   1      12.480  10.860   9.050  1.00 94.00           O
                TER
                END
                """);

        BioJavaStructureReader reader =
                new BioJavaStructureReader(
                        StructureReaderOptions.defaults());

        Structure structure = reader.read(pdbFile);

        assertNotNull(structure);
        assertEquals(1, structure.getChainCount());
        assertEquals(1, structure.getResidueCount());
        assertEquals(4, structure.getAtomCount());

        Chain chain = structure.getChains().getFirst();

        assertEquals("A", chain.id());
        assertEquals(1, chain.residueCount());

        Residue residue = chain.residues().getFirst();

        assertEquals("ALA", residue.getName());
        assertEquals(1, residue.getNumber());
        assertNull(residue.getInsertionCode());
        assertEquals(4, residue.getAtomCount());
    }

    @Test
    void shouldReadAtomProperties() throws IOException {
        Path pdbFile = writePdb(
                "atom-properties.pdb",
                """
                ATOM     42  CA  GLY B   7       1.250   2.500   3.750  0.75 88.50           C
                TER
                END
                """);

        Structure structure =
                new BioJavaStructureReader().read(pdbFile);

        Atom atom = structure.getChains()
                .getFirst()
                .residues()
                .getFirst()
                .getAtoms()
                .getFirst();

        assertEquals(42, atom.getPdbSerial());
        assertEquals("CA", atom.getName());

        assertEquals(1.250, atom.getPosition().x(), 1.0e-9);
        assertEquals(2.500, atom.getPosition().y(), 1.0e-9);
        assertEquals(3.750, atom.getPosition().z(), 1.0e-9);

        assertEquals(0.75, atom.getOccupancy(), 1.0e-6);
        assertEquals(88.50, atom.getBFactor(), 1.0e-6);
        assertEquals(0.0, atom.getCharge(), 1.0e-9);
        assertNull(atom.getAmberType());
        assertEquals("C", atom.getElement().symbol());
    }

    @Test
    void shouldPreserveInsertionCode() throws IOException {
        Path pdbFile = writePdb(
                "insertion-code.pdb",
                """
                ATOM      1  N   SER A  10A      1.000   2.000   3.000  1.00 80.00           N
                ATOM      2  CA  SER A  10A      2.000   2.000   3.000  1.00 80.00           C
                ATOM      3  C   SER A  10A      3.000   2.000   3.000  1.00 80.00           C
                TER
                END
                """);

        Structure structure =
                new BioJavaStructureReader().read(pdbFile);

        Residue residue = structure.getChains()
                .getFirst()
                .residues()
                .getFirst();

        assertEquals(10, residue.getNumber());
        assertEquals('A', residue.getInsertionCode());
    }

    @Test
    void shouldCreateSeparateChains() throws IOException {
        Path pdbFile = writePdb(
                "multiple-chains.pdb",
                """
                ATOM      1  N   ALA A   1       1.000   1.000   1.000  1.00 90.00           N
                ATOM      2  CA  ALA A   1       2.000   1.000   1.000  1.00 90.00           C
                TER
                ATOM      3  N   GLY B   1       5.000   5.000   5.000  1.00 90.00           N
                ATOM      4  CA  GLY B   1       6.000   5.000   5.000  1.00 90.00           C
                TER
                END
                """);

        Structure structure =
                new BioJavaStructureReader().read(pdbFile);

        assertEquals(2, structure.getChainCount());
        assertEquals(2, structure.getResidueCount());
        assertEquals(4, structure.getAtomCount());

        assertEquals(
                List.of("A", "B"),
                structure.getChains()
                        .stream()
                        .map(Chain::id)
                        .toList());
    }

    @Test
    void shouldMergePolymerAndWaterPartitionsWithSameChainIdentifier()
            throws IOException {
        org.biojava.nbio.structure.Structure bioStructure = bioStructure(
                bioChain("A", bioGroup("ALA", 1, true)),
                bioChain("A", bioGroup("HOH", 2, false)));

        Structure structure = new BioJavaStructureReader().convertStructure(
                bioStructure, Path.of("synthetic.pdb"));

        assertEquals(1, structure.getChainCount());
        assertEquals("A", structure.getChains().getFirst().id());
        assertEquals(List.of("ALA", "HOH"), residueNames(structure.getChains().getFirst()));
    }

    @Test
    void shouldMergePolymerLigandAndWaterPartitionsWithoutLosingOrder()
            throws IOException {
        org.biojava.nbio.structure.Structure bioStructure = bioStructure(
                bioChain("A", bioGroup("GLY", 1, true)),
                bioChain("A", bioGroup("LIG", 1, false)),
                bioChain("A", bioGroup("HOH", 1, false)));

        Structure structure = new BioJavaStructureReader().convertStructure(
                bioStructure, Path.of("synthetic.pdb"));

        assertEquals(1, structure.getChainCount());
        assertEquals(List.of("GLY", "LIG", "HOH"),
                residueNames(structure.getChains().getFirst()));
    }

    @Test
    void shouldPreserveFirstSeenOrderWhileMergingMultipleBiologicalChains()
            throws IOException {
        org.biojava.nbio.structure.Structure bioStructure = bioStructure(
                bioChain("A", bioGroup("ALA", 1, true)),
                bioChain("A", bioGroup("HOH", 2, false)),
                bioChain("B", bioGroup("GLY", 1, true)),
                bioChain("B", bioGroup("LIG", 3, false)));

        Structure structure = new BioJavaStructureReader().convertStructure(
                bioStructure, Path.of("synthetic.pdb"));

        assertEquals(List.of("A", "B"),
                structure.getChains().stream().map(Chain::id).toList());
        assertEquals(List.of("ALA", "HOH"), residueNames(structure.getChains().get(0)));
        assertEquals(List.of("GLY", "LIG"), residueNames(structure.getChains().get(1)));
    }

    @Test
    void shouldNotConvertTheExactSameBioJavaGroupTwice() throws IOException {
        Group sharedWater = bioGroup("HOH", 9, false);
        org.biojava.nbio.structure.Structure bioStructure = bioStructure(
                bioChain("A", bioGroup("ALA", 1, true), sharedWater),
                bioChain("A", sharedWater));

        Structure structure = new BioJavaStructureReader().convertStructure(
                bioStructure, Path.of("synthetic.pdb"));

        assertEquals(List.of("ALA", "HOH"), residueNames(structure.getChains().getFirst()));
    }

    @Test
    void shouldWrapModelConversionFailureAsIOException() {
        org.biojava.nbio.structure.Structure bioStructure = bioStructure(
                bioChain("A", bioGroup(" ", 1, false)));

        IOException exception = assertThrows(IOException.class,
                () -> new BioJavaStructureReader().convertStructure(
                        bioStructure, Path.of("malformed.pdb")));

        assertTrue(exception.getMessage().startsWith(
                "Failed to convert structure from malformed.pdb:"));
        assertInstanceOf(IllegalArgumentException.class, exception.getCause());
    }

    @Test
    void shouldReadPolymerHeterogenAndWaterSharingOnePdbChain() throws IOException {
        Path pdbFile = writePdb("split-chain-regression.pdb", """
                ATOM      1  N   ALA A   1       1.000   1.000   1.000  1.00 90.00           N
                ATOM      2  CA  ALA A   1       2.000   1.000   1.000  1.00 90.00           C
                HETATM    3  C1  LIG A 101       4.000   1.000   1.000  1.00 30.00           C
                HETATM    4  O   HOH A 201       6.000   1.000   1.000  1.00 20.00           O
                TER
                END
                """);

        Structure structure = assertDoesNotThrow(
                () -> new BioJavaStructureReader().read(pdbFile));

        assertEquals(1, structure.getChainCount());
        assertEquals("A", structure.getChains().getFirst().id());
        assertEquals(List.of("ALA", "LIG", "HOH"),
                residueNames(structure.getChains().getFirst()));
    }

    @Test
    void shouldReadRealPdbsWhoseBioJavaChainsAreCategoryPartitioned() {
        Map<String, List<String>> expectedChains = Map.of(
                "1A4W", List.of("L", "H", "I"),
                "1G9V", List.of("A", "B", "C", "D"),
                "4HVP", List.of("A", "B"),
                "1HVR", List.of("A", "B"));

        expectedChains.forEach((pdbId, chains) -> {
            Structure structure = assertDoesNotThrow(() ->
                    new BioJavaStructureReader().read(realPdbFixture(pdbId)));
            assertEquals(chains,
                    structure.getChains().stream().map(Chain::id).toList(), pdbId);
            assertTrue(structure.getChains().stream()
                    .flatMap(chain -> chain.residues().stream())
                    .anyMatch(residue -> residue.getClassificationEvidence().stream()
                            .anyMatch(evidence -> evidence.classification()
                                    == ResidueClassification.STANDARD_AMINO_ACID)), pdbId);
        });

        for (String pdbId : List.of("1A4W", "1G9V")) {
            Structure structure = assertDoesNotThrow(() ->
                    new BioJavaStructureReader().read(realPdbFixture(pdbId)));
            assertTrue(hasClassification(structure, ResidueClassification.HETERO), pdbId);
            assertTrue(hasClassification(structure, ResidueClassification.WATER), pdbId);
        }
    }

    @Test
    void shouldSelectHighestOccupancyAlternateLocation()
            throws IOException {

        Path pdbFile = writePdb(
                "alternate-location.pdb",
                """
                ATOM      1  N   SER A   1       1.000   1.000   1.000  1.00 90.00           N
                ATOM      2  CA ASER A   1       2.000   2.000   2.000  0.40 90.00           C
                ATOM      3  CA BSER A   1       8.000   8.000   8.000  0.60 90.00           C
                ATOM      4  C   SER A   1       3.000   1.000   1.000  1.00 90.00           C
                TER
                END
                """);

        Structure structure =
                new BioJavaStructureReader().read(pdbFile);

        Residue residue = structure.getChains()
                .getFirst()
                .residues()
                .getFirst();

        Atom alphaCarbon =
                residue.findAtom("CA").orElseThrow();

        assertEquals(8.000, alphaCarbon.getPosition().x(), 1.0e-9);
        assertEquals(8.000, alphaCarbon.getPosition().y(), 1.0e-9);
        assertEquals(8.000, alphaCarbon.getPosition().z(), 1.0e-9);

        assertEquals(
                1,
                residue.getAtoms()
                        .stream()
                        .filter(atom -> "CA".equals(atom.getName()))
                        .count());
    }

    @Test
    void shouldPreferAltLocAWhenOccupancyIsEqual()
            throws IOException {

        Path pdbFile = writePdb(
                "alternate-location-tie.pdb",
                """
                ATOM      1  N   SER A   1       1.000   1.000   1.000  1.00 90.00           N
                ATOM      2  CA BSER A   1       8.000   8.000   8.000  0.50 90.00           C
                ATOM      3  CA ASER A   1       2.000   2.000   2.000  0.50 90.00           C
                ATOM      4  C   SER A   1       3.000   1.000   1.000  1.00 90.00           C
                TER
                END
                """);

        Structure structure =
                new BioJavaStructureReader().read(pdbFile);

        Atom alphaCarbon = structure.getChains()
                .getFirst()
                .residues()
                .getFirst()
                .findAtom("CA")
                .orElseThrow();

        assertEquals(2.000, alphaCarbon.getPosition().x(), 1.0e-9);
    }

    @Test
    void shouldAttachClassificationEvidence() throws IOException {
        Path pdbFile = writePdb(
                "classification.pdb",
                """
                ATOM      1  N   ALA A   1       1.000   1.000   1.000  1.00 90.00           N
                ATOM      2  CA  ALA A   1       2.000   1.000   1.000  1.00 90.00           C
                ATOM      3  C   ALA A   1       3.000   1.000   1.000  1.00 90.00           C
                TER
                END
                """);

        Structure structure =
                new BioJavaStructureReader().read(pdbFile);

        Residue residue = structure.getChains()
                .getFirst()
                .residues()
                .getFirst();

        assertFalse(
                residue.getClassificationEvidence().isEmpty());

        ResidueClassificationEvidence evidence =
                residue.getClassificationEvidence().getFirst();

        assertEquals(
                ResidueClassification.STANDARD_AMINO_ACID,
                evidence.classification());

        assertEquals(
                ClassificationSource.CCD,
                evidence.source());
    }

    @Test
    void shouldDispatchCifRegardlessOfDefaultLocale() throws IOException {
        Path cifFile = temporaryDirectory.resolve("turkish-locale.CIF");
        Files.writeString(cifFile, """
                data_test
                #
                loop_
                _atom_site.group_PDB
                _atom_site.id
                _atom_site.type_symbol
                _atom_site.label_atom_id
                _atom_site.label_comp_id
                _atom_site.label_asym_id
                _atom_site.label_seq_id
                _atom_site.Cartn_x
                _atom_site.Cartn_y
                _atom_site.Cartn_z
                _atom_site.occupancy
                _atom_site.B_iso_or_equiv
                _atom_site.pdbx_PDB_model_num
                ATOM 1 N N ALA A 1 1.000 2.000 3.000 1.00 90.00 1
                ATOM 2 C CA ALA A 1 2.000 3.000 4.000 1.00 90.00 1
                #
                """);

        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            Structure structure =
                    new BioJavaStructureReader().read(cifFile);

            assertEquals(1, structure.getResidueCount());
            assertEquals(2, structure.getAtomCount());
        } finally {
            Locale.setDefault(previous);
        }
    }

    private Path writePdb(
            String fileName,
            String contents) throws IOException {

        Path path = temporaryDirectory.resolve(fileName);

        Files.writeString(
                path,
                normalizePdb(contents));

        return path;
    }

    private org.biojava.nbio.structure.Structure bioStructure(
            org.biojava.nbio.structure.Chain... chains) {
        StructureImpl structure = new StructureImpl();
        structure.setChains(List.of(chains));
        return structure;
    }

    private org.biojava.nbio.structure.Chain bioChain(
            String identifier, Group... groups) {
        ChainImpl chain = new ChainImpl();
        chain.setName(identifier);
        chain.setId(identifier);
        for (Group group : groups) {
            chain.addGroup(group);
        }
        return chain;
    }

    private Group bioGroup(String name, int number, boolean polymer) {
        Group group = polymer ? new AminoAcidImpl() : new HetatomImpl();
        group.setPDBName(name);
        group.setResidueNumber(new ResidueNumber("A", number, null));
        AtomImpl atom = new AtomImpl();
        atom.setName(polymer ? "CA" : "C1");
        atom.setPDBserial(number);
        atom.setCoords(new double[]{number, 0.0, 0.0});
        atom.setOccupancy(1.0f);
        atom.setTempFactor(0.0f);
        atom.setElement(org.biojava.nbio.structure.Element.C);
        group.addAtom(atom);
        return group;
    }

    private List<String> residueNames(Chain chain) {
        return chain.residues().stream().map(Residue::getName).toList();
    }

    private boolean hasClassification(
            Structure structure, ResidueClassification classification) {
        return structure.getChains().stream().flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getClassificationEvidence().stream())
                .anyMatch(evidence -> evidence.classification() == classification);
    }

    private Path realPdbFixture(String pdbId) {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path fixture = directory.resolve(
                    "resources/shared-resources/src/main/resources/pipeline/"
                            + pdbId + ".pdb");
            if (Files.isRegularFile(fixture)) {
                return fixture;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Cannot locate real PDB fixture " + pdbId);
    }

    private String normalizePdb(String contents) {
        return contents.stripIndent()
                .lines()
                .map(String::stripTrailing)
                .reduce(
                        new StringBuilder(),
                        (builder, line) ->
                                builder.append(line)
                                        .append(System.lineSeparator()),
                        StringBuilder::append)
                .toString();
    }
}
