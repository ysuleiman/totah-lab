package totah.lab.athena.relaxation;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Structure;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Fixed-frame displacement metrics over explicit, bijective atom maps. */
public final class DisplacementCalculator {
    private DisplacementCalculator() {}

    public static DetailedDisplacements calculate(Inputs inputs) {
        Objects.requireNonNull(inputs, "inputs");
        Metrics ligand = metrics(inputs.beforeLigand(), inputs.afterLigand(),
                inputs.ligandHeavyAtoms(), true, "ligand");
        Metrics backbone = metrics(inputs.beforeReceptor(), inputs.afterReceptor(),
                inputs.backboneAtoms(), false, "backbone");
        Metrics sideChains = metrics(inputs.beforeReceptor(), inputs.afterReceptor(),
                inputs.localSideChainAtoms(), false, "local side-chain");
        Metrics sam = metrics(inputs.beforeSam(), inputs.afterSam(), inputs.samAtoms(),
                false, "SAM");
        double maximum = Math.max(Math.max(ligand.maximum(), backbone.maximum()),
                Math.max(sideChains.maximum(), sam.maximum()));
        return new DetailedDisplacements(ligand.rmsd(), ligand.maximum(), backbone.rmsd(),
                sideChains.rmsd(), sam.rmsd(), sam.maximum(), maximum,
                "FIXED_RECEPTOR_FRAME_NO_SUPERPOSITION");
    }

    private static Metrics metrics(Structure before, Structure after,
            Map<AtomReference, AtomReference> mapping, boolean requireHeavy,
            String label) {
        Objects.requireNonNull(before, "before " + label);
        Objects.requireNonNull(after, "after " + label);
        Objects.requireNonNull(mapping, label + " mapping");
        if (mapping.isEmpty()) {
            return new Metrics(0.0, 0.0);
        }
        if (mapping.values().stream().distinct().count() != mapping.size()) {
            throw new IllegalArgumentException(label + " atom mapping is not bijective");
        }
        double sumSquares = 0.0;
        double maximum = 0.0;
        for (Map.Entry<AtomReference, AtomReference> pair : mapping.entrySet()) {
            Atom first = before.findAtom(pair.getKey()).orElseThrow(() ->
                    new IllegalArgumentException("unresolved pre-" + label + " atom " + pair.getKey()));
            Atom second = after.findAtom(pair.getValue()).orElseThrow(() ->
                    new IllegalArgumentException("unresolved post-" + label + " atom " + pair.getValue()));
            if (requireHeavy && (!first.isHeavyAtom() || !second.isHeavyAtom())) {
                throw new IllegalArgumentException(label + " mapping contains non-heavy atom");
            }
            double distance = distance(first.getPosition(), second.getPosition());
            sumSquares += distance * distance;
            maximum = Math.max(maximum, distance);
        }
        return new Metrics(Math.sqrt(sumSquares / mapping.size()), maximum);
    }

    private static double distance(Point3D first, Point3D second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(distance)) {
            throw new IllegalArgumentException("nonfinite coordinate displacement");
        }
        return distance;
    }

    public record Inputs(Structure beforeReceptor, Structure afterReceptor,
            Structure beforeLigand, Structure afterLigand,
            Structure beforeSam, Structure afterSam,
            Map<AtomReference, AtomReference> ligandHeavyAtoms,
            Map<AtomReference, AtomReference> backboneAtoms,
            Map<AtomReference, AtomReference> localSideChainAtoms,
            Map<AtomReference, AtomReference> samAtoms) {
        public Inputs {
            ligandHeavyAtoms = copy(ligandHeavyAtoms);
            backboneAtoms = copy(backboneAtoms);
            localSideChainAtoms = copy(localSideChainAtoms);
            samAtoms = copy(samAtoms);
        }

        private static Map<AtomReference, AtomReference> copy(
                Map<AtomReference, AtomReference> value) {
            return Map.copyOf(Objects.requireNonNull(value, "mapping"));
        }
    }

    public record DetailedDisplacements(double ligandHeavyAtomRmsdAngstroms,
            double ligandMaximumDisplacementAngstroms, double backboneRmsdAngstroms,
            double localSideChainRmsdAngstroms, double samRmsdAngstroms,
            double samMaximumDisplacementAngstroms,
            double maximumSystemDisplacementAngstroms, String frameConvention) {
        public DetailedDisplacements {
            for (double value : new double[]{ligandHeavyAtomRmsdAngstroms,
                    ligandMaximumDisplacementAngstroms, backboneRmsdAngstroms,
                    localSideChainRmsdAngstroms, samRmsdAngstroms,
                    samMaximumDisplacementAngstroms,
                    maximumSystemDisplacementAngstroms}) {
                if (!Double.isFinite(value) || value < 0.0) {
                    throw new IllegalArgumentException("displacements must be finite and nonnegative");
                }
            }
            if (!"FIXED_RECEPTOR_FRAME_NO_SUPERPOSITION".equals(frameConvention)) {
                throw new IllegalArgumentException("unsupported frame convention");
            }
        }

        public RelaxationEvidence.Displacements asRelaxationEvidence() {
            return new RelaxationEvidence.Displacements(ligandHeavyAtomRmsdAngstroms,
                    localSideChainRmsdAngstroms, backboneRmsdAngstroms,
                    samRmsdAngstroms, maximumSystemDisplacementAngstroms);
        }
    }

    private record Metrics(double rmsd, double maximum) {}
}
