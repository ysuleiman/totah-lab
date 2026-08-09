package totah.lab.web.poseanalysis;

import org.springframework.stereotype.Service;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.PdbWriteOptions;
import totah.lab.hermes.file.pdb.writer.PdbWriter;
import totah.lab.hermes.file.pdbqt.PdbqtWriteOptions;
import totah.lab.hermes.file.pdbqt.writer.PdbqtWriter;
import totah.lab.proteus.protein.mutation.AmbiguousCovalentTopologyPolicy;
import totah.lab.proteus.protein.mutation.AppliedMutation;
import totah.lab.proteus.protein.mutation.Mutation;
import totah.lab.proteus.protein.mutation.MutationContext;
import totah.lab.proteus.protein.mutation.MutationPurpose;
import totah.lab.proteus.protein.mutation.MutationRequest;
import totah.lab.proteus.protein.mutation.MutationSet;
import totah.lab.proteus.protein.mutation.ProteinMutationOperation;
import totah.lab.proteus.protein.variant.ProteinVariant;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prepares a docking-ready mutant receptor from a persisted docking
 * run: the run's receptor is loaded through the poseanalysis
 * seam (the same chemflow artifact the pose analyses read, so the
 * coordinate frame is identical to wild type), the substitution — or
 * a comma-separated list of substitutions applied sequentially — is
 * applied with proteus' fixed-backbone {@link ProteinMutationOperation},
 * and the result is written with hermes' {@link PdbqtWriter} or
 * {@link PdbWriter} — chosen by the output file extension
 * ({@code .pdb} for DiffDock pipelines, {@code .pdbqt} otherwise).
 *
 * <p>Proteus builds the new side chain from its side-chain template
 * library; those atoms carry an element but no AutoDock4 type and a
 * zero charge, which the writer would reject. The typing remedy here is
 * deliberately conservative: type and charge for each new side-chain
 * atom are copied from the same-named atoms of an exemplar residue of
 * the replacement type elsewhere in the same wild-type receptor (the
 * Meeko/obabel-prepared WT atoms are the typing reference this pipeline
 * already trusts). When the receptor has no exemplar of the replacement
 * type the preparation fails with a clear message instead of inventing
 * chemistry.</p>
 *
 * <p>PDBQT files carry no explicit connectivity, so the proteus
 * ambiguous-covalent-topology check runs as WARN_AND_PROCEED; the
 * warning is carried into the variant provenance and printed.</p>
 */
@Service
public class MutationPreparationService implements MutationPreparationOperation {

    private static final Pattern SPEC =
            Pattern.compile("^([A-Za-z])(\\d+)([A-Za-z])$");

    /** Backbone atom names, mirroring proteus' fixed-backbone set. */
    private static final List<String> BACKBONE = List.of(
            "N", "CA", "C", "O", "OXT", "H", "HA", "HA2", "HA3");

    private static final Map<String, String> ONE_TO_THREE = oneToThree();

    private final PoseAnalysisService poseAnalysis;
    private final PoseAnalysisRepository repository;
    private final ProteinMutationOperation mutationOperation =
            new ProteinMutationOperation();
    private final PdbqtWriter pdbqtWriter = new PdbqtWriter();
    private final PdbWriter pdbWriter = new PdbWriter();

    public MutationPreparationService(
            PoseAnalysisService poseAnalysis,
            PoseAnalysisRepository repository
    ) {
        this.poseAnalysis =
                Objects.requireNonNull(poseAnalysis, "poseAnalysis");
        this.repository =
                Objects.requireNonNull(repository, "repository");
    }

    /** One applied substitution of a preparation run. */
    public record AppliedMutationReport(
            String spec,
            ResidueId target,
            String wildType,
            String mutant,
            String rotamerId,
            double stericScore,
            List<String> targetAtomsBefore,
            List<String> targetAtomsAfter,
            ResidueId typingExemplar
    ) {
    }

    /** Everything produced by one mutation preparation. */
    public record MutationPreparationResult(
            long runId,
            String targetName,
            Long receptorId,
            String mutationSpec,
            List<AppliedMutationReport> mutations,
            String rotamerMethod,
            List<String> warnings,
            Structure wildTypeStructure,
            Structure mutantStructure,
            Path outputPath
    ) {
    }

    /**
     * Applies {@code mutationSpec} — a compact spec such as
     * {@code F43L} or a comma-separated list applied sequentially to
     * the same receptor, such as {@code F39L,L40M,V41A,R42V,F43L} —
     * to the receptor of docking run {@code runId} and writes the
     * mutant receptor to {@code output} (parent directories are
     * created; the format follows the extension).
     *
     * @throws IllegalStateException when the run, receptor artifact,
     *         residue, wild type or typing exemplar cannot be resolved —
     *         the message states exactly what is wrong
     */
    public MutationPreparationResult prepare(
            long runId,
            String mutationSpec,
            Path output
    ) {
        if (output == null) {
            throw new IllegalStateException(
                    "An output PDBQT path is required for mutation"
                            + " preparation"
            );
        }
        PoseRunProjection run = repository.findRun(runId)
                .orElseThrow(() -> new IllegalStateException(
                        "No docking run " + runId
                ));

        Structure wildType;
        try {
            wildType = poseAnalysis.receptorStructure(
                    run, new HashMap<>());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Receptor of run " + runId + " cannot be loaded: "
                            + exception.getMessage(),
                    exception
            );
        }

        List<String> specs = splitSpecs(mutationSpec);

        // PDBQT carries no explicit connectivity; the proteus
        // ambiguous-topology check is downgraded to a provenance
        // warning rather than a hard failure.
        MutationContext context = new MutationContext(
                AmbiguousCovalentTopologyPolicy.WARN_AND_PROCEED,
                false,
                false,
                0.0
        );
        String parentTarget = run.getTargetName() == null
                ? "receptor-" + run.getReceptorId()
                : run.getTargetName();

        // Sequential application: each spec is validated against the
        // structure the previous substitutions produced, so composite
        // specs are checked at every step.
        Structure current = wildType;
        List<Mutation> mutations = new ArrayList<>();
        List<AppliedMutation> applied = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        String rotamerMethod = null;
        for (int index = 0; index < specs.size(); index++) {
            Mutation mutation = parse(specs.get(index), current, runId);
            MutationSet set = new MutationSet(
                    "mutation-prep-run-" + runId + "-" + (index + 1),
                    parentTarget,
                    List.of(mutation),
                    MutationPurpose.SELECTIVITY_VALIDATION
            );
            ProteinVariant variant = mutationOperation.apply(
                    new MutationRequest(current, set, context));
            applied.addAll(variant.provenance().appliedMutations());
            warnings.addAll(variant.provenance().warnings());
            rotamerMethod = variant.provenance().rotamerMethod();
            mutations.add(mutation);
            current = variant.structure();
        }

        // Type every new side chain from the WT receptor's exemplars
        // (the original structure still holds the wild-type residue at
        // every mutated position, so exemplars are always wild-type).
        Structure typed = current;
        List<ResidueId> exemplars = new ArrayList<>();
        for (Mutation mutation : mutations) {
            ResidueId exemplar = typingExemplar(
                    wildType,
                    mutation.replacementResidueName(),
                    runId
            );
            typed = typeMutatedSideChain(
                    typed, wildType, mutation, exemplar);
            exemplars.add(exemplar);
        }

        try {
            writeStructure(typed, output);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Mutant receptor cannot be written to " + output
                            + ": " + exception.getMessage(),
                    exception
            );
        }

        List<AppliedMutationReport> reports = new ArrayList<>();
        for (int index = 0; index < mutations.size(); index++) {
            Mutation mutation = mutations.get(index);
            reports.add(new AppliedMutationReport(
                    specs.get(index),
                    mutation.target(),
                    mutation.expectedResidueName(),
                    mutation.replacementResidueName(),
                    applied.get(index).rotamerId(),
                    applied.get(index).stericScore(),
                    atomNames(findResidue(
                            wildType, mutation.target()).orElseThrow()),
                    atomNames(findResidue(
                            typed, mutation.target()).orElseThrow()),
                    exemplars.get(index)
            ));
        }

        return new MutationPreparationResult(
                runId,
                run.getTargetName(),
                run.getReceptorId(),
                String.join(",", specs),
                List.copyOf(reports),
                rotamerMethod,
                List.copyOf(warnings),
                wildType,
                typed,
                output.toAbsolutePath().normalize()
        );
    }

    /**
     * Splits the spec argument into normalized single-mutation specs:
     * one spec ({@code F43L}) or a comma-separated list
     * ({@code F39L,L40M,...}).
     */
    private static List<String> splitSpecs(String mutationSpec) {
        if (mutationSpec == null || mutationSpec.isBlank()) {
            throw new IllegalStateException(
                    "A mutation spec is required, for example F43L or"
                            + " F39L,L40M"
            );
        }
        List<String> specs = new ArrayList<>();
        for (String token : mutationSpec.split(",")) {
            String spec = token.trim().toUpperCase(Locale.ROOT);
            if (spec.isEmpty()) {
                throw new IllegalStateException(
                        "Empty mutation spec in list: " + mutationSpec
                );
            }
            specs.add(spec);
        }
        return List.copyOf(specs);
    }

    /**
     * The output writer is chosen by file extension: {@code .pdb}
     * produces a standard PDB (the DiffDock input format),
     * {@code .pdbqt} — and any other extension, keeping the original
     * behavior — produces PDBQT.
     */
    private void writeStructure(Structure typed, Path output)
            throws IOException {
        String filename = output.getFileName() == null
                ? ""
                : output.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".pdb")) {
            pdbWriter.write(typed, output, PdbWriteOptions.defaults());
            return;
        }
        pdbqtWriter.write(typed, output, PdbqtWriteOptions.defaults());
    }

    /**
     * The provenance report the runner prints to stdout. */
    public String render(MutationPreparationResult result) {
        StringBuilder report = new StringBuilder();
        report.append("Mutation preparation — mutant receptor for")
                .append(" external docking\n");
        report.append("Run ").append(result.runId())
                .append(" (").append(result.targetName())
                .append(", receptor ").append(result.receptorId())
                .append(")\n");
        report.append("Mutations: ").append(result.mutationSpec())
                .append(" (").append(result.mutations().size())
                .append(" substitution")
                .append(result.mutations().size() == 1 ? "" : "s")
                .append(")\n");
        for (AppliedMutationReport mutation : result.mutations()) {
            report.append("  ").append(mutation.spec())
                    .append(": ").append(mutation.wildType())
                    .append(' ').append(mutation.target().chainId())
                    .append(':')
                    .append(mutation.target().residueNumber())
                    .append(" -> ").append(mutation.mutant())
                    .append("; rotamer ").append(mutation.rotamerId())
                    .append(String.format(
                            Locale.ROOT,
                            " (steric score %.4f)",
                            mutation.stericScore()
                    ))
                    .append("; typing exemplar ")
                    .append(mutation.mutant())
                    .append(' ')
                    .append(mutation.typingExemplar().chainId())
                    .append(':')
                    .append(mutation.typingExemplar().residueNumber())
                    .append('\n');
            report.append("    before: ")
                    .append(mutation.targetAtomsBefore())
                    .append('\n');
            report.append("    after:  ")
                    .append(mutation.targetAtomsAfter())
                    .append('\n');
        }
        report.append("Rotamer method: ").append(result.rotamerMethod())
                .append('\n');
        if (result.warnings().isEmpty()) {
            report.append("Warnings: none\n");
        } else {
            report.append("Warnings:\n");
            for (String warning : result.warnings()) {
                report.append("  - ").append(warning).append('\n');
            }
        }
        report.append("Atoms: WT ")
                .append(result.wildTypeStructure().getAtomCount())
                .append(" -> mutant ")
                .append(result.mutantStructure().getAtomCount())
                .append('\n');
        report.append("Coordinate frame: fixed-backbone mutation;")
                .append(" backbone and all other residues keep their WT")
                .append(" coordinates\n");
        report.append("Output: ").append(result.outputPath()).append('\n');
        return report.toString();
    }

    /**
     * Parses a compact spec such as {@code F43L} into a proteus
     * {@link Mutation}: wild-type one-letter code, residue number,
     * mutant one-letter code. The wild type is validated against the
     * actual residue at that position of the loaded receptor.
     */
    private Mutation parse(
            String mutationSpec,
            Structure wildType,
            long runId
    ) {
        String spec = mutationSpec == null
                ? ""
                : mutationSpec.trim().toUpperCase(Locale.ROOT);
        Matcher matcher = SPEC.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalStateException(
                    "Mutation spec must be a wild-type letter, the"
                            + " residue number and a mutant letter, for"
                            + " example F43L: " + mutationSpec
            );
        }
        String expected = threeLetter(matcher.group(1));
        String replacement = threeLetter(matcher.group(3));
        int position;
        try {
            position = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(
                    "Mutation spec residue number is not a number: "
                            + mutationSpec
            );
        }

        Residue residue = null;
        String chainId = null;
        for (Chain chain : wildType.getChains()) {
            for (Residue candidate : chain.residues()) {
                if (candidate.getNumber() == position) {
                    residue = candidate;
                    chainId = chain.id();
                    break;
                }
            }
            if (residue != null) {
                break;
            }
        }
        if (residue == null) {
            throw new IllegalStateException(
                    "Mutation " + spec + ": the receptor of run " + runId
                            + " has no residue " + position
            );
        }
        if (!residue.getName().equalsIgnoreCase(expected)) {
            throw new IllegalStateException(
                    "Mutation " + spec + " expects " + expected + " at "
                            + chainId + ':' + position + " but the"
                            + " receptor of run " + runId + " has "
                            + residue.getName() + " there"
            );
        }
        return new Mutation(
                new ResidueId(
                        chainId,
                        position,
                        residue.getInsertionCode()
                ),
                expected,
                replacement
        );
    }

    /**
     * The first wild-type residue of the replacement type — the typing
     * exemplar for the new side chain. Fails clearly when the receptor
     * has none: AutoDock4 types are copied from the WT receptor's own
     * preparation, never invented.
     */
    private static ResidueId typingExemplar(
            Structure wildType,
            String replacementResidueName,
            long runId
    ) {
        for (Chain chain : wildType.getChains()) {
            for (Residue residue : chain.residues()) {
                if (residue.getName()
                        .equalsIgnoreCase(replacementResidueName)) {
                    return new ResidueId(
                            chain.id(),
                            residue.getNumber(),
                            residue.getInsertionCode()
                    );
                }
            }
        }
        throw new IllegalStateException(
                "The receptor of run " + runId + " has no "
                        + replacementResidueName + " residue; the new"
                        + " side chain cannot be typed without a"
                        + " wild-type exemplar (AutoDock4 types and"
                        + " charges are copied from the receptor's own"
                        + " preparation, never invented)"
        );
    }

    /**
     * Rebuilds the mutated residue's new side-chain atoms with the
     * AutoDock4 type and charge of the same-named exemplar atoms, so
     * the result passes {@link PdbqtWriter}'s per-atom validation.
     * Backbone atoms and every other residue keep the WT atoms
     * untouched.
     */
    private static Structure typeMutatedSideChain(
            Structure mutant,
            Structure wildType,
            Mutation mutation,
            ResidueId exemplarId
    ) {
        Residue exemplar = findResidue(wildType, exemplarId)
                .orElseThrow(() -> new IllegalStateException(
                        "Typing exemplar " + exemplarId
                                + " not found in the wild-type receptor"
                ));
        Map<String, Atom> exemplarAtoms = new LinkedHashMap<>();
        for (Atom atom : exemplar.getAtoms()) {
            exemplarAtoms.putIfAbsent(atom.getName(), atom);
        }

        List<Chain> chains = new ArrayList<>();
        for (Chain chain : mutant.getChains()) {
            List<Residue> residues = new ArrayList<>();
            for (Residue residue : chain.residues()) {
                if (chain.id().equals(mutation.target().chainId())
                        && residue.getNumber()
                        == mutation.target().residueNumber()
                        && Objects.equals(
                                residue.getInsertionCode(),
                                mutation.target().insertionCode())) {
                    residues.add(retype(
                            residue,
                            exemplarAtoms,
                            mutation.target()
                    ));
                } else {
                    residues.add(residue);
                }
            }
            chains.add(new Chain(chain.id(), residues));
        }
        return new Structure(
                chains,
                mutant.bonds(),
                mutant.getConnectivityMetadata()
        );
    }

    private static Residue retype(
            Residue residue,
            Map<String, Atom> exemplarAtoms,
            ResidueId target
    ) {
        List<Atom> atoms = new ArrayList<>();
        for (Atom atom : residue.getAtoms()) {
            if (BACKBONE.contains(atom.getName())) {
                atoms.add(atom);
                continue;
            }
            Atom exemplar = exemplarAtoms.get(atom.getName());
            if (exemplar == null
                    || exemplar.getAutoDockType() == null) {
                throw new IllegalStateException(
                        "No typed exemplar atom " + atom.getName()
                                + " for the new side chain of " + target
                                + "; the receptor's preparation has no"
                                + " type to copy"
                );
            }
            atoms.add(atom.toBuilder()
                    .autoDockType(exemplar.getAutoDockType())
                    .charge(exemplar.getCharge())
                    .build());
        }
        return new Residue(
                residue.getName(),
                residue.getNumber(),
                residue.getInsertionCode(),
                residue.getClassificationEvidence(),
                atoms
        );
    }

    private static java.util.Optional<Residue> findResidue(
            Structure structure,
            ResidueId id
    ) {
        for (Chain chain : structure.getChains()) {
            if (!chain.id().equals(id.chainId())) {
                continue;
            }
            for (Residue residue : chain.residues()) {
                if (residue.getNumber() == id.residueNumber()
                        && Objects.equals(
                                residue.getInsertionCode(),
                                id.insertionCode())) {
                    return java.util.Optional.of(residue);
                }
            }
        }
        return java.util.Optional.empty();
    }

    private static List<String> atomNames(Residue residue) {
        return residue.getAtoms().stream()
                .map(Atom::getName)
                .toList();
    }

    /** Package-private: shared by the mutation-pose report service. */
    static String threeLetter(String oneLetter) {
        String name = ONE_TO_THREE.get(
                oneLetter.toUpperCase(Locale.ROOT));
        if (name == null) {
            throw new IllegalStateException(
                    "Not a standard amino-acid one-letter code: "
                            + oneLetter
            );
        }
        return name;
    }

    private static Map<String, String> oneToThree() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("A", "ALA");
        map.put("R", "ARG");
        map.put("N", "ASN");
        map.put("D", "ASP");
        map.put("C", "CYS");
        map.put("Q", "GLN");
        map.put("E", "GLU");
        map.put("G", "GLY");
        map.put("H", "HIS");
        map.put("I", "ILE");
        map.put("L", "LEU");
        map.put("K", "LYS");
        map.put("M", "MET");
        map.put("F", "PHE");
        map.put("P", "PRO");
        map.put("S", "SER");
        map.put("T", "THR");
        map.put("W", "TRP");
        map.put("Y", "TYR");
        map.put("V", "VAL");
        return Map.copyOf(map);
    }
}
