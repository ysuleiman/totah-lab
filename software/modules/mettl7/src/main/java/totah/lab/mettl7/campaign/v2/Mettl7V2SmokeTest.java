package totah.lab.mettl7.campaign.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import totah.lab.athena.ligand.interaction.DefaultLigandInteractionAnalyzer;
import totah.lab.athena.tmt.NearAttackGeometry;
import totah.lab.daedalus.docking.DockingInput;
import totah.lab.daedalus.docking.PocketGridBox;
import totah.lab.daedalus.docking.VinaDockingOptions;
import totah.lab.daedalus.docking.VinaDockingRunner;
import totah.lab.daedalus.docking.VinaExecutionOptions;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Executes the matched WT plumbing smoke pair; outputs are non-biological. */
public final class Mettl7V2SmokeTest {
    private Mettl7V2SmokeTest() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException(
                    "Usage: <repo-root> <vina> <output-root> <cpu-per-job>");
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path vina = Path.of(args[1]).toAbsolutePath().normalize();
        Path output = Path.of(args[2]).toAbsolutePath().normalize();
        int cpuPerJob = Integer.parseInt(args[3]);
        Files.createDirectories(output);

        List<Callable<Map<String, Object>>> tasks = List.of(
                () -> run(root, vina, output, "A0", "METTL7A", cpuPerJob),
                () -> run(root, vina, output, "B0", "METTL7B", cpuPerJob));
        List<Map<String, Object>> receipts = new ArrayList<>();
        Instant started = Instant.now();
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (var future : executor.invokeAll(tasks)) receipts.add(future.get());
        }
        boolean pass = receipts.stream().allMatch(row -> "PASS".equals(row.get("status")));
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("test_id", "METTL7_V2_MATCHED_WT_PLUMBING_SMOKE");
        receipt.put("biological_interpretation_authorized", false);
        receipt.put("status", pass ? "PASS" : "FAIL");
        receipt.put("cpu_per_job", cpuPerJob);
        receipt.put("concurrency", 2);
        receipt.put("wall_seconds", Duration.between(started, Instant.now()).toMillis() / 1000.0);
        receipt.put("runs", receipts);
        new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                .writeValue(output.resolve("smoke_receipt.json").toFile(), receipt);
        if (!pass) throw new IOException("V2 smoke test failed; see smoke_receipt.json");
        System.out.println("SMOKE_TEST=PASS");
    }

    private static Map<String, Object> run(Path root, Path vina, Path output,
            String receptorId, String paralog, int cpuPerJob) throws Exception {
        Instant started = Instant.now();
        Path receptor = root.resolve("analysis/mettl7/mechanistic_matrix_v2/receptors/")
                .resolve(receptorId + "_SAM_BOUND.pdbqt");
        Path ligandPath = root.resolve("analysis/mettl7/mechanistic_matrix_v2/ligands/prepared/CAPTOPRIL_RSH.pdbqt");
        Path runDirectory = output.resolve(receptorId + "__CAPTOPRIL_RSH__s1");
        Files.createDirectories(runDirectory);
        Path poses = runDirectory.resolve("poses.pdbqt");
        PocketGridBox box = paralog.equals("METTL7A")
                ? Mettl7NativeDockingWindows.mettl7a()
                : Mettl7NativeDockingWindows.mettl7b();
        VinaDockingOptions options = new VinaDockingOptions(
                box.center().x(), box.center().y(), box.center().z(),
                box.size().x(), box.size().y(), box.size().z(),
                Mettl7MechanisticMatrixV2Protocol.EXHAUSTIVENESS, 1);
        var result = new VinaDockingRunner(vina).run(
                new DockingInput(receptor, ligandPath, Optional.empty()), options,
                Mettl7MechanisticMatrixV2Protocol.poseOutputOptions(),
                new VinaExecutionOptions(cpuPerJob), poses);
        Files.writeString(runDirectory.resolve("vina.log"), result.output());

        PdbqtReader reader = new PdbqtReader();
        Structure receptorStructure = PdbqtGaiaMapper.toStructure(reader.read(receptor));
        Ligand ligand = PdbqtGaiaMapper.toLigand(reader.read(poses).firstModel(), "CAPTOPRIL_RSH");
        var interactions = new DefaultLigandInteractionAnalyzer().analyze(receptorStructure, ligand);
        Point3D acceptor = uniqueElement(ligand.structure(), "S").getPosition();
        Point3D samMethyl = uniqueNamedSamAtom(receptorStructure, "C9").getPosition();
        Point3D samSulfur = uniqueNamedSamAtom(receptorStructure, "S8").getPosition();
        NearAttackGeometry geometry = NearAttackGeometry.from(
                acceptor, samMethyl, samSulfur, 0);

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("run_id", receptorId + "__CAPTOPRIL_RSH__s1");
        receipt.put("status", result.exitCode() == 0 && !result.poses().isEmpty()
                && Files.isRegularFile(poses) ? "PASS" : "FAIL");
        receipt.put("paralog", paralog);
        receipt.put("sam_atom_count", receptorStructure.getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .filter(residue -> residue.getName().equalsIgnoreCase("SAM"))
                .mapToInt(residue -> residue.getAtomCount()).sum());
        receipt.put("native_box_center", List.of(box.center().x(), box.center().y(), box.center().z()));
        receipt.put("native_box_size", List.of(box.size().x(), box.size().y(), box.size().z()));
        receipt.put("vina_exit_code", result.exitCode());
        receipt.put("parsed_pose_count", result.poses().size());
        receipt.put("athena_interaction_count", interactions.size());
        receipt.put("near_attack_distance_A", geometry.substrateSulfurToMethylCarbonAngstrom());
        receipt.put("near_attack_angle_deg", geometry.substrateSulfurMethylCarbonSamSulfurAngleDegrees());
        receipt.put("elapsed_seconds", Duration.between(started, Instant.now()).toMillis() / 1000.0);
        return Map.copyOf(receipt);
    }

    private static Atom uniqueNamedSamAtom(Structure structure, String name) {
        return structure.getChains().stream().flatMap(chain -> chain.residues().stream())
                .filter(residue -> residue.getName().equalsIgnoreCase("SAM"))
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(atom -> atom.getName().equals(name)).findFirst().orElseThrow();
    }

    private static Atom uniqueElement(Structure structure, String element) {
        List<Atom> atoms = structure.getChains().stream()
                .flatMap(chain -> chain.residues().stream())
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(atom -> atom.getElement().symbol().equalsIgnoreCase(element)).toList();
        if (atoms.size() != 1) {
            throw new IllegalStateException("Expected one " + element + " acceptor, found " + atoms.size());
        }
        return atoms.getFirst();
    }
}
