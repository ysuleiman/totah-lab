package totah.lab.athena.relaxation;

import totah.lab.athena.clash.StericClashAnalysis;
import totah.lab.athena.energy.EnergyEvaluation;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Canonical reporting-only physical-validity evaluator for bounded relaxation. */
public final class PhysicalValidityEvaluator {
    private PhysicalValidityEvaluator() {}

    public static Result evaluate(Input input, Policy policy) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(policy, "policy");
        List<Failure> failures = new ArrayList<>();
        finiteCoordinates(input.receptor(), Component.PROTEIN, failures);
        finiteCoordinates(input.ligand(), Component.LIGAND, failures);
        if (input.sam() != null) finiteCoordinates(input.sam(), Component.SAM, failures);
        if (input.energy() == null || input.energy().components().values().stream()
                .anyMatch(value -> value == null || !Double.isFinite(value))) {
            failures.add(new Failure(Code.NONFINITE_ENERGY, Component.SYSTEM,
                    "energy is absent or contains a nonfinite component"));
        }
        topology(input.referenceLigand(), input.ligand(), Component.LIGAND, failures);
        topology(input.referenceReceptor(), input.receptor(), Component.PROTEIN, failures);
        topology(input.referenceSam(), input.sam(), Component.SAM, failures);
        if (!input.parametersComplete()) {
            failures.add(new Failure(Code.MISSING_PARAMETERS, Component.SYSTEM,
                    "parameter completeness gate failed"));
        }
        if (!input.atomMappingValid()) {
            failures.add(new Failure(Code.INVALID_ATOM_MAPPING, Component.SYSTEM,
                    "explicit atom mapping gate failed"));
        }
        if (!input.minimizationSucceeded()) {
            failures.add(new Failure(Code.MINIMIZATION_FAILURE, Component.SYSTEM,
                    "minimizer did not report success"));
        }
        List<StericClashAnalysis.Clash> clashes = StericClashAnalysis.findClashes(
                input.combinedStructure(), new StericClashAnalysis.Options(policy.severeClashRadiusScale()));
        if (clashes.size() > policy.maximumSevereClashes()) {
            failures.add(new Failure(Code.SEVERE_CLASH, Component.SYSTEM,
                    "canonical Athena severe-clash count " + clashes.size()
                            + " exceeds " + policy.maximumSevereClashes()));
        }
        DisplacementCalculator.DetailedDisplacements d = input.displacements();
        if (d != null) {
            if (d.ligandMaximumDisplacementAngstroms() > policy.maximumLigandDisplacementAngstroms()) {
                failures.add(new Failure(Code.RESTRAINT_ESCAPE, Component.LIGAND,
                        "ligand maximum displacement exceeds policy"));
            }
            if (d.ligandHeavyAtomRmsdAngstroms() > policy.maximumLigandRmsdAngstroms()) {
                failures.add(new Failure(Code.EXCESSIVE_LIGAND_DISTORTION, Component.LIGAND,
                        "ligand heavy-atom RMSD exceeds policy"));
            }
            if (d.backboneRmsdAngstroms() > policy.maximumBackboneRmsdAngstroms()
                    || d.localSideChainRmsdAngstroms() > policy.maximumLocalSideChainRmsdAngstroms()) {
                failures.add(new Failure(Code.EXCESSIVE_RECEPTOR_MOVEMENT, Component.PROTEIN,
                        "receptor displacement exceeds policy"));
            }
            if (d.samRmsdAngstroms() > policy.maximumSamRmsdAngstroms()) {
                failures.add(new Failure(Code.RESTRAINT_ESCAPE, Component.SAM,
                        "SAM RMSD exceeds policy"));
            }
        }
        return new Result(failures.isEmpty(), failures, clashes);
    }

    private static void finiteCoordinates(Structure structure, Component component,
            List<Failure> failures) {
        structure.getChains().forEach(chain -> chain.residues().forEach(residue ->
                residue.getAtoms().forEach(atom -> {
                    var p = atom.getPosition();
                    if (!Double.isFinite(p.x()) || !Double.isFinite(p.y()) || !Double.isFinite(p.z())) {
                        failures.add(new Failure(Code.NONFINITE_COORDINATE, component,
                                "nonfinite coordinate at " + chain.id() + ":"
                                        + residue.getNumber() + ":" + atom.getName()));
                    }
                })));
    }

    private static void topology(Structure reference, Structure observed, Component component,
            List<Failure> failures) {
        if (reference == null && observed == null) return;
        if (reference == null || observed == null || reference.getAtomCount() != observed.getAtomCount()
                || !atomReferences(reference).equals(atomReferences(observed))
                || !new HashSet<>(reference.bonds()).equals(new HashSet<>(observed.bonds()))) {
            failures.add(new Failure(switch (component) {
                case LIGAND -> Code.LIGAND_TOPOLOGY_INTEGRITY;
                case PROTEIN -> Code.PROTEIN_TOPOLOGY_INTEGRITY;
                case SAM -> Code.SAM_INTEGRITY;
                default -> throw new IllegalArgumentException("invalid topology component");
            }, component, "atom/bond topology differs from reference"));
        }
    }

    private static Set<AtomReference> atomReferences(Structure structure) {
        Set<AtomReference> result = new HashSet<>();
        structure.getChains().forEach(chain -> chain.residues().forEach(residue ->
                residue.getAtoms().forEach(atom -> result.add(new AtomReference(chain.id(),
                        residue.getNumber(), residue.getInsertionCode() == null ? ' ' : residue.getInsertionCode(),
                        atom.getName())))));
        return result;
    }

    public record Policy(double severeClashRadiusScale, int maximumSevereClashes,
            double maximumLigandDisplacementAngstroms, double maximumLigandRmsdAngstroms,
            double maximumBackboneRmsdAngstroms, double maximumLocalSideChainRmsdAngstroms,
            double maximumSamRmsdAngstroms) {
        public Policy {
            if (!Double.isFinite(severeClashRadiusScale) || severeClashRadiusScale <= 0
                    || maximumSevereClashes < 0) throw new IllegalArgumentException("invalid clash policy");
            for (double value : new double[]{maximumLigandDisplacementAngstroms,
                    maximumLigandRmsdAngstroms, maximumBackboneRmsdAngstroms,
                    maximumLocalSideChainRmsdAngstroms, maximumSamRmsdAngstroms}) {
                if (!Double.isFinite(value) || value < 0) {
                    throw new IllegalArgumentException("validity thresholds must be finite/nonnegative");
                }
            }
        }
    }

    public record Input(Structure referenceReceptor, Structure receptor,
            Structure referenceLigand, Structure ligand, Structure referenceSam, Structure sam,
            Structure combinedStructure, EnergyEvaluation energy,
            DisplacementCalculator.DetailedDisplacements displacements,
            boolean minimizationSucceeded, boolean parametersComplete, boolean atomMappingValid) {
        public Input {
            Objects.requireNonNull(referenceReceptor, "referenceReceptor");
            Objects.requireNonNull(receptor, "receptor");
            Objects.requireNonNull(referenceLigand, "referenceLigand");
            Objects.requireNonNull(ligand, "ligand");
            Objects.requireNonNull(combinedStructure, "combinedStructure");
        }
    }

    public record Result(boolean valid, List<Failure> failures,
            List<StericClashAnalysis.Clash> severeClashes) {
        public Result {
            failures = List.copyOf(failures);
            severeClashes = List.copyOf(severeClashes);
            if (valid != failures.isEmpty()) throw new IllegalArgumentException("inconsistent validity result");
        }
    }

    public record Failure(Code code, Component component, String detail) {
        public Failure {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(component, "component");
            if (detail == null || detail.isBlank()) throw new IllegalArgumentException("failure detail required");
        }
    }

    public enum Code { NONFINITE_COORDINATE, NONFINITE_ENERGY, LIGAND_TOPOLOGY_INTEGRITY,
        PROTEIN_TOPOLOGY_INTEGRITY, SAM_INTEGRITY, SEVERE_CLASH, RESTRAINT_ESCAPE,
        EXCESSIVE_LIGAND_DISTORTION, EXCESSIVE_RECEPTOR_MOVEMENT, MINIMIZATION_FAILURE,
        MISSING_PARAMETERS, INVALID_ATOM_MAPPING }
    public enum Component { SYSTEM, PROTEIN, LIGAND, SAM }
}
