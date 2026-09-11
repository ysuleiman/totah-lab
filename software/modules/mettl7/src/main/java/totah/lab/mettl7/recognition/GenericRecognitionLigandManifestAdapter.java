package totah.lab.mettl7.recognition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/** Manifest-only execution adapter; it introduces no recognition semantics. */
public final class GenericRecognitionLigandManifestAdapter {
    public static final String VERSION="GENERIC_RECOGNITION_LIGAND_MANIFEST_ADAPTER_V1";
    private GenericRecognitionLigandManifestAdapter(){}

    public static void main(String[] args)throws IOException{
        if(args.length!=4)throw new IllegalArgumentException("usage: <root> <source-run-manifest> <output> <comma-separated-ligand-ids>");
        run(Path.of(args[0]),Path.of(args[1]),Path.of(args[2]),Set.of(args[3].split(",")));
    }

    public static Mettl7RecognitionBatchMaterializer.EvidenceSummary run(Path root,Path sourceManifest,
            Path output,Set<String> selectedLigands)throws IOException{
        root=root.toAbsolutePath().normalize();output=output.toAbsolutePath().normalize();
        JsonNode document;
        try (var reader = Files.newBufferedReader(sourceManifest, StandardCharsets.UTF_8)) {
            document = new ObjectMapper().readTree(reader);
        }
        if (document == null || !document.isObject()) throw new IOException("manifest object required");
        if (selectedLigands.isEmpty()) throw new IOException("selected ligands required");
        Set<String> seeds = new LinkedHashSet<>();
        for (JsonNode seed : array(document, "seeds")) {
            if ((!seed.isIntegralNumber() && !seed.isTextual()) || seed.asText().isBlank()
                    || !seeds.add(seed.asText())) throw new IOException("invalid or duplicate declared seed");
        }
        if (seeds.isEmpty()) throw new IOException("declared seeds required");
        // This adapter executes the paired METTL7A/B campaign, using its declared seeds.
        Set<String> enzymes = Set.of("7A", "7B");
        JsonNode receptors = document.path("receptor_sources");
        if (!receptors.isObject() || receptors.size() != 2
                || !receptors.has("7A") || !receptors.has("7B")) {
            throw new IOException("paired receptor_sources must declare 7A and 7B");
        }
        Map<String,Ligand> ligands=new LinkedHashMap<>();
        for(JsonNode node:array(document, "ligands")){
            String id=node.path("compound").asText();if(!selectedLigands.contains(id))continue;
            Path sdf=root.resolve(required(node, "sdf")),prepared=root.resolve(required(node, "pdbqt"));
            String preparedHash=required(node, "pdbqt_sha256");
            requireHash(sdf,required(node, "sdf_sha256"));requireHash(prepared,preparedHash);
            if (ligands.putIfAbsent(id,new Ligand(id,sdf,prepared,preparedHash,
                    required(node, "canonical_isomeric_smiles"),required(node, "modeled_microstate"))) != null) {
                throw new IOException("duplicate selected ligand " + id);
            }
        }
        if(!ligands.keySet().equals(new LinkedHashSet<>(selectedLigands)))throw new IOException("selected ligand absent from manifest");
        Map<RunKey, JsonNode> runs = new LinkedHashMap<>();
        Set<String> runIds = new LinkedHashSet<>();
        for (JsonNode run : array(document, "runs")) {
            String id = run.path("compound").asText();
            if (!ligands.containsKey(id)) continue;
            String enzyme = required(run, "enzyme"), seed = required(run, "seed");
            if (!enzymes.contains(enzyme)) throw new IOException("unknown enzyme " + enzyme);
            if (!seeds.contains(seed)) throw new IOException("undeclared seed " + seed);
            RunKey key = new RunKey(id, enzyme, seed);
            String runId = required(run, "key");
            if (!runIds.add(runId)) throw new IOException("duplicate run ID " + runId);
            if (!runId.equals(enzyme + "_" + id + "_seed" + seed)) throw new IOException("undeclared run ID " + runId);
            if (runs.putIfAbsent(key, run) != null) throw new IOException("duplicate run " + key);
        }
        for (String id : ligands.keySet()) for (String enzyme : List.of("7A", "7B")) for (String seed : seeds) {
            RunKey key = new RunKey(id, enzyme, seed);
            if (!runs.containsKey(key)) throw new IOException("missing selected run " + key);
        }
        List<Mettl7RecognitionBatchMaterializer.ManifestSource> sources=new ArrayList<>();
        List<String> manifestRows = new ArrayList<>();
        for (var entry : runs.entrySet()) {
            RunKey key = entry.getKey(); JsonNode run = entry.getValue(); Ligand ligand = ligands.get(key.id());
            Path runLigand = root.resolve(required(run, "ligand"));
            String runLigandHash = required(run, "ligand_sha256");
            requireHash(runLigand, runLigandHash);
            if (!runLigand.toAbsolutePath().normalize().equals(ligand.prepared().toAbsolutePath().normalize())
                    || !runLigandHash.equals(ligand.preparedHash())) throw new IOException("run ligand identity mismatch " + key);
            Path receptor=root.resolve(required(run, "receptor")),raw=root.resolve(required(run, "output"));
            requireHash(receptor,required(run, "receptor_sha256"));requireHash(raw,required(run, "output_sha256"));
            int poses=new PdbqtReader().read(raw).models().size();
            if (poses < 1) throw new IOException("run contains no poses " + key);
            String paralog=key.enzyme().substring(1),arm=key.id().toUpperCase(Locale.ROOT)+"_"+paralog;
            sources.add(new Mettl7RecognitionBatchMaterializer.ManifestSource(arm,key.seed(),raw,ligand.sdf(),receptor,
                    poses,ligand.prepared(),key.id()));
            manifestRows.add(csv(key.id(),paralog,key.seed(),ligand.sdf(),sha256(ligand.sdf()),ligand.prepared(),
                    ligand.preparedHash(),receptor,sha256(receptor),raw,sha256(raw),poses,
                    ligand.stereochemistry()+";"+ligand.microstate()));
        }
        // Do not overwrite a frozen manifest until all selected input provenance and coverage pass.
        Files.createDirectories(output);Path frozen=output.resolve("ANALOGUE_RECOGNITION_MANIFEST.csv");
        try(BufferedWriter writer=Files.newBufferedWriter(frozen,StandardCharsets.UTF_8)){
            writer.write("ligand_id,paralog,seed,sdf,sdf_sha256,prepared_pdbqt,prepared_sha256,receptor,receptor_sha256,raw_pose,raw_sha256,pose_count,stereochemistry_microstate\n");
            for (String row : manifestRows) writer.write(row);
        }
        var evidence=Mettl7RecognitionBatchMaterializer.runWithEvidence(root,output.resolve("materialization"),sources);
        Mettl7RecognitionBasinExecutor.execute(root,output.resolve("basins"),evidence);
        return evidence;
    }

    private static JsonNode array(JsonNode document, String field) throws IOException {
        JsonNode value = document.path(field);
        if (!value.isArray()) throw new IOException("manifest array required: " + field);
        return value;
    }

    private static String required(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if ((!value.isTextual() && !value.isIntegralNumber()) || value.asText().isBlank()) {
            throw new IOException("manifest value required: " + field);
        }
        return value.asText();
    }

    private record RunKey(String id, String enzyme, String seed) {}

    private static void requireHash(Path path,String expected)throws IOException{if(!Files.isRegularFile(path))throw new IOException("missing "+path);
        String actual=sha256(path);if(!actual.equals(expected))throw new IOException("hash mismatch "+path);}
    private static String sha256(Path path)throws IOException{try{var d=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[]b=new byte[8192];for(int n;(n=in.read(b))>=0;)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String csv(Object...v){StringBuilder b=new StringBuilder();for(int i=0;i<v.length;i++){if(i>0)b.append(',');b.append('"').append(String.valueOf(v[i]).replace("\"","\"\"")).append('"');}return b.append('\n').toString();}
    private record Ligand(String id,Path sdf,Path prepared,String preparedHash,String stereochemistry,String microstate){}
}
