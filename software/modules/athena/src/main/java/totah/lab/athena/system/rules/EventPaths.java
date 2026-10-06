package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Bounded enumeration over selected evidence tuples, not a new protein or molecular graph. */
final class EventPaths {
    private EventPaths() { }
    private record Edge(String key,String from,String to) { }
    static Map<String,Object> analyze(EventInputs.Selection selected,RuleRequest request)throws Exception {
        require(selected.states().size()==1,"paths require exactly one selected state");
        var state=selected.states().values().iterator().next();var p=selected.plan().get("path");
        fields(p,"start","end","fromRole","toRole","maximumLength","coverage");
        var start=JSON.treeToValue(p.get("start"),totah.lab.mnemosyne.EvidenceSubject.class);
        var end=JSON.treeToValue(p.get("end"),totah.lab.mnemosyne.EvidenceSubject.class);
        require(start.state().equals(state.binding().state())&&end.state().equals(state.binding().state()),"path endpoints state mismatch");
        require(!start.equals(end),"distinct path endpoints required");
        var length=p.get("maximumLength");require(length.isIntegralNumber()&&length.canConvertToInt()&&length.intValue()>0,"positive path length required");
        String fromRole=text(p,"fromRole"),toRole=text(p,"toRole");require(!fromRole.equals(toRole),"distinct endpoint roles required");
        var edges=new ArrayList<Edge>();boolean resolved=true;
        for(var entry:state.events().entrySet()) {
            var e=entry.getValue();String from=null,to=null;
            for(var role:array(e.key(),"roles")){if(text(role,"role").equals(fromRole))from=canonical(role.get("entity"));if(text(role,"role").equals(toRole))to=canonical(role.get("entity"));}
            require(from!=null&&to!=null,"selected event lacks path roles");
            resolved&=e.cell().eligible()&&e.qualified();
            if(!e.cell().present()||!e.qualified())continue;
            edges.add(new Edge(entry.getKey(),from,to));
            if(text(e.key(),"direction").equals("UNDIRECTED"))edges.add(new Edge(entry.getKey(),to,from));
        }
        edges.sort(Comparator.comparing(Edge::key).thenComparing(Edge::from).thenComparing(Edge::to));
        String scope=hash(Map.of("events",state.events().keySet(),"fromRole",fromRole,"toRole",toRole));
        boolean covered=EventInputs.coverage(array(p,"coverage"),selected.artifacts(),state.binding(),scope);
        var walk=new Walk(edges,Math.min(length.intValue(),request.maximumHops()),request.maximumCandidates(),request.maximumNodes());
        walk.visit(canonical(p.get("start")),canonical(p.get("end")));
        boolean complete=!walk.truncated && request.maximumHops()>=length.intValue() && resolved&&covered;
        var result=new TreeMap<String,Object>();result.put("paths",walk.paths);result.put("complete",complete);result.put("truncated",walk.truncated||request.maximumHops()<length.intValue());
        result.put("assessment",!walk.paths.isEmpty()?SUPPORTED_PRESENT.name():complete?ABSENT_FALSE.name():UNKNOWN_INCONCLUSIVE.name());
        result.put("eventSources",state.events());result.put("requestedMaximumLength",length.intValue());result.put("coverage",p.get("coverage"));return result;
    }
    private static final class Walk {
        final List<Edge> edges;final int depth,budget,nodeBudget;int examined;boolean truncated;
        final List<List<String>> paths=new ArrayList<>();
        Walk(List<Edge> edges,int depth,int budget,int nodeBudget){this.edges=edges;this.depth=depth;this.budget=budget;this.nodeBudget=nodeBudget;}
        private record Frame(String node,List<String> path,Set<String> visited) { }
        void visit(String node,String target) {
            var pending=new ArrayDeque<Frame>();pending.push(new Frame(node,List.of(),Set.of()));
            var allVisited=new HashSet<String>();
            while(!pending.isEmpty()) {
                var f=pending.pop();
                if(!allVisited.contains(f.node)&&allVisited.size()>=nodeBudget){truncated=true;break;}
                allVisited.add(f.node);
                if(f.node.equals(target)){paths.add(f.path);continue;}
                if(f.path.size()>=depth)continue;
                var visited=new HashSet<>(f.visited);visited.add(f.node);
                // Reverse push retains canonical traversal order without recursive stack growth.
                for(int i=edges.size()-1;i>=0;i--) {
                    var e=edges.get(i);if(!e.from.equals(f.node)||visited.contains(e.to))continue;
                    if(examined++>=budget){truncated=true;break;}
                    var next=new ArrayList<>(f.path);next.add(e.key);
                    pending.push(new Frame(e.to,List.copyOf(next),Set.copyOf(visited)));
                }
                if(truncated)break;
            }
            paths.sort(Comparator.comparing(EventPayload::canonical));
        }
    }
}
