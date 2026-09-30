package totah.lab.athena.fragment.quantum;

import java.util.*;
import totah.lab.aether.model.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.*;
import static totah.lab.athena.fragment.quantum.PreparedQuantumFragment.*;

/** Deterministic construction, never protonation repair or optimization. */
public final class QuantumFragmentBuilder {
    public static final double ANGSTROM_PER_BOHR=0.52917721092;
    public static final String CAP_PROTOCOL="athena-cap-18-1;neutral-saturated-C-C-single-only;H=1.09A;source-bond-direction;no-relaxation";
    public static final String PROTOCOL="athena-fragment-18-1;source-order;angstrom-per-bohr=0.52917721092;explicit-H-valence;"+CAP_PROTOCOL;
    private static final Set<String> BACKBONE=Set.of("N","CA","C","O","OXT");
    private QuantumFragmentBuilder() {}

    public static PreparedQuantumFragment whole(QuantumEnvironment environment,String component,String id) {
        var atoms=QuantumEnvironment.atoms(structure(environment,component)).keySet();
        return build(environment,component,id,atoms,atoms,List.of());
    }
    /** Selects distinct explicit units in source atom order; disconnected units retain independent caps. */
    public static PreparedQuantumFragment receptorUnits(QuantumEnvironment environment,List<ResidueId> units,boolean sidechains,String id) {
        // Preserve legacy diagnostics as well as receipts for existing 1/2-unit callers.
        if(units.isEmpty()||new HashSet<>(units).size()!=units.size())throw new IllegalArgumentException(
                units.size()<=2?"One or two distinct explicit receptor units required":"Distinct explicit receptor units required");
        var structure=environment.state().receptor();var all=QuantumEnvironment.atoms(structure);var residues=FragmentSelection.residues(structure);
        var retain=new LinkedHashSet<AtomReference>();var scope=new LinkedHashSet<AtomReference>();var problems=new ArrayList<String>();
        for(var unit:units) {
            var residue=Optional.ofNullable(residues.get(unit)).orElseThrow(()->new IllegalArgumentException("Missing requested residue"));
            if(sidechains&&residue.getName().equals("PRO"))problems.add("PROLINE_RING_CUT_UNSUPPORTED");
            for(var ref:all.keySet())if(sameResidue(ref,unit)) {scope.add(ref);if(!sidechains||!BACKBONE.contains(ref.atomName()))retain.add(ref);}
        }
        if(sidechains)for(var bond:structure.getBonds()) {
            var a=all.get(bond.atom1());var b=all.get(bond.atom2());
            if(a.isHydrogen()&&BACKBONE.contains(bond.atom2().atomName()))retain.remove(bond.atom1());
            if(b.isHydrogen()&&BACKBONE.contains(bond.atom1().atomName()))retain.remove(bond.atom2());
        }
        return build(environment,"receptor",id,retain,scope,problems);
    }
    static boolean sameResidue(AtomReference atom,ResidueId residue) {
        return atom.chainId().equals(residue.chainId())&&atom.residueNumber()==residue.residueNumber()
                &&atom.insertionCode()==(residue.insertionCode()==null?' ':residue.insertionCode());
    }
    private static Structure structure(QuantumEnvironment e,String component) {
        return switch(component) {case "receptor"->e.state().receptor();case "ligand"->e.state().ligand();case "cofactor"->e.state().cofactor().orElseThrow();default->throw new IllegalArgumentException("Unknown component");};
    }
    private static PreparedQuantumFragment build(QuantumEnvironment environment,String component,String id,Set<AtomReference> requested,Set<AtomReference> scope,List<String> initialProblems) {
        QuantumEnvironment.text(id);var source=structure(environment,component);var all=QuantumEnvironment.atoms(source);
        if(!all.keySet().containsAll(requested))throw new IllegalArgumentException("Fragment selection includes unknown atoms");
        var problems=new ArrayList<>(initialProblems);var retained=new ArrayList<OriginalAtom>();var deleted=new ArrayList<OriginalAtom>();
        var evidence=environment.chemistry().get(component);
        Map<AtomReference,Integer> charges=evidence==null?Map.of():evidence.formalCharges().charges();
        var caps=new ArrayList<CapAtom>();var names=new HashMap<AtomReference,String>();
        for(var chain:source.getChains())for(var residue:chain.residues())for(var atom:residue.getAtoms())
            names.put(new AtomReference(chain.id(),residue.getNumber(),residue.getInsertionCode()==null?' ':residue.getInsertionCode(),atom.getName()),residue.getName());
        int position=0;
        for(var entry:all.entrySet()) {
            if(!scope.contains(entry.getKey())){position++;continue;}
            var atom=entry.getValue();var q=charges.get(entry.getKey());
            var original=new OriginalAtom(entry.getKey(),names.get(entry.getKey()),position++,atom.getPdbSerial(),atom.getElement().getAtomicNumber(),atom.getPosition(),q==null?OptionalInt.empty():OptionalInt.of(q));
            (requested.contains(entry.getKey())?retained:deleted).add(original);
        }
        if(retained.isEmpty()||retained.stream().noneMatch(a->a.atomicNumber()!=1))problems.add("NO_HEAVY_FRAGMENT_ATOMS");
        if(evidence==null||evidence.status()==QuantumEnvironment.ChargeStatus.AMBIGUOUS)problems.add("AMBIGUOUS_CHARGE_PROTONATION");
        if(evidence==null||!evidence.completeTopology())problems.add("INCOMPLETE_TOPOLOGY_EVIDENCE");
        if(evidence==null||!evidence.completeExplicitHydrogens())problems.add("INCOMPLETE_HYDROGEN_EVIDENCE");
        int multiplicity=evidence==null?1:evidence.multiplicity();
        if(multiplicity!=1)problems.add("OPEN_SHELL_UNSUPPORTED");
        int charge=0;boolean completeCharge=true;
        for(var atom:retained) {
            Integer q=charges.get(atom.reference());
            if(q==null){completeCharge=false;problems.add("MISSING_FORMAL_CHARGE:"+atom.reference());}
            else charge=Math.addExact(charge,q);
        }
        var valences=new HashMap<AtomReference,Integer>();var boundaries=new ArrayList<Bond>();
        for(var bond:source.getBonds()) {
            boolean left=requested.contains(bond.atom1()),right=requested.contains(bond.atom2());
            if(!left&&!right)continue;
            int order=switch(bond.order()){case SINGLE->1;case DOUBLE->2;case TRIPLE->3;default->0;};
            if(order==0)problems.add("UNRESOLVED_BOND_ORDER:"+bond);
            if(left&&right) {valences.merge(bond.atom1(),order,Integer::sum);valences.merge(bond.atom2(),order,Integer::sum);}
            else boundaries.add(bond);
        }
        boundaries.sort(Comparator.comparing((Bond b)->requested.contains(b.atom1())?b.atom1():b.atom2())
                .thenComparing(b->requested.contains(b.atom1())?b.atom2():b.atom1()));
        for(var boundary:boundaries) {
            var keep=requested.contains(boundary.atom1())?boundary.atom1():boundary.atom2();var drop=keep.equals(boundary.atom1())?boundary.atom2():boundary.atom1();
            boolean saturated=source.getBonds().stream().filter(b->b.atom1().equals(keep)||b.atom2().equals(keep)).allMatch(b->b.order()==BondOrder.SINGLE);
            if(evidence==null||!evidence.completeTopology()||boundary.order()!=BondOrder.SINGLE||!saturated
                    ||all.get(keep).getElement().getAtomicNumber()!=6||all.get(drop).getElement().getAtomicNumber()!=6
                    ||!Integer.valueOf(0).equals(charges.get(keep))||!Integer.valueOf(0).equals(charges.get(drop))) {
                problems.add("UNSUPPORTED_CAP_BOUNDARY:"+boundary);continue;
            }
            var p=all.get(keep).getPosition();var q=all.get(drop).getPosition();double distance=p.distance(q);
            if(distance<=1e-12){problems.add("COINCIDENT_CAP_BOUNDARY");continue;}
            var cap=p.add(p.vectorTo(q).scale(1.09/distance));
            caps.add(new CapAtom("CAP:"+caps.size(),keep,drop,p,q,cap,0,CAP_PROTOCOL));valences.merge(keep,1,Integer::sum);
        }
        for(var atom:retained) {
            Integer q=charges.get(atom.reference());
            if(q!=null&&!validValence(atom.atomicNumber(),q,valences.getOrDefault(atom.reference(),0)))problems.add("INCOMPLETE_OR_UNSUPPORTED_VALENCE:"+atom.reference());
        }
        var status=problems.isEmpty()?(caps.isEmpty()?evidence.status():QuantumEnvironment.ChargeStatus.ASSIGNED_BY_FROZEN_RULE):QuantumEnvironment.ChargeStatus.AMBIGUOUS;
        Optional<MolecularFragment> quantum=Optional.empty();
        if(problems.isEmpty())try {
            var nuclei=new ArrayList<NuclearCenter>();
            for(var a:retained)nuclei.add(new NuclearCenter(bohr(a.originalAngstrom()),a.atomicNumber()));
            for(var a:caps)nuclei.add(new NuclearCenter(bohr(a.capAngstrom()),1));
            quantum=Optional.of(new MolecularFragment(id,new QuantumSystem(nuclei,charge,multiplicity)));
        }catch(IllegalArgumentException exception){problems.add("UNSUPPORTED_QUANTUM_SYSTEM:"+exception.getMessage());status=QuantumEnvironment.ChargeStatus.AMBIGUOUS;}
        var hash=ContentHash.accumulator().line(PROTOCOL).line(environment.identity()).line(component).line(ContentHash.sha256(id));
        for(var list:List.of(retained,deleted)) {hash.line(Integer.toString(list.size()));for(var atom:list)hash.line(atom.reference().toString()).line(Integer.toString(atom.sourceOrder())).line(coordinates(atom.originalAngstrom()));}
        for(var cap:caps)hash.line(cap.id()).line(cap.retainedBoundary().toString()).line(cap.deletedBoundary().toString()).line(coordinates(cap.capAngstrom()));
        hash.line(status.name()).line(completeCharge?Integer.toString(charge):"UNAVAILABLE").line(Integer.toString(multiplicity));
        for(String problem:problems)hash.line(problem);
        return new PreparedQuantumFragment(id,component,environment.identity(),environment.state().stateId(),QuantumEnvironment.structureHash(source),environment.state().provenance(),retained,deleted,caps,status,completeCharge?OptionalInt.of(charge):OptionalInt.empty(),
                multiplicity,evidence==null?"UNAVAILABLE":evidence.protonationAssignment(),evidence==null?"UNAVAILABLE":evidence.source(),quantum,problems,hash.finish());
    }
    static Point3D bohr(Point3D p){return new Point3D(p.x()/ANGSTROM_PER_BOHR,p.y()/ANGSTROM_PER_BOHR,p.z()/ANGSTROM_PER_BOHR);}
    static String coordinates(Point3D p){return Double.toHexString(p.x())+"|"+Double.toHexString(p.y())+"|"+Double.toHexString(p.z());}
    static boolean validValence(int z,int charge,int bonds) {
        return switch(z) {
            case 1->charge==0&&bonds==1;
            case 6->charge==0?bonds==4:Math.abs(charge)==1&&bonds==3;
            case 7->charge==0?bonds==3:charge==1?bonds==4:charge==-1&&bonds==2;
            case 8->charge==0?bonds==2:charge==-1?bonds==1:charge==1&&bonds==3;
            case 15->charge==0?(bonds==3||bonds==5):charge==1&&bonds==4;
            case 16->charge==0?(bonds==2||bonds==4||bonds==6):charge==1?bonds==3:charge==-1&&bonds==1;
            case 17->charge==0?bonds==1:charge==-1&&bonds==0;
            default->false;
        };
    }
}
