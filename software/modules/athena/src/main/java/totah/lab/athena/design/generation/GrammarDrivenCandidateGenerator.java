package totah.lab.athena.design.generation;

import totah.lab.athena.design.grammar.DesignGrammar;
import totah.lab.athena.design.grammar.ExecutableScaffoldGrammar;
import totah.lab.athena.design.grammar.ExecutableScaffoldGrammarValidator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Deterministic, target-independent orchestration of configured molecular edits. */
public final class GrammarDrivenCandidateGenerator {
    private static final String PLACEHOLDER = "configuration-supplied";

    public GenerationReadiness inspect(DesignGrammar grammar) {
        List<String> blockers = new ArrayList<>();
        if (grammar.editableVectors().isEmpty()) blockers.add("no editable vectors");
        if (grammar.scaffoldInvariants().stream().anyMatch(x -> x.required() && x.protectedAtomOrGroupIds().isEmpty()))
            blockers.add("required scaffold invariant lacks machine-readable protected atom/group identifiers");
        for (var vector : grammar.editableVectors()) {
            if (vector.attachmentSite() == null || vector.attachmentSite().isBlank()) blockers.add(vector.id()+": missing attachment site");
            if (vector.allowedTransformationClasses().isEmpty()) blockers.add(vector.id()+": no allowed transformation classes");
            if (vector.permittedSubstituentClasses().isEmpty() || vector.permittedSubstituentClasses().contains(PLACEHOLDER))
                blockers.add(vector.id()+": permitted substituent classes are not resolved configuration");
            if (vector.protectedNeighborhood().isEmpty()) blockers.add(vector.id()+": protected/invariant neighborhood is empty");
        }
        return new GenerationReadiness(blockers.isEmpty(), blockers);
    }

    public GenerationReadiness inspect(ExecutableScaffoldGrammar grammar) {
        List<String> blockers=new ArrayList<>(new ExecutableScaffoldGrammarValidator().validate(grammar).errors());
        for(var vector:grammar.editableVectors()){
            if(vector.allowedTransformationClasses().isEmpty())blockers.add(vector.id()+": no allowed transformations");
            if(vector.permittedReplacementFeatures().isEmpty())blockers.add(vector.id()+": no permitted replacement features");
        }
        return new GenerationReadiness(blockers.isEmpty(),blockers);
    }

    public List<GeneratedMolecule> generate(DesignGrammar grammar, GenerationRequest request,
                                            MolecularTransformationBackend backend) {
        Objects.requireNonNull(grammar); Objects.requireNonNull(request); Objects.requireNonNull(backend);
        GenerationReadiness readiness = inspect(grammar);
        if (!readiness.ready()) throw new IllegalStateException(String.join("; ", readiness.blockers()));
        var unique = new LinkedHashMap<String, GeneratedMolecule>();
        outer: for (var vector : grammar.editableVectors()) {
            for (String transformation : vector.allowedTransformationClasses()) {
                for (String substituent : vector.permittedSubstituentClasses()) {
                    for (var product : backend.apply(request.parentRepresentation(), vector, transformation, substituent)) {
                        String key=product.canonicalRepresentation();
                        if (key.equals(request.parentRepresentation()) || unique.containsKey(key)) continue;
                        int ordinal=unique.size()+1;
                        boolean exploration=exploration(ordinal,request.maximumCandidates(),grammar.explorationPolicy().maximumFraction());
                        unique.put(key,new GeneratedMolecule(request.idPrefix()+String.format("%04d",ordinal),request.parentId(),key,vector.id(),transformation,substituent,product.exactEdit(),exploration,product.provenance()));
                        if(unique.size()>=request.maximumCandidates())break outer;
                    }
                }
            }
        }
        return List.copyOf(unique.values());
    }

    private static boolean exploration(int ordinal,int maximum,double fraction){int count=(int)Math.floor(maximum*fraction);return count>0&&stableBucket(ordinal,maximum)<count;}
    private static int stableBucket(int value,int modulus){try{byte[]d=MessageDigest.getInstance("SHA-256").digest(Integer.toString(value).getBytes(StandardCharsets.UTF_8));return Math.floorMod(((d[0]&255)<<8)|(d[1]&255),modulus);}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}

    public record GenerationRequest(String parentId,String parentRepresentation,String idPrefix,int maximumCandidates){public GenerationRequest{if(maximumCandidates<1)throw new IllegalArgumentException("maximumCandidates");}}
    public record GenerationReadiness(boolean ready,List<String> blockers){public GenerationReadiness{blockers=List.copyOf(blockers);}}
}
