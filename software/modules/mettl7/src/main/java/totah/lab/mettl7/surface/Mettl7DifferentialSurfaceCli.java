package totah.lab.mettl7.surface;

import totah.lab.athena.surface.differential.DifferentialResidueScore;
import totah.lab.athena.surface.differential.DifferentialSurfaceAnalyzer;
import totah.lab.athena.surface.differential.DifferentialSurfaceMap;
import totah.lab.athena.surface.differential.DifferentialSurfaceOptions;
import totah.lab.athena.surface.differential.ExplicitResidueCorrespondence;
import totah.lab.athena.surface.differential.LocalResidueNeighborhood;
import totah.lab.athena.surface.differential.SurfaceResidue;
import totah.lab.athena.surface.differential.SurfDiffCompatibleSasa;
import totah.lab.athena.surface.differential.SurfDiffPhysicochemicalDifference;
import totah.lab.athena.surface.differential.SurfDiffWeights;
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
                a.size(), b.size(), aToB.queryToSubject().size());
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
        Map<ResidueId, Map<ResidueId, Double>> queryNeighborhoods =
                LocalResidueNeighborhood.build(query.stream()
                        .filter(row -> row.isSurface(options)).toList(),
                        options.neighborhoodRadiusAngstroms());
        Map<ResidueId, Map<ResidueId, Double>> subjectNeighborhoods =
                LocalResidueNeighborhood.build(subject.stream()
                        .filter(row -> row.isSurface(options)).toList(),
                        options.neighborhoodRadiusAngstroms());

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
        try (BufferedWriter writer = Files.newBufferedWriter(
                output.resolve(direction + "_neighborhood_evidence.csv"))) {
            writer.write("direction,central_query,central_subject,member_side,member_number,member_name,distance_A,distance_weight,relative_sasa,exposure_weight,physchem_difference,status\n");
            for (DifferentialResidueScore score : map.residues()) {
                ResidueId central = score.queryResidue();
                ResidueId subjectCentral = score.subjectResidue().orElse(null);
                for (Map.Entry<ResidueId, Double> member
                        : queryNeighborhoods.get(central).entrySet()) {
                    SurfaceResidue q = queryById.get(member.getKey());
                    SurfaceResidue s = scoringCorrespondence.subjectOf(member.getKey())
                            .map(subjectById::get).orElse(null);
                    writeMember(writer, direction, central, subjectCentral, "QUERY",
                            q, s, member.getValue(), options);
                }
                if (subjectCentral != null && subjectNeighborhoods.containsKey(subjectCentral)) {
                    for (Map.Entry<ResidueId, Double> member
                            : subjectNeighborhoods.get(subjectCentral).entrySet()) {
                        if (!scoringCorrespondence.queryToSubject().containsValue(member.getKey())) {
                            SurfaceResidue s = subjectById.get(member.getKey());
                            writeMember(writer, direction, central, subjectCentral,
                                    "SUBJECT_ONLY", null, s, member.getValue(), options);
                        }
                    }
                }
            }
        }
    }

    private static void writeMember(
            BufferedWriter writer, String direction, ResidueId central,
            ResidueId subjectCentral, String side, SurfaceResidue query,
            SurfaceResidue subject, double distance,
            DifferentialSurfaceOptions options) throws IOException {
        SurfaceResidue exposure = query == null ? subject : query;
        double difference = query == null || subject == null ? 1.0
                : SurfDiffPhysicochemicalDifference.betweenThreeLetter(
                        query.residue().getName(), subject.residue().getName());
        writer.write(String.format(Locale.ROOT,
                "%s,%d,%s,%s,%d,%s,%.12f,%.12f,%.12f,%.12f,%.12f,%s%n",
                direction, central.residueNumber(), subjectCentral == null ? ""
                        : Integer.toString(subjectCentral.residueNumber()),
                side, exposure.id().residueNumber(), exposure.residue().getName(),
                distance, SurfDiffWeights.distance(distance,
                        options.scoringRadiusAngstroms()),
                exposure.relativeSasa(), SurfDiffWeights.exposure(exposure.relativeSasa()),
                difference, query == null ? "SUBJECT_ONLY" : subject == null
                        ? "QUERY_ONLY" : "MAPPED"));
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
            Path path, Path a, Path b, int aCount, int bCount, int mapped)
            throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("mode=SURFDIFF_COMPATIBLE\n");
            writer.write("generic_commit=da5a1e60b\n");
            writer.write("upstream_surfdiff_commit=b8dceec575de43dff1b5297774affe356a183466\n");
            writer.write("correspondence=" + Mettl7ResidueCorrespondenceAdapter.DEFINITION + "\n");
            writer.write("mettl7a_path=" + a.toAbsolutePath() + "\n");
            writer.write("mettl7a_sha256=" + sha256(a) + "\n");
            writer.write("mettl7b_path=" + b.toAbsolutePath() + "\n");
            writer.write("mettl7b_sha256=" + sha256(b) + "\n");
            writer.write("a_residues=" + aCount + "\n");
            writer.write("b_residues=" + bCount + "\n");
            writer.write("mapped=" + mapped + "\n");
            writer.write("coverage=1.0\n");
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
}
