package totah.lab.daedalus.docking;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.hephaestus.client.HephaestusClient;
import totah.lab.hephaestus.client.HephaestusClients;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandPreparationResult;
import totah.lab.hermes.file.writer.pdbqt.validation.PdbqtValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end acceptance: SAM (S-adenosyl-L-methionine, CCD SAM) from a
 * raw SDF with explicit hydrogens, bond orders and 3D coordinates
 * (RCSB ligand-expo SAM_ideal.sdf) through SDF parsing, preparation
 * (charges, AD4 typing, torsions), validated PDBQT export, and a real
 * AutoDock Vina run against the METTL7B (Q6UX53) receptor.
 *
 * The receptor is Q6UX53_TMT1B_HUMAN_3_clean.pdbqt, the plain rigid
 * receptor PDBQT (2351 ATOM records, AD4 charges/types, no flex
 * residues). The search box is centered on the fpocket pocket-2
 * alpha-sphere centroid computed from pocket2_vert.pqr and sized
 * 24 x 26 x 24 A to enclose the pocket-2 alpha-sphere hull.
 */
class LigandDockingAcceptanceTest {

    private static final Path DEFAULT_VINA = Path.of("/Users/yazan/bin/vina");

    @TempDir
    Path temporaryDirectory;

    private final HephaestusClient client = HephaestusClients.createDefault();

    @Test
    void preparesSamFromSdfToValidDeterministicPdbqt() throws Exception {
        Path sdf = resource("/ligand/SAM.sdf");

        LigandPreparationResult first = client.prepareLigand(
                sdf, LigandPreparationOptions.defaults());
        LigandPreparationResult second = client.prepareLigand(
                sdf, LigandPreparationOptions.defaults());

        assertTrue(first.successful());
        assertTrue(client.validatePreparedLigand(first.preparedLigand()).valid());
        assertEquals(49, atomLines(export(first, "sam-1.pdbqt")).size());

        String firstPdbqt = Files.readString(export(first, "sam-1.pdbqt"));
        String secondPdbqt = Files.readString(export(second, "sam-2.pdbqt"));
        assertEquals(firstPdbqt, secondPdbqt);

        List<String> lines = firstPdbqt.lines().toList();
        List<String> atoms = lines.stream()
                .filter(line -> line.startsWith("ATOM")).toList();
        assertEquals(49, atoms.size());
        assertEquals(49, atoms.stream()
                .map(line -> line.substring(6, 11).trim()).distinct().count());
        assertTrue(atoms.stream().allMatch(line -> !line.endsWith("  ")));
        // The sulfonium sulfur (SD, atom 8) must type as S, not SA.
        assertTrue(atoms.stream().anyMatch(line ->
                line.contains(" S8 ") && line.endsWith(" S")));
        assertEquals(1, lines.stream().filter("ROOT"::equals).count());
        assertEquals(1, lines.stream().filter("ENDROOT"::equals).count());
        long branches = lines.stream().filter(line -> line.startsWith("BRANCH ")).count();
        assertEquals(branches,
                lines.stream().filter(line -> line.startsWith("ENDBRANCH ")).count());

        // SAM rotatable bonds under the pipeline rules (single, non-aromatic,
        // both ends heavy with heavy-degree > 1, non-ring, non-amide):
        // CA-C, CA-CB, CB-CG, CG-SD, SD-C5', C5'-C4' and the glycosidic
        // C1'-N9 bond. Ring bonds (ribose, adenine), double bonds
        // (carboxyl C=O, adenine), terminal bonds (SD-CE methyl, C-OH,
        // N-H of the methionine ammonium) are excluded: TORSDOF = 7.
        assertEquals(7, branches);
        assertEquals("TORSDOF 7", lines.stream()
                .filter(line -> line.startsWith("TORSDOF")).findFirst().orElseThrow());

        assertTrue(new PdbqtValidator()
                .validateLigandPdbqt(export(first, "sam-1.pdbqt")).valid());
    }

    @Test
    void docksSamIntoMettl7bPocket2WithVina() throws Exception {
        Path vina = vinaExecutable();
        Assumptions.assumeTrue(Files.exists(vina),
                "AutoDock Vina not available at " + vina);

        LigandPreparationResult prepared = client.prepareLigand(
                resource("/ligand/SAM.sdf"), LigandPreparationOptions.defaults());
        assertTrue(prepared.successful());
        Path ligandPdbqt = export(prepared, "sam.pdbqt");

        double[] centroid = pocketCentroid(
                resource("/Q6UX53/fpocket/pockets/pocket2_vert.pqr"));
        VinaDockingOptions box = new VinaDockingOptions(
                centroid[0], centroid[1], centroid[2],
                24.0, 26.0, 24.0, 8, 42);
        VinaDockingResult result = new VinaDockingRunner(vina).run(
                new DockingInput(
                        resource("/Q6UX53/Q6UX53_TMT1B_HUMAN_3_clean.pdbqt"),
                        ligandPdbqt, Optional.empty()),
                box);

        assertEquals(0, result.exitCode(), () -> result.output());
        assertTrue(result.poses().size() >= 1, () -> result.output());
        assertTrue(result.bestPose().orElseThrow().affinityKcalPerMol() < 0.0,
                () -> result.output());
    }

    private Path vinaExecutable() {
        String property = System.getProperty("vina.executable");
        if (property != null && !property.isBlank()) {
            return Path.of(property);
        }
        String environment = System.getenv("VINA_EXECUTABLE");
        if (environment != null && !environment.isBlank()) {
            return Path.of(environment);
        }
        return DEFAULT_VINA;
    }

    private List<String> atomLines(Path pdbqt) throws Exception {
        return Files.readAllLines(pdbqt).stream()
                .filter(line -> line.startsWith("ATOM")).toList();
    }

    private Path export(LigandPreparationResult result, String name) throws Exception {
        return client.writePreparedLigand(
                result.preparedLigand(), temporaryDirectory.resolve(name));
    }

    private Path resource(String name) throws Exception {
        var url = getClass().getResource(name);
        if (url == null) {
            throw new AssertionError("missing test resource " + name);
        }
        return Path.of(url.toURI());
    }

    /** Centroid of the fpocket alpha-sphere vertices of one pocket file. */
    private double[] pocketCentroid(Path vertPqr) throws Exception {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        int count = 0;
        for (String line : Files.readAllLines(vertPqr)) {
            if (!line.startsWith("ATOM")) {
                continue;
            }
            String[] tokens = line.trim().split("\\s+");
            x += Double.parseDouble(tokens[5]);
            y += Double.parseDouble(tokens[6]);
            z += Double.parseDouble(tokens[7]);
            count++;
        }
        if (count == 0) {
            throw new AssertionError("no alpha spheres in " + vertPqr);
        }
        return new double[]{x / count, y / count, z / count};
    }
}
