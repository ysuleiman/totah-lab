package totah.lab.athena.recognition;

import org.junit.jupiter.api.Test;
import totah.lab.athena.design.feature.LigandFeature;
import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionThresholds;
import totah.lab.athena.interaction.InteractionType;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionStableFeatureAssignerTest {
    private final InteractionStableFeatureAssigner assigner=new InteractionStableFeatureAssigner();
    @Test void aromaticPi(){assertAssigned(InteractionType.PI_STACK_PARALLEL,LigandFeature.Type.AROMATIC_RING);}
    @Test void donor(){assertAssigned(InteractionType.HYDROGEN_BOND,LigandFeature.Type.H_BOND_DONOR);}
    @Test void acceptor(){assertAssigned(InteractionType.HYDROGEN_BOND,LigandFeature.Type.H_BOND_ACCEPTOR);}
    @Test void chargedGroup(){assertAssigned(InteractionType.SALT_BRIDGE,LigandFeature.Type.POSITIVE_CENTER);}
    @Test void hydrophobic(){assertAssigned(InteractionType.HYDROPHOBIC_CONTACT,LigandFeature.Type.HYDROPHOBE);}
    @Test void symmetryEquivalent(){var r=assigner.assign(interaction(InteractionType.PI_CATION),Set.of("o"),map(
            feature("a",LigandFeature.Type.AROMATIC_RING,List.of("o")),feature("b",LigandFeature.Type.AROMATIC_RING,List.of("o"))),EvidenceQuality.ADEQUATE);assertThat(r.status()).isEqualTo(InteractionStableFeatureAssigner.Status.SYMMETRY_EQUIVALENT_FEATURE_ASSIGNMENT);}
    @Test void nonEquivalentAmbiguity(){var r=assigner.assign(interaction(InteractionType.PI_CATION),Set.of("o"),map(
            feature("a",LigandFeature.Type.AROMATIC_RING,List.of("o")),feature("b",LigandFeature.Type.AROMATIC_RING,List.of("o","p"))),EvidenceQuality.ADEQUATE);assertThat(r.status()).isEqualTo(InteractionStableFeatureAssigner.Status.AMBIGUOUS_FEATURE_ASSIGNMENT);}
    @Test void noCompatible(){var r=assigner.assign(interaction(InteractionType.HYDROPHOBIC_CONTACT),Set.of("o"),map(feature("a",LigandFeature.Type.HALOGEN,List.of("o"))),EvidenceQuality.ADEQUATE);assertThat(r.status()).isEqualTo(InteractionStableFeatureAssigner.Status.FEATURE_ASSIGNMENT_UNAVAILABLE);}
    @Test void degradedPreserved(){var r=assigner.assign(interaction(InteractionType.PI_STACK_PARALLEL),Set.of("o"),map(feature("a",LigandFeature.Type.AROMATIC_RING,List.of("o"))),EvidenceQuality.DEGRADED);assertThat(r.interactionQuality()).isEqualTo(EvidenceQuality.DEGRADED);}
    @Test void deterministicHash(){var f=map(feature("a",LigandFeature.Type.HYDROPHOBE,List.of("o")));var a=assigner.assign(interaction(InteractionType.HYDROPHOBIC_CONTACT),Set.of("o"),f,EvidenceQuality.ADEQUATE);var b=assigner.assign(interaction(InteractionType.HYDROPHOBIC_CONTACT),Set.of("o"),f,EvidenceQuality.ADEQUATE);assertThat(a.assignmentSha256()).isEqualTo(b.assignmentSha256());}
    @Test void halogenBondUsesDonorHalogenRatherThanItsBondedCarbon(){
        Atom protein=atom("O",1), chlorine=atom("CL",2,Element.CL), carbon=atom("C",3);
        Interaction interaction=new Interaction(InteractionType.HALOGEN_BOND,new ResidueId("A",1,null),
                List.of(protein),List.of(chlorine,carbon),3.0,120.0,170.0,null,null,InteractionThresholds.athenaDefaults());
        var result=assigner.assign(interaction,Map.of(2,"halogen-orbit",3,"carbon-orbit"),
                map(feature("cl",LigandFeature.Type.HALOGEN,List.of("halogen-orbit"))),EvidenceQuality.ADEQUATE);
        assertThat(result.status()).isEqualTo(InteractionStableFeatureAssigner.Status.UNIQUE_FEATURE_ASSIGNMENT);
        assertThat(result.stableFeatureId()).contains("cl");
    }
    @Test void halogenBondWithoutMappedDonorHalogenFailsClosed(){
        Atom protein=atom("O",1), chlorine=atom("CL",2,Element.CL), carbon=atom("C",3);
        Interaction interaction=new Interaction(InteractionType.HALOGEN_BOND,new ResidueId("A",1,null),
                List.of(protein),List.of(chlorine,carbon),3.0,120.0,170.0,null,null,InteractionThresholds.athenaDefaults());
        var result=assigner.assign(interaction,Map.of(3,"carbon-orbit"),
                map(feature("cl",LigandFeature.Type.HALOGEN,List.of("halogen-orbit"))),EvidenceQuality.DEGRADED);
        assertThat(result.status()).isEqualTo(InteractionStableFeatureAssigner.Status.FEATURE_ASSIGNMENT_UNAVAILABLE);
        assertThat(result.interactionQuality()).isEqualTo(EvidenceQuality.DEGRADED);
    }
    @Test void bromineAndIodineUseExistingHalogenFeatures(){
        for(Element element:List.of(Element.BR,Element.I)){
            var result=assigner.assign(halogenBond(element),Map.of(2,"x",3,"c"),
                    map(feature("halogen",LigandFeature.Type.HALOGEN,List.of("x"))),EvidenceQuality.ADEQUATE);
            assertThat(result.status()).isEqualTo(InteractionStableFeatureAssigner.Status.UNIQUE_FEATURE_ASSIGNMENT);
        }
    }
    @Test void halogenSymmetryEquivalentFeaturesRemainEquivalent(){
        var result=assigner.assign(halogenBond(Element.CL),Map.of(2,"x",3,"c"),map(
                feature("a",LigandFeature.Type.HALOGEN,List.of("x")),
                feature("b",LigandFeature.Type.HALOGEN,List.of("x"))),EvidenceQuality.ADEQUATE);
        assertThat(result.status()).isEqualTo(InteractionStableFeatureAssigner.Status.SYMMETRY_EQUIVALENT_FEATURE_ASSIGNMENT);
    }
    @Test void halogenNonEquivalentFeaturesRemainAmbiguous(){
        var result=assigner.assign(halogenBond(Element.CL),Map.of(2,"x",3,"c"),map(
                feature("a",LigandFeature.Type.HALOGEN,List.of("x")),
                feature("b",LigandFeature.Type.HALOGEN,List.of("x","other"))),EvidenceQuality.ADEQUATE);
        assertThat(result.status()).isEqualTo(InteractionStableFeatureAssigner.Status.AMBIGUOUS_FEATURE_ASSIGNMENT);
    }
    @Test void nonHalogenDonorAtomIsRejectedForHalogenAssignment(){
        var result=assigner.assign(halogenBond(Element.C),Map.of(2,"x",3,"c"),
                map(feature("halogen",LigandFeature.Type.HALOGEN,List.of("x"))),EvidenceQuality.ADEQUATE);
        assertThat(result.status()).isEqualTo(InteractionStableFeatureAssigner.Status.FEATURE_ASSIGNMENT_UNAVAILABLE);
    }
    @Test void halogenFeatureTypeMismatchIsRejected(){
        var result=assigner.assign(halogenBond(Element.CL),Map.of(2,"x",3,"c"),
                map(feature("hydrophobe",LigandFeature.Type.HYDROPHOBE,List.of("x"))),EvidenceQuality.ADEQUATE);
        assertThat(result.status()).isEqualTo(InteractionStableFeatureAssigner.Status.FEATURE_ASSIGNMENT_UNAVAILABLE);
    }
    @Test void halogenAssignmentHashIsDeterministic(){
        var features=map(feature("halogen",LigandFeature.Type.HALOGEN,List.of("x")));
        var first=assigner.assign(halogenBond(Element.CL),Map.of(2,"x",3,"c"),features,EvidenceQuality.DEGRADED);
        var second=assigner.assign(halogenBond(Element.CL),Map.of(3,"c",2,"x"),features,EvidenceQuality.DEGRADED);
        assertThat(first.assignmentSha256()).isEqualTo(second.assignmentSha256());
        assertThat(first.interactionQuality()).isEqualTo(EvidenceQuality.DEGRADED);
    }
    @Test void hydrogenBondDirectionDisambiguatesDualRoleAtom(){
        var features=map(feature("donor",LigandFeature.Type.H_BOND_DONOR,List.of("o")),
                feature("acceptor",LigandFeature.Type.H_BOND_ACCEPTOR,List.of("o")));
        assertThat(assigner.assign(hydrogenBond(true),Set.of("o"),features,EvidenceQuality.ADEQUATE)
                .stableFeatureId()).contains("donor");
        assertThat(assigner.assign(hydrogenBond(false),Set.of("o"),features,EvidenceQuality.ADEQUATE)
                .stableFeatureId()).contains("acceptor");
    }
    private void assertAssigned(InteractionType type,LigandFeature.Type feature){
        Interaction value=type==InteractionType.HYDROGEN_BOND
                ?hydrogenBond(feature==LigandFeature.Type.H_BOND_DONOR):interaction(type);
        assertThat(assigner.assign(value,Set.of("o"),map(feature("a",feature,List.of("o"))),EvidenceQuality.ADEQUATE).status()).isEqualTo(InteractionStableFeatureAssigner.Status.UNIQUE_FEATURE_ASSIGNMENT);}
    private static StableLigandFeatureMap map(StableLigandFeatureMap.Feature...f){return new StableLigandFeatureMap("lig",List.of(f),"orbits","features",EvidenceQuality.ADEQUATE,List.of());}
    private static StableLigandFeatureMap.Feature feature(String id,LigandFeature.Type t,List<String>o){return new StableLigandFeatureMap.Feature(id,t,o,List.of(id+"-src"),EvidenceQuality.ADEQUATE,Map.of());}
    private static Interaction interaction(InteractionType t){Atom p=atom("P",1),l=atom("L",2);return new Interaction(t,new ResidueId("A",1,null),List.of(p),List.of(l),3.0,null,null,null,null,InteractionThresholds.athenaDefaults());}
    private static Interaction hydrogenBond(boolean ligandDonor){Atom p=atom("P",1),l=atom("L",2),h=hydrogen("H",3);
        return new Interaction(InteractionType.HYDROGEN_BOND,new ResidueId("A",1,null),
                ligandDonor?List.of(p):List.of(p,h),ligandDonor?List.of(l,h):List.of(l),3.0,null,null,null,null,InteractionThresholds.athenaDefaults());}
    private static Interaction halogenBond(Element donor){return new Interaction(InteractionType.HALOGEN_BOND,
            new ResidueId("A",1,null),List.of(atom("O",1)),List.of(atom("X",2,donor),atom("C",3)),
            3.0,120.0,170.0,null,null,InteractionThresholds.athenaDefaults());}
    private static Atom atom(String n,int s){return Atom.builder().name(n).pdbSerial(s).element(Element.C).position(new Point3D(0,0,0)).build();}
    private static Atom atom(String n,int s,Element element){return Atom.builder().name(n).pdbSerial(s).element(element).position(new Point3D(0,0,0)).build();}
    private static Atom hydrogen(String n,int s){return Atom.builder().name(n).pdbSerial(s).element(Element.H).position(new Point3D(0,0,0)).build();}
}
