package totah.lab.athena.fragment.quantum;

import java.util.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.structure.ResidueId;
import static totah.lab.athena.fragment.quantum.QuantumEnvironment.InteractionClass;

/** Immutable ordered fragment pair; channel and constituent labels cannot be collapsed away. */
public record FragmentCalculationPlan(String id,Channel channel,PreparedQuantumFragment left,
                                      PreparedQuantumFragment right,List<InteractionClass> constituentClasses,
                                      String selectionHash,String receiptHash) {
    public enum Channel { LIGAND_PROTEIN, LIGAND_COFACTOR, PROTEIN_COFACTOR, LIGAND_WATER }
    public FragmentCalculationPlan {
        constituentClasses=List.copyOf(constituentClasses);
        if(constituentClasses.isEmpty())throw new IllegalArgumentException("Explicit structural class or unclassified required");
        if(!left.environmentHash().equals(right.environmentHash()))throw new IllegalArgumentException("Fragments belong to different structural states");
    }
    public static List<FragmentCalculationPlan> pairs(QuantumEnvironment environment,FragmentSelection selection) {
        requireSelection(environment,selection);var plans=new ArrayList<FragmentCalculationPlan>();
        var ligand=QuantumFragmentBuilder.whole(environment,"ligand","ligand:"+environment.state().ligandId());
        var proteins=new LinkedHashMap<ResidueId,PreparedQuantumFragment>();
        var cofactors=new ArrayList<PreparedQuantumFragment>();
        for(var residue:selection.directContactResidues()) {
            var fragment=QuantumFragmentBuilder.receptorUnits(environment,List.of(residue),true,residueId(residue));proteins.put(residue,fragment);
            plans.add(plan(Channel.LIGAND_PROTEIN,ligand,fragment,FragmentSelection.interactionClasses(environment,residue),selection));
        }
        for(var residue:selection.waterIncluded()) {
            var fragment=QuantumFragmentBuilder.receptorUnits(environment,List.of(residue),false,residueId(residue));
            plans.add(plan(Channel.LIGAND_WATER,ligand,fragment,FragmentSelection.interactionClasses(environment,residue),selection));
        }
        for(var residue:selection.cofactorIncluded()) {
            var fragment=QuantumFragmentBuilder.receptorUnits(environment,List.of(residue),false,residueId(residue));cofactors.add(fragment);
            plans.add(plan(Channel.LIGAND_COFACTOR,ligand,fragment,FragmentSelection.interactionClasses(environment,residue),selection));
        }
        if(selection.standaloneCofactorIncluded()) {
            var fragment=QuantumFragmentBuilder.whole(environment,"cofactor","cofactor:"+environment.state().stateId());cofactors.add(fragment);
            plans.add(plan(Channel.LIGAND_COFACTOR,ligand,fragment,Set.of(InteractionClass.unclassified),selection));
        }
        for(var protein:proteins.values())for(var cofactor:cofactors)if(close(protein,cofactor))
            plans.add(plan(Channel.PROTEIN_COFACTOR,protein,cofactor,Set.of(InteractionClass.unclassified),selection));
        return List.copyOf(plans);
    }
    /** Cluster membership is explicit; second-shell selection alone never creates a quantum cluster. */
    public static FragmentCalculationPlan localCluster(QuantumEnvironment environment,FragmentSelection selection,List<ResidueId> requested) {
        requireSelection(environment,selection);
        var allowed=new HashSet<>(selection.directContactResidues());allowed.addAll(selection.secondShellResidues());
        if(!allowed.containsAll(requested))throw new IllegalArgumentException("Cluster includes unselected receptor units");
        // Canonical source order, independent of caller's list ordering.
        var ordered=FragmentSelection.residues(environment.state().receptor()).keySet().stream().filter(requested::contains).toList();
        if(ordered.size()!=requested.size())throw new IllegalArgumentException("Duplicate cluster member");
        var ligand=QuantumFragmentBuilder.whole(environment,"ligand","ligand:"+environment.state().ligandId());
        var fragment=QuantumFragmentBuilder.receptorUnits(environment,ordered,true,"cluster:"+ordered);
        var classes=EnumSet.noneOf(InteractionClass.class);for(var residue:ordered)classes.addAll(FragmentSelection.interactionClasses(environment,residue));
        return plan(Channel.LIGAND_PROTEIN,ligand,fragment,classes,selection);
    }
    private static boolean close(PreparedQuantumFragment a,PreparedQuantumFragment b) {
        for(var x:a.retained())if(x.atomicNumber()!=1)for(var y:b.retained())if(y.atomicNumber()!=1&&x.originalAngstrom().distanceSquared(y.originalAngstrom())<=16)return true;
        return false;
    }
    private static String residueId(ResidueId residue){return "residue:"+residue.chainId()+":"+residue.residueNumber()+":"+residue.insertionCode();}
    private static void requireSelection(QuantumEnvironment environment,FragmentSelection selection) {
        if(!environment.identity().equals(selection.environmentHash()))throw new IllegalArgumentException("Selection belongs to another structural state");
    }
    private static FragmentCalculationPlan plan(Channel channel,PreparedQuantumFragment left,PreparedQuantumFragment right,Set<InteractionClass> classes,FragmentSelection selection) {
        var ordered=classes.stream().sorted().toList();String id=channel+":"+left.id()+":"+right.id();
        String hash=ContentHash.sha256("athena-fragment-plan-18-1\n"+id+"\n"+selection.receiptHash()+"\n"+left.receiptHash()+"\n"+right.receiptHash()+"\n"+ordered);
        return new FragmentCalculationPlan(id,channel,left,right,ordered,selection.receiptHash(),hash);
    }
}
