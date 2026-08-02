package totah.lab.hermes.file.writer.pdbqt.validation;

import totah.lab.hermes.file.writer.pdbqt.PdbqtFlexibleReceptorInput;
import totah.lab.hermes.file.writer.pdbqt.PdbqtFragmentInput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PdbqtSerializerValidator {
    private static final Set<String> TYPES=Set.of("C","A","N","NA","O","OA","S","SA","P","HD","H","F","Cl","Br","I","Mg","Mn","Fe","Zn","Ca");

    public PdbqtValidationReport validate(PdbqtFlexibleReceptorInput input) {
        List<PdbqtValidationIssue> issues=new ArrayList<>();
        if(input==null){issues.add(error(PdbqtValidationCode.NULL_INPUT,"Serializer input is null.","input"));return new PdbqtValidationReport(issues);}
        Set<Integer> indices=new HashSet<>();
        input.rigidAtoms().forEach(rigid->validateAtom(rigid.atom(),indices,issues,"rigid"));
        for(var residue:input.flexibleResidues()){
            Map<String,PdbqtFragmentInput> fragments=new HashMap<>();
            Set<Integer> residueAtoms=new HashSet<>();
            for(var fragment:residue.fragments()){
                if(fragments.put(fragment.fragmentId(),fragment)!=null)issues.add(error(PdbqtValidationCode.DUPLICATE_FRAGMENT,"Duplicate fragment ID.",fragment.fragmentId()));
                boolean anchorFound=false;
                for(var atom:fragment.atoms()){
                    validateAtom(atom,indices,issues,residue.chainId()+":"+residue.residueNumber());
                    residueAtoms.add(atom.canonicalAtomIndex());
                    if(atom.canonicalAtomIndex()==fragment.anchorAtomIndex())anchorFound=true;
                }
                if(!anchorFound)issues.add(error(PdbqtValidationCode.INVALID_ANCHOR,"Fragment anchor is not in fragment atoms.",fragment.fragmentId()));
            }
            if(!residueAtoms.contains(residue.anchorAtomIndex()))issues.add(error(PdbqtValidationCode.INVALID_ANCHOR,"Flexible residue anchor is missing.",residue.chainId()+":"+residue.residueNumber()));
            Map<String,List<String>> graph=new HashMap<>();
            for(var bond:residue.rotatableBonds()){
                if(!fragments.containsKey(bond.parentFragmentId())||!fragments.containsKey(bond.childFragmentId()))
                    issues.add(error(PdbqtValidationCode.UNKNOWN_FRAGMENT,"Bond references unknown fragment.",bond.toString()));
                if(!residueAtoms.contains(bond.parentAtomIndex())||!residueAtoms.contains(bond.childAtomIndex()))
                    issues.add(error(PdbqtValidationCode.BRANCH_REFERENCE_MISSING,"Bond references missing atom.",bond.toString()));
                graph.computeIfAbsent(bond.parentFragmentId(),ignored->new ArrayList<>()).add(bond.childFragmentId());
            }
            if(cyclic(graph))issues.add(error(PdbqtValidationCode.BRANCH_GRAPH_CYCLIC,"Fragment graph is cyclic.",residue.chainId()+":"+residue.residueNumber()));
        }
        if(indices.size()!=input.preparedAtomCount())issues.add(error(PdbqtValidationCode.PARTITION_INCOMPLETE,"Rigid and flexible atoms do not exactly partition prepared atoms.","input"));
        for(int i=0;i<input.preparedAtomCount();i++)if(!indices.contains(i))issues.add(error(PdbqtValidationCode.MISSING_ATOM,"Canonical atom is missing.",Integer.toString(i)));
        return new PdbqtValidationReport(issues);
    }
    private void validateAtom(totah.lab.hermes.file.writer.pdbqt.PdbqtAtomInput atom,Set<Integer> indices,List<PdbqtValidationIssue> issues,String location){
        if(!indices.add(atom.canonicalAtomIndex()))issues.add(error(PdbqtValidationCode.DUPLICATE_ATOM,"Atom occurs more than once.",Integer.toString(atom.canonicalAtomIndex())));
        var p=atom.coordinates(); if(p==null||!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z()))issues.add(error(PdbqtValidationCode.INVALID_COORDINATE,"Coordinates are missing or non-finite.",location));
        if(!Double.isFinite(atom.charge()))issues.add(error(PdbqtValidationCode.INVALID_CHARGE,"Charge is non-finite.",location));
        if(atom.ad4Type()==null||!TYPES.contains(atom.ad4Type()))issues.add(error(PdbqtValidationCode.MISSING_AD4_TYPE,"AD4 type is missing or unsupported.",location));
    }
    private boolean cyclic(Map<String,List<String>> graph){Set<String>visited=new HashSet<>(),active=new HashSet<>();for(String n:graph.keySet())if(cyclic(n,graph,visited,active))return true;return false;}
    private boolean cyclic(String n,Map<String,List<String>>g,Set<String>v,Set<String>a){if(a.contains(n))return true;if(!v.add(n))return false;a.add(n);for(String c:g.getOrDefault(n,List.of()))if(cyclic(c,g,v,a))return true;a.remove(n);return false;}
    private PdbqtValidationIssue error(PdbqtValidationCode code,String message,String location){return new PdbqtValidationIssue(PdbqtValidationSeverity.ERROR,code,message,location);}
}
