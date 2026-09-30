package totah.lab.athena.relaxation;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProteinDefinedMaskTest {
    static final AtomSelectionRules.Mobility FIXED = AtomSelectionRules.Mobility.FIXED;
    static final AtomSelectionRules.Mobility RESTRAINED = AtomSelectionRules.Mobility.POSITION_RESTRAINED;
    static final AtomSelectionRules.Mobility MOBILE = AtomSelectionRules.Mobility.LOCAL_MOBILE;
    static final SystemAtomMapping.ComponentRole SIDE = SystemAtomMapping.ComponentRole.PROTEIN_SIDE_CHAIN;
    static ProteinDefinedMask mask() {
        return new ProteinDefinedMask(List.of(rule(196, MOBILE), rule(192, RESTRAINED),
                rule(203, RESTRAINED), rule(206, RESTRAINED)), "network-mask-v1");
    }
    static ProteinDefinedMask.Rule rule(int position, AtomSelectionRules.Mobility mobility) {
        return new ProteinDefinedMask.Rule("A", position, ' ', SIDE, Set.of(), mobility);
    }
    static Atom atom(String name, double x) {
        return Atom.builder().name(name).element(Element.C).position(new Point3D(x,0,0)).build();
    }
    static Structure protein(boolean b) {
        List<Residue> rs = new ArrayList<>();
        for (int p : List.of(151,192,196,201,202,203,206,207)) {
            List<Atom> atoms = new ArrayList<>(List.of(atom("CA",p),atom("CB",p+1)));
            if (b && p==196) atoms.add(atom("CG",999));
            rs.add(new Residue(p==196 ? (b?"LYS":"HIS") : "ALA", p, atoms));
        }
        return new Structure(List.of(new Chain("A",rs)));
    }
    static Structure ligand(boolean present) {
        return present ? new Structure(List.of(new Chain("L",List.of(new Residue("LIG",1,
                List.of(atom("C1", -10000))))))) : new Structure(List.of());
    }
    static LocalRelaxationProtocol protocol(Structure p, Structure l) {
        List<SystemAtomMapping.Entry> entries=new ArrayList<>();
        for (var c:p.getChains()) for(var r:c.residues()) for(var a:r.getAtoms())
            entries.add(new SystemAtomMapping.Entry(new AtomReference(c.id(),r.getNumber(),' ',a.getName()),
                    entries.size(),a.getName().equals("CA")?SystemAtomMapping.ComponentRole.PROTEIN_BACKBONE:SIDE));
        for(var c:l.getChains())for(var r:c.residues())for(var a:r.getAtoms())
            entries.add(new SystemAtomMapping.Entry(new AtomReference(c.id(),r.getNumber(),' ',a.getName()),
                    entries.size(),SystemAtomMapping.ComponentRole.LIGAND));
        var k=new HarmonicForceConstant(418.4,HarmonicForceConstant.Unit.KILOJOULES_PER_MOLE_NANOMETRE_SQUARED);
        var none=new LocalRelaxationProtocol.Restraint(LocalRelaxationProtocol.Policy.NONE,Optional.empty(),0);
        var rules=new AtomSelectionRules(FIXED,RESTRAINED,MOBILE,RESTRAINED,FIXED,
                AtomSelectionRules.Mobility.UNRESTRAINED,RESTRAINED,
                AtomSelectionRules.Mobility.UNRESTRAINED,RESTRAINED,FIXED,FIXED,
                AtomSelectionRules.RadiusReference.LIGAND_HEAVY_ATOMS,"legacy-rules-v1");
        return new LocalRelaxationProtocol("test",5,LocalRelaxationProtocol.BackbonePolicy.FIXED,Optional.empty(),
                LocalRelaxationProtocol.SideChainPolicy.WITHIN_RADIUS_RESTRAINED,Optional.of(k),none,none,
                SystemAtomMapping.create(entries,"fixture"),rules,"LocalEnergyMinimizer",1,100,8,true);
    }
    static RelaxationAtomSelector.Selection select(boolean b, boolean lig, ProteinDefinedMask mask) {
        var p=protein(b);var l=ligand(lig);
        return new RelaxationAtomSelector().select(protocol(p,l),p,l,null,mask);
    }
    @Test void apoHasNoLigandParticlesAndCorrectMask() {
        var s=select(false,false,mask());assertEquals(16,s.atoms().size());
        for(var a:s.atoms())assertEquals(a.componentRole()!=SIDE?FIXED:
                a.gaiaAtom().residueNumber()==196?MOBILE:
                Set.of(192,203,206).contains(a.gaiaAtom().residueNumber())?RESTRAINED:FIXED,a.mobility());
    }
    @Test void exactProteinAssignmentsAndSemanticHashAreLigandIndependent() {
        var apo=select(false,false,mask());var holo=select(false,true,mask());
        assertEquals(apo.atoms(),holo.atoms().subList(0,apo.atoms().size()));
        assertEquals(apo.semanticPolicySha256(),holo.semanticPolicySha256());
        assertEquals(FIXED,holo.atoms().getLast().mobility());
        assertNotEquals(apo.selectionMaskSha256(),holo.selectionMaskSha256()); // Different particle maps.
    }
    @Test void residueIdentityAndSidechainSizeDoNotChangePositionalRules() {
        var a=select(false,false,mask());var b=select(true,false,mask());
        assertEquals(a.semanticPolicySha256(),b.semanticPolicySha256());
        for(var x:b.atoms())if(x.gaiaAtom().residueNumber()==196&&x.componentRole()==SIDE)assertEquals(MOBILE,x.mobility());
    }
    @Test void ruleOrderDoesNotChangeReceiptAndMaskIsImmutable() {
        var m=mask();assertEquals(select(false,false,m),select(false,false,new ProteinDefinedMask(m.rules().reversed(),m.provenance())));
        assertThrows(UnsupportedOperationException.class,()->m.rules().clear());
    }
    @Test void missingAndOverlappingRulesFailClosed() {
        assertThrows(IllegalArgumentException.class,()->select(false,false,new ProteinDefinedMask(List.of(rule(999,MOBILE)),"missing")));
        assertThrows(IllegalArgumentException.class,()->select(false,false,new ProteinDefinedMask(List.of(rule(196,MOBILE),rule(196,MOBILE)),"duplicate")));
        var missingAtom=new ProteinDefinedMask.Rule("A",196,' ',SIDE,Set.of("CB","ZZ"),MOBILE);
        assertThrows(IllegalArgumentException.class,()->select(false,false,new ProteinDefinedMask(List.of(missingAtom),"missing-atom")));
    }
    @Test void backboneCannotBecomeMobileAndLegacyStillRequiresLigand() {
        assertThrows(IllegalArgumentException.class,()->new ProteinDefinedMask.Rule("A",196,' ',
                SystemAtomMapping.ComponentRole.PROTEIN_BACKBONE,Set.of(),MOBILE));
        var p=protein(false);var l=ligand(false);
        assertThrows(IllegalArgumentException.class,()->new RelaxationAtomSelector().select(protocol(p,l),p,l,null));
    }
    @Test void atomSpecificRuleOnlyMovesRequestedAtom() {
        var m=new ProteinDefinedMask(List.of(new ProteinDefinedMask.Rule("A",196,' ',SIDE,Set.of("CB"),MOBILE)),"specific");
        var s=select(true,false,m);
        assertEquals(FIXED,s.atoms().stream().filter(a->a.gaiaAtom().atomName().equals("CG")).findFirst().orElseThrow().mobility());
    }
    public static void main(String[] args) {System.out.println(select(false,false,mask()));System.out.println(select(true,true,mask()));}
}
