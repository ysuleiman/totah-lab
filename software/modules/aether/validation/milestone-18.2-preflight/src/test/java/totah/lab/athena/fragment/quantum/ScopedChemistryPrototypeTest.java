package totah.lab.athena.fragment.quantum;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.gaia.structure.*;
import totah.lab.gaia.chemistry.*;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.athena.interaction.perception.FormalChargeAssignments;
import totah.lab.aether.model.*;
import totah.lab.aether.provenance.ContentHash;
import static org.junit.jupiter.api.Assertions.*;

/** DESIGN PROTOTYPE ONLY. No METTL7 loading, integral, basis, SCF, cache or D3 call. */
class ScopedChemistryPrototypeTest {
    static final ResidueId UNIT=new ResidueId("R",149,null);
    static AtomReference ref(String n){return new AtomReference("R",149,' ',n);}
    static Atom atom(String n,Element e,double x,double y,double z,int id){return M18Fixtures.atom(n,e,x,y,z,id);}
    static List<Atom> syntheticSourceAtoms(){return List.of(
        atom("N",Element.N,-1,0,0,1),atom("H1",Element.H,-1.5,.8,0,2),atom("H2",Element.H,-1.5,-.8,0,3),
        atom("CA",Element.C,0,0,0,4),atom("HA",Element.H,0,0,1,5),atom("CB",Element.C,1.54,0,0,6),
        atom("HB2",Element.H,1.54,1,0,7),atom("HB3",Element.H,1.54,0,-1,8),atom("OG",Element.O,2.8,0,0,9),
        atom("C",Element.C,0,-1.5,0,10),atom("O",Element.O,0,-2.7,0,11),atom("OXT",Element.O,1.2,-1.5,0,12),atom("HOXT",Element.H,1.9,-1.5,0,13));}
    static Structure source(){return new Structure(List.of(new Chain("R",List.of(
        new Residue("SER",149,syntheticSourceAtoms()),new Residue("UNATTESTED",150,List.of(atom("U",Element.C,20,0,0,99)))))));}
    static List<Bond> referenceBonds(){
        var bs=new ArrayList<Bond>();
        for(String pair:List.of("N H1","N H2","N CA","CA HA","CA CB","CA C","CB HB2","CB HB3","CB OG","OG HG","C OXT","OXT HOXT")){
            var t=pair.split(" ");bs.add(new Bond(ref(t[0]),ref(t[1]),BondOrder.SINGLE));
        }
        bs.add(new Bond(ref("C"),ref("O"),BondOrder.DOUBLE));return List.copyOf(bs);
    }
    // Immutable candidate contract binds the *original* source and its exact covered domain.
    record ScopedProof(String originalHash,Set<AtomReference> coveredSource,List<Bond> reconstructedBonds,
                       Map<AtomReference,Integer> charges,Atom addedHydrogen,String addedHydrogenRule,
                       String independentTemplateId,int multiplicity) {
        ScopedProof {coveredSource=Set.copyOf(coveredSource);reconstructedBonds=List.copyOf(reconstructedBonds);charges=Map.copyOf(charges);}
    }
    static ScopedProof proof(Structure source){
        var covered=new HashSet<AtomReference>();var charges=new HashMap<AtomReference,Integer>();
        for(var a:syntheticSourceAtoms()){covered.add(ref(a.getName()));charges.put(ref(a.getName()),0);}charges.put(ref("HG"),0);
        return new ScopedProof(QuantumEnvironment.structureHash(source),covered,referenceBonds(),charges,
            atom("HG",Element.H,3.76,0,0,14),"SYNTHETIC_ONLY_OG_H_0.96A_FIXED_PLUS_X","HAND_ENUMERATED_NEUTRAL_SERINE_V1",1);
    }
    static Structure validatedProjection(Structure source,ScopedProof p){
        if(!p.originalHash().equals(QuantumEnvironment.structureHash(source)))throw new IllegalArgumentException("source hash");
        Set<AtomReference> expected=new HashSet<>();for(var a:syntheticSourceAtoms())expected.add(ref(a.getName()));
        if(!p.coveredSource().equals(expected))throw new IllegalArgumentException("scope or boundary coverage");
        if(!p.independentTemplateId().equals("HAND_ENUMERATED_NEUTRAL_SERINE_V1")||!new HashSet<>(p.reconstructedBonds()).equals(new HashSet<>(referenceBonds()))||p.reconstructedBonds().size()!=referenceBonds().size())throw new IllegalArgumentException("independent topology mismatch");
        if(!p.addedHydrogenRule().equals("SYNTHETIC_ONLY_OG_H_0.96A_FIXED_PLUS_X")||!p.addedHydrogen().getName().equals("HG")||p.addedHydrogen().getElement()!=Element.H||!p.addedHydrogen().getPosition().equals(new Point3D(3.76,0,0)))throw new IllegalArgumentException("hydrogen provenance");
        expected.add(ref("HG"));if(!p.charges().keySet().equals(expected)||p.charges().values().stream().anyMatch(q->q!=0)||p.multiplicity()!=1)throw new IllegalArgumentException("charge or multiplicity");
        var atoms=new ArrayList<>(source.findResidue(UNIT).orElseThrow().getAtoms());atoms.add(p.addedHydrogen());
        for(int i=0;i<syntheticSourceAtoms().size();i++)if(!atoms.get(i).getPosition().equals(syntheticSourceAtoms().get(i).getPosition()))throw new IllegalArgumentException("source geometry");
        var valence=new HashMap<AtomReference,Integer>();for(var b:p.reconstructedBonds()){int n=b.order()==BondOrder.DOUBLE?2:1;valence.merge(b.atom1(),n,Integer::sum);valence.merge(b.atom2(),n,Integer::sum);}
        for(var a:atoms)if(!QuantumFragmentBuilder.validValence(a.getElement().getAtomicNumber(),p.charges().get(ref(a.getName())),valence.getOrDefault(ref(a.getName()),0)))throw new IllegalArgumentException("valence");
        return new Structure(List.of(new Chain("R",List.of(new Residue("SER",149,atoms)))),p.reconstructedBonds());
    }
    static PreparedQuantumFragment build(Structure source,ScopedProof p)throws Exception {
        // VERIFIED below refers ONLY to this complete, independently checked synthetic
        // projection. It is never an attestation of the original whole source/receptor.
        var projection=validatedProjection(source,p);var water=M18Fixtures.water(true);
        var base=M18Fixtures.environment(projection,water.state().ligand(),false,false,QuantumEnvironment.InteractionClass.hydrogen_bond);
        var evidence=new QuantumEnvironment.ChemistryEvidence(QuantumEnvironment.ChargeStatus.VERIFIED,
            QuantumEnvironment.structureHash(projection),"Independent explicit synthetic template; derived scoped projection; original="+p.originalHash(),
            "Neutral serine source model; no physiological claim",new FormalChargeAssignments(p.charges()),true,true,p.multiplicity());
        var env=new QuantumEnvironment(base.state(),base.annotations(),base.classAnnotations(),Map.of("receptor",evidence),base.waterResidues(),base.cofactorResidues(),true,true);
        var raw=QuantumFragmentBuilder.receptorUnits(env,List.of(UNIT),true,"SYNTHETIC_SCOPED_SER_SIDECHAIN");
        if(raw.quantum().isEmpty())throw new IllegalArgumentException(raw.unavailableReasons().toString());
        String proofHash=ContentHash.sha256(p.originalHash()+"\n"+QuantumEnvironment.structureHash(projection)+"\n"+p.independentTemplateId()+"\n"+p.addedHydrogenRule()+"\n"+raw.receiptHash());
        // Proposed adapter provenance wraps the proven original/derived distinction;
        // existing downstream constructors already accept a nonambiguous prepared result.
        return new PreparedQuantumFragment(raw.id(),raw.sourceComponent(),proofHash,raw.sourceStateId(),p.originalHash(),
            Map.of("scope","R:149:SER","original_hash",p.originalHash(),"derived_projection_hash",QuantumEnvironment.structureHash(projection),"added_H_mapping","HG=>OG;"+p.addedHydrogenRule(),"proof",proofHash),
            raw.retained(),raw.deleted(),raw.caps(),QuantumEnvironment.ChargeStatus.ASSIGNED_BY_FROZEN_RULE,raw.formalCharge(),raw.multiplicity(),raw.protonationAssignment(),raw.chargeSource(),raw.quantum(),List.of(),proofHash);
    }
    @Test void proposedScopeReachesAetherInputWithoutAttestingSpectator()throws Exception {
        var source=source();var p=proof(source);var f=build(source,p);
        assertEquals(QuantumEnvironment.ChargeStatus.ASSIGNED_BY_FROZEN_RULE,f.chargeStatus());assertTrue(f.quantum().isPresent());
        assertEquals(1,f.caps().size());assertEquals(1.09,f.caps().getFirst().capAngstrom().distance(f.caps().getFirst().retainedOriginalAngstrom()),1e-14);
        assertEquals(0,f.formalCharge().orElseThrow());assertEquals(6,f.quantum().orElseThrow().system().nuclei().size());
        assertEquals(0,f.retained().stream().mapToInt(a->a.formalCharge().orElseThrow()).sum()+f.caps().stream().mapToInt(a->a.formalCharge()).sum());
        var other=new MolecularFragment("OTHER_SYNTHETIC_METHANOL",new QuantumSystem(f.quantum().orElseThrow().system().nuclei().stream().map(n->new NuclearCenter(n.centerBohr().add(new totah.lab.gaia.geometry.Vector3D(20,0,0)),n.charge())).toList(),0,1));
        var pair=new FragmentPair(f.quantum().orElseThrow(),other);assertEquals(12,pair.complex().nuclei().size());
        assertEquals(f.receiptHash(),build(source,p).receiptHash());assertFalse(f.retained().stream().anyMatch(a->a.reference().residueNumber()==150));
        String path=System.getProperty("m182.result");if(path!=null)Files.writeString(Path.of(path),new ObjectMapper().enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("status","SCREENING_ONLY","reaches_Aether_input",true,"final_formula","CH4O","charge",0,"multiplicity",1,"receipt",f.receiptHash(),"cap_protocol",QuantumFragmentBuilder.CAP_PROTOCOL,"Aether_calculation_calls",0,"scope_limitation","Closed synthetic residue projection only; native peptide-boundary proof still required"))+"\n");
    }
    @Test void rejectsMissingBoundaryCoverage(){var s=source();var p=proof(s);var covered=new HashSet<>(p.coveredSource());covered.remove(ref("CA"));assertThrows(IllegalArgumentException.class,()->build(s,new ScopedProof(p.originalHash(),covered,p.reconstructedBonds(),p.charges(),p.addedHydrogen(),p.addedHydrogenRule(),p.independentTemplateId(),1)));}
    @Test void rejectsMissingCharge(){var s=source();var p=proof(s);var q=new HashMap<>(p.charges());q.remove(ref("HG"));assertThrows(IllegalArgumentException.class,()->build(s,new ScopedProof(p.originalHash(),p.coveredSource(),p.reconstructedBonds(),q,p.addedHydrogen(),p.addedHydrogenRule(),p.independentTemplateId(),1)));}
    @Test void rejectsWrongBondOrder(){var s=source();var p=proof(s);var bs=new ArrayList<>(p.reconstructedBonds());bs.set(bs.size()-1,new Bond(ref("C"),ref("O"),BondOrder.SINGLE));assertThrows(IllegalArgumentException.class,()->build(s,new ScopedProof(p.originalHash(),p.coveredSource(),bs,p.charges(),p.addedHydrogen(),p.addedHydrogenRule(),p.independentTemplateId(),1)));}
    @Test void rejectsDifferentSource(){var s=source();var p=proof(s);assertThrows(IllegalArgumentException.class,()->build(M18Fixtures.transform(s),p));}
    @Test void legacyInputRuleRemainsStrict(){var s=source();var p=proof(s);assertThrows(IllegalArgumentException.class,()->new QuantumEnvironment.ChemistryEvidence(QuantumEnvironment.ChargeStatus.ASSIGNED_BY_FROZEN_RULE,p.originalHash(),"source","rule",new FormalChargeAssignments(p.charges()),true,true,1));}
}
