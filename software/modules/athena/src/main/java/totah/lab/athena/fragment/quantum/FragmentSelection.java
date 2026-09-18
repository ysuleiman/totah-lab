package totah.lab.athena.fragment.quantum;

import java.util.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.structure.*;
import static totah.lab.athena.fragment.quantum.QuantumEnvironment.InteractionClass;

/** Frozen geometry-only selection; chemistry availability never changes the selected neighborhood. */
public record FragmentSelection(List<ResidueId> directContactResidues, List<ResidueId> secondShellResidues,
                                List<ResidueId> waterIncluded, List<ResidueId> cofactorIncluded,
                                boolean standaloneCofactorIncluded, String environmentHash, String receiptHash) {
    public static final String PROTOCOL="athena-fragment-selection-18-1;direct-heavy<=4.0A;second-heavy<=3.5A;source-residue-order;explicit-water-cofactor;no-energy-selection";
    public FragmentSelection {
        directContactResidues=List.copyOf(directContactResidues);secondShellResidues=List.copyOf(secondShellResidues);
        waterIncluded=List.copyOf(waterIncluded);cofactorIncluded=List.copyOf(cofactorIncluded);
    }
    public static FragmentSelection select(QuantumEnvironment environment) {
        var units=residues(environment.state().receptor());
        var ligand=QuantumEnvironment.atoms(environment.state().ligand()).values().stream().filter(Atom::isHeavyAtom).toList();
        var direct=new ArrayList<ResidueId>();var second=new ArrayList<ResidueId>();
        var water=new ArrayList<ResidueId>();var cofactor=new ArrayList<ResidueId>();
        for(var entry:units.entrySet())if(near(entry.getValue().getAtoms(),ligand,4.0)) {
            if(environment.waterResidues().contains(entry.getKey())) {if(environment.includeWater())water.add(entry.getKey());}
            else if(environment.cofactorResidues().contains(entry.getKey())) {if(environment.includeCofactor())cofactor.add(entry.getKey());}
            else direct.add(entry.getKey());
        }
        for(var entry:units.entrySet())if(!direct.contains(entry.getKey())&&!environment.waterResidues().contains(entry.getKey())&&!environment.cofactorResidues().contains(entry.getKey())) {
            for(var id:direct)if(near(entry.getValue().getAtoms(),units.get(id).getAtoms(),3.5)) {second.add(entry.getKey());break;}
        }
        boolean standalone=environment.includeCofactor()&&environment.state().cofactor().isPresent()
                &&near(QuantumEnvironment.atoms(environment.state().cofactor().orElseThrow()).values(),ligand,4.0);
        String identity=environment.identity();var hash=ContentHash.accumulator().line(PROTOCOL).line(identity);
        for(var ids:List.of(direct,second,water,cofactor)) {hash.line(Integer.toString(ids.size()));for(var id:ids)hash.line(id.toString());}
        hash.line(Boolean.toString(standalone));
        return new FragmentSelection(direct,second,water,cofactor,standalone,identity,hash.finish());
    }
    static LinkedHashMap<ResidueId,Residue> residues(Structure structure) {
        var result=new LinkedHashMap<ResidueId,Residue>();
        for(var chain:structure.getChains())for(var residue:chain.residues())result.put(new ResidueId(chain.id(),residue.getNumber(),residue.getInsertionCode()),residue);
        return result;
    }
    static boolean near(Collection<Atom> left,Collection<Atom> right,double cutoff) {
        for(var a:left)if(a.isHeavyAtom())for(var b:right)if(b.isHeavyAtom()&&a.getPosition().distanceSquared(b.getPosition())<=cutoff*cutoff)return true;
        return false;
    }
    public static InteractionClass interactionClass(QuantumEnvironment environment,ResidueId residue) {
        var classes=interactionClasses(environment,residue);
        return classes.size()==1?classes.iterator().next():InteractionClass.mixed;
    }
    public static Set<InteractionClass> interactionClasses(QuantumEnvironment environment,ResidueId residue) {
        var classes=EnumSet.noneOf(InteractionClass.class);
        for(var annotation:environment.classAnnotations())if(annotation.residue().equals(residue))classes.add(annotation.interactionClass());
        for(var annotation:environment.annotations())if(annotation.residue().equals(residue))classes.add(switch(annotation.type()) {
            case HYDROGEN_BOND->InteractionClass.hydrogen_bond;
            case SALT_BRIDGE->InteractionClass.ionic;
            case HYDROPHOBIC_CONTACT->InteractionClass.dispersion;
            case PI_STACK_PARALLEL,PI_STACK_T_SHAPED->InteractionClass.pi_pi;
            case PI_CATION->InteractionClass.cation_pi;
            case HALOGEN_BOND->InteractionClass.halogen;
        });
        if(classes.isEmpty())classes.add(InteractionClass.unclassified);
        return Collections.unmodifiableSet(classes);
    }
}
