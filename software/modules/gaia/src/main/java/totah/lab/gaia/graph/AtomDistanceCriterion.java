package totah.lab.gaia.graph;

import totah.lab.gaia.geometry.AtomSelection;

import java.util.Objects;

/** Neutral atom selection and distance cutoff for proximity queries. */
public record AtomDistanceCriterion(
        AtomSelection atomSelection,
        double cutoffAngstroms) {

    public AtomDistanceCriterion {
        Objects.requireNonNull(atomSelection, "atomSelection");
        validateCutoff(cutoffAngstroms);
    }

    public static AtomDistanceCriterion allAtomsWithin(
            double cutoffAngstroms) {

        return new AtomDistanceCriterion(
                AtomSelection.ALL,
                cutoffAngstroms);
    }

    public static AtomDistanceCriterion heavyAtomsWithin(
            double cutoffAngstroms) {

        return new AtomDistanceCriterion(
                AtomSelection.HEAVY,
                cutoffAngstroms);
    }

    static void validateCutoff(double cutoffAngstroms) {
        if (!Double.isFinite(cutoffAngstroms)
                || cutoffAngstroms <= 0.0) {
            throw new IllegalArgumentException(
                    "cutoffAngstroms must be finite and positive");
        }
    }
}
