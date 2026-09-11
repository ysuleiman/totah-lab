package totah.lab.athena.design.grammar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Deterministic structural validation of an executable scaffold grammar. */
public final class ExecutableScaffoldGrammarValidator {
 public Validation validate(ExecutableScaffoldGrammar grammar){List<String> errors=new ArrayList<>();Set<String> atoms=new HashSet<>();for(var a:grammar.parentGraph().atoms())if(!atoms.add(a.id()))errors.add("duplicate atom "+a.id());Set<String>bonds=new HashSet<>();for(var b:grammar.parentGraph().bonds()){if(!atoms.contains(b.firstAtomId())||!atoms.contains(b.secondAtomId()))errors.add("bond references missing atom");bonds.add(key(b));}for(String a:grammar.protectedAtomIds())if(!atoms.contains(a))errors.add("protected atom missing: "+a);for(var b:grammar.protectedBonds())if(!bonds.contains(key(b)))errors.add("protected bond missing from parent: "+key(b));Set<String>vectors=new HashSet<>();for(var v:grammar.editableVectors()){if(!vectors.add(v.id()))errors.add("duplicate vector "+v.id());if(!atoms.contains(v.anchorAtomId()))errors.add(v.id()+": anchor missing");if(v.permittedEditRegion().isEmpty())errors.add(v.id()+": empty edit region");for(String a:v.permittedEditRegion())if(!atoms.contains(a))errors.add(v.id()+": edit atom missing "+a);Set<String>overlap=new HashSet<>(v.permittedEditRegion());overlap.retainAll(grammar.protectedAtomIds());if(!overlap.isEmpty())errors.add(v.id()+": edit/protected overlap "+overlap);for(String a:v.protectedNeighborhood())if(!atoms.contains(a))errors.add(v.id()+": protected neighbor missing "+a);for(var b:v.attachmentBonds())if(!bonds.contains(key(b)))errors.add(v.id()+": attachment bond missing");}for(var f:grammar.featureProtections())if(!atoms.contains(f.atomId()))errors.add("feature atom missing: "+f.featureId());return new Validation(errors.isEmpty(),errors,atoms.size(),bonds.size(),vectors.size());}
 private static String key(ExecutableScaffoldGrammar.IndexedBond b){String endpoints=b.firstAtomId().compareTo(b.secondAtomId())<=0?b.firstAtomId()+"|"+b.secondAtomId():b.secondAtomId()+"|"+b.firstAtomId();return endpoints+"|order="+b.order()+"|aromatic="+b.aromatic()+"|stereo="+b.stereochemistry();}
 public record Validation(boolean valid,List<String> errors,int atomCount,int bondCount,int vectorCount){public Validation{errors=List.copyOf(errors);}}
}
