package totah.lab.mettl7.surface;

import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionProfile;
import totah.lab.athena.interaction.InteractionProfiler;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtFile;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;
import java.util.stream.Collectors;

/** Overlays canonical Athena interactions on frozen METTL7 SurfDiff evidence. */
@Deprecated(forRemoval = true)
public final class Mettl7LigandDifferentialOverlayCli {
    private Mettl7LigandDifferentialOverlayCli() {
    }

    public static void main(String[] args) throws IOException {
        if (!Boolean.getBoolean("mettl7.legacy.overlay.enabled")) {
            throw new IllegalStateException("NONCANONICAL_LEGACY_OVERLAY_DISABLED; use Mettl7RecognitionBatchMaterializer");
        }
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: <regression fixture root> <surface output>");
        }
        run(Path.of(args[0]), Path.of(args[1]));
    }

    public static void run(Path fixtures, Path output) throws IOException {
        Files.createDirectories(output);
        validateReceipt(output);
        FixtureManifest manifest = FixtureManifest.load(fixtures);
        Map<Integer, SurfaceEvidence> aVsB = readSurface(
                output.resolve("METTL7A_VS_METTL7B_residue_scores.csv"),
                "METTL7A_VS_METTL7B");
        Map<Integer, SurfaceEvidence> bVsA = readSurface(
                output.resolve("METTL7B_VS_METTL7A_residue_scores.csv"),
                "METTL7B_VS_METTL7A");
        Path result = output.resolve("LIGAND_INTERACTION_DIFFERENTIAL_OVERLAY.csv");
        Path temporary = Files.createTempFile(output, "overlay-", ".csv.tmp");
        boolean moved = false;
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary)) {
                writer.write(Mettl7Csv.row("anchor", "paralog", "family", "pose_file",
                    "original_docking_mode", "pdbqt_model_number", "pose_extracted",
                    "admissibility", "perception_degraded", "perception_provenance",
                    "protein_chain", "protein_residue", "protein_name", "interaction_type",
                    "interaction_stage", "thresholds_provenance", "distance_A",
                    "primary_angle_deg", "secondary_angle_deg", "protein_atoms",
                    "ligand_atoms", "protein_group_id", "ligand_group_id",
                    "corresponding_paralog", "corresponding_residue", "corresponding_name",
                    "rup", "rus", "rss", "rsasa", "exposure_weight",
                    "residue_identity_status", "local_environment_status",
                    "protein_differential_evidence", "ligand_interaction_evidence"));
                writeNetarsudil(fixtures, manifest, writer, aVsB, bVsA);
                writeDcmb(fixtures, manifest, writer, aVsB, bVsA);
            }
            Files.move(temporary, result, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
        writeOverlayReceipt(output, result);
    }

    private static void writeNetarsudil(Path root, FixtureManifest manifest,
            BufferedWriter writer,
            Map<Integer, SurfaceEvidence> aVsB, Map<Integer, SurfaceEvidence> bVsA)
            throws IOException {
        Path n = root.resolve("netarsudil");
        manifest.verify("netarsudil/netarsudil_CID66599893_neutral.sdf");
        manifest.verify("netarsudil/7B_neutral_seed483271.pdbqt");
        manifest.verify("netarsudil/METTL7B_SAM_TOPOLOGY_COMPLETE_EXACT_COORDS.pdb");
        manifest.verify("netarsudil/corrected_7A_lowest_strain_mode15.pdbqt");
        manifest.verify("netarsudil/METTL7A_SAM_receptor.pdbqt");
        Structure ligandTopology = new SdfLigandReader().read(
                n.resolve("netarsudil_CID66599893_neutral.sdf")).structure();
        profileAndWrite("NETARSUDIL", "B", "B_FAMILY_5",
                n.resolve("7B_neutral_seed483271.pdbqt"), 5, 5, false, "ADMISSIBLE",
                new PdbReader().read(n.resolve("METTL7B_SAM_TOPOLOGY_COMPLETE_EXACT_COORDS.pdb")),
                transplantByUniqueAtomName(ligandTopology, model(
                        new PdbqtReader().read(n.resolve("7B_neutral_seed483271.pdbqt")), 5)),
                bVsA, writer);
        Path aPose = n.resolve("corrected_7A_lowest_strain_mode15.pdbqt");
        profileAndWrite("NETARSUDIL", "A", "A_ADMISSIBLE_CONTROL", aPose,
                15, 1, true, "ADMISSIBLE",
                proteinOnly(PdbqtGaiaMapper.toStructure(new PdbqtReader().read(
                        n.resolve("METTL7A_SAM_receptor.pdbqt")))),
                transplantByUniqueAtomName(ligandTopology,
                        new PdbqtReader().read(aPose).firstModel()), aVsB, writer);
    }

    private static void writeDcmb(Path root, FixtureManifest manifest,
            BufferedWriter writer,
            Map<Integer, SurfaceEvidence> aVsB, Map<Integer, SurfaceEvidence> bVsA)
            throws IOException {
        Path stage = root.resolve("stage12j");
        manifest.verify("stage12j/family_results.csv");
        manifest.verify("stage12j/7A_WT_SAM_BOUND.pdbqt");
        manifest.verify("stage12j/7B_WT_SAM_BOUND.pdbqt");
        Structure receptorA = proteinOnly(PdbqtGaiaMapper.toStructure(
                new PdbqtReader().read(stage.resolve("7A_WT_SAM_BOUND.pdbqt"))));
        Structure receptorB = proteinOnly(PdbqtGaiaMapper.toStructure(
                new PdbqtReader().read(stage.resolve("7B_WT_SAM_BOUND.pdbqt"))));
        for (Map<String, String> row : Mettl7Csv.read(stage.resolve("family_results.csv"))) {
            String system = row.get("system");
            if (!Set.of("7A_WT", "7B_WT").contains(system)) continue;
            requireDcmbAdmissible(row);
            String paralog = system.substring(1, 2);
            String family = paralog + "_" + row.get("enantiomer") + row.get("family");
            Path pose = stage.resolve("raw").resolve(system + "_"
                    + row.get("enantiomer") + "_s" + row.get("representative_seed")
                    + ".pdbqt");
            manifest.verify(root.relativize(pose).toString());
            int mode = Integer.parseInt(row.get("representative_mode"));
            Structure ligand = PdbqtGaiaMapper.toLigandWithMeekoTopology(
                    model(new PdbqtReader().read(pose), mode), family).structure();
            profileAndWrite("DCMB", paralog, family, pose, mode, mode, false,
                    "SAM_COMPATIBLE",
                    "A".equals(paralog) ? receptorA : receptorB, ligand,
                    "A".equals(paralog) ? aVsB : bVsA, writer);
        }
    }

    private static void profileAndWrite(String anchor, String paralog, String family,
            Path pose, int originalMode, int fileModel, boolean extracted,
            String admissibility, Structure receptor, Structure ligand,
            Map<Integer, SurfaceEvidence> surface, BufferedWriter writer) throws IOException {
        InteractionProfile profile = new InteractionProfiler().profile(receptor, ligand);
        for (Interaction interaction : profile.interactions()) {
            if (!"A".equals(interaction.residue().chainId())) continue;
            int number = interaction.residue().residueNumber();
            SurfaceEvidence evidence = surface.get(number);
            Residue proteinResidue = residue(receptor, number);
            String identityStatus = evidence == null ? "UNAVAILABLE"
                    : evidence.rup() == 0.0 ? "PRESERVED" : "ALTERED";
            String environmentStatus = evidence == null ? "UNAVAILABLE"
                    : evidence.rus() == 0.0 ? "PRESERVED" : "ALTERED";
            writer.write(Mettl7Csv.row(anchor, paralog, family, pose.toString(),
                    originalMode, fileModel, extracted, admissibility,
                    profile.anyPerceptionDegraded(), perception(profile), "A", number,
                    proteinResidue.getName(), interaction.type(),
                    "REFINED", interaction.thresholdsProvenance(),
                    interaction.distanceAngstroms(), interaction.primaryAngleDegrees(),
                    interaction.secondaryAngleDegrees(), atoms(interaction.proteinAtoms()),
                    atoms(interaction.ligandAtoms()), interaction.proteinGroupId(),
                    interaction.ligandGroupId(), other(paralog),
                    evidence == null ? "" : evidence.subjectNumber(),
                    evidence == null ? "" : evidence.subjectName(),
                    evidence == null ? "" : evidence.rup(),
                    evidence == null ? "" : evidence.rus(),
                    evidence == null ? "" : evidence.rss(),
                    evidence == null ? "" : evidence.rsasa(),
                    evidence == null ? "" : evidence.exposureWeight(), identityStatus,
                    environmentStatus, "RUP|RUS|RSS|RSASA|EXPOSURE",
                    "ATHENA_INTERACTION_PROFILE"));
        }
    }

    private static Structure proteinOnly(Structure structure) {
        List<Chain> chains = new ArrayList<>();
        for (Chain chain : structure.getChains()) {
            List<Residue> residues = chain.residues().stream()
                    .filter(residue -> !"SAM".equals(residue.getName())).toList();
            if (!residues.isEmpty()) chains.add(new Chain(chain.id(), residues));
        }
        Set<AtomReference> retained = chains.stream().flatMap(chain ->
                chain.residues().stream().flatMap(residue -> residue.getAtoms().stream()
                        .map(atom -> new AtomReference(chain.id(), residue.getNumber(),
                                residue.getInsertionCode() == null ? ' '
                                        : residue.getInsertionCode(), atom.getName()))))
                .collect(Collectors.toSet());
        List<Bond> bonds = structure.bonds().stream()
                .filter(bond -> retained.contains(bond.atom1()) && retained.contains(bond.atom2()))
                .toList();
        return new Structure(chains, bonds, structure.getConnectivityMetadata());
    }

    private static Structure transplantByUniqueAtomName(Structure source, PdbqtModel pose)
            throws IOException {
        Map<String, PdbqtAtom> byName = new LinkedHashMap<>();
        for (PdbqtAtom atom : pose.atoms()) {
            if (byName.put(atom.atomName(), atom) != null) {
                throw new IOException("duplicate pose atom name " + atom.atomName());
            }
        }
        List<Chain> chains = new ArrayList<>();
        Set<String> used = new java.util.LinkedHashSet<>();
        for (Chain chain : source.getChains()) {
            List<Residue> residues = new ArrayList<>();
            for (Residue residue : chain.residues()) {
                List<Atom> atoms = new ArrayList<>();
                for (Atom atom : residue.getAtoms()) {
                    PdbqtAtom posed = byName.get(atom.getName());
                    if (posed == null) throw new IOException("missing pose atom " + atom.getName());
                    used.add(atom.getName());
                    atoms.add(atom.toBuilder().position(posed.position()).build());
                }
                residues.add(residue.toBuilder().atoms(atoms).build());
            }
            chains.add(new Chain(chain.id(), residues));
        }
        if (!used.equals(byName.keySet())) throw new IOException("pose/topology atom mismatch");
        return new Structure(chains, source.bonds(), source.getConnectivityMetadata());
    }

    private static PdbqtModel model(PdbqtFile file, int number) throws IOException {
        return file.models().stream().filter(row -> row.modelNumber() == number)
                .findFirst().orElseThrow(() -> new IOException("missing model " + number));
    }

    private static Residue residue(Structure structure, int number) throws IOException {
        return structure.getChains().stream().flatMap(c -> c.residues().stream())
                .filter(r -> r.getNumber() == number && !"SAM".equals(r.getName()))
                .findFirst().orElseThrow(() -> new IOException("missing receptor residue " + number));
    }

    private static Map<Integer, SurfaceEvidence> readSurface(Path path, String direction)
            throws IOException {
        LinkedHashMap<Integer, SurfaceEvidence> result = new LinkedHashMap<>();
        for (Map<String, String> row : Mettl7Csv.read(path)) {
            requireColumns(row, path, "direction", "query_number", "subject_number",
                    "subject_name", "rup", "rus", "rss", "query_rsasa",
                    "exposure_weight");
            if (!direction.equals(row.get("direction"))) {
                throw new IOException("wrong surface direction in " + path);
            }
            int query = Integer.parseInt(row.get("query_number"));
            String subject = row.get("subject_number");
            SurfaceEvidence previous = result.put(query, new SurfaceEvidence(subject,
                    row.get("subject_name"), Double.parseDouble(row.get("rup")),
                    Double.parseDouble(row.get("rus")), Double.parseDouble(row.get("rss")),
                    Double.parseDouble(row.get("query_rsasa")),
                    Double.parseDouble(row.get("exposure_weight"))));
            if (previous != null) throw new IOException("duplicate surface residue " + query);
        }
        return Map.copyOf(result);
    }

    private static String atoms(List<Atom> atoms) {
        return atoms.stream().map(Atom::getName).collect(Collectors.joining(";"));
    }

    private static String other(String paralog) { return "A".equals(paralog) ? "B" : "A"; }

    private static String perception(InteractionProfile profile) {
        return profile.perception().stream().map(Object::toString)
                .collect(Collectors.joining(";"));
    }

    private static void requireColumns(Map<String, String> row, Path path,
            String... columns) throws IOException {
        for (String column : columns) {
            if (!row.containsKey(column)) throw new IOException(
                    "missing CSV column " + column + " in " + path);
        }
    }

    private static void validateReceipt(Path output) throws IOException {
        Map<String, String> receipt = new LinkedHashMap<>();
        for (String line : Files.readAllLines(output.resolve("analysis_receipt.txt"))) {
            int separator = line.indexOf('=');
            if (separator <= 0) throw new IOException("malformed analysis receipt");
            receipt.put(line.substring(0, separator), line.substring(separator + 1));
        }
        requireReceipt(receipt, "mode", Mettl7SurfDiffPolicy.MODE);
        requireReceipt(receipt, "generic_baseline_commit",
                Mettl7SurfDiffPolicy.GENERIC_BASELINE_COMMIT);
        requireReceipt(receipt, "generic_runtime_bytecode_sha256",
                Mettl7SurfDiffPolicy.GENERIC_RUNTIME_BYTECODE_SHA256);
        requireReceipt(receipt, "correspondence",
                Mettl7ResidueCorrespondenceAdapter.DEFINITION);
        verifyHash(output.resolve("METTL7A_VS_METTL7B_residue_scores.csv"),
                receipt.get("a_vs_b_scores_sha256"));
        verifyHash(output.resolve("METTL7B_VS_METTL7A_residue_scores.csv"),
                receipt.get("b_vs_a_scores_sha256"));
        verifyHash(output.resolve("METTL7_AB_CORRESPONDENCE_V1.csv"),
                receipt.get("correspondence_sha256"));
    }

    private static void requireReceipt(Map<String, String> receipt, String key,
            String expected) throws IOException {
        if (!expected.equals(receipt.get(key))) throw new IOException(
                "analysis receipt mismatch for " + key);
    }

    private static void verifyHash(Path path, String expected) throws IOException {
        if (expected == null || !expected.equals(sha256(path))) {
            throw new IOException("analysis artifact hash mismatch: " + path);
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void writeOverlayReceipt(Path output, Path overlay) throws IOException {
        Path temporary = Files.createTempFile(output, "overlay-receipt-", ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temporary)) {
            writer.write("overlay_schema=METTL7_LIGAND_DIFFERENTIAL_OVERLAY_V2\n");
            writer.write("canonical_status=NONCANONICAL_LEGACY_OVERLAY\n");
            writer.write("replacement=Mettl7RecognitionBatchMaterializer canonical chemical correspondence + SurfDiff attachment\n");
            writer.write("surface_policy_bytecode_sha256="
                    + Mettl7SurfDiffPolicy.GENERIC_RUNTIME_BYTECODE_SHA256 + "\n");
            writer.write("fixture_manifest_sha256="
                    + Mettl7SurfDiffPolicy.FIXTURE_MANIFEST_SHA256 + "\n");
            writer.write("surface_receipt_sha256="
                    + sha256(output.resolve("analysis_receipt.txt")) + "\n");
            writer.write("overlay_sha256=" + sha256(overlay) + "\n");
        }
        Files.move(temporary, output.resolve("overlay_receipt.txt"),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static void requireDcmbAdmissible(Map<String, String> row) throws IOException {
        requireColumns(row, Path.of("family_results.csv"), "system", "enantiomer",
                "family", "sam_compatibility");
        if (!"compatible".equals(row.get("sam_compatibility"))) {
            throw new IOException("inadmissible DCMB family: " + row.get("system") + " "
                    + row.get("enantiomer") + row.get("family"));
        }
    }

    private record FixtureManifest(Path root, Map<String, String> hashes) {
        static FixtureManifest load(Path root) throws IOException {
            Path manifest = root.resolve("MANIFEST.csv");
            if (!Mettl7SurfDiffPolicy.FIXTURE_MANIFEST_SHA256.equals(sha256(manifest))) {
                throw new IOException("fixture manifest hash mismatch: " + manifest);
            }
            LinkedHashMap<String, String> hashes = new LinkedHashMap<>();
            for (Map<String, String> row : Mettl7Csv.read(manifest)) {
                requireColumns(row, manifest, "fixture_path", "sha256");
                if (hashes.put(row.get("fixture_path"), row.get("sha256")) != null) {
                    throw new IOException("duplicate fixture manifest path");
                }
            }
            return new FixtureManifest(root, Map.copyOf(hashes));
        }

        void verify(String relative) throws IOException {
            String normalized = relative.replace('\\', '/');
            String expected = hashes.get(normalized);
            if (expected == null) throw new IOException(
                    "fixture absent from manifest: " + normalized);
            verifyHash(root.resolve(normalized), expected);
        }
    }

    private record SurfaceEvidence(String subjectNumber, String subjectName,
            double rup, double rus, double rss, double rsasa, double exposureWeight) {
    }
}
