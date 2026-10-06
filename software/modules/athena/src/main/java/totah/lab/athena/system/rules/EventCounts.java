package totah.lab.athena.system.rules;

import java.util.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Exact descriptive counts over explicitly selected states; no probabilities or implicit zeros. */
final class EventCounts {
    private EventCounts() { }
    record Cell(String status, List<String> originalStatuses, List<Object> sources) {
        Cell { originalStatuses=List.copyOf(originalStatuses); sources=List.copyOf(sources); }
        boolean eligible() { return status.equals(SUPPORTED_PRESENT.name()) || status.equals(ABSENT_FALSE.name()); }
        boolean present() { return status.equals(SUPPORTED_PRESENT.name()); }
    }
    static Cell missing() { return new Cell(UNKNOWN_INCONCLUSIVE.name(),List.of("MISSING_EVENT"),List.of()); }
    static Cell project(List<Cell> members, boolean complete) {
        var statuses = members.stream().flatMap(x->x.originalStatuses.stream()).sorted().toList();
        var sources = members.stream().flatMap(x->x.sources.stream()).toList();
        if (members.stream().anyMatch(c->c.status.equals("CONFLICTING"))) return new Cell("CONFLICTING",statuses,sources);
        if (members.stream().anyMatch(Cell::present)) return new Cell(SUPPORTED_PRESENT.name(),statuses,sources);
        if (complete && members.stream().allMatch(c->c.status.equals(ABSENT_FALSE.name()))) return new Cell(ABSENT_FALSE.name(),statuses,sources);
        return new Cell(UNKNOWN_INCONCLUSIVE.name(),statuses,sources);
    }
    static Map<String,Object> summary(SortedMap<String,Cell> cells) {
        long positive=cells.values().stream().filter(Cell::present).count();
        long eligible=cells.values().stream().filter(Cell::eligible).count();
        long conflicts=cells.values().stream().filter(c->c.status.equals("CONFLICTING")).count();
        var out=new TreeMap<String,Object>(); out.put("selected",cells.size());out.put("present",positive);
        out.put("absent",eligible-positive);out.put("eligible",eligible);out.put("conflicting",conflicts);
        out.put("unevaluable",cells.size()-eligible);out.put("fraction",eligible==0?null:Map.of("numerator",positive,"denominator",eligible));
        out.put("states",cells); return Collections.unmodifiableMap(out);
    }
    static Map<String,Object> cooccurrence(SortedMap<String,SortedMap<String,Cell>> projections, SortedSet<String> states) {
        var counts=new TreeMap<String,Long>();var excluded=new TreeMap<String,Object>();long eligible=0, joint=0;
        for(String state:states) {
            var mask=new TreeMap<String,String>(); projections.forEach((key,cells)->mask.put(key,cells.getOrDefault(state,missing()).status));
            if(mask.values().stream().anyMatch(s->!Set.of(SUPPORTED_PRESENT.name(),ABSENT_FALSE.name()).contains(s))) {excluded.put(state,mask);continue;}
            String pattern=String.join("",mask.values().stream().map(s->s.equals(SUPPORTED_PRESENT.name())?"1":"0").toList());
            counts.merge(pattern,1L,Math::addExact);eligible++;if(!pattern.contains("0"))joint++;
        }
        var out=new TreeMap<String,Object>();out.put("projectionOrder",List.copyOf(projections.keySet()));out.put("contingency",counts);
        out.put("eligible",eligible);out.put("excludedStates",excluded);out.put("joint",joint);
        out.put("fraction",eligible==0?null:Map.of("numerator",joint,"denominator",eligible));return out;
    }
}
