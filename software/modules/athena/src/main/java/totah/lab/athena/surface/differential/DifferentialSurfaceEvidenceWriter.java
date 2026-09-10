package totah.lab.athena.surface.differential;

import totah.lab.gaia.structure.ResidueId;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Writes an auditable member-level trace from the canonical scoring path. */
public final class DifferentialSurfaceEvidenceWriter {
    private DifferentialSurfaceEvidenceWriter() {
    }

    public static void write(Path path, String direction,
            List<SurfaceResidue> query, List<SurfaceResidue> subject,
            ExplicitResidueCorrespondence correspondence,
            DifferentialSurfaceOptions options) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(direction, "direction");
        List<SurfaceResidue> querySurface = query.stream()
                .filter(row -> row.isSurface(options)).toList();
        List<SurfaceResidue> subjectSurface = subject.stream()
                .filter(row -> row.isSurface(options)).toList();
        Map<ResidueId, SurfaceResidue> queryById =
                DifferentialSurfaceAnalyzer.index(querySurface);
        Map<ResidueId, SurfaceResidue> subjectById =
                DifferentialSurfaceAnalyzer.index(subjectSurface);
        Map<ResidueId, Map<ResidueId, Double>> queryNeighborhoods =
                LocalResidueNeighborhood.build(querySurface,
                        options.neighborhoodRadiusAngstroms());
        Map<ResidueId, Map<ResidueId, Double>> subjectNeighborhoods =
                LocalResidueNeighborhood.build(subjectSurface,
                        options.neighborhoodRadiusAngstroms());
        Map<ResidueId, ResidueId> reverse =
                DifferentialSurfaceAnalyzer.reverse(correspondence);

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("direction,central_query_chain,central_query_number,central_subject_chain,central_subject_number,query_member_chain,query_member_number,query_member_name,subject_member_chain,subject_member_number,subject_member_name,corresponding_subject_chain,corresponding_subject_number,corresponding_subject_name,query_distance_A,subject_distance_A,effective_distance_A,distance_weight,exposure_source,relative_sasa,exposure_weight,rup,numerator_contribution,denominator_contribution,status\n");
            for (SurfaceResidue central : querySurface) {
                ResidueId subjectCentral = correspondence.subjectOf(central.id()).orElse(null);
                List<DifferentialSurfaceAnalyzer.Member> members =
                        DifferentialSurfaceAnalyzer.combinedMembers(central.id(),
                                queryNeighborhoods, subjectNeighborhoods,
                                correspondence, reverse, queryById, subjectById, options);
                for (DifferentialSurfaceAnalyzer.Member member : members) {
                    if (member.distance() > options.scoringRadiusAngstroms()) continue;
                    SurfaceResidue q = member.query() == null ? null
                            : queryById.get(member.query());
                    SurfaceResidue s = member.subject() == null ? null
                            : subjectById.get(member.subject());
                    ResidueId correspondingId = q == null ? null
                            : correspondence.subjectOf(q.id()).orElse(null);
                    SurfaceResidue corresponding = correspondingId == null ? null
                            : subjectById.get(correspondingId);
                    double rup = q == null
                            ? SurfDiffPhysicochemicalDifference.unmatched()
                            : DifferentialSurfaceAnalyzer.rup(q, correspondence, subjectById);
                    if (options.mutationsOnly() && rup <= 0.0 && member.distance() != 0.0) {
                        continue;
                    }
                    double distanceWeight = SurfDiffWeights.distance(
                            member.distance(), options.scoringRadiusAngstroms());
                    double exposureWeight = SurfDiffWeights.exposure(member.relativeSasa());
                    writer.write(row(
                            direction, central.id().chainId(), central.id().residueNumber(),
                            subjectCentral == null ? "" : subjectCentral.chainId(),
                            subjectCentral == null ? "" : Integer.toString(subjectCentral.residueNumber()),
                            id(member.query(), true), id(member.query(), false), name(q),
                            id(member.subject(), true), id(member.subject(), false), name(s),
                            id(correspondingId, true), id(correspondingId, false),
                            name(corresponding),
                            number(member.queryDistance()), number(member.subjectDistance()),
                            decimal(member.distance()), decimal(distanceWeight),
                            q == null ? "SUBJECT" : "QUERY",
                            decimal(member.relativeSasa()), decimal(exposureWeight), decimal(rup),
                            decimal(rup * distanceWeight * exposureWeight),
                            decimal(distanceWeight * exposureWeight),
                            q == null ? "SUBJECT_ONLY" : s == null ? "QUERY_ONLY" : "MAPPED"));
                }
            }
        }
    }

    private static String id(ResidueId id, boolean chain) {
        if (id == null) return "";
        return chain ? id.chainId() : Integer.toString(id.residueNumber());
    }

    private static String name(SurfaceResidue residue) {
        return residue == null ? "" : residue.residue().getName();
    }

    private static String number(Double value) {
        return value == null ? "" : String.format(Locale.ROOT, "%.12f", value);
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.12f", value);
    }

    private static String row(Object... values) {
        return java.util.Arrays.stream(values)
                .map(value -> value == null ? "" : value.toString())
                .collect(java.util.stream.Collectors.joining(","))
                + System.lineSeparator();
    }
}
