package totah.lab.mettl7.surface;

import totah.lab.athena.surface.differential.DifferentialResidueScore;
import totah.lab.athena.surface.differential.DifferentialSurfaceAnalyzer;
import totah.lab.athena.surface.differential.DifferentialSurfaceEvidenceWriter;
import totah.lab.athena.surface.differential.DifferentialSurfaceMap;
import totah.lab.athena.surface.differential.DifferentialSurfaceOptions;
import totah.lab.athena.surface.differential.ExplicitResidueCorrespondence;
import totah.lab.athena.surface.differential.SurfaceResidue;
import totah.lab.athena.surface.differential.SurfDiffCompatibleSasa;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.reader.PdbReader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runs the frozen whole-protein directional METTL7A/B surface comparison. */
public final class Mettl7DifferentialSurfaceCli {
    private Mettl7DifferentialSurfaceCli() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "usage: <METTL7A protein PDB> <METTL7B protein PDB> <output directory>");
        }
        run(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
    }

    public static void run(Path aPath, Path bPath, Path output) throws IOException {
        String runtimeHash = genericBytecodeHash();
        if (!Mettl7SurfDiffPolicy.GENERIC_RUNTIME_BYTECODE_SHA256.equals(runtimeHash)) {
            throw new IOException("SURFDIFF_COMPATIBLE bytecode does not match frozen policy: "
                    + runtimeHash);
        }
        Files.createDirectories(output);
        DifferentialSurfaceOptions options =
                DifferentialSurfaceOptions.SURFDIFF_COMPATIBLE;
        Structure aStructure = new PdbReader().read(aPath);
        Structure bStructure = new PdbReader().read(bPath);
        List<SurfaceResidue> a = SurfDiffCompatibleSasa.calculate(aStructure, options);
        List<SurfaceResidue> b = SurfDiffCompatibleSasa.calculate(bStructure, options);
        Mettl7ResidueCorrespondenceAdapter adapter =
                new Mettl7ResidueCorrespondenceAdapter();
        ExplicitResidueCorrespondence aToB = adapter.correspond(a, b);
        ExplicitResidueCorrespondence bToA = adapter.correspond(b, a);
        writeCorrespondence(output.resolve("METTL7_AB_CORRESPONDENCE_V1.csv"),
                a, b, aToB);
        analyze("METTL7B_VS_METTL7A", b, a, bToA, output, options);
        analyze("METTL7A_VS_METTL7B", a, b, aToB, output, options);
        writeReceipt(output.resolve("analysis_receipt.txt"), aPath, bPath,
                a.size(), b.size(), aToB.queryToSubject().size(), output);
    }

    private static void analyze(
            String direction,
            List<SurfaceResidue> query,
            List<SurfaceResidue> subject,
            ExplicitResidueCorrespondence correspondence,
            Path output,
            DifferentialSurfaceOptions options) throws IOException {
        ExplicitResidueCorrespondence scoringCorrespondence =
                surfaceCorrespondence(query, subject, correspondence, options);
        DifferentialSurfaceMap map = new DifferentialSurfaceAnalyzer()
                .analyze(query, subject, scoringCorrespondence, options);
        Map<ResidueId, SurfaceResidue> queryById = index(query);
        Map<ResidueId, SurfaceResidue> subjectById = index(subject);

        try (BufferedWriter writer = Files.newBufferedWriter(
                output.resolve(direction + "_residue_scores.csv"))) {
            writer.write("direction,query_chain,query_number,query_name,subject_chain,subject_number,subject_name,query_sasa,query_rsasa,exposure_weight,rup,rus,rss,sector\n");
            for (DifferentialResidueScore score : map.residues().stream()
                    .sorted(Comparator.comparingDouble(
                            DifferentialResidueScore::rus).reversed()).toList()) {
                SurfaceResidue q = queryById.get(score.queryResidue());
                SurfaceResidue s = score.subjectResidue().map(subjectById::get).orElse(null);
                writer.write(String.format(Locale.ROOT,
                        "%s,%s,%d,%s,%s,%s,%s,%.12f,%.12f,%.12f,%.12f,%.12f,%.12f,%s%n",
                        direction, q.id().chainId(), q.id().residueNumber(),
                        q.residue().getName(), s == null ? "" : s.id().chainId(),
                        s == null ? "" : Integer.toString(s.id().residueNumber()),
                        s == null ? "XXX" : s.residue().getName(),
                        q.sasaSquareAngstroms(), q.relativeSasa(), score.exposureWeight(),
                        score.rup(), score.rus(), score.rss(), sector(q.id().residueNumber())));
            }
        }
        DifferentialSurfaceEvidenceWriter.write(
                output.resolve(direction + "_neighborhood_evidence.csv"),
                direction, query, subject, scoringCorrespondence, options);
    }

    private static void writeCorrespondence(
            Path path, List<SurfaceResidue> a, List<SurfaceResidue> b,
            ExplicitResidueCorrespondence correspondence) throws IOException {
        Map<ResidueId, SurfaceResidue> bById = index(b);
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("definition,a_chain,a_number,a_insertion,a_name,b_chain,b_number,b_insertion,b_name,identity,a_ca_present,b_ca_present,status\n");
            for (SurfaceResidue residue : a) {
                ResidueId bId = correspondence.subjectOf(residue.id()).orElseThrow();
                SurfaceResidue match = bById.get(bId);
                writer.write(String.format(Locale.ROOT,
                        "%s,A,%d,,%s,A,%d,,%s,%s,true,true,MAPPED%n",
                        Mettl7ResidueCorrespondenceAdapter.DEFINITION,
                        residue.id().residueNumber(), residue.residue().getName(),
                        match.id().residueNumber(), match.residue().getName(),
                        residue.residue().getName().equals(match.residue().getName())));
            }
        }
    }

    private static void writeReceipt(
            Path path, Path a, Path b, int aCount, int bCount, int mapped,
            Path output)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("mode=" + Mettl7SurfDiffPolicy.MODE + "\n");
            writer.write("generic_baseline_commit="
                    + Mettl7SurfDiffPolicy.GENERIC_BASELINE_COMMIT + "\n");
            writer.write("generic_runtime_bytecode_sha256=" + genericBytecodeHash() + "\n");
            writer.write("upstream_surfdiff_commit="
                    + Mettl7SurfDiffPolicy.UPSTREAM_COMMIT + "\n");
            writer.write("correspondence=" + Mettl7ResidueCorrespondenceAdapter.DEFINITION + "\n");
            writer.write("mettl7a_path=" + a.toAbsolutePath() + "\n");
            writer.write("mettl7a_sha256=" + sha256(a) + "\n");
            writer.write("mettl7b_path=" + b.toAbsolutePath() + "\n");
            writer.write("mettl7b_sha256=" + sha256(b) + "\n");
            writer.write("a_residues=" + aCount + "\n");
            writer.write("b_residues=" + bCount + "\n");
            writer.write("mapped=" + mapped + "\n");
            writer.write("coverage=" + ((double) mapped / Math.max(aCount, bCount)) + "\n");
            writer.write("a_vs_b_scores_sha256=" + sha256(output.resolve(
                    "METTL7A_VS_METTL7B_residue_scores.csv")) + "\n");
            writer.write("b_vs_a_scores_sha256=" + sha256(output.resolve(
                    "METTL7B_VS_METTL7A_residue_scores.csv")) + "\n");
            writer.write("correspondence_sha256=" + sha256(output.resolve(
                    "METTL7_AB_CORRESPONDENCE_V1.csv")) + "\n");
        }
    }

    private static Map<ResidueId, SurfaceResidue> index(List<SurfaceResidue> rows) {
        LinkedHashMap<ResidueId, SurfaceResidue> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(row.id(), row));
        return Map.copyOf(result);
    }

    private static ExplicitResidueCorrespondence surfaceCorrespondence(
            List<SurfaceResidue> query,
            List<SurfaceResidue> subject,
            ExplicitResidueCorrespondence full,
            DifferentialSurfaceOptions options) {
        Map<ResidueId, SurfaceResidue> subjectById = index(subject);
        LinkedHashMap<ResidueId, ResidueId> filtered = new LinkedHashMap<>();
        for (SurfaceResidue residue : query) {
            if (!residue.isSurface(options)) {
                continue;
            }
            full.subjectOf(residue.id()).ifPresent(subjectId -> {
                SurfaceResidue subjectResidue = subjectById.get(subjectId);
                if (subjectResidue != null && subjectResidue.isSurface(options)) {
                    filtered.put(residue.id(), subjectId);
                }
            });
        }
        return new ExplicitResidueCorrespondence(filtered);
    }

    private static String sector(int residue) {
        if (residue >= 39 && residue <= 47) return "39-47";
        if (residue >= 144 && residue <= 151) return "144-151";
        if (residue == 175) return "175";
        if (residue >= 195 && residue <= 203) return "195-203";
        if (residue >= 228 && residue <= 237) return "228-237";
        return "";
    }

    private static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String genericBytecodeHash() throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
        List<Class<?>> classes = List.of(DifferentialSurfaceAnalyzer.class,
                DifferentialSurfaceEvidenceWriter.class,
                DifferentialSurfaceOptions.class,
                ExplicitResidueCorrespondence.class,
                totah.lab.athena.surface.differential.LocalResidueNeighborhood.class,
                totah.lab.athena.surface.differential.SurfDiffPhysicochemicalDifference.class,
                totah.lab.athena.surface.differential.SurfDiffWeights.class,
                SurfaceResidue.class, SurfDiffCompatibleSasa.class);
        for (Class<?> type : classes) {
            String resource = "/" + type.getName().replace('.', '/') + ".class";
            try (var stream = type.getResourceAsStream(resource)) {
                if (stream == null) throw new IOException("missing class resource " + resource);
                digest.update(stream.readAllBytes());
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
