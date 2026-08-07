package totah.lab.daedalus.ligandprep;

import totah.lab.daedalus.ligandprep.PdbqtLigandReader.PdbqtAtom;
import totah.lab.daedalus.ligandprep.PdbqtLigandReader.PdbqtLigand;
import totah.lab.gaia.geometry.Point3D;

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
            Double maxCoordinateDelta
    ) {
    }

    public static LigandPrepComparison compare(
            PdbqtLigand ours,
            PdbqtLigand meeko
    ) {
        List<PdbqtAtom> ourHeavy = ours.heavyAtoms();
        List<PdbqtAtom> meekoHeavy = meeko.heavyAtoms();

        double ourTotal = ours.totalCharge();
        double meekoTotal = meeko.totalCharge();

        boolean[] matchedOurs = new boolean[ourHeavy.size()];
        int matched = 0;
        double chargeDeltaSum = 0.0;
        int typeAgreements = 0;
        double maxDelta = 0.0;

        for (PdbqtAtom meekoAtom : meekoHeavy) {
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
            if (best < 0) {
                continue;
            }

            matchedOurs[best] = true;
            matched++;
            PdbqtAtom ourAtom = ourHeavy.get(best);
            chargeDeltaSum += Math.abs(
                    ourAtom.charge() - meekoAtom.charge());
            if (ourAtom.ad4Type().equals(meekoAtom.ad4Type())) {
                typeAgreements++;
            }
            maxDelta = Math.max(maxDelta, bestDistance);
        }

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
                matched == 0 ? null : maxDelta
        );
    }

    private static double distance(Point3D first, Point3D second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
