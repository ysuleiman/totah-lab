package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import java.io.IOException;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import static totah.lab.athena.system.rules.EventPayload.*;

/** V18-local lossless text extraction. No molecular graph, defaults or conformer construction. */
final class SourceSiteMetadata {
    static final String DEFINITION="ATHENA.V18.MMCIF_SOURCE_SITE_METADATA/1";
    static final List<String> SITE=List.of("_atom_site.occupancy","_atom_site.B_iso_or_equiv","_atom_site.label_alt_id","_atom_site.pdbx_PDB_model_num","_atom_site.id");
    static final List<String> ALT=List.of("atom_sites_alt","atom_sites_alt_ens","atom_sites_alt_gen");
    private static final String NUMBER="-?(([0-9]+)[.]?|([0-9]*[.][0-9]+))([(][0-9]+[)])?([eE][+-]?[0-9]+)?";
    private SourceSiteMetadata() { }
    static final class Outside extends IOException { Outside(String reason){super(reason);} }
    private record Token(int start,int end,String form,String value) {
        boolean unquoted(){return form.equals("UNQUOTED");}
        String lower(){return value.toLowerCase(Locale.ROOT);}
        boolean control(){return unquoted()&&(value.startsWith("_")||lower().startsWith("data_")||lower().startsWith("save_")||Set.of("loop_","stop_","global_").contains(lower()));}
    }
    private record Cell(Token item,Token token) { }
    private record Block(String name,Map<String,List<Map<String,Cell>>> categories) { }
    private static List<Token> tokens(byte[] bytes)throws IOException {
        // Validate without replacing malformed sequences. Byte indices, never UTF-16 offsets, are authoritative.
        try{StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes));}catch(CharacterCodingException e){throw new Outside("Not supported textual UTF-8 mmCIF");}
        if(bytes.length>=2&&(bytes[0]&255)==31&&(bytes[1]&255)==139)throw new Outside("Compressed source is not textual mmCIF");
        var out=new ArrayList<Token>();int p=0;
        while(p<bytes.length){int c=bytes[p]&255;if(ws(c)){p++;continue;}if(c=='#'){while(p<bytes.length&&bytes[p]!='\n'&&bytes[p]!='\r')p++;continue;}
            int start=p;
            if(c==';'&&(p==0||bytes[p-1]=='\n'||bytes[p-1]=='\r')){
                int content=++p;while(p<bytes.length&&!(bytes[p]==';'&&(p==0||bytes[p-1]=='\n'||bytes[p-1]=='\r')))p++;
                if(p==bytes.length)throw new IOException("Unterminated CIF semicolon text");int end=p++;if(p<bytes.length&&!ws(bytes[p]&255))throw new IOException("Invalid semicolon delimiter suffix");
                // The opening delimiter's same-line text belongs to the value; exclude the delimiter-separating final newline.
                int valueEnd=end;if(valueEnd>content&&bytes[valueEnd-1]=='\n')valueEnd--;if(valueEnd>content&&bytes[valueEnd-1]=='\r')valueEnd--;
                out.add(new Token(start,p,"SEMICOLON_TEXT",decode(bytes,content,valueEnd)));continue;
            }
            if(c=='\''||c=='"'){
                int content=++p;while(p<bytes.length&&!(bytes[p]==c&&(p+1==bytes.length||ws(bytes[p+1]&255)))){if(bytes[p]=='\n'||bytes[p]=='\r')throw new IOException("Newline in quoted CIF value");p++;}
                if(p==bytes.length)throw new IOException("Unterminated quoted CIF value");out.add(new Token(start,p+1,c=='\''?"SINGLE_QUOTED":"DOUBLE_QUOTED",decode(bytes,content,p)));p++;continue;
            }
            if(c=='['||c==']'||c=='{'||c=='}'||c=='$')throw new Outside("CIF2/STAR collection or reference outside V18 text profile");
            while(p<bytes.length&&!ws(bytes[p]&255)){if((bytes[p]&255)<32)throw new IOException("Control byte in CIF token");p++;}
            out.add(new Token(start,p,"UNQUOTED",decode(bytes,start,p)));
        }
        return out;
    }
    private static boolean ws(int c){return c==' '||c=='\t'||c=='\n'||c=='\r';}
    private static String decode(byte[] b,int a,int z){return new String(b,a,z-a,StandardCharsets.UTF_8);}
    private static String category(Token t)throws IOException {int dot=t.value.indexOf('.');if(dot<2||dot==t.value.length()-1)throw new IOException("Invalid mmCIF item name");return t.lower().substring(1,dot);}
    private static List<Block> blocks(byte[] bytes)throws IOException {
        var ts=tokens(bytes);var blocks=new ArrayList<Block>();Map<String,List<Map<String,Cell>>> categories=null;var modes=new HashMap<String,String>();int p=0;
        while(p<ts.size()){
            var t=ts.get(p++);String lower=t.lower();
            if(t.unquoted()&&(lower.startsWith("save_")||lower.equals("global_")||lower.equals("stop_")))throw new Outside("Dictionary or STAR control outside V18 data-file profile");
            if(t.unquoted()&&lower.startsWith("data_")){if(t.value.length()==5)throw new IOException("Unnamed data block");categories=new LinkedHashMap<>();blocks.add(new Block(t.value.substring(5),categories));modes=new HashMap<>();continue;}
            if(categories==null)throw new Outside("Text is not a mmCIF data file");
            if(t.unquoted()&&lower.equals("loop_")){
                var items=new ArrayList<Token>();while(p<ts.size()&&ts.get(p).unquoted()&&ts.get(p).value.startsWith("_"))items.add(ts.get(p++));if(items.isEmpty())throw new IOException("Loop without items");
                String cat=category(items.getFirst());var names=new HashSet<String>();for(var item:items)if(!category(item).equals(cat)||!names.add(item.lower()))throw new IOException("Mixed category or duplicate CIF item");
                if(categories.containsKey(cat))throw new IOException("Repeated category");var rows=new ArrayList<Map<String,Cell>>();categories.put(cat,rows);modes.put(cat,"LOOP");
                while(p<ts.size()&&!ts.get(p).control()){
                    var row=new LinkedHashMap<String,Cell>();for(var item:items){if(p>=ts.size()||ts.get(p).control())throw new IOException("Incomplete loop row");row.put(item.lower(),new Cell(item,ts.get(p++)));}rows.add(row);
                }
                if(rows.isEmpty())throw new IOException("Empty CIF loop");continue;
            }
            if(t.unquoted()&&t.value.startsWith("_")){
                String cat=category(t);if("LOOP".equals(modes.get(cat)))throw new IOException("Repeated loop category");
                if(p>=ts.size()||ts.get(p).control())throw new IOException("Missing CIF item value");
                if(!categories.containsKey(cat)){categories.put(cat,new ArrayList<>(List.of(new LinkedHashMap<>())));modes.put(cat,"SINGLE");}
                var row=categories.get(cat).getFirst();if(row.putIfAbsent(t.lower(),new Cell(t,ts.get(p++)))!=null)throw new IOException("Duplicate CIF item");continue;
            }
            throw new IOException("Unexpected CIF value/control");
        }
        if(blocks.isEmpty())throw new Outside("No mmCIF data block");return blocks;
    }
    static int integer(JsonNode n){require(n!=null&&n.isIntegralNumber()&&n.canConvertToInt()&&n.intValue()>=0,"exact nonnegative source ordinal required");return n.intValue();}
    private static ObjectNode field(Cell c){var n=JSON.createObjectNode();if(c==null)return n.put("state","OMITTED");var t=c.token;String state=t.unquoted()&&t.value.equals("?")?"MISSING_QUESTION":t.unquoted()&&t.value.equals(".")?"INAPPLICABLE_DOT":"EXPLICIT";n.put("state",state);n.putArray("itemSpan").add(c.item.start).add(c.item.end);n.putArray("valueSpan").add(t.start).add(t.end);n.put("lexicalForm",t.form);n.put("value",t.value);return n;}
    static ObjectNode extract(byte[] bytes,JsonNode plan,Object planPin)throws IOException {
        fields(plan,"schema","definition","state","source","blockOrdinal","blockName","correspondence","sourceProtocol");require(text(plan,"schema").equals("athena-source-site-metadata-plan/1")&&text(plan,"definition").equals(DEFINITION),"V18 exact plan schema/definition required");binding(plan.get("state"));
        var blocks=blocks(bytes);int ordinal=integer(plan.get("blockOrdinal"));require(ordinal<blocks.size(),"selected block absent");var b=blocks.get(ordinal);require(b.name.equals(text(plan,"blockName")),"block ordinal/name mismatch");
        var out=JSON.createObjectNode();out.put("schema","athena-source-site-metadata/1");out.put("definition",DEFINITION);out.set("state",plan.get("state"));out.set("plan",JSON.valueToTree(planPin));out.set("source",plan.get("source"));out.put("blockOrdinal",ordinal);out.put("blockName",b.name);out.set("correspondence",plan.get("correspondence"));
        var sites=out.putArray("atomSiteRows");var sourceRows=b.categories.getOrDefault("atom_site",List.of());
        for(int i=0;i<sourceRows.size();i++){var row=sites.addObject();row.put("rowOrdinal",i);var f=row.putObject("fields");for(var name:SITE)f.set(name,field(sourceRows.get(i).get(name.toLowerCase(Locale.ROOT))));}
        var alt=out.putObject("alternateCategories");
        for(var name:ALT){var c=alt.putObject(name);c.put("present",b.categories.containsKey(name));var rows=c.putArray("rows");var original=b.categories.getOrDefault(name,List.of());for(int i=0;i<original.size();i++){
            var row=rows.addObject();row.put("rowOrdinal",i);var f=row.putObject("fields");for(var entry:original.get(i).entrySet())f.set(entry.getKey(),field(entry.getValue()));
            for(var id:name.equals("atom_sites_alt_gen")?List.of("ens_id","alt_id"):List.of("id"))if(!f.has("_"+name+"."+id))f.set("_"+name+"."+id,field(null));
        }}
        out.set("diagnostics",diagnostics(out));return out;
    }
    private static boolean explicit(JsonNode f){return f.path("state").asText().equals("EXPLICIT");}
    private static String value(JsonNode f){return explicit(f)?f.path("value").asText():null;}
    private static void diagnostic(List<JsonNode> d,String code,String cat,Integer row,String item){var n=JSON.createObjectNode();n.put("code",code);n.put("category",cat);if(row==null)n.putNull("rowOrdinal");else n.put("rowOrdinal",row);if(item==null)n.putNull("item");else n.put("item",item);d.add(n);}
    private static ArrayNode diagnostics(JsonNode report){
        var d=new ArrayList<JsonNode>();var ids=new HashSet<String>();
        for(var row:report.path("atomSiteRows")){int i=row.path("rowOrdinal").intValue();var f=row.path("fields");String id=value(f.path("_atom_site.id"));if(id!=null&&!ids.add(id))diagnostic(d,"DUPLICATE_SOURCE_ID","atom_site",i,"_atom_site.id");
            for(var pair:List.of(List.of("_atom_site.occupancy","NONNUMERIC_OCCUPANCY"),List.of("_atom_site.B_iso_or_equiv","NONNUMERIC_B_FACTOR"))){var n=f.path(pair.getFirst());if(explicit(n)&&!n.path("value").asText().matches(NUMBER))diagnostic(d,pair.getLast(),"atom_site",i,pair.getFirst());}
            if(!explicit(f.path("_atom_site.pdbx_PDB_model_num")))diagnostic(d,"UNRESOLVED_MODEL","atom_site",i,"_atom_site.pdbx_PDB_model_num");
            if(!explicit(f.path("_atom_site.label_alt_id")))diagnostic(d,"UNRESOLVED_ALT_ID","atom_site",i,"_atom_site.label_alt_id");
        }
        var alt=report.path("alternateCategories");var sets=new HashMap<String,Set<String>>();
        for(var cat:List.of("atom_sites_alt","atom_sites_alt_ens")){var unique=new HashSet<String>();sets.put(cat,unique);for(var row:alt.path(cat).path("rows")){String id=value(row.path("fields").path("_"+cat+".id"));if(id==null)diagnostic(d,"MISSING_RELATION",cat,row.path("rowOrdinal").intValue(),"_"+cat+".id");else if(!unique.add(id))diagnostic(d,"DUPLICATE_RELATION_ID",cat,row.path("rowOrdinal").intValue(),"_"+cat+".id");}}
        var rel=alt.path("atom_sites_alt_gen").path("rows");if(rel.isEmpty())diagnostic(d,"MISSING_RELATION","atom_sites_alt_gen",null,null);var pairs=new HashSet<String>();
        for(var row:rel){var f=row.path("fields");int i=row.path("rowOrdinal").intValue();String a=value(f.path("_atom_sites_alt_gen.alt_id")),e=value(f.path("_atom_sites_alt_gen.ens_id"));if(a==null||e==null)diagnostic(d,"MISSING_RELATION","atom_sites_alt_gen",i,null);else{
            if(!sets.get("atom_sites_alt").contains(a)||!sets.get("atom_sites_alt_ens").contains(e))diagnostic(d,"DANGLING_RELATION","atom_sites_alt_gen",i,null);
            if(!pairs.add(canonical(List.of(a,e))))diagnostic(d,"DUPLICATE_RELATION","atom_sites_alt_gen",i,null);
        }}
        var out=JSON.createArrayNode();d.stream().sorted(Comparator.comparing(EventPayload::canonical)).forEach(out::add);return out;
    }
    static Optional<ArrayNode> memberships(JsonNode report){
        for(var d:report.path("diagnostics"))if(Set.of("MISSING_RELATION","DANGLING_RELATION","DUPLICATE_RELATION","DUPLICATE_RELATION_ID","UNRESOLVED_MODEL").contains(d.path("code").asText()))return Optional.empty();
        for(var row:report.path("atomSiteRows"))if(Set.of("MISSING_QUESTION","OMITTED").contains(row.path("fields").path("_atom_site.label_alt_id").path("state").asText()))return Optional.empty();
        var members=new TreeMap<String,ObjectNode>();
        for(var relation:report.path("alternateCategories").path("atom_sites_alt_gen").path("rows")){
            var f=relation.path("fields");String a=value(f.path("_atom_sites_alt_gen.alt_id")),e=value(f.path("_atom_sites_alt_gen.ens_id"));boolean found=false;
            for(var row:report.path("atomSiteRows")){var sf=row.path("fields");if(!Objects.equals(a,value(sf.path("_atom_site.label_alt_id"))))continue;String model=value(sf.path("_atom_site.pdbx_PDB_model_num"));if(model==null)return Optional.empty();found=true;String key=canonical(List.of(e,a,model));var n=members.computeIfAbsent(key,k->{var v=JSON.createObjectNode();v.put("ensembleId",e);v.put("altId",a);v.put("modelValue",model);v.putArray("rowOrdinals");return v;});((ArrayNode)n.get("rowOrdinals")).add(row.path("rowOrdinal").intValue());}
            if(!found)return Optional.empty();
        }
        var out=JSON.createArrayNode();members.values().forEach(out::add);return out.isEmpty()?Optional.empty():Optional.of(out);
    }
}
