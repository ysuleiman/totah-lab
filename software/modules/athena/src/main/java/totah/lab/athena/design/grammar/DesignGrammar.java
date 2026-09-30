package totah.lab.athena.design.grammar;

import totah.lab.athena.ligand.screening.PhysicochemicalGate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Target-independent molecular-design grammar. All scientific identifiers are configuration data. */
public record DesignGrammar(
        String id,
        String version,
        List<FeatureGroup> positiveFeatureGroups,
        List<FeatureGroup> counterRecognitionGroups,
        List<ScaffoldInvariant> scaffoldInvariants,
        List<EditableVector> editableVectors,
        List<DistanceEnvelope> distanceEnvelopes,
        List<AngleConstraint> angleConstraints,
        List<HardConstraint> hardConstraints,
        List<SoftObjective> softObjectives,
        ChemicalStatePolicy chemicalStatePolicy,
        PhysicochemicalGate.Policy physicochemicalPolicy,
        ExplorationPolicy explorationPolicy,
        Set<String> templateIds,
        Map<String, String> provenance) {
    public DesignGrammar {
        require(id, "id"); require(version, "version");
        positiveFeatureGroups=List.copyOf(positiveFeatureGroups);counterRecognitionGroups=List.copyOf(counterRecognitionGroups);
        scaffoldInvariants=List.copyOf(scaffoldInvariants);editableVectors=List.copyOf(editableVectors);
        distanceEnvelopes=List.copyOf(distanceEnvelopes);angleConstraints=List.copyOf(angleConstraints);
        hardConstraints=List.copyOf(hardConstraints);softObjectives=List.copyOf(softObjectives);
        templateIds=Set.copyOf(templateIds);provenance=Map.copyOf(provenance);
        Objects.requireNonNull(chemicalStatePolicy);Objects.requireNonNull(physicochemicalPolicy);Objects.requireNonNull(explorationPolicy);
    }
    private static void require(String value,String name){if(value==null||value.isBlank())throw new IllegalArgumentException(name+" required");}

    public enum FeatureType { HYDROPHOBE, H_BOND_DONOR, H_BOND_ACCEPTOR, POSITIVE_CENTER, NEGATIVE_CENTER, AROMATIC_RING, POLAR, CUSTOM }
    public enum FeatureRole { POSITIVE_RECOGNITION, COUNTER_RECOGNITION, SCAFFOLD_INVARIANT, ALTERNATIVE, EDITABLE_VECTOR }
    public enum Requirement { REQUIRED, OPTIONAL, ALTERNATIVE }
    public enum HardConstraintType { VALID_STRUCTURE, CHEMICAL_STATE, PHYSICOCHEMICAL, LIABILITY, SYNTHESIS, SCAFFOLD_CONNECTIVITY, CUSTOM }

    public record MolecularFeature(String id,FeatureType type,FeatureRole role,String atomOrGroupId,Map<String,String> attributes){
        public MolecularFeature{require(id,"feature id");Objects.requireNonNull(type);Objects.requireNonNull(role);attributes=Map.copyOf(attributes);}
    }
    public record FeatureGroup(String id,Requirement requirement,int minimumSatisfied,List<MolecularFeature> features,List<String> alternativeGroupIds){
        public FeatureGroup{require(id,"group id");Objects.requireNonNull(requirement);features=List.copyOf(features);alternativeGroupIds=List.copyOf(alternativeGroupIds);if(minimumSatisfied<0)throw new IllegalArgumentException("minimumSatisfied");}
    }
    public record ScaffoldInvariant(String id,String definition,Set<String> protectedAtomOrGroupIds,boolean required){
        public ScaffoldInvariant{require(id,"invariant id");protectedAtomOrGroupIds=Set.copyOf(protectedAtomOrGroupIds);}
    }
    public record EditableVector(String id,String attachmentSite,List<String> allowedTransformationClasses,List<String> permittedSubstituentClasses,List<String> geometricObjective,Set<String> protectedNeighborhood,List<String> hardRestrictions,List<String> softIntentions){
        public EditableVector{require(id,"vector id");allowedTransformationClasses=List.copyOf(allowedTransformationClasses);permittedSubstituentClasses=List.copyOf(permittedSubstituentClasses);geometricObjective=List.copyOf(geometricObjective);protectedNeighborhood=Set.copyOf(protectedNeighborhood);hardRestrictions=List.copyOf(hardRestrictions);softIntentions=List.copyOf(softIntentions);}
    }
    public record DistanceEnvelope(String id,String firstFeatureId,String secondFeatureId,double minimum,double maximum,boolean hard){
        public DistanceEnvelope{require(id,"distance id");if(!Double.isFinite(minimum)||!Double.isFinite(maximum)||minimum<0||minimum>maximum)throw new IllegalArgumentException("distance bounds");}
    }
    public record AngleConstraint(String id,String featureId,double minimumDegrees,double maximumDegrees,boolean hard){
        public AngleConstraint{require(id,"angle id");if(!Double.isFinite(minimumDegrees)||!Double.isFinite(maximumDegrees)||minimumDegrees<0||maximumDegrees>180||minimumDegrees>maximumDegrees)throw new IllegalArgumentException("angle bounds");}
    }
    public record HardConstraint(String id,HardConstraintType type,String definition){public HardConstraint{require(id,"hard constraint id");Objects.requireNonNull(type);}}
    public record SoftObjective(String id,String definition,Set<String> relatedFeatureGroupIds){public SoftObjective{require(id,"soft objective id");relatedFeatureGroupIds=Set.copyOf(relatedFeatureGroupIds);}}
    public record ChemicalStatePolicy(int minimumFormalCharge,int maximumFormalCharge,boolean assayPlausibilityRequired,boolean statesSeparate){public ChemicalStatePolicy{if(minimumFormalCharge>maximumFormalCharge)throw new IllegalArgumentException("charge bounds");}}
    public record ExplorationPolicy(double minimumFraction,double maximumFraction,int maximumSoftExceptions){public ExplorationPolicy{if(minimumFraction<0||maximumFraction>1||minimumFraction>maximumFraction||maximumSoftExceptions<0)throw new IllegalArgumentException("exploration policy");}}
}
