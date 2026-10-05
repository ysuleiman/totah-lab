package totah.lab.athena.system;

import totah.lab.athena.design.backend.*;
import totah.lab.gaia.chemistry.BondOrder;
import java.util.*;
import static totah.lab.athena.system.SystemGraphCertificate.*;

/** Independent checks; unknown source chemistry never becomes invented topology or a global rejection. */
public final class SystemGraphValidation {
    public static final String VERSION="system-graph-validation/1";
    private final MolecularSanitizer sanitizer;
    private final CanonicalIdentityService identity;
    private final StereochemistryService stereo;
    public SystemGraphValidation(MolecularSanitizer sanitizer,CanonicalIdentityService identity,StereochemistryService stereo) {
        this.sanitizer=sanitizer;this.identity=identity;this.stereo=stereo;
    }
    public record Result(Map<Capability,Qualification> capabilities,List<Check> checks) {
        public Result {capabilities=Collections.unmodifiableMap(new TreeMap<>(capabilities));checks=List.copyOf(checks);}
    }
    public Result validate(SystemStateView state) {
        var checks=new ArrayList<Check>();var caps=new EnumMap<Capability,Qualification>(Capability.class);
        for(var c:Capability.values())caps.put(c,q(Status.NOT_EVALUATED,"no qualified evaluator for this capability"));
        boolean coordinates=!state.atoms().isEmpty() && state.atoms().values().stream().allMatch(a -> Double.isFinite(a.getPosition().x())&&Double.isFinite(a.getPosition().y())&&Double.isFinite(a.getPosition().z()));
        checks.add(new Check("GEOMETRY","coordinates",coordinates?Status.QUALIFIED:Status.FAILED,"finite, nonempty full frame"));
        Status spatial=!coordinates?Status.FAILED:state.frameQualified()?Status.QUALIFIED:Status.CONDITIONAL;
        caps.put(Capability.DISTANCE_QUERIES,q(spatial,state.frameQualified()?"source asserts common frame; coordinates valid":"component frame provenance not qualified"));
        caps.put(Capability.NEIGHBORHOOD_TRAVERSAL,caps.get(Capability.DISTANCE_QUERIES));
        boolean elements=state.atoms().values().stream().allMatch(a->a.getElement()!=null);
        caps.put(Capability.CLASH_ANALYSIS,q(!coordinates?Status.FAILED:elements?spatial:Status.CONDITIONAL,
                elements?"radii from existing Element table; explicit scale required":"unknown elements cannot silently pass clash assessment"));
        checks.add(new Check("GEOMETRY","frame-provenance",state.frameQualified()?Status.QUALIFIED:Status.CONDITIONAL,"explicit source assertion; numerical proximity alone does not prove common frame"));
        checks.add(new Check("CHEMISTRY","source-connectivity",state.graph().structure().getConnectivityMetadata().provenance()==totah.lab.gaia.structure.ConnectivityProvenance.EXPLICIT?Status.QUALIFIED:Status.CONDITIONAL,
                state.graph().structure().getConnectivityMetadata().toString()));
        checks.add(new Check("CHEMISTRY","bond-orders",state.graph().structure().bonds().isEmpty() || state.graph().structure().bonds().stream().anyMatch(b->b.order()==BondOrder.UNKNOWN)?Status.CONDITIONAL:Status.QUALIFIED,"only supplied orders; none inferred"));
        checks.add(new Check("GEOMETRY","covalent-geometry",Status.NOT_EVALUATED,"no qualified covalent-length/angle ruleset registered; source bonds preserved"));
        boolean mappingValid=true, complete=!state.components().isEmpty(), chemistryValid=true, backendEvaluated=sanitizer!=null&&identity!=null&&stereo!=null;
        var identities=new HashSet<totah.lab.mnemosyne.ScientificReference>();
        for(var component:state.components()) {
            if(!identities.add(component.identity()))mappingValid=false;
            try { component.chemistry().validateTopology(true); checks.add(new Check("CHEMISTRY","topology/"+component.identity(),Status.QUALIFIED,"existing MolecularGraph validation")); }
            catch(RuntimeException e){chemistryValid=false;checks.add(new Check("CHEMISTRY","topology/"+component.identity(),Status.FAILED,e.toString()));}
            if(component.correspondenceAlternatives().size()!=1)complete=false;
            for(var mapping:component.correspondenceAlternatives()) {
                if(mapping.size()!=component.chemistry().atoms().size())complete=false;
                if(new HashSet<>(mapping.values()).size()!=mapping.size())mappingValid=false;
                for(var e:mapping.entrySet()) {
                    var chemical=component.chemistry().atom(e.getKey());var coordinate=state.atoms().get(e.getValue());
                    if(chemical.isEmpty()||coordinate==null){complete=false;continue;}
                    if(coordinate.getElement()==null||!coordinate.getElement().name().equalsIgnoreCase(chemical.orElseThrow().element()))mappingValid=false;
                    if(component.correspondenceAlternatives().size()==1 && chemical.orElseThrow().coordinates()!=null) {
                        var p=chemical.orElseThrow().coordinates();var q=coordinate.getPosition();
                        if(Math.abs(p.x()-q.x())>1e-6||Math.abs(p.y()-q.y())>1e-6||Math.abs(p.z()-q.z())>1e-6)mappingValid=false;
                    }
                }
            }
            if(backendEvaluated)try {
                var sanitized=sanitizer.sanitize(component.chemistry(),new MolecularSanitizer.SanitizationPolicy(Set.of(),true));
                var identified=identity.identify(component.chemistry());var stereoResult=stereo.validate(component.chemistry());
                boolean valid=sanitized.valid()&&sanitized.graph().equals(component.chemistry())&&stereoResult.valid();chemistryValid &=valid;
                checks.add(new Check("CHEMISTRY","backend/"+component.identity(),valid?Status.QUALIFIED:Status.FAILED,
                        new String(SystemStateView.bytes(Map.of("identity",identified.canonicalKey(),"sanitize",sanitized.evidence(),"stereo",stereoResult.evidence())),java.nio.charset.StandardCharsets.UTF_8)));
            }catch(Exception e){chemistryValid=false;checks.add(new Check("CHEMISTRY","backend/"+component.identity(),Status.FAILED,e.toString()));}
            else checks.add(new Check("CHEMISTRY","backend/"+component.identity(),Status.NOT_EVALUATED,"chemical services not supplied"));
        }
        checks.add(new Check("CORRESPONDENCE","atom/component mappings",!mappingValid?Status.FAILED:complete?Status.QUALIFIED:Status.CONDITIONAL,
                "unresolved references and symmetry alternatives preserved; no mapping selected implicitly"));
        caps.put(Capability.GRAPH_TRANSFORMATIONS,q(!mappingValid||!chemistryValid?Status.FAILED:!backendEvaluated?Status.NOT_EVALUATED:complete?Status.QUALIFIED:Status.CONDITIONAL,
                "scoped to the explicitly listed component MolecularGraphs only; requires validated chemistry and unambiguous complete correspondence; does not certify absent protein/SAM chemical graphs or edit authorization"));
        boolean typed=state.atoms().values().stream().allMatch(a->a.getAutoDockType()!=null&&!a.getAutoDockType().isBlank());
        boolean charges=state.charges().charges().keySet().containsAll(state.atoms().keySet());
        boolean chargeRefs=state.atoms().keySet().containsAll(state.charges().charges().keySet());
        checks.add(new Check("CHEMISTRY","formal-charge-coverage",!chargeRefs?Status.FAILED:charges?Status.QUALIFIED:Status.CONDITIONAL,"partial charge is not formal charge; unassigned atoms stay unknown"));
        checks.add(new Check("CHEMISTRY","hydrogens-protonation",state.protonationQualified()?Status.QUALIFIED:Status.CONDITIONAL,"qualification is source-declared; no hydrogens generated"));
        caps.put(Capability.INTERACTION_TYPING,q(!chemistryValid||!mappingValid||!chargeRefs||!coordinates?Status.FAILED:typed&&charges&&state.frameQualified()?Status.QUALIFIED:Status.CONDITIONAL,
                "perception coverage/typing/charge limitations must accompany every result"));
        caps.put(Capability.HBOND_ANALYSIS,q(!chemistryValid||!coordinates||!mappingValid||!chargeRefs?Status.FAILED:typed&&state.protonationQualified()&&state.frameQualified()?Status.QUALIFIED:Status.CONDITIONAL,
                "requires explicit prepared donor H/acceptor typing and qualified protonation; empty detector output alone is not FALSE"));
        return new Result(caps,checks);
    }
    private static Qualification q(Status status,String reason){return new Qualification(status,List.of(reason));}
}
