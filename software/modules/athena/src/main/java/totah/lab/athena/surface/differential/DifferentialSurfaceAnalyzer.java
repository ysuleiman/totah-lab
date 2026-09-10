package totah.lab.athena.surface.differential;

import totah.lab.gaia.structure.ResidueId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** In-memory implementation of the frozen SurfDiff local scoring kernel. */
public final class DifferentialSurfaceAnalyzer {

    public DifferentialSurfaceMap analyze(
            List<SurfaceResidue> query,
            List<SurfaceResidue> subject,
            ExplicitResidueCorrespondence correspondence,
            DifferentialSurfaceOptions options) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(correspondence, "correspondence");
        Objects.requireNonNull(options, "options");
        if (options.mode() != DifferentialSurfaceMode.SURFDIFF_COMPATIBLE) {
            throw new IllegalArgumentException("Athena extension is not implemented");
        }

        List<SurfaceResidue> querySurface = query.stream()
                .filter(residue -> residue.isSurface(options)).toList();
        List<SurfaceResidue> subjectSurface = subject.stream()
                .filter(residue -> residue.isSurface(options)).toList();
        Map<ResidueId, SurfaceResidue> queryById = index(querySurface);
        Map<ResidueId, SurfaceResidue> subjectById = index(subjectSurface);
        validateCorrespondence(correspondence, queryById, subjectById);
        Map<ResidueId, Map<ResidueId, Double>> queryNeighborhoods =
                LocalResidueNeighborhood.build(
                        querySurface, options.neighborhoodRadiusAngstroms());
        Map<ResidueId, Map<ResidueId, Double>> subjectNeighborhoods =
                LocalResidueNeighborhood.build(
                        subjectSurface, options.neighborhoodRadiusAngstroms());
        Map<ResidueId, ResidueId> subjectToQuery = reverse(correspondence);

        List<DifferentialResidueScore> scores = new ArrayList<>();
        for (SurfaceResidue central : querySurface) {
            double centralRup = rup(central, correspondence, subjectById);
            List<Member> members = combinedMembers(
                    central.id(), queryNeighborhoods, subjectNeighborhoods,
                    correspondence, subjectToQuery, queryById, subjectById, options);
            double numerator = 0.0;
            double denominator = 0.0;
            for (Member member : members) {
                if (member.distance() > options.scoringRadiusAngstroms()) {
                    continue;
                }
                double memberRup = member.query() == null
                        ? SurfDiffPhysicochemicalDifference.unmatched()
                        : rup(queryById.get(member.query()), correspondence, subjectById);
                if (options.mutationsOnly() && memberRup <= 0.0
                        && member.distance() != 0.0) {
                    continue;
                }
                double distanceWeight = SurfDiffWeights.distance(
                        member.distance(), options.scoringRadiusAngstroms());
                double exposureWeight = SurfDiffWeights.exposure(member.relativeSasa());
                numerator += memberRup * distanceWeight * exposureWeight;
                denominator += distanceWeight * exposureWeight;
            }
            double rus = denominator > 0.0 ? numerator / denominator : 0.0;
            scores.add(new DifferentialResidueScore(
                    central.id(), correspondence.subjectOf(central.id()),
                    central.relativeSasa(), SurfDiffWeights.exposure(central.relativeSasa()),
                    centralRup, rus, 1.0 - rus));
        }
        return new DifferentialSurfaceMap(options.mode(), options, scores,
                queryNeighborhoods);
    }

    static double rup(
            SurfaceResidue query,
            ExplicitResidueCorrespondence correspondence,
            Map<ResidueId, SurfaceResidue> subjectById) {
        return correspondence.subjectOf(query.id())
                .map(subjectById::get)
                .map(subject -> SurfDiffPhysicochemicalDifference.betweenThreeLetter(
                        query.residue().getName(), subject.residue().getName()))
                .orElseGet(SurfDiffPhysicochemicalDifference::unmatched);
    }

    static List<Member> combinedMembers(
            ResidueId central,
            Map<ResidueId, Map<ResidueId, Double>> queryNeighborhoods,
            Map<ResidueId, Map<ResidueId, Double>> subjectNeighborhoods,
            ExplicitResidueCorrespondence correspondence,
            Map<ResidueId, ResidueId> subjectToQuery,
            Map<ResidueId, SurfaceResidue> queryById,
            Map<ResidueId, SurfaceResidue> subjectById,
            DifferentialSurfaceOptions options) {
        LinkedHashMap<MemberKey, Member> members = new LinkedHashMap<>();
        for (Map.Entry<ResidueId, Double> entry
                : queryNeighborhoods.get(central).entrySet()) {
            SurfaceResidue residue = queryById.get(entry.getKey());
            members.put(new MemberKey(entry.getKey(), null),
                    new Member(entry.getKey(), null, entry.getValue(), null,
                            entry.getValue(),
                            residue.relativeSasa()));
        }
        if (!options.symmetricNeighborhood()) {
            return List.copyOf(members.values());
        }
        Optional<ResidueId> subjectCentral = correspondence.subjectOf(central);
        if (subjectCentral.isEmpty()) {
            return List.copyOf(members.values());
        }
        for (Map.Entry<ResidueId, Double> entry
                : subjectNeighborhoods.get(subjectCentral.get()).entrySet()) {
            ResidueId mappedQuery = subjectToQuery.get(entry.getKey());
            if (mappedQuery != null) {
                MemberKey queryKey = new MemberKey(mappedQuery, null);
                Member existing = members.get(queryKey);
                if (existing != null) {
                    members.put(queryKey, new Member(mappedQuery, entry.getKey(),
                            existing.queryDistance(), entry.getValue(),
                            Math.min(existing.distance(), entry.getValue()),
                            existing.relativeSasa()));
                } else {
                    SurfaceResidue residue = queryById.get(mappedQuery);
                    members.put(queryKey, new Member(mappedQuery, entry.getKey(),
                            null, entry.getValue(), entry.getValue(),
                            residue.relativeSasa()));
                }
            } else {
                SurfaceResidue residue = subjectById.get(entry.getKey());
                members.put(new MemberKey(null, entry.getKey()),
                        new Member(null, entry.getKey(), null, entry.getValue(),
                                entry.getValue(),
                                residue.relativeSasa()));
            }
        }
        return List.copyOf(members.values());
    }

    static Map<ResidueId, SurfaceResidue> index(List<SurfaceResidue> residues) {
        LinkedHashMap<ResidueId, SurfaceResidue> result = new LinkedHashMap<>();
        for (SurfaceResidue residue : residues) {
            if (result.put(residue.id(), residue) != null) {
                throw new IllegalArgumentException("duplicate residue " + residue.id());
            }
        }
        return Map.copyOf(result);
    }

    private static void validateCorrespondence(
            ExplicitResidueCorrespondence correspondence,
            Map<ResidueId, SurfaceResidue> query,
            Map<ResidueId, SurfaceResidue> subject) {
        if (!query.keySet().containsAll(correspondence.queryToSubject().keySet())
                || !subject.keySet().containsAll(correspondence.queryToSubject().values())) {
            throw new IllegalArgumentException("correspondence references absent residues");
        }
    }

    static Map<ResidueId, ResidueId> reverse(
            ExplicitResidueCorrespondence correspondence) {
        HashMap<ResidueId, ResidueId> result = new HashMap<>();
        correspondence.queryToSubject().forEach((query, subject) -> result.put(subject, query));
        return Map.copyOf(result);
    }

    private record MemberKey(ResidueId query, ResidueId subject) {
    }

    record Member(
            ResidueId query,
            ResidueId subject,
            Double queryDistance,
            Double subjectDistance,
            double distance,
            double relativeSasa) {
    }
}
