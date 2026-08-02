package totah.lab.hermes.file.writer.pdbqt.validation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PdbqtFlexibilityValidator {
    public PdbqtValidationReport validate(List<String> lines) {
        List<PdbqtValidationIssue> issues=new ArrayList<>(); boolean inRoot=false; int rootCount=0;
        ArrayDeque<Bond>branches=new ArrayDeque<>(); Set<String>edges=new HashSet<>(); Set<Integer>serials=new HashSet<>();
        int torsions=0; Integer declared=null;
        for(int i=0;i<lines.size();i++){
            String[]p=lines.get(i).trim().split("\\s+");if(p.length==0||p[0].isEmpty())continue;String location="line "+(i+1);
            switch(p[0]){
                case "BEGIN_RES"->{serials.clear();branches.clear();edges.clear();rootCount=0;torsions=0;declared=null;}
                case "ATOM","HETATM"->{try{serials.add(Integer.parseInt(p[1]));}catch(RuntimeException ignored){}}
                case "ROOT"->{if(inRoot)issues.add(error(PdbqtValidationCode.ROOT_UNBALANCED,"Nested ROOT is invalid.",location));inRoot=true;rootCount++;}
                case "ENDROOT"->{if(!inRoot)issues.add(error(PdbqtValidationCode.ROOT_UNBALANCED,"ENDROOT has no ROOT.",location));inRoot=false;}
                case "BRANCH"->{Bond b=parseBond(p,issues,location);if(b!=null){if(!edges.add(b.key()))issues.add(error(PdbqtValidationCode.BRANCH_GRAPH_CYCLIC,"Branch edge is duplicated.",location));branches.push(b);torsions++;}}
                case "ENDBRANCH"->{Bond b=parseBond(p,issues,location);if(b!=null&&(branches.isEmpty()||!branches.pop().equals(b)))issues.add(error(PdbqtValidationCode.BRANCH_UNBALANCED,"ENDBRANCH does not close the active BRANCH.",location));}
                case "TORSDOF"->{try{declared=Integer.parseInt(p[1]);}catch(RuntimeException e){issues.add(error(PdbqtValidationCode.TORSDOF_MISMATCH,"TORSDOF is malformed.",location));}}
                case "END_RES"->{if(inRoot||rootCount!=1)issues.add(error(PdbqtValidationCode.ROOT_UNBALANCED,"Flexible residue must contain one balanced ROOT.",location));if(!branches.isEmpty())issues.add(error(PdbqtValidationCode.BRANCH_UNBALANCED,"Branches remain open at END_RES.",location));if(declared!=null&&declared!=torsions)issues.add(error(PdbqtValidationCode.TORSDOF_MISMATCH,"TORSDOF does not equal branch count.",location));validateReferences(edges,serials,issues);if(cyclic(edges))issues.add(error(PdbqtValidationCode.BRANCH_GRAPH_CYCLIC,"Branch graph is cyclic.",location));}
                default->{}
            }
        }
        if(inRoot)issues.add(error(PdbqtValidationCode.ROOT_UNBALANCED,"ROOT is not closed.","end of file"));
        if(!branches.isEmpty())issues.add(error(PdbqtValidationCode.BRANCH_UNBALANCED,"BRANCH is not closed.","end of file"));
        return new PdbqtValidationReport(issues);
    }
    private void validateReferences(Set<String>edges,Set<Integer>serials,List<PdbqtValidationIssue>issues){for(String edge:edges){String[]parts=edge.split(":");int a=Integer.parseInt(parts[0]),b=Integer.parseInt(parts[1]);if(!serials.contains(a)||!serials.contains(b))issues.add(error(PdbqtValidationCode.BRANCH_REFERENCE_MISSING,"Branch references an absent atom serial.",edge));}}
    private boolean cyclic(Set<String>edges){java.util.Map<Integer,List<Integer>>graph=new java.util.HashMap<>();for(String edge:edges){String[]p=edge.split(":");graph.computeIfAbsent(Integer.parseInt(p[0]),ignored->new ArrayList<>()).add(Integer.parseInt(p[1]));}Set<Integer>visited=new HashSet<>(),active=new HashSet<>();for(Integer node:graph.keySet())if(cyclic(node,graph,visited,active))return true;return false;}
    private boolean cyclic(int node,java.util.Map<Integer,List<Integer>>graph,Set<Integer>visited,Set<Integer>active){if(active.contains(node))return true;if(!visited.add(node))return false;active.add(node);for(int child:graph.getOrDefault(node,List.of()))if(cyclic(child,graph,visited,active))return true;active.remove(node);return false;}
    private Bond parseBond(String[]p,List<PdbqtValidationIssue>issues,String location){try{if(p.length!=3)throw new NumberFormatException();return new Bond(Integer.parseInt(p[1]),Integer.parseInt(p[2]));}catch(NumberFormatException e){issues.add(error(PdbqtValidationCode.MALFORMED_RECORD,"Branch record is malformed.",location));return null;}}
    private PdbqtValidationIssue error(PdbqtValidationCode code,String message,String location){return new PdbqtValidationIssue(PdbqtValidationSeverity.ERROR,code,message,location);}
    private record Bond(int parent,int child){String key(){return parent+":"+child;}}
}
