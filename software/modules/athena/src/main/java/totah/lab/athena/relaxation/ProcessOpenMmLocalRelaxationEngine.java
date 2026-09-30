package totah.lab.athena.relaxation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.athena.energy.EnergyEvaluationException;
import totah.lab.athena.energy.MolecularState;
import totah.lab.athena.energy.openmm.OpenMmForceGroupMap;
import totah.lab.athena.energy.openmm.OpenMmProcessExecutor;
import totah.lab.athena.energy.openmm.OpenMmResponseValidator;
import totah.lab.athena.energy.openmm.OpenMmSystemManifest;
import totah.lab.athena.energy.openmm.OpenMmSystemManifestLoader;
import totah.lab.athena.energy.openmm.ProcessOpenMmBackend;
import totah.lab.athena.energy.openmm.VerifiedOpenMmSystem;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** OpenMM-backed deterministic bounded minimization; never performs dynamics. */
public final class ProcessOpenMmLocalRelaxationEngine implements LocalRelaxationEngine {
    private final OpenMmSystemManifestLoader manifestLoader = new OpenMmSystemManifestLoader();
    private final OpenMmProcessExecutor executor;
    private final OpenMmForceGroupMap forceGroups;
    private final RelaxationAtomSelector selector = new RelaxationAtomSelector();
    private final ObjectMapper mapper;

    public ProcessOpenMmLocalRelaxationEngine(Path pythonExecutable, Path runnerScript,
            Path receiptDirectory, OpenMmForceGroupMap forceGroups) {
        this.executor = new OpenMmProcessExecutor(pythonExecutable, runnerScript, receiptDirectory);
        this.mapper = executor.mapper();
        this.forceGroups = Objects.requireNonNull(forceGroups, "forceGroups");
    }

    @Override
    public Result relax(MolecularState state, LocalRelaxationProtocol protocol)
            throws EnergyEvaluationException {
        return relaxInternal(state, protocol, null);
    }

    /** Additive apo-capable path. No dummy ligand or ligand-centered selection is used. */
    public Result relax(MolecularState state, LocalRelaxationProtocol protocol, ProteinDefinedMask mask)
            throws EnergyEvaluationException {
        return relaxInternal(state, protocol, Objects.requireNonNull(mask, "mask"));
    }

    private Result relaxInternal(MolecularState state, LocalRelaxationProtocol protocol,
            ProteinDefinedMask mask) throws EnergyEvaluationException {
        try {
            validateSupported(protocol);
            String manifestPath = state.provenance().get(ProcessOpenMmBackend.MANIFEST_PROVENANCE_KEY);
            if (manifestPath == null || manifestPath.isBlank()) {
                throw stateFailure("state lacks frozen OpenMM manifest");
            }
            VerifiedOpenMmSystem verified = manifestLoader.load(Path.of(manifestPath));
            if (verified.manifest().systemFormat() != OpenMmSystemManifest.SystemFormat.OPENMM_XML) {
                throw stateFailure("bounded minimization requires frozen OPENMM_XML");
            }
            if (!verified.manifest().systemAtomMappingSha256()
                    .equals(protocol.systemAtomMapping().sha256())) {
                throw stateFailure("protocol atom mapping is not the mapping frozen by the system manifest");
            }
            validateGroups(verified.manifest());
            Structure sam = state.cofactor().orElse(null);
            RelaxationAtomSelector.Selection selection = mask == null
                    ? selector.select(protocol, state.receptor(), state.ligand(), sam)
                    : selector.select(protocol, state.receptor(), state.ligand(), sam, mask);
            ObjectNode request = request(state, protocol, verified, selection);
            OpenMmProcessExecutor.Execution execution = executor.execute(request);
            JsonNode response = execution.response();
            OpenMmResponseValidator.validateMinimization(response,
                    forceGroups.groups().keySet(), "CPU",
                    Map.of("Threads", protocol.cpuThreads().toString(),
                            "DeterministicForces", Boolean.toString(protocol.deterministicForces())),
                    protocol.systemAtomMapping().entries().size());
            List<Point3D> positions = parsePositions(response.required("positionsNanometres"));
            if (positions.size() != protocol.systemAtomMapping().entries().size()) {
                throw stateFailure("OpenMM output particle count differs from explicit atom mapping");
            }
            Structure relaxedReceptor = update(state.receptor(), protocol.systemAtomMapping(),
                    positions, false, false);
            Structure relaxedLigand = update(state.ligand(), protocol.systemAtomMapping(),
                    positions, true, false);
            Structure relaxedSam = sam == null ? new Structure(List.of())
                    : update(sam, protocol.systemAtomMapping(), positions, false, true);
            DisplacementCalculator.DetailedDisplacements detailedDisplacements = displacements(state,
                    relaxedReceptor, relaxedLigand, relaxedSam, protocol, selection);
            RelaxationEvidence.Displacements displacements = detailedDisplacements.asRelaxationEvidence();
            boolean converged = response.required("converged").asBoolean();
            List<String> failures = new ArrayList<>();
            if (!converged) failures.add("MINIMIZATION_NOT_CONVERGED");
            if (protocol.ligandRestraint().policy() != LocalRelaxationProtocol.Policy.NONE
                    && detailedDisplacements.ligandMaximumDisplacementAngstroms()
                    > protocol.ligandRestraint().maximumDisplacementAngstroms()) {
                failures.add("LIGAND_RESTRAINT_ESCAPE");
            }
            if (protocol.samRestraint().policy() != LocalRelaxationProtocol.Policy.NONE
                    && detailedDisplacements.samMaximumDisplacementAngstroms()
                    > protocol.samRestraint().maximumDisplacementAngstroms()) {
                failures.add("SAM_RESTRAINT_ESCAPE");
            }
            Map<String, String> provenance = new LinkedHashMap<>(execution.receiptProvenance());
            provenance.put("openmm.version", response.required("openMmVersion").asText());
            provenance.put("openmm.platform", response.required("platform").asText());
            provenance.put("mapping.sha256", selection.mappingSha256());
            provenance.put("selection.semantic.sha256", selection.semanticPolicySha256());
            provenance.put("selection.mask.sha256", selection.selectionMaskSha256());
            provenance.put("selection.rules", selection.selectionRuleProvenance());
            provenance.put("pre.energy.kj_per_mol",
                    response.required("preMinimizationTotalKilojoulesPerMole").asText());
            provenance.put("post.energy.kj_per_mol",
                    response.required("totalKilojoulesPerMole").asText());
            provenance.put("pre.force.groups.kj_per_mol",
                    response.required("preMinimizationForceGroupKilojoulesPerMole").toString());
            provenance.put("post.force.groups.kj_per_mol",
                    response.required("forceGroupKilojoulesPerMole").toString());
            provenance.put("maximum.force.kj_per_mol_nm",
                    response.required("maximumForceKilojoulesPerMoleNanometre").asText());
            provenance.put("ligand.maximum.displacement.angstrom",
                    Double.toString(detailedDisplacements.ligandMaximumDisplacementAngstroms()));
            return new Result(relaxedReceptor, relaxedLigand, relaxedSam, converged,
                    displacements, failures, provenance);
        } catch (EnergyEvaluationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw stateFailure("invalid bounded-minimization state/result: " + exception.getMessage());
        }
    }

    private ObjectNode request(MolecularState state, LocalRelaxationProtocol protocol,
            VerifiedOpenMmSystem verified, RelaxationAtomSelector.Selection selection) {
        ObjectNode root = mapper.createObjectNode();
        root.put("operation", "MINIMIZE");
        root.put("systemArtifact", verified.manifest().systemArtifact().toString());
        root.put("systemFormat", "OPENMM_XML");
        root.put("coordinateArtifact", verified.manifest().coordinateArtifact().toString());
        root.put("platform", "CPU");
        root.putObject("platformProperties").put("Threads", protocol.cpuThreads().toString())
                .put("DeterministicForces", Boolean.toString(protocol.deterministicForces()));
        ObjectNode groups = root.putObject("forceGroups");
        forceGroups.groups().forEach((id, group) -> groups.put(Integer.toString(id),
                group.openMmForceClass()));
        ObjectNode frozenGroups = root.putObject("frozenForceGroups");
        verified.manifest().forceGroupAssignments().forEach((id, forceClass) ->
                frozenGroups.put(Integer.toString(id), forceClass));
        root.put("restraintForceGroup", restraintGroup(verified.manifest()));
        root.put("forceToleranceKilojoulesPerMoleNanometre",
                protocol.forceToleranceKilojoulesPerMoleNanometre());
        root.put("maximumIterations", protocol.maximumIterations());
        ArrayNode xyz = root.putArray("positionsNanometres");
        for (SystemAtomMapping.Entry entry : protocol.systemAtomMapping().entries().stream()
                .sorted(java.util.Comparator.comparingInt(SystemAtomMapping.Entry::openMmParticleIndex)).toList()) {
            Point3D point = resolve(state, entry).getPosition();
            xyz.addArray().add(point.x() / 10.0).add(point.y() / 10.0).add(point.z() / 10.0);
        }
        ArrayNode mask = root.putArray("selection");
        selection.atoms().forEach(atom -> {
            ObjectNode item = mask.addObject();
            item.put("particleIndex", atom.openMmParticleIndex());
            item.put("componentRole", atom.componentRole().name());
            item.put("mobility", atom.mobility().name());
            item.put("reason", atom.reason());
        });
        ObjectNode constants = root.putObject(
                "restraintConstantsKilojoulesPerMoleNanometreSquared");
        selection.atoms().stream().filter(atom -> atom.mobility()
                == AtomSelectionRules.Mobility.POSITION_RESTRAINED)
                .map(RelaxationAtomSelector.SelectedAtom::componentRole).distinct()
                .forEach(role -> constants.put(role.name(), forceConstant(role, protocol)));
        return root;
    }

    private static double forceConstant(SystemAtomMapping.ComponentRole role,
            LocalRelaxationProtocol protocol) {
        return switch (role) {
            case PROTEIN_BACKBONE -> protocol.backboneRestraintForceConstant().orElseThrow()
                    .kilojoulesPerMoleNanometreSquared();
            case PROTEIN_SIDE_CHAIN -> protocol.localSideChainRestraintForceConstant().orElseThrow()
                    .kilojoulesPerMoleNanometreSquared();
            case LIGAND -> protocol.ligandRestraint().forceConstant().orElseThrow()
                    .kilojoulesPerMoleNanometreSquared();
            case SAM -> protocol.samRestraint().forceConstant().orElseThrow()
                    .kilojoulesPerMoleNanometreSquared();
            case SOLVENT, ION -> throw new IllegalArgumentException(
                    "restrained solvent/ion requires a dedicated explicit force constant contract");
        };
    }

    private static int restraintGroup(OpenMmSystemManifest manifest) {
        return manifest.relaxationRestraintForceGroup();
    }

    private void validateGroups(OpenMmSystemManifest manifest) throws EnergyEvaluationException {
        Map<Integer, String> requested = new LinkedHashMap<>();
        forceGroups.groups().forEach((id, group) -> requested.put(id, group.openMmForceClass()));
        for (Map.Entry<Integer, String> frozen : manifest.forceGroupAssignments().entrySet()) {
            if (!Objects.equals(requested.get(frozen.getKey()), frozen.getValue())) {
                throw stateFailure("force groups differ from manifest");
            }
        }
        int restraintGroup = manifest.relaxationRestraintForceGroup();
        if (!"CustomExternalForce".equals(requested.get(restraintGroup))) {
            throw stateFailure("declared relaxation restraint group is not mapped to CustomExternalForce");
        }
        if (requested.size() > manifest.forceGroupAssignments().size()
                + (manifest.forceGroupAssignments().containsKey(restraintGroup) ? 0 : 1)) {
            throw stateFailure("requested force groups contain undeclared non-restraint groups");
        }
    }

    private static void validateSupported(LocalRelaxationProtocol protocol)
            throws EnergyEvaluationException {
        if (!"LocalEnergyMinimizer".equals(protocol.minimizerType())) {
            throw stateFailure("unsupported minimizer type " + protocol.minimizerType());
        }
        if (protocol.cpuThreads() != 8 || !protocol.deterministicForces()) {
            throw stateFailure("validated minimization backend requires CPU Threads=8 and DeterministicForces=true");
        }
        if (protocol.ligandRestraint().policy() == LocalRelaxationProtocol.Policy.POSITIONAL_AND_ORIENTATIONAL
                || protocol.samRestraint().policy() == LocalRelaxationProtocol.Policy.POSITIONAL_AND_ORIENTATIONAL) {
            throw stateFailure("orientational restraints are not implemented; no positional substitution allowed");
        }
    }

    private static Atom resolve(MolecularState state, SystemAtomMapping.Entry entry) {
        Structure source = switch (entry.componentRole()) {
            case LIGAND -> state.ligand();
            case SAM -> state.cofactor().orElseThrow(() -> new IllegalArgumentException("SAM mapping without SAM"));
            default -> state.receptor();
        };
        return source.findAtom(entry.gaiaAtom()).orElseThrow(() ->
                new IllegalArgumentException("unresolved mapped atom " + entry));
    }

    private static List<Point3D> parsePositions(JsonNode node) {
        List<Point3D> result = new ArrayList<>();
        node.forEach(xyz -> result.add(new Point3D(xyz.get(0).asDouble() * 10.0,
                xyz.get(1).asDouble() * 10.0, xyz.get(2).asDouble() * 10.0)));
        return result;
    }

    private static Structure update(Structure source, SystemAtomMapping mapping,
            List<Point3D> positions, boolean ligand, boolean sam) {
        Map<AtomReference, Point3D> replacements = new LinkedHashMap<>();
        mapping.entries().forEach(entry -> {
            boolean matches = ligand ? entry.componentRole() == SystemAtomMapping.ComponentRole.LIGAND
                    : sam ? entry.componentRole() == SystemAtomMapping.ComponentRole.SAM
                    : entry.componentRole() != SystemAtomMapping.ComponentRole.LIGAND
                    && entry.componentRole() != SystemAtomMapping.ComponentRole.SAM;
            if (matches) replacements.put(entry.gaiaAtom(), positions.get(entry.openMmParticleIndex()));
        });
        List<Chain> chains = new ArrayList<>();
        for (Chain chain : source.getChains()) {
            List<Residue> residues = new ArrayList<>();
            for (Residue residue : chain.residues()) {
                List<Atom> atoms = new ArrayList<>();
                for (Atom atom : residue.getAtoms()) {
                    AtomReference reference = new AtomReference(chain.id(), residue.getNumber(),
                            residue.getInsertionCode() == null ? ' ' : residue.getInsertionCode(), atom.getName());
                    Point3D replacement = replacements.get(reference);
                    atoms.add(replacement == null ? atom : atom.toBuilder().position(replacement).build());
                }
                residues.add(residue.toBuilder().atoms(atoms).build());
            }
            chains.add(new Chain(chain.id(), residues));
        }
        return new Structure(chains, source.bonds(), source.getConnectivityMetadata());
    }

    private static DisplacementCalculator.DetailedDisplacements displacements(MolecularState state,
            Structure receptor, Structure ligand, Structure sam,
            LocalRelaxationProtocol protocol, RelaxationAtomSelector.Selection selection) {
        Map<AtomReference, AtomReference> ligandMap = new LinkedHashMap<>(), backboneMap = new LinkedHashMap<>(),
                sideMap = new LinkedHashMap<>(), samMap = new LinkedHashMap<>();
        for (var atom : selection.atoms()) {
            Map<AtomReference, AtomReference> target = switch (atom.componentRole()) {
                case LIGAND -> ligandMap;
                case PROTEIN_BACKBONE -> backboneMap;
                case PROTEIN_SIDE_CHAIN -> sideMap;
                case SAM -> samMap;
                default -> null;
            };
            if (target != null) target.put(atom.gaiaAtom(), atom.gaiaAtom());
        }
        Structure beforeSam = state.cofactor().orElse(new Structure(List.of()));
        return DisplacementCalculator.calculate(new DisplacementCalculator.Inputs(
                state.receptor(), receptor, state.ligand(), ligand, beforeSam, sam,
                ligandMap, backboneMap, sideMap, samMap));
    }

    private static EnergyEvaluationException stateFailure(String message) {
        return new EnergyEvaluationException(EnergyEvaluationException.FailureCode.STATE_INCOMPLETE, message);
    }
}
