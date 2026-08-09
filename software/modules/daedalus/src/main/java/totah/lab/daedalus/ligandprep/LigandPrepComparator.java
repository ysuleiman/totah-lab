package totah.lab.daedalus.ligandprep;

import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.gaia.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;

/**
 * Compares one hephaestus-prepared ligand PDBQT against its Meeko
 * reference. Both writers emit atoms in torsion-tree order, and the
 * trees differ, so heavy atoms are aligned by COORDINATES: both
 * pipelines preserve the source SDF coordinates (to the PDBQT's
 * 3-decimal precision), making each atom's position its identity.
 * Each Meeko heavy atom is matched to the nearest hephaestus heavy
 * atom within {@value #MATCH_TOLERANCE_ANGSTROMS} Å (greedy, unique);
 * hydrogens are excluded because Meeko merges non-polar hydrogens
 * while hephaestus keeps the explicit ones.
 */
public final class LigandPrepComparator {

    static final double MATCH_TOLERANCE_ANGSTROMS = 0.02;

    private LigandPrepComparator() {
    }

    public record LigandPrepComparison(
            int ourHeavyAtoms,
            int meekoHeavyAtoms,
            int matchedHeavyAtoms,
            boolean atomCountsMatch,
            double ourTotalCharge,
            double meekoTotalCharge,
            double totalChargeDelta,
            Double meanAbsChargeDelta,
            Double ad4TypeAgreement,
            int ourTorsdof,
            int meekoTorsdof,
            int torsdofDelta,
            int rotorsOurs,
            int rotorsMeeko,
            int rotorsMatched,
            Double maxCoordinateDelta
    ) {
    }

    /**
     * True when the rotatable-bond identity sets differ, even if the
     * torsion counts agree.
     */
    static boolean rotorSetsDiffer(LigandPrepComparison comparison) {
        return comparison.rotorsMatched() != comparison.rotorsOurs()
                || comparison.rotorsMatched()
                != comparison.rotorsMeeko();
    }

    public static LigandPrepComparison compare(
            PdbqtModel ours,
            PdbqtModel meeko
    ) {
        List<PdbqtAtom> ourHeavy = ours.heavyAtoms();
        List<PdbqtAtom> meekoHeavy = meeko.heavyAtoms();

        double ourTotal = ours.totalCharge();
        double meekoTotal = meeko.totalCharge();

        List<int[]> matches = matchHeavyAtoms(ourHeavy, meekoHeavy);

        int typeAgreements = 0;
        double chargeDeltaSum = 0.0;
        double maxDelta = 0.0;
        for (int[] match : matches) {
            PdbqtAtom ourAtom = ourHeavy.get(match[0]);
            PdbqtAtom meekoAtom = meekoHeavy.get(match[1]);
            chargeDeltaSum += Math.abs(
                    ourAtom.partialCharge() - meekoAtom.partialCharge());
            if (ourAtom.autodockType().equals(meekoAtom.autodockType())) {
                typeAgreements++;
            }
            maxDelta = Math.max(maxDelta, match[2] / 1000.0);
        }

        java.util.Set<String> ourRotors = rotorKeys(ours);
        java.util.Set<String> meekoRotors = rotorKeys(meeko);
        int rotorsOurs = ourRotors.size();
        int rotorsMeeko = meekoRotors.size();
        int rotorsMatched = (int) ourRotors.stream()
                .filter(meekoRotors::contains)
                .count();

        int matched = matches.size();
        return new LigandPrepComparison(
                ourHeavy.size(),
                meekoHeavy.size(),
                matched,
                ourHeavy.size() == meekoHeavy.size(),
                ourTotal,
                meekoTotal,
                Math.abs(ourTotal - meekoTotal),
                matched == 0 ? null : chargeDeltaSum / matched,
                matched == 0 ? null : (double) typeAgreements / matched,
                ours.torsdof(),
                meeko.torsdof(),
                Math.abs(ours.torsdof() - meeko.torsdof()),
                rotorsOurs,
                rotorsMeeko,
                rotorsMatched,
                matched == 0 ? null : maxDelta
        );
    }

    /*
     * Rotatable-bond identity: each BRANCH bond becomes a key of its
     * two atoms' coordinates (both writers preserve source
     * coordinates), so the sets compare order-independently.
     */
    private static java.util.Set<String> rotorKeys(PdbqtModel ligand) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (int[] bond : ligand.rotatableBondSerials()) {
            Point3D first = ligand.atoms().get(bond[0] - 1).position();
            Point3D second = ligand.atoms().get(bond[1] - 1).position();
            String a = key(first);
            String b = key(second);
            keys.add(a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a);
        }
        return keys;
    }

    private static String key(Point3D point) {
        return String.format(java.util.Locale.ROOT,
                "%.3f,%.3f,%.3f", point.x(), point.y(), point.z());
    }

    /**
     * Greedy unique nearest-coordinate matching of heavy atoms
     * (both pipelines preserve source coordinates at PDBQT
     * precision). Each entry is {ourIndex, meekoIndex,
     * distanceInMilliAngstrom}.
     */
    public static List<int[]> matchHeavyAtoms(
            List<PdbqtAtom> ourHeavy,
            List<PdbqtAtom> meekoHeavy
    ) {
        boolean[] matchedOurs = new boolean[ourHeavy.size()];
        List<int[]> matches = new ArrayList<>();

        for (int meekoIndex = 0; meekoIndex < meekoHeavy.size();
                meekoIndex++) {
            PdbqtAtom meekoAtom = meekoHeavy.get(meekoIndex);
            int best = -1;
            double bestDistance = MATCH_TOLERANCE_ANGSTROMS;
            for (int index = 0; index < ourHeavy.size(); index++) {
                if (matchedOurs[index]) {
                    continue;
                }
                double distance = distance(
                        ourHeavy.get(index).position(),
                        meekoAtom.position());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = index;
                }
            }
            if (best >= 0) {
                matchedOurs[best] = true;
                matches.add(new int[]{
                        best,
                        meekoIndex,
                        (int) Math.round(bestDistance * 1000.0)
                });
            }
        }
        return matches;
    }

    private static double distance(Point3D first, Point3D second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
