package totah.lab.athena.system;

import totah.lab.athena.clash.StericClashAnalysis;
import totah.lab.athena.interaction.*;
import totah.lab.gaia.geometry.AtomSelection;
import totah.lab.gaia.graph.AtomDistanceCriterion;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.mnemosyne.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static totah.lab.athena.system.SystemGraphCertificate.Capability.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Adapters to existing analyzers only; no new motif or energy rules. */
public final class ExistingSystemAnalyzers {
    private ExistingSystemAnalyzers() { }
    private abstract static class Base implements SystemGraphAnalyzer {
        private final String name; Base(String name){this.name=name;}
        public ScientificReference method(){return new ScientificReference(ScientificReference.Kind.METHOD,"athena.system",name,"1");}
        public Set<String> evidenceTypes(){return Set.of("athena:system-state");}
    }
    public static SystemGraphAnalyzer distances() {return new Base("distances") {
        public Set<SystemGraphCertificate.Capability> requires(){return Set.of(DISTANCE_QUERIES);}
        public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of(DISTANCE_QUERIES,NEIGHBORHOOD_TRAVERSAL);}
        public List<Finding> analyze(SystemStateView state,List<EvidenceEnvelope> evidence,Map<String,String> config) {
            double cutoff=positive(config,"contactCutoffAngstrom",4.5);
            var criterion=AtomDistanceCriterion.heavyAtomsWithin(cutoff);
            var pairs=state.graph().atomProximities(criterion);
            var covalent=state.graph().structure().bonds().stream().map(b -> Map.of("bond",b,"distance",state.atoms().get(b.atom1()).getPosition().distance(state.atoms().get(b.atom2()).getPosition()))).toList();
            return List.of(new Finding("spatial",List.of(state.subject()),SUPPORTED_PRESENT,
                    Map.of("cutoffAngstrom",Double.toString(cutoff),"pairs",text(pairs),"covalentDistances",text(covalent)),
                    List.of("existing ResidueGraph spatial queries; no interaction inferred"),List.of("cutoff query, not complete physical coupling")));
        }
    };}
    public static SystemGraphAnalyzer clashes(){return new Base("clashes") {
        public Set<SystemGraphCertificate.Capability> requires(){return Set.of(CLASH_ANALYSIS);}
        public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of(CLASH_ANALYSIS);}
        public List<Finding> analyze(SystemStateView state,List<EvidenceEnvelope> evidence,Map<String,String> config) {
            var clashes=StericClashAnalysis.findClashes(state.graph().structure(),new StericClashAnalysis.Options(positive(config,"clashRadiusScale",0.7)));
            var coincident=new ArrayList<String>();var entries=new ArrayList<>(state.atoms().entrySet());
            for(int i=0;i<entries.size();i++)for(int j=i+1;j<entries.size();j++)if(entries.get(i).getValue().getPosition().equals(entries.get(j).getValue().getPosition()))coincident.add(entries.get(i).getKey()+" / "+entries.get(j).getKey());
            return List.of(new Finding("clashes",List.of(state.subject()),clashes.isEmpty()?ABSENT_FALSE:SUPPORTED_PRESENT,
                    Map.of("clashes",text(clashes),"coincidentAtoms",text(coincident)),List.of("existing scaled-vdW clash criteria"),
                    List.of("existing same-residue/explicit-bond exclusions; unknown connectivity limits physical interpretation")));
        }
    };}
    /** Explicit ligand residue selection; no ligand guessing or pocket cropping. */
    public static SystemGraphAnalyzer interactions(Set<ResidueId> ligandResidues){
        Set<ResidueId> selection=Set.copyOf(ligandResidues);
        return new Base("interactions-"+SystemStateView.digest(selection.stream().sorted(Comparator.comparing(ResidueId::toString)).toList())) {
            public Set<SystemGraphCertificate.Capability> requires(){return Set.of(DISTANCE_QUERIES);}
            public Set<SystemGraphCertificate.Capability> qualifies(){return Set.of(INTERACTION_TYPING,HBOND_ANALYSIS);}
            public List<Finding> analyze(SystemStateView state,List<EvidenceEnvelope> evidence,Map<String,String> config) {
                if(selection.isEmpty()||!state.graph().residueIds().containsAll(selection)||!Collections.disjoint(selection,state.cofactors()))throw new IllegalArgumentException("invalid explicit ligand selection");
                var receptor=state.graph().view(state.graph().residueIds().stream().filter(r->!selection.contains(r)&&!state.cofactors().contains(r)).toList()).toStructure();
                double radius=1;
                for(var a:state.atoms().values())for(var b:state.atoms().values())radius=Math.max(radius,a.getPosition().distance(b.getPosition())+1);
                var profile=new InteractionProfiler(InteractionThresholds.athenaDefaults(),radius).profile(receptor,
                        state.graph().view(selection).toStructure(),state.graph().view(state.cofactors()).toStructure(),state.charges());
                boolean complete=state.protonationQualified()&&!profile.anyPerceptionDegraded()&&state.atoms().values().stream().allMatch(a->a.getAutoDockType()!=null);
                return List.of(new Finding("interactions",List.of(state.subject()),complete?(profile.interactions().isEmpty()?ABSENT_FALSE:SUPPORTED_PRESENT):UNKNOWN_INCONCLUSIVE,
                        Map.of("profile",text(profile)),List.of("existing interaction detectors; typed result is not binding or causality"),
                        complete?List.of():List.of("incomplete preparation/perception; empty output cannot establish absence")));
            }
        };
    }
    private static double positive(Map<String,String> config,String key,double fallback){double d=Double.parseDouble(config.getOrDefault(key,Double.toString(fallback)));if(!Double.isFinite(d)||d<=0)throw new IllegalArgumentException(key);return d;}
    private static String text(Object value){return new String(SystemStateView.bytes(value),StandardCharsets.UTF_8);}
}
