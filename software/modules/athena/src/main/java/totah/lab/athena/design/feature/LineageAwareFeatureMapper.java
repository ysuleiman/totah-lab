package totah.lab.athena.design.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Inherits compatible parent roles and returns every compatible new-feature assignment. */
public final class LineageAwareFeatureMapper {
    public Result map(List<ParentAssociation> parentAssociations,
                      List<LigandFeature> candidateFeatures,
                      Map<String, String> parentToCandidateAtomIds,
                      Map<String, Set<LigandFeature.Type>> permittedTypesByGrammarFeature) {
        List<Assignment> inherited = new ArrayList<>(), novel = new ArrayList<>(), invalidated = new ArrayList<>();
        for (ParentAssociation parent : parentAssociations) {
            Set<String> mappedAtoms = parent.parentAtomIds().stream().map(parentToCandidateAtomIds::get)
                    .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            var matches = candidateFeatures.stream().filter(f -> f.type() == parent.type())
                    .filter(f -> f.atomIds().equals(mappedAtoms)).toList();
            if (mappedAtoms.size() != parent.parentAtomIds().size() || matches.isEmpty()
                    || !permitted(permittedTypesByGrammarFeature, parent.grammarFeatureId(), parent.type())) {
                invalidated.add(new Assignment(parent.grammarFeatureId(), null, "INHERITANCE_INVALIDATED"));
            } else matches.forEach(f -> inherited.add(new Assignment(parent.grammarFeatureId(), f.id(), "INHERITED_COMPATIBLE")));
        }
        permittedTypesByGrammarFeature.forEach((grammarId, types) -> candidateFeatures.stream()
                .filter(f -> types.contains(f.type()))
                .filter(f -> inherited.stream().noneMatch(a -> grammarId.equals(a.grammarFeatureId()) && f.id().equals(a.candidateFeatureId())))
                .forEach(f -> novel.add(new Assignment(grammarId, f.id(), "CHEMICALLY_COMPATIBLE_NEW_ASSIGNMENT"))));
        return new Result(inherited, novel, invalidated);
    }

    private static boolean permitted(Map<String, Set<LigandFeature.Type>> map,String id,LigandFeature.Type type) {
        return map.getOrDefault(id, Set.of()).contains(type);
    }
    public record ParentAssociation(String grammarFeatureId, LigandFeature.Type type, Set<String> parentAtomIds) {
        public ParentAssociation { parentAtomIds = Set.copyOf(parentAtomIds); }
    }
    public record Assignment(String grammarFeatureId, String candidateFeatureId, String basis) {}
    public record Result(List<Assignment> inherited,List<Assignment> novel,List<Assignment> invalidated) {
        public Result { inherited=List.copyOf(inherited);novel=List.copyOf(novel);invalidated=List.copyOf(invalidated); }
    }
}
