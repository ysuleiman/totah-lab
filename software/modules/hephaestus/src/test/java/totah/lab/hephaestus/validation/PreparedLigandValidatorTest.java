package totah.lab.hephaestus.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.ligand.DefaultLigandPreparer;
import totah.lab.hephaestus.ligand.LigandPreparationRequest;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hermes.file.reader.SdfLigand;
import totah.lab.hermes.file.reader.SdfLigandReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreparedLigandValidatorTest {

    @TempDir
    Path temporaryDirectory;

    private final PreparedLigandValidator validator = new PreparedLigandValidator();

    @Test
    void acceptsFullyPreparedLigand() throws Exception {
        PreparedLigand prepared = prepareEthanol();

        ValidationReport report = validator.validate(prepared);

        assertTrue(report.valid());
    }

    @Test
    void flagsMissingTopologyChargesTypesAndTorsionModel() throws Exception {
        SdfLigand model = ethanolModel();
        PreparedLigand unprepared = PreparedLigand.of(model.ligand());

        ValidationReport report = validator.validate(unprepared);

        assertTrue(report.hasErrors());
        assertTrue(report.issues().stream().anyMatch(
                issue -> issue.code() == ValidationCode.MISSING_TOPOLOGY));
        assertTrue(report.issues().stream().anyMatch(
                issue -> issue.code() == ValidationCode.MISSING_CHARGE_ASSIGNMENT));
        assertTrue(report.issues().stream().anyMatch(
                issue -> issue.code() == ValidationCode.MISSING_ATOM_TYPE_ASSIGNMENT));
        assertTrue(report.issues().stream().anyMatch(
                issue -> issue.code() == ValidationCode.MISSING_AD4_TYPE));
        assertTrue(report.issues().stream().anyMatch(
                issue -> issue.code() == ValidationCode.METADATA_INCONSISTENT));
    }

    @Test
    void flagsNonFiniteCharge() throws Exception {
        PreparedLigand prepared = prepareEthanol();
        List<Atom> atoms = new ArrayList<>(atomsOf(prepared.ligand()));
        Atom first = atoms.getFirst();
        atoms.set(0, first.toBuilder().charge(Double.NaN).build());

        ValidationReport report = validator.validate(
                prepared.withLigand(replaced(prepared.ligand(), atoms)));

        assertTrue(report.hasErrors());
        assertTrue(report.issues().stream().anyMatch(
                issue -> issue.code() == ValidationCode.NONFINITE_CHARGE));
    }

    @Test
    void flagsChargeTotalThatBreaksFormalCharge() throws Exception {
        PreparedLigand prepared = prepareEthanol();
        List<Atom> atoms = new ArrayList<>(atomsOf(prepared.ligand()));
        Atom first = atoms.getFirst();
        atoms.set(0, first.toBuilder().charge(first.getCharge() + 0.5).build());

        ValidationReport report = validator.validate(
                prepared.withLigand(replaced(prepared.ligand(), atoms)));

        assertTrue(report.hasErrors());
        assertTrue(report.issues().stream().anyMatch(
                issue -> issue.code() == ValidationCode.METADATA_INCONSISTENT));
    }

    @Test
    void flagsMultiChainStructures() throws Exception {
        PreparedLigand prepared = prepareEthanol();
        Ligand ligand = prepared.ligand();
        Chain chain = ligand.structure().getChains().getFirst();
        Ligand twoChains = new Ligand(
                ligand.id(), ligand.name(), ligand.componentCode().orElse(null),
                null, null, ligand.formalCharge(),
                new Structure(List.of(chain,
                        new Chain("B", chain.residues()))));

        ValidationReport report = validator.validate(
                prepared.withLigand(twoChains));

        assertFalse(report.valid());
        assertTrue(report.issues().stream().anyMatch(
                issue -> issue.code() == ValidationCode.METADATA_INCONSISTENT));
    }

    private PreparedLigand prepareEthanol() throws Exception {
        SdfLigand model = ethanolModel();
        return DefaultLigandPreparer.sdf(model)
                .prepare(new LigandPreparationRequest(model.ligand()))
                .preparedLigand();
    }

    private SdfLigand ethanolModel() throws Exception {
        StringBuilder text = new StringBuilder("ETH\n  unit-test\n\n");
        String[] symbols = {"C", "C", "O", "H", "H", "H", "H", "H", "H"};
        int[][] bonds = {{1, 2, 1}, {2, 3, 1}, {3, 4, 1}, {1, 5, 1}, {1, 6, 1},
                {1, 7, 1}, {2, 8, 1}, {2, 9, 1}};
        text.append(String.format(Locale.US, "%3d%3d  0  0  0  0  0  0  0  0999 V2000",
                symbols.length, bonds.length)).append('\n');
        for (int index = 0; index < symbols.length; index++) {
            text.append(String.format(Locale.US, "%10.4f%10.4f%10.4f %-3s 0  0  0  0  0  0",
                    index * 1.5, (index % 3) * 1.5, (index % 2) * 1.5, symbols[index]))
                    .append('\n');
        }
        for (int[] bond : bonds) {
            text.append(String.format(Locale.US, "%3d%3d%3d  0  0  0",
                    bond[0], bond[1], bond[2])).append('\n');
        }
        text.append("M  END\n$$$$\n");
        Path path = temporaryDirectory.resolve("ethanol.sdf");
        Files.writeString(path, text.toString());
        return new SdfLigandReader().readModel(path);
    }

    private List<Atom> atomsOf(Ligand ligand) {
        return ligand.structure().getChains().getFirst()
                .residues().getFirst().getAtoms();
    }

    private Ligand replaced(Ligand ligand, List<Atom> atoms) {
        Chain chain = ligand.structure().getChains().getFirst();
        Residue residue = chain.residues().getFirst();
        return new Ligand(
                ligand.id(), ligand.name(), ligand.componentCode().orElse(null),
                null, null, ligand.formalCharge(),
                new Structure(List.of(new Chain(chain.id(), List.of(
                        residue.toBuilder().atoms(atoms).build())))));
    }
}
