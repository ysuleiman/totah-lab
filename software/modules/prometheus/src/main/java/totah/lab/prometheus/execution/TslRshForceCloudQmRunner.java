package totah.lab.prometheus.execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import totah.lab.prometheus.recovery.ArtifactChecksums;

/** Java scientific controller for the immutable 60-point TSL-RSH force cloud. */
public final class TslRshForceCloudQmRunner {
    private static final String EXECUTOR_ID = ExternalPythonExecutionPolicy.HARDENED_TSLRSH_WORKER;
    private static final String PROTOCOL = "f5074b2774fb757201d9a43eba4f63d4a5f33d6cc72d420fadd1919be9ede396";
    private static final String BACKEND = "PYSCF_NUMERICAL_WORKER";
    private static final String BACKEND_VERSION = "2.14.0";
    private static final ObjectMapper JSON = new ObjectMapper();

    private TslRshForceCloudQmRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 5 || args.length > 7) {
            throw new IllegalArgumentException("usage: <qualify|campaign> <repo> <python> <force-cloud> <output> [jobs threads]");
        }
        ExternalPythonExecutionPolicy.requireAuthorizedNumericalWorker(EXECUTOR_ID);
        String mode = args[0];
        Path repo = Path.of(args[1]).toAbsolutePath().normalize();
        Path python = Path.of(args[2]).toAbsolutePath().normalize();
        Path cloud = Path.of(args[3]).toAbsolutePath().normalize();
        Path output = Path.of(args[4]).toAbsolutePath().normalize();
        Path worker = repo.resolve("software/modules/prometheus/scripts/run_hardened_pyscf_energy_gradient.py");
        verifyFrozenInputs(cloud);
        List<String> elements = readElements(cloud.resolve("ATOM_ORDER.csv"));
        String atomOrder = ArtifactChecksums.sha256(cloud.resolve("ATOM_ORDER.csv"));
        if (mode.equals("qualify")) {
            qualify(repo, python, worker, output, elements, atomOrder);
        } else if (mode.equals("campaign")) {
            if (args.length != 7) throw new IllegalArgumentException("campaign requires jobs and threads");
            requireQualified(output);
            campaign(python, worker, cloud, output, elements, atomOrder,
                    Integer.parseInt(args[5]), Integer.parseInt(args[6]));
        } else throw new IllegalArgumentException("unknown mode: " + mode);
    }

    private static void qualify(Path repo, Path python, Path worker, Path output,
            List<String> elements, String atomOrder) throws Exception {
        Path reference = repo.resolve("analysis/mettl7-phase2/execution-unit-05O/qm-native-minima/MIN01");
        Path geometry = reference.resolve("final.xyz");
        Path directory = output.resolve("qualification/MIN01");
        Result result = execute(python, worker, directory, "QUAL-MIN01", "QUALIFICATION",
                geometry, ArtifactChecksums.sha256(geometry), elements, atomOrder, 2, 3000);
        JsonNode trusted = JSON.readTree(reference.resolve("result.json").toFile());
        double energyError = Math.abs(result.json.path("energy_hartree").asDouble()
                - trusted.path("energy_hartree").asDouble());
        double[][] expected = readMatrix(reference.resolve("final_gradient_hartree_per_bohr.txt"), elements.size());
        double gradientError = maxError(expected, result.json.path("gradient_hartree_per_bohr"));
        boolean passed = energyError <= 1.0e-9 && gradientError <= 1.0e-8;
        ObjectNode report = JSON.createObjectNode();
        report.put("status", passed ? "QUALIFIED" : "FAILED_REPRODUCTION");
        report.put("external_qm_backend_qualified", passed);
        report.put("qm_launch_ready", passed);
        report.put("reference_geometry_sha256", ArtifactChecksums.sha256(geometry));
        report.put("protocol_sha256", PROTOCOL);
        report.put("result_identity", result.json.path("result_identity").asText());
        report.put("energy_absolute_error_hartree", energyError);
        report.put("gradient_max_absolute_error_hartree_per_bohr", gradientError);
        report.put("energy_tolerance_hartree", 1.0e-9);
        report.put("gradient_tolerance_hartree_per_bohr", 1.0e-8);
        report.put("qualified_at", Instant.now().toString());
        writeJson(output.resolve("QUALIFICATION_REPORT.json"), report);
        Files.writeString(output.resolve("QUALIFICATION_REPORT.sha256"),
                ArtifactChecksums.sha256(output.resolve("QUALIFICATION_REPORT.json"))
                        + "  QUALIFICATION_REPORT.json\n", StandardCharsets.UTF_8);
        if (!passed) throw new IOException("hardened backend failed trusted-result reproduction; campaign forbidden");
    }

    private static void campaign(Path python, Path worker, Path cloud, Path output,
            List<String> elements, String atomOrder, int jobs, int threads) throws Exception {
        if (jobs < 1 || threads < 1 || jobs * threads > 8) throw new IOException("unsafe concurrency configuration");
        List<Snapshot> snapshots = readManifest(cloud);
        if (snapshots.size() != 60) throw new IOException("frozen manifest does not contain 60 snapshots");
        try (var pool = Executors.newFixedThreadPool(jobs)) {
            var futures = snapshots.stream().map(snapshot -> pool.submit(() -> execute(python, worker,
                    output.resolve("calculations").resolve(snapshot.id), snapshot.id, snapshot.split,
                    cloud.resolve(snapshot.geometryPath), snapshot.geometrySha, elements, atomOrder,
                    threads, 3500))).toList();
            for (var future : futures) future.get();
        }
        freeze(output, snapshots, atomOrder);
    }

    private static Result execute(Path python, Path worker, Path directory, String id, String role,
            Path geometry, String geometrySha, List<String> elements, String atomOrder,
            int threads, int memoryMb) throws Exception {
        if (!ArtifactChecksums.sha256(geometry).equals(geometrySha)) throw new IOException("geometry checksum mismatch: " + id);
        Files.createDirectories(directory);
        Path specPath = directory.resolve("calculation_specification.json");
        ObjectNode spec = specification(id, role, geometrySha, elements, atomOrder, threads, memoryMb);
        writeJson(specPath, spec);
        Path resultPath = directory.resolve("result.json");
        if (Files.isRegularFile(resultPath)) {
            JsonNode existing = JSON.readTree(resultPath.toFile());
            validateResult(spec, existing, elements.size());
            writeValidation(directory, resultPath);
            writeChecksums(directory);
            return new Result(existing);
        }
        ProcessBuilder builder = new ProcessBuilder(python.toString(), worker.toString(), "--spec", specPath.toString(),
                "--geometry", geometry.toString(), "--output-directory", directory.toString());
        setThreads(builder.environment(), threads);
        builder.redirectErrorStream(true);
        builder.redirectOutput(directory.resolve("java_process_output.log").toFile());
        int exit = builder.start().waitFor();
        if (exit != 0) throw new IOException("numerical worker failed for " + id + "; see " + directory);
        JsonNode result = JSON.readTree(resultPath.toFile());
        validateResult(spec, result, elements.size());
        writeValidation(directory, resultPath);
        writeChecksums(directory);
        return new Result(result);
    }

    private static void writeValidation(Path directory, Path resultPath) throws IOException {
        ObjectNode validation = JSON.createObjectNode();
        validation.put("status", "JAVA_VALIDATED");
        validation.put("result_sha256", ArtifactChecksums.sha256(resultPath));
        validation.put("validated_at", Instant.now().toString());
        validation.put("geometry_identity_valid", true);
        validation.put("atom_order_valid", true);
        validation.put("protocol_identity_valid", true);
        validation.put("force_is_exact_negative_gradient", true);
        writeJson(directory.resolve("JAVA_VALIDATION.json"), validation);
    }

    private static ObjectNode specification(String id, String role, String geometrySha, List<String> elements,
            String atomOrder, int threads, int memoryMb) throws IOException {
        ObjectNode s = JSON.createObjectNode();
        s.put("schema_version", 1); s.put("snapshot_id", id); s.put("dataset_role", role);
        s.put("geometry_checksum", geometrySha); s.put("atom_order_checksum", atomOrder);
        s.put("protocol_checksum", PROTOCOL); s.put("atom_count", elements.size()); s.putPOJO("atom_elements", elements);
        s.put("formal_charge", 0); s.put("multiplicity", 1); s.put("method", "PBE"); s.put("basis", "def2-SVP");
        s.put("dispersion", "D3(BJ)"); s.put("density_fitting", true); s.put("auxiliary_basis", "def2-SVP-JKFIT");
        s.put("grid_level", 2); s.put("grid_pruning", "NWCHEM_PRUNE"); s.put("grid_partition", "ORIGINAL_BECKE");
        s.put("radial_grid", "TREUTLER_AHLRICHS"); s.put("radii_adjust", "TREUTLER_ATOMIC_RADII_ADJUST");
        s.put("scf_convergence_tolerance", 1e-8); s.put("maximum_scf_cycles", 160);
        s.put("initial_guess_policy", "MINAO_ONLY_NO_CHECKPOINT"); s.put("checkpoint_policy", "DISABLED");
        s.put("memory_limit_mb", memoryMb); s.put("thread_count", threads); s.put("backend_id", BACKEND);
        s.put("backend_version", BACKEND_VERSION); s.put("d3_implementation", "simple-dftd3-python"); s.put("d3_version", "1.5.0");
        s.put("d3_generation", "D3"); s.put("d3_damping", "BJ_RATIONAL");
        s.put("d3_functional_mapping", "pbe"); s.put("d3_atm_enabled", false);
        s.put("d3_parameter_source", "s-dftd3 parameters.toml");
        s.put("d3_parameter_database_sha256", "b1d9d1b9882dcad5361a99c34745ad44f8a274d80c907d9d0187255e4323d645");
        ObjectNode d3=s.putObject("d3_parameters"); d3.put("s6",1.0);d3.put("s8",0.7875);d3.put("s9",0.0);d3.put("a1",0.4289);d3.put("a2",4.4407);d3.put("alp",14.0);
        String payload = String.join("\n", id, role, geometrySha, atomOrder, PROTOCOL, BACKEND, BACKEND_VERSION,
                "PBE|D3(BJ)|def2-SVP|DF:def2-SVP-JKFIT|grid:2:NWCHEM_PRUNE:ORIGINAL_BECKE:TREUTLER_AHLRICHS:TREUTLER_ATOMIC_RADII_ADJUST",
                "charge=0|multiplicity=1|conv=1e-8|maxcycle=160|guess=MINAO_ONLY_NO_CHECKPOINT|checkpoint=DISABLED",
                "d3=1.5.0|generation=D3|damping=BJ_RATIONAL|mapping=pbe|atm=false|s6=1.0|s8=0.7875|s9=0.0|a1=0.4289|a2=4.4407|alp=14.0|parameter_db_sha256=b1d9d1b9882dcad5361a99c34745ad44f8a274d80c907d9d0187255e4323d645",
                "memory_mb="+memoryMb+"|threads="+threads, String.join(",", elements));
        s.put("specification_identity_payload", payload);
        s.put("specification_file_checksum", sha256(payload));
        s.put("result_identity", sha256("result\n" + payload));
        return s;
    }

    private static void validateResult(JsonNode spec, JsonNode result, int atoms) throws IOException {
        for (String key : List.of("result_identity", "geometry_checksum", "atom_order_checksum", "protocol_checksum",
                "specification_file_checksum", "backend_id", "backend_version", "d3_generation", "d3_damping",
                "d3_functional_mapping", "d3_parameter_source", "d3_parameter_database_sha256")) {
            if (!result.path(key).asText().equals(spec.path(key).asText())) throw new IOException("result identity mismatch: " + key);
        }
        if (result.path("d3_atm_enabled").asBoolean() != spec.path("d3_atm_enabled").asBoolean()
                || !result.path("d3_parameters").equals(spec.path("d3_parameters"))) {
            throw new IOException("result D3 identity mismatch");
        }
        if (!result.path("scf_converged").asBoolean() || !Double.isFinite(result.path("energy_hartree").asDouble()))
            throw new IOException("unconverged or nonfinite result");
        JsonNode g=result.path("gradient_hartree_per_bohr"), f=result.path("force_hartree_per_bohr");
        if (g.size()!=atoms || f.size()!=atoms) throw new IOException("incomplete gradient/force");
        for(int i=0;i<atoms;i++) { if(g.get(i).size()!=3||f.get(i).size()!=3) throw new IOException("invalid Cartesian row");
            for(int j=0;j<3;j++){double gv=g.get(i).get(j).asDouble(),fv=f.get(i).get(j).asDouble();
                if(!Double.isFinite(gv)||!Double.isFinite(fv)||Double.doubleToRawLongBits(fv)!=Double.doubleToRawLongBits(-gv))
                    throw new IOException("force/gradient invariant failed at "+i+","+j);}}
    }

    private static void freeze(Path output, List<Snapshot> snapshots, String atomOrder) throws IOException {
        ObjectNode root=JSON.createObjectNode(); root.put("status","FROZEN_READ_ONLY_QM_TARGET_DATASET");
        root.put("target_count",60);root.put("training_count",45);root.put("holdout_count",15);
        root.put("protocol_sha256",PROTOCOL);root.put("atom_order_sha256",atomOrder);root.put("forcebalance_may_execute_qm",false);
        ArrayNode targets=root.putArray("targets");
        ObjectNode training=JSON.createObjectNode();training.put("status","FROZEN_FORCE_FITTING_TRAINING_VIEW");
        training.put("target_count",45);training.put("holdout_targets_included",false);training.put("forcebalance_may_execute_qm",false);
        training.put("protocol_sha256",PROTOCOL);ArrayNode trainingTargets=training.putArray("targets");
        ObjectNode sealed=JSON.createObjectNode();sealed.put("status","SEALED_HOLDOUT_IDENTITY_ONLY");sealed.put("target_count",15);
        sealed.put("target_values_exposed",false);ArrayNode sealedTargets=sealed.putArray("targets");
        for(Snapshot s:snapshots){Path rp=output.resolve("calculations").resolve(s.id).resolve("result.json");JsonNode r=JSON.readTree(rp.toFile());
            ObjectNode n=targets.addObject();n.put("snapshot_id",s.id);n.put("dataset_role",s.split);n.put("geometry_sha256",s.geometrySha);
            n.put("result_identity",r.path("result_identity").asText());n.put("result_sha256",ArtifactChecksums.sha256(rp));n.put("result_path",rp.toString());
            n.put("energy_hartree",r.path("energy_hartree").asDouble());n.set("gradient_hartree_per_bohr",r.path("gradient_hartree_per_bohr"));n.set("force_hartree_per_bohr",r.path("force_hartree_per_bohr"));
            if(s.split.equals("TRAIN"))trainingTargets.add(n.deepCopy());else{ObjectNode h=sealedTargets.addObject();h.put("snapshot_id",s.id);h.put("geometry_sha256",s.geometrySha);h.put("result_identity",r.path("result_identity").asText());h.put("result_sha256",ArtifactChecksums.sha256(rp));}}
        Path dataset=output.resolve("FROZEN_QM_TARGET_DATASET.json");writeJson(dataset,root);
        Files.writeString(output.resolve("FROZEN_QM_TARGET_DATASET.sha256"),ArtifactChecksums.sha256(dataset)+"  FROZEN_QM_TARGET_DATASET.json\n");
        Path trainingPath=output.resolve("FORCE_FITTING_TRAINING_TARGETS.json");writeJson(trainingPath,training);
        Files.writeString(output.resolve("FORCE_FITTING_TRAINING_TARGETS.sha256"),ArtifactChecksums.sha256(trainingPath)+"  FORCE_FITTING_TRAINING_TARGETS.json\n");
        Path sealedPath=output.resolve("SEALED_HOLDOUT_IDENTITIES.json");writeJson(sealedPath,sealed);
        Files.writeString(output.resolve("SEALED_HOLDOUT_IDENTITIES.sha256"),ArtifactChecksums.sha256(sealedPath)+"  SEALED_HOLDOUT_IDENTITIES.json\n");
    }

    private static void requireQualified(Path output) throws IOException { Path p=output.resolve("QUALIFICATION_REPORT.json");
        if(!Files.isRegularFile(p)||!JSON.readTree(p.toFile()).path("external_qm_backend_qualified").asBoolean()) throw new IOException("qualification gate not passed"); }
    private static void verifyFrozenInputs(Path cloud) throws IOException {
        Map<String,String> expected=new LinkedHashMap<>();for(String line:Files.readAllLines(cloud.resolve("SHA256SUMS"))){String[] p=line.trim().split("\\s+",2);if(p.length==2)expected.put(p[1].replaceFirst("^\\*",""),p[0]);}
        for(var e:expected.entrySet()){Path p=cloud.resolve(e.getKey());if(Files.isRegularFile(p)&&!ArtifactChecksums.sha256(p).equals(e.getValue()))throw new IOException("frozen input checksum mismatch: "+e.getKey());}
        JsonNode status=JSON.readTree(cloud.resolve("FORCE_CLOUD_STATUS.json").toFile());if(status.path("retained_count").asInt()!=60||status.path("training_count").asInt()!=45||status.path("holdout_count").asInt()!=15||!status.path("protocol_sha256").asText().equals(PROTOCOL))throw new IOException("force-cloud invariant failed");
        JsonNode seal=JSON.readTree(cloud.resolve("HOLDOUT_SEAL.json").toFile());if(seal.path("holdout_count").asInt()!=15||!seal.path("split_manifest_sha256").asText().equals(ArtifactChecksums.sha256(cloud.resolve("TRAIN_HOLDOUT_SPLIT.csv"))))throw new IOException("holdout seal invalid");
    }
    private static List<Snapshot> readManifest(Path cloud)throws IOException{List<Snapshot> out=new ArrayList<>();for(String line:Files.readAllLines(cloud.resolve("SNAPSHOT_MANIFEST.csv")).subList(1,61)){String[]p=line.split(",",10);if(!p[8].equals(PROTOCOL))throw new IOException("snapshot protocol mismatch");out.add(new Snapshot(p[0],p[1],p[5],p[6]));}return out;}
    private static List<String> readElements(Path path)throws IOException{return Files.readAllLines(path).stream().skip(1).map(s->s.split(",")[1].toUpperCase(Locale.ROOT)).toList();}
    private static double[][] readMatrix(Path p,int rows)throws IOException{double[][]v=new double[rows][3];List<String>l=Files.readAllLines(p);for(int i=0;i<rows;i++){String[]x=l.get(i).trim().split("\\s+");for(int j=0;j<3;j++)v[i][j]=Double.parseDouble(x[j]);}return v;}
    private static double maxError(double[][]e,JsonNode a){double m=0;for(int i=0;i<e.length;i++)for(int j=0;j<3;j++)m=Math.max(m,Math.abs(e[i][j]-a.get(i).get(j).asDouble()));return m;}
    private static void setThreads(Map<String,String>env,int n){String v=Integer.toString(n);for(String k:List.of("OMP_NUM_THREADS","OPENBLAS_NUM_THREADS","MKL_NUM_THREADS","VECLIB_MAXIMUM_THREADS","NUMEXPR_NUM_THREADS","BLIS_NUM_THREADS"))env.put(k,v);}
    private static void writeJson(Path p,JsonNode n)throws IOException{Files.createDirectories(p.getParent());Path t=Files.createTempFile(p.getParent(),p.getFileName().toString(),".tmp");JSON.writerWithDefaultPrettyPrinter().writeValue(t.toFile(),n);Files.move(t,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    private static void writeChecksums(Path d)throws IOException{StringBuilder b=new StringBuilder();try(var s=Files.list(d)){for(Path p:s.filter(Files::isRegularFile).sorted().toList())if(!p.getFileName().toString().equals("SHA256SUMS"))b.append(ArtifactChecksums.sha256(p)).append("  ").append(p.getFileName()).append('\n');}Files.writeString(d.resolve("SHA256SUMS"),b);}
    private static String sha256(String value){try{var md=java.security.MessageDigest.getInstance("SHA-256");return java.util.HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private record Snapshot(String id,String split,String geometryPath,String geometrySha){}
    private record Result(JsonNode json){}
}
