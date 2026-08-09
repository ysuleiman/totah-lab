package totah.lab.athena.pocket.component;

/** Pairwise evidence used to decide whether two cavities describe one site. */
public record PocketPairComparison(long firstPocketId, long secondPocketId,
        int sharedResidues, double residueJaccard,
        double minimumSphereSurfaceGap, double centroidDistance,
        int sharedCoveredLigandAtoms, double coveredLigandAtomJaccard,
        int sharedContactedLigandAtoms, double contactedLigandAtomJaccard,
        double minimumEngagedLigandAtomDistance,
        boolean sameChainContext, boolean sameHumanTargetContext,
        boolean samePhysicalSite) {}
