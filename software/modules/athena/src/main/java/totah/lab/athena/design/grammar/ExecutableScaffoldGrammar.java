package totah.lab.athena.design.grammar;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Machine-executable indexed scaffold grammar; contains no target-specific constants. */
public record ExecutableScaffoldGrammar(
        String id, String version, String parentId, String parentGraphSha256,
        IndexedGraph parentGraph, Set<String> protectedAtomIds,
        List<IndexedBond> protectedBonds, List<ExecutableEditVector> editableVectors,
        List<FeatureProtection> featureProtections, Map<String,String> provenance) {
    public ExecutableScaffoldGrammar { protectedAtomIds=Set.copyOf(protectedAtomIds);protectedBonds=List.copyOf(protectedBonds);editableVectors=List.copyOf(editableVectors);featureProtections=List.copyOf(featureProtections);provenance=Map.copyOf(provenance); }
    public record IndexedGraph(List<IndexedAtom> atoms,List<IndexedBond> bonds){public IndexedGraph{atoms=List.copyOf(atoms);bonds=List.copyOf(bonds);}}
    public record IndexedAtom(String id,int sourceIndex,String element,boolean aromatic,Integer formalCharge,String stereochemistry){}
    public record IndexedBond(String firstAtomId,String secondAtomId,String order,boolean aromatic,String stereochemistry){}
    public record ExecutableEditVector(String id,String anchorAtomId,List<IndexedBond> attachmentBonds,Set<String> currentSubgraphAtomIds,Set<String> protectedNeighborhood,Set<String> permittedEditRegion,List<String> allowedTransformationClasses,List<String> permittedReplacementFeatures,List<String> softIntentions){public ExecutableEditVector{attachmentBonds=List.copyOf(attachmentBonds);currentSubgraphAtomIds=Set.copyOf(currentSubgraphAtomIds);protectedNeighborhood=Set.copyOf(protectedNeighborhood);permittedEditRegion=Set.copyOf(permittedEditRegion);allowedTransformationClasses=List.copyOf(allowedTransformationClasses);permittedReplacementFeatures=List.copyOf(permittedReplacementFeatures);softIntentions=List.copyOf(softIntentions);}}
    public record FeatureProtection(String featureId,String atomId,String disposition,String softAlternative){}
}
