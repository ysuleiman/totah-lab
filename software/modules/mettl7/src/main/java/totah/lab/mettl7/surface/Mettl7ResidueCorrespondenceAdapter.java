package totah.lab.mettl7.surface;

import totah.lab.athena.surface.differential.ExplicitResidueCorrespondence;
import totah.lab.athena.surface.differential.ResidueCorrespondenceProvider;
import totah.lab.athena.surface.differential.SurfaceResidue;
import totah.lab.gaia.structure.ResidueId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Established METTL7A/B chain-A, equal-number residue correspondence. */
public final class Mettl7ResidueCorrespondenceAdapter
        implements ResidueCorrespondenceProvider {
    public static final String DEFINITION = "METTL7_AB_DIRECT_NUMBERING_V1";

    @Override
    public ExplicitResidueCorrespondence correspond(
            List<SurfaceResidue> query,
            List<SurfaceResidue> subject) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(subject, "subject");
        Map<Integer, SurfaceResidue> subjectByNumber = indexChainA(subject);
        LinkedHashMap<ResidueId, ResidueId> mapping = new LinkedHashMap<>();
        for (SurfaceResidue residue : query) {
            requireChainAAndNoInsertion(residue);
            SurfaceResidue match = subjectByNumber.get(residue.id().residueNumber());
            if (match == null) {
                throw new IllegalArgumentException(
                        "missing subject residue " + residue.id().residueNumber());
            }
            mapping.put(residue.id(), match.id());
        }
        if (mapping.size() != subject.size()) {
            throw new IllegalArgumentException(
                    "METTL7 structures have unequal residue coverage");
        }
        return new ExplicitResidueCorrespondence(mapping);
    }

    private static Map<Integer, SurfaceResidue> indexChainA(
            List<SurfaceResidue> residues) {
        LinkedHashMap<Integer, SurfaceResidue> result = new LinkedHashMap<>();
        for (SurfaceResidue residue : residues) {
            requireChainAAndNoInsertion(residue);
            if (result.put(residue.id().residueNumber(), residue) != null) {
                throw new IllegalArgumentException("duplicate residue number");
            }
        }
        return Map.copyOf(result);
    }

    private static void requireChainAAndNoInsertion(SurfaceResidue residue) {
        if (!"A".equals(residue.id().chainId())
                || residue.id().insertionCode() != null) {
            throw new IllegalArgumentException(
                    "expected chain A without insertion codes: " + residue.id());
        }
        if (residue.residue().getAlphaCarbonPosition().isEmpty()) {
            throw new IllegalArgumentException(
                    "missing CA atom: " + residue.id());
        }
    }
}
