package totah.lab.athena.recognition;

import totah.lab.athena.design.feature.LigandFeature;
import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Assigns an existing interaction to an existing stable feature by canonical atom orbit. */
public final class InteractionStableFeatureAssigner {
    public static final String VERSION="INTERACTION_STABLE_FEATURE_ASSIGNER_V2";

    /**
     * Assigns from the canonical interaction atom contract.  The mapping is
     * keyed by the ligand atom PDB serial used in {@code interaction}; it
     * prevents participant-role information from being discarded before
     * feature assignment.
     */
    public Result assign(Interaction interaction, Map<Integer, String> ligandAtomSerialToOrbit,
            StableLigandFeatureMap features, EvidenceQuality interactionQuality) {
        Objects.requireNonNull(interaction);
        Objects.requireNonNull(ligandAtomSerialToOrbit);
        Set<String> participating = new LinkedHashSet<>();
        for (var atom : interaction.ligandAtoms()) {
            if (!atom.isHeavyAtom()) {
                continue;
            }
            if (interaction.type() == InteractionType.HALOGEN_BOND
                    && (atom.getElement() == null || !atom.getElement().isHalogen())) {
                continue;
            }
            String orbit = ligandAtomSerialToOrbit.get(atom.getPdbSerial());
            if (orbit != null) {
                participating.add(orbit);
            }
        }
        if (participating.isEmpty()) {
            return unavailable(interaction, participating, interactionQuality);
        }
        return assign(interaction, participating, features, interactionQuality);
    }

    public Result assign(Interaction interaction, Set<String> participatingLigandAtomOrbits,
            StableLigandFeatureMap features, EvidenceQuality interactionQuality) {
        Objects.requireNonNull(interaction); Objects.requireNonNull(participatingLigandAtomOrbits);
        Objects.requireNonNull(features); Objects.requireNonNull(interactionQuality);
        List<StableLigandFeatureMap.Feature> candidates=features.features().stream()
                .filter(f->compatible(f.type(),interaction))
                .filter(f->f.canonicalAtomOrbits().containsAll(participatingLigandAtomOrbits))
                .toList();
        Status status;
        Optional<String> selected=Optional.empty();
        if(candidates.isEmpty()) status=Status.FEATURE_ASSIGNMENT_UNAVAILABLE;
        else if(candidates.size()==1){status=Status.UNIQUE_FEATURE_ASSIGNMENT;selected=Optional.of(candidates.getFirst().stableId());}
        else {
            boolean equivalent=candidates.stream().map(f->List.of(f.type(),f.canonicalAtomOrbits())).distinct().count()==1;
            status=equivalent?Status.SYMMETRY_EQUIVALENT_FEATURE_ASSIGNMENT:Status.AMBIGUOUS_FEATURE_ASSIGNMENT;
            if(equivalent) selected=Optional.of(candidates.stream().map(StableLigandFeatureMap.Feature::stableId).sorted().findFirst().orElseThrow());
        }
        String canonical=interaction.type()+"|"+participatingLigandAtomOrbits.stream().sorted().toList()+"|"
                +candidates.stream().map(StableLigandFeatureMap.Feature::stableId).sorted().toList()+"|"+status+"|"+interactionQuality;
        return new Result(status,selected,candidates.stream().map(StableLigandFeatureMap.Feature::stableId).sorted().toList(),
                interactionQuality,sha256(canonical),VERSION);
    }

    private Result unavailable(Interaction interaction, Set<String> participating,
            EvidenceQuality interactionQuality) {
        String canonical=interaction.type()+"|"+participating.stream().sorted().toList()+"|[]|"
                +Status.FEATURE_ASSIGNMENT_UNAVAILABLE+"|"+interactionQuality;
        return new Result(Status.FEATURE_ASSIGNMENT_UNAVAILABLE,Optional.empty(),List.of(),
                interactionQuality,sha256(canonical),VERSION);
    }

    public static boolean compatible(LigandFeature.Type feature, InteractionType interaction) {
        return switch(interaction){
            case HYDROGEN_BOND -> feature==LigandFeature.Type.H_BOND_DONOR||feature==LigandFeature.Type.H_BOND_ACCEPTOR;
            case SALT_BRIDGE -> feature==LigandFeature.Type.POSITIVE_CENTER||feature==LigandFeature.Type.NEGATIVE_CENTER;
            case HYDROPHOBIC_CONTACT -> feature==LigandFeature.Type.HYDROPHOBE;
            case PI_STACK_PARALLEL,PI_STACK_T_SHAPED -> feature==LigandFeature.Type.AROMATIC_RING;
            case PI_CATION -> feature==LigandFeature.Type.AROMATIC_RING||feature==LigandFeature.Type.POSITIVE_CENTER;
            case HALOGEN_BOND -> feature==LigandFeature.Type.HALOGEN;
        };
    }

    /**
     * Compatibility including participant direction encoded by the canonical
     * {@link Interaction} atom-list contract. A ligand-side hydrogen-bond
     * donor is [heavy atom, hydrogen]; a ligand-side acceptor is one atom.
     */
    public static boolean compatible(LigandFeature.Type feature, Interaction interaction) {
        Objects.requireNonNull(feature); Objects.requireNonNull(interaction);
        if (interaction.type() != InteractionType.HYDROGEN_BOND) {
            return compatible(feature, interaction.type());
        }
        boolean ligandIsDonor = interaction.ligandAtoms().stream().anyMatch(atom -> !atom.isHeavyAtom());
        return ligandIsDonor ? feature == LigandFeature.Type.H_BOND_DONOR
                : feature == LigandFeature.Type.H_BOND_ACCEPTOR;
    }
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    public enum Status { UNIQUE_FEATURE_ASSIGNMENT,SYMMETRY_EQUIVALENT_FEATURE_ASSIGNMENT,AMBIGUOUS_FEATURE_ASSIGNMENT,FEATURE_ASSIGNMENT_UNAVAILABLE }
    public record Result(Status status,Optional<String> stableFeatureId,List<String> candidateFeatureIds,
            EvidenceQuality interactionQuality,String assignmentSha256,String implementationVersion){
        public Result{candidateFeatureIds=List.copyOf(candidateFeatureIds);}
        public boolean assigned(){return stableFeatureId.isPresent();}
    }
}
