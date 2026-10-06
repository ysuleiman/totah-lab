package totah.lab.athena.interaction;

import totah.lab.athena.interaction.perception.*;
import totah.lab.gaia.structure.*;
import java.util.*;

/** Pre-threshold geometry from the native detectors, not a second chemistry engine. */
public final class InteractionMeasurements {
    private InteractionMeasurements() { }
    @FunctionalInterface interface Observer {
        Observer NONE = (family, first, second, values) -> { };
        void accept(String family, List<Atom> first, List<Atom> second, Map<String,Double> values);
    }
    public record Candidate(String family, List<AtomReference> first, List<AtomReference> second,
                            Map<String,Double> values) {
        public Candidate {
            first=List.copyOf(first);second=List.copyOf(second);values=Collections.unmodifiableMap(new TreeMap<>(values));
            if(values.values().stream().anyMatch(v->v==null||!Double.isFinite(v)))throw new IllegalArgumentException("nonfinite measurement");
        }
    }
    public record Result(List<Candidate> candidates, boolean complete, List<String> limitations, Map<String,String> perception) {
        public Result { candidates=List.copyOf(candidates);limitations=List.copyOf(limitations);perception=Collections.unmodifiableMap(new TreeMap<>(perception)); }
    }
    private static final class BudgetReached extends RuntimeException { }

    /** Explicit partners, original atom identities, charge assignments and collection budget. */
    public static Result collect(String family, Structure first, Structure second, FormalChargeAssignments charges,
                                 InteractionThresholds preparation, int budget) {
        if(budget<1)throw new IllegalArgumentException("positive candidate budget required");
        var refs=new IdentityHashMap<Atom,AtomReference>();
        for(var s:List.of(first,second))for(var c:s.getChains())for(var r:c.residues())for(var a:r.getAtoms())
            if(refs.put(a,new AtomReference(c.id(),r.getNumber(),r.getInsertionCode()==null?' ':r.getInsertionCode(),a.getName()))!=null)
                throw new IllegalArgumentException("partners overlap");
        var rows=new ArrayList<Candidate>();var limits=new ArrayList<String>();var perception=new TreeMap<String,String>();
        perception.put("implementation","Athena native perception/1; not OpenBabel/PLIP");
        if(refs.keySet().stream().anyMatch(a->a.getElement()==null))limits.add("unknown element in requested partners");
        Observer observer=(kind,a,b,v)->{
            if(rows.size()==budget)throw new BudgetReached();
            rows.add(new Candidate(kind,a.stream().map(refs::get).toList(),b.stream().map(refs::get).toList(),v));
            if(v.getOrDefault("amineGuardRequired",0.0)==1 && !v.containsKey("angle"))limits.add("amine guard unavailable");
        };
        boolean topology=List.of(first,second).stream().allMatch(s->s.getConnectivityMetadata().provenance()==ConnectivityProvenance.EXPLICIT);
        try {
            switch(family) {
                case "HBOND" -> {
                    perception.put("donor-acceptor","AD4 HD and NA/OA/SA; nearest-heavy donor pairing within "+preparation.donorBondCutoff()+" angstrom");
                    if(refs.keySet().stream().anyMatch(a->a.getAutoDockType()==null||a.getAutoDockType().isBlank()))limits.add("incomplete donor/acceptor typing");
                    for(var a:refs.keySet())if("HD".equals(a.getAutoDockType())) {
                        // Native receptor donors are paired within their residue; ligand donors
                        // use the full explicitly supplied ligand partner. Match that exact scope.
                        var side=first.getChains().stream().flatMap(c->c.residues().stream())
                                .filter(r->r.getAtoms().contains(a)).map(Residue::getAtoms).findFirst()
                                .orElseGet(()->second.getChains().stream().flatMap(c->c.residues().stream())
                                        .flatMap(r->r.getAtoms().stream()).toList());
                        if(side.stream().noneMatch(b->b.isHeavyAtom()&&a.getPosition().distance(b.getPosition())<=preparation.donorBondCutoff()))limits.add("unpaired donor hydrogen");
                    }
                    new HydrogenBondDetector().detect(first,second,preparation,observer);
                }
                case "HYDROPHOBIC" -> {
                    var p=new HydrophobicAtomPerception();var a=p.perceive(first);var b=p.perceive(second);
                    perception.put("first-hydrophobic",a.provenance()+": "+a.note());perception.put("second-hydrophobic",b.provenance()+": "+b.note());
                    if(a.degraded()||b.degraded()||!topology)limits.add("hydrophobic perception lacks explicit complete bonding");
                    new HydrophobicContactDetector().detect(first,a,b,preparation,observer);
                }
                case "PI_STACKING", "PI_CATION", "SALT_BRIDGE" -> {
                    var rp=new AromaticRingPerception();var a=rp.perceive(first);var b=rp.perceive(second);
                    for(var ring:java.util.stream.Stream.concat(a.stream(),b.stream()).toList())perception.put("ring/"+ring.ringId(),ring.source()+": "+ring.note());
                    if(!family.equals("SALT_BRIDGE") && (!topology||java.util.stream.Stream.concat(a.stream(),b.stream()).anyMatch(r->r.degraded()||InteractionGeometry.ringPlane(r).isEmpty())))
                        limits.add("ring perception/planes incomplete or not explicitly bonded");
                    if(family.equals("PI_STACKING"))new PiStackingDetector().detect(a,b,preparation,observer);
                    else {
                        var cp=new ChargedGroupPerception();var ca=cp.perceive(first,charges);var cb=cp.perceive(second,charges);
                        if(!topology||!charges.charges().keySet().containsAll(refs.values()))limits.add("authoritative formal-charge coverage/topology incomplete");
                        for(var group:java.util.stream.Stream.concat(ca.stream(),cb.stream()).toList()) {
                            perception.put("charge/"+group.owner()+"/"+group.type()+"/"+group.atoms().stream().map(refs::get).toList(),group.provenance()+": "+group.note());
                            int sum=group.atoms().stream().map(refs::get).mapToInt(charges::charge).sum();
                            if(group.degraded()||sum==0||(sum>0)!=(group.sign()==ChargeSign.POSITIVE))limits.add("perceived group charge not supported by explicit formal assignments");
                        }
                        if(family.equals("SALT_BRIDGE"))new SaltBridgeDetector().detect(ca,cb,preparation,observer);
                        else new PiCationDetector().detect(ca,a,cb,b,preparation,observer);
                    }
                }
                default -> throw new IllegalArgumentException("no native measurement implementation: "+family);
            }
        } catch(BudgetReached exhausted) { limits.add("candidate budget exhausted; incomplete coverage"); }
        var reasons=limits.stream().distinct().sorted().toList();
        return new Result(rows,reasons.isEmpty(),reasons,perception);
    }

    /** The native predicates shared by legacy detection and manifest-driven evaluation. */
    public static InteractionType classify(String family, Map<String,Double> m, InteractionThresholds t) {
        double d=m.get("distance");
        if(d<=t.minDist())return null;
        return switch(family) {
            case "HYDROPHOBIC" -> d<=t.hydrophobicDistMax()?InteractionType.HYDROPHOBIC_CONTACT:null;
            case "SALT_BRIDGE" -> d<=t.saltBridgeDistMax()?InteractionType.SALT_BRIDGE:null;
            case "HBOND" -> d<=t.donorAcceptorCutoff()&&m.get("hydrogenDistance")<=t.hydrogenAcceptorCutoff()&&m.get("angle")>=t.minDonorAngleDegrees()?InteractionType.HYDROGEN_BOND:null;
            case "PI_STACKING" -> d>t.piStackDistMax()||m.get("offset")>t.piStackOffsetMax()?null:
                    m.get("angle")<=t.piStackParallelAngleDev()?InteractionType.PI_STACK_PARALLEL:
                    Math.abs(90-m.get("angle"))<=t.piStackTShapeAngleDev()?InteractionType.PI_STACK_T_SHAPED:null;
            case "PI_CATION" -> d<=t.piCationDistMax()&&m.get("offset")<=t.piCationOffsetMax()&&(!m.containsKey("angle")||m.get("angle")<=t.piCationTertamineAngleMax())?InteractionType.PI_CATION:null;
            default -> throw new IllegalArgumentException("unregistered native predicate: "+family);
        };
    }
}
