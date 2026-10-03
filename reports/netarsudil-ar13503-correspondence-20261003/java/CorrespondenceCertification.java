import com.actelion.research.chem.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import java.security.MessageDigest;

/** Task-local certification of named inputs; no new chemical search algorithm or generated ligand. */
public final class CorrespondenceCertification {
    static final ObjectMapper J = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    static final String ROOT = "analysis/mettl7-selectivity-design-map-v1-20260920/";
    static final String IDENTITIES = "analysis/track2a-inhibitor-dataset-2026-09-12/sources/identity/";
    static final String PARENT = "analysis/mettl7-netarsudil-autodock4-matched-rigid-2026-09-10/inputs/source/parent_netarsudil.sdf";
    static final String AR = "research/mettl7-netarsudil-sam-mechanism/phase2-matched-sar/prepared/ar_13503_deesterified.sdf";
    static void require(boolean ok, String reason) { if (!ok) throw new IllegalArgumentException(reason); }
    static String sha(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    static JsonNode json(String path) throws IOException { return J.readTree(Files.readAllBytes(Path.of(path))); }
    static StereoMolecule smiles(String text) throws Exception {
        var m = new StereoMolecule(); new SmilesParser().parse(m, text); m.ensureHelperArrays(Molecule.cHelperCIP); return m;
    }
    static String identity(StereoMolecule m) { return new Canonizer(m).getIDCode(); }
    static void sameIdentity(StereoMolecule a, StereoMolecule b) { require(identity(a).equals(identity(b)), "IDENTITY_NOT_EQUIVALENT"); }
    static String atomId(StereoMolecule m, int i) { return m.getAtomLabel(i) + m.getAtomMapNo(i); }
    static StereoMolecule sdf(String path, boolean declaredAbsolute) throws Exception {
        String text = Files.readString(Path.of(path)); String[] lines = text.split("\\R");
        require(lines.length > 4 && lines[3].contains("V2000"), "UNSUPPORTED_OR_MISSING_TOPOLOGY");
        var parser = new MolfileParser(); parser.setAssumeChiralTrue(declaredAbsolute);
        var m = new StereoMolecule(); require(parser.parse(m, text), "SDF_PARSE_FAILED");
        require(m.getAllAtoms() == Integer.parseInt(lines[3].substring(0,3).trim()), "SDF_ATOMS_LOST");
        for (int i = 0; i < m.getAllAtoms(); i++) {
            // OCL MolfileParser uses float input and reverses Y/Z (proper rotation); verify row identity in that convention.
            String line = lines[i+4]; require(m.getAtomLabel(i).equals(line.substring(31,34).trim()), "SOURCE_ORDER_ELEMENT_MISMATCH");
            require(Math.abs(m.getAtomX(i)-(double)Float.parseFloat(line.substring(0,10)))<1e-12
                    && Math.abs(m.getAtomY(i)+(double)Float.parseFloat(line.substring(10,20)))<1e-12
                    && Math.abs(m.getAtomZ(i)+(double)Float.parseFloat(line.substring(20,30)))<1e-12, "SOURCE_ORDER_COORDINATE_MISMATCH");
            m.setAtomMapNo(i,i+1,false);
        }
        m.ensureHelperArrays(Molecule.cHelperCIP); return m;
    }
    static StereoMolecule heavy(StereoMolecule source) {
        var m = new StereoMolecule(source); m.removeExplicitHydrogens(false); m.ensureHelperArrays(Molecule.cHelperCIP); return m;
    }
    static List<Map<String,String>> mappings(StereoMolecule parent, StereoMolecule ar) {
        parent.ensureHelperArrays(Molecule.cHelperCIP); ar.ensureHelperArrays(Molecule.cHelperCIP);
        require(parent.getAtoms()<=40 && ar.getAtoms()<=30, "TASK_SIZE_BOUND");
        var fragment = new StereoMolecule(ar); fragment.setFragment(true);
        var search = new SSSearcher(); search.setMol(fragment,parent);
        search.findFragmentInMolecule(SSSearcher.cCountModeRigorous, SSSearcher.cMatchAtomCharge|SSSearcher.cMatchAtomMass);
        require(search.getMatchList().size()<=256, "MAPPING_LIMIT");
        var unique = new TreeMap<String,Map<String,String>>();
        for (int[] match : search.getMatchList()) {
            boolean valid = match.length==ar.getAtoms(); var keep = new boolean[parent.getAllAtoms()];
            var map = new TreeMap<String,String>();
            for (int i=0;i<match.length;i++) {
                int p=match[i]; if (p<0) { valid=false; break; } keep[p]=true;
                valid &= parent.getAtomicNo(p)==ar.getAtomicNo(i) && parent.getAtomCharge(p)==ar.getAtomCharge(i)
                        && parent.getAtomMass(p)==ar.getAtomMass(i) && parent.getAtomCIPParity(p)==ar.getAtomCIPParity(i)
                        && parent.getAtomESRType(p)==ar.getAtomESRType(i);
                map.put(atomId(parent,p),atomId(ar,i));
            }
            if (!valid || map.size()!=ar.getAtoms()) continue;
            var retained = new StereoMolecule(); parent.copyMoleculeByAtoms(retained,keep,false,null); retained.setFragment(false);
            if (!identity(retained).equals(identity(ar))) continue;
            for(int i=0;i<ar.getAtoms();i++)for(int j=i+1;j<ar.getAtoms();j++) {
                int a=ar.getBond(i,j), p=parent.getBond(match[i],match[j]);
                if ((a<0)!=(p<0)) valid=false;
                else if(a>=0) valid &= ar.isAromaticBond(a)==parent.isAromaticBond(p)
                        && (ar.isAromaticBond(a) || ar.getBondOrder(a)==parent.getBondOrder(p));
            }
            if(valid) unique.put(map.toString(),map);
        }
        return List.copyOf(unique.values());
    }
    static Map<String,Integer> byId(StereoMolecule m) {
        var result = new TreeMap<String,Integer>(); for(int i=0;i<m.getAllAtoms();i++) require(result.put(atomId(m,i),i)==null,"DUPLICATE_ID"); return result;
    }
    static Set<String> ids(StereoMolecule m) { return byId(m).keySet(); }
    static List<String> strings(JsonNode n) { var r=new ArrayList<String>(); n.forEach(x->r.add(x.asText())); return r; }
    static List<String> attachedHydrogens(StereoMolecule m, int a) {
        var ids=new ArrayList<String>();for(int h=0;h<m.getAllAtoms();h++)if(m.getAtomicNo(h)==1 && m.getBond(a,h)>=0)ids.add(atomId(m,h));return ids.stream().sorted().toList();
    }
    static String featureStatus(List<String> group, Map<String,String> map) {
        long n=group.stream().filter(map::containsKey).count();
        return n==0?"REMOVED":n==group.size()?"PRESERVED_ATOM_SET_GEOMETRY_UNKNOWN":"PARTIALLY_RETAINED_CHEMICALLY_CHANGED";
    }
    static Object certify() throws Exception {
        var sources=json(ROOT+"SOURCES.json"); var groups=sources.get("groups");
        require(sources.get("template").asText().equals(Path.of(PARENT).toAbsolutePath().toString()),"FROZEN_TEMPLATE_MISMATCH");
        var pRef=smiles(json(IDENTITIES+"netarsudil.json").at("/PropertyTable/Properties/0/SMILES").asText());
        var aRef=smiles(json(IDENTITIES+"AR-13503.json").at("/PropertyTable/Properties/0/SMILES").asText());
        var pRaw=sdf(PARENT,false); var aRaw=sdf(AR,false); var pAll=sdf(PARENT,true); var aAll=sdf(AR,true);
        sameIdentity(pRef,pAll); sameIdentity(aRef,aAll);
        var p=heavy(pAll);var a=heavy(aAll);var maps=mappings(p,a);require(!maps.isEmpty(),"NO_CERTIFIED_CORRESPONDENCE");
        require(p.getAtoms()==34 && a.getAtoms()==24,"UNEXPECTED_REFERENCE_COUNTS");
        var report=new TreeMap<String,Object>();
        report.put("status","CERTIFIED_NEUTRAL_REFERENCE_CORRESPONDENCE_WITH_EXPLICIT_STEREO_BRIDGE_POLICY");
        report.put("mapping_method","OpenChemLib 2026.7.2 SSSearcher rigorous full-AR embeddings; exact induced retained-graph canonical identity, charge, isotope, per-mapped-atom CIP and ESR, bond validation");
        report.put("stereo_policy",Map.of("raw_parent_sdf_matches_absolute_reference",identity(pRaw).equals(identity(pRef)),"raw_AR_sdf_matches_absolute_reference",identity(aRaw).equals(identity(aRef)),
                "policy","MolfileParser.setAssumeChiralTrue(true) ONLY for in-memory bridge to pinned absolute reference identities; no file changed. Unset V2000 chiral flags otherwise yield relative/racemic ESR groups. Coordinates encode S; authoritative identities specify S.",
                "unconditional_SDF_only_absolute_identity_certified",false));
        report.put("charge_policy","All reference/formal charges neutral; explicit-H preparation and heavy-only search are separately accounted. No assay-pH protonation, salt, or lot identity inferred.");
        for(var m:List.of(pAll,aAll))for(int i=0;i<m.getAllAtoms();i++)require(m.getAtomCharge(i)==0,"NON_NEUTRAL_FORM_REQUIRES_SEPARATE_CERTIFICATION");
        var chembl=json("analysis/track2a-inhibitor-dataset-2026-09-12/sources/chembl_molecule/AR-13503.json").at("/molecules/0/molecule_structures");
        sameIdentity(aRef,smiles(chembl.get("canonical_smiles").asText()));
        sameIdentity(aRef,sdf("analysis/mettl7-mechanistic-repair-2026-09-12/ar_13503_deesterified_reference.sdf",true));
        report.put("AR_representations_inspected",List.of(
            Map.of("source","PubChem CID 134128281 cached isomeric SMILES","form","neutral absolute-S reference"),
            Map.of("source","ChEMBL CHEMBL4753043 cached canonical SMILES","form","same neutral absolute-S identity"),
            Map.of("source",AR,"form","explicit-H modeled 3D; unset chiral flag; coordinate S handedness; absolute reference binding conditional"),
            Map.of("source","analysis/mettl7-mechanistic-repair-2026-09-12/ar_13503_deesterified_reference.sdf","form","same identity under the same explicit stereo bridge policy")));
        report.put("representation_scope","These recovered reference/prepared forms only; not an exhaustive external salt/tautomer inventory or actual assay-lot certification.");
        report.put("parent_canonical_identity",identity(pRef));report.put("AR_canonical_identity",identity(aRef));
        report.put("atom_id_definition","Element plus 1-based atom record ordinal in each pinned SDF, matching Hermes names; parent and AR numbering are independent. Heavy-atom alternatives never selected arbitrarily.");
        report.put("heavy_atom_mapping_alternatives",maps);report.put("alternative_count",maps.size());report.put("physical_atom_lineage_established",false);
        var deleted = new TreeSet<>(ids(p));deleted.removeAll(maps.getFirst().keySet());
        for(var map:maps) {var other=new TreeSet<>(ids(p));other.removeAll(map.keySet());require(other.equals(deleted),"ALTERNATIVE_DELETION_AMBIGUITY");}
        report.put("deleted_parent_heavy_atoms",deleted);report.put("added_AR_heavy_atoms",List.of());
        var atomRows=new ArrayList<Object>();var pi=byId(p);var ai=byId(a);
        for(String name:ids(p)) {var alternatives=maps.stream().map(m->m.get(name)).filter(Objects::nonNull).distinct().sorted().toList();
            var row=new TreeMap<String,Object>();row.put("parent_atom",name);row.put("AR_candidates",alternatives);row.put("status",alternatives.isEmpty()?"DELETED":alternatives.size()>1?"SYMMETRY_EQUIVALENT_ALTERNATIVES":"RETAINED");row.put("parent_formal_charge",p.getAtomCharge(pi.get(name)));row.put("parent_CIP",p.getAtomCIPParity(pi.get(name)));atomRows.add(row);}
        report.put("parent_heavy_atom_accounting",atomRows);
        var deletedBonds=new ArrayList<Object>();int preserved=0;
        for(int b=0;b<p.getBonds();b++) {String x=atomId(p,p.getBondAtom(0,b)),y=atomId(p,p.getBondAtom(1,b));
            if(deleted.contains(x)||deleted.contains(y))deletedBonds.add(Map.of("parent_atoms",List.of(x,y),"order",p.getBondOrder(b),"aromatic",p.isAromaticBond(b)));
            else preserved++;
        }
        report.put("deleted_parent_heavy_bonds",deletedBonds);report.put("retained_heavy_bonds",preserved);report.put("retained_heavy_bond_order_changes",List.of());
        report.put("chemical_change","Parent C6-O8 acyl bond lost with distal acyl/aromatic/methyl region. O8 survives as AR O1: ester alkoxy oxygen becomes alcohol oxygen, gains one H. This is not deletion of every ester atom.");
        var hydrogenRows=new ArrayList<Object>();var deletedH=new TreeSet<String>();var addedH=new TreeSet<String>();var pidx=byId(pAll);var aidx=byId(aAll);
        for(var map:maps) {
            var rows=new ArrayList<Object>();
            for(String parent:ids(p)) {var ph=attachedHydrogens(pAll,pidx.get(parent));String ar=map.get(parent);
                if(ar==null){deletedH.addAll(ph);continue;}var ah=attachedHydrogens(aAll,aidx.get(ar));
                if(ph.isEmpty()&&!ah.isEmpty())addedH.addAll(ah);
                else require(ph.size()==ah.size(),"UNSUPPORTED_H_COUNT_CHANGE");
                for(String h:ph)rows.add(Map.of("parent_H",h,"AR_H_candidates",ah,"basis","same mapped heavy-atom neighbor; equivalent H atoms not assigned physical lineage"));
            }hydrogenRows.add(rows);
        }
        report.put("hydrogen_mapping_alternatives_as_neighbor_orbits",hydrogenRows);report.put("deleted_parent_explicit_H",deletedH);report.put("added_AR_explicit_H",addedH);
        require(pAll.getAllAtoms()-deleted.size()-deletedH.size()+addedH.size()==aAll.getAllAtoms(),"ALL_ATOM_ACCOUNTING_FAILURE");
        var featureRows=new TreeMap<String,Object>();
        for(var it=groups.fields();it.hasNext();) {var e=it.next();var g=strings(e.getValue());require(ids(p).containsAll(g),"UNKNOWN_FEATURE_ATOM");
            var sets=maps.stream().map(m->g.stream().map(m::get).filter(Objects::nonNull).sorted().toList()).distinct().toList();
            require(sets.size()==1,"FEATURE_MAPPING_AMBIGUITY");
            featureRows.put(e.getKey(),Map.of("parent_atoms",g,"AR_atoms",sets.getFirst(),"status",featureStatus(g,maps.getFirst()),"orientation_contact_retention","UNKNOWN_NO_AR_POSE_INFERRED"));
        }
        report.put("feature_correspondence",featureRows);
        var hermes=new SdfLigandReader().read(Path.of(PARENT)).structure();var names=new TreeSet<String>();
        for(var c:hermes.getChains())for(var r:c.residues())for(var atom:r.getAtoms())if(atom.isHeavyAtom())names.add(atom.getName());require(names.equals(ids(p)),"HERMES_NAME_BRIDGE_FAILED");
        var cache=new HashMap<String,List<totah.lab.hermes.file.pdbqt.PdbqtModel>>();int checked=0;
        for(var pose:sources.get("poses"))if(pose.get("cohort").asText().equals("AD4_MATCHED")) {
            String file=pose.get("pose_file").asText();if(!cache.containsKey(file))cache.put(file,new PdbqtReader().read(Path.of(file)).models());
            var model=cache.get(file).get(pose.get("model").asInt()-1);require(model.modelNumber()==pose.get("model").asInt(),"MODEL_NUMBER_MISMATCH");var seen=new TreeSet<String>();
            for(var atom:model.atoms())if(!atom.hydrogen()){require(seen.add(atom.atomName()),"DUPLICATE_POSE_NAME");require(pi.containsKey(atom.atomName()) && atom.element().equals(p.getAtomLabel(pi.get(atom.atomName()))),"POSE_ATOM_ELEMENT_MISMATCH");}
            require(seen.equals(names),"POSE_FEATURE_NAME_COVERAGE_FAILED");checked++;
        }
        require(checked==200,"MATCHED_POSE_COUNT_MISMATCH");report.put("matched_parent_poses_name_element_bridge_checked",checked);
        report.put("pose_bridge_limit","PDBQT names/elements checked for every pose; coordinates/topology provenance relies on unchanged frozen inputs and prior raw DLG audit, not inferred bond orders from coordinates.");
        var basins=json(ROOT+"NETARSUDIL_B_POSITIVE_MAP_V1.json").get("B2_B3");var refs=new TreeMap<String,Object>();
        for(var it=basins.fields();it.hasNext();) {var e=it.next();var rows=new TreeMap<String,Object>();for(var f=e.getValue().get("features").fieldNames();f.hasNext();) {String name=f.next();require(featureRows.containsKey(name),"UNMAPPED_BASIN_FEATURE");rows.put(name,featureRows.get(name));}
            refs.put(e.getKey(),Map.of("pose_ids",strings(e.getValue().get("pose_ids")),"parent_medoid",e.getValue().get("geometry_medoid_pose").asText(),"feature_correspondence",rows,"AR_basin_occupancy","NOT_INFERRED"));}
        report.put("frozen_vector_feature_bridge",refs);report.put("scientific_scope","Atom/feature correspondence only. No crossed experimental hypothesis adjudication, potency prediction, A sparing, causal contact or B2/B3 discrimination claimed.");
        return report;
    }
    public static void main(String[] args) throws Exception {
        Path out=Path.of(args[0]);var pins=json(out.resolve("INPUTS.json").toString()).get("sha256");
        for(var it=pins.fields();it.hasNext();) {var e=it.next();require(sha(Path.of(e.getKey())).equals(e.getValue().asText()),"SOURCE_HASH_MISMATCH: "+e.getKey());}
        byte[] bytes=J.writerWithDefaultPrettyPrinter().writeValueAsBytes(certify());Path file=out.resolve("CORRESPONDENCE.json");
        if(Files.exists(file))require(Arrays.equals(bytes,Files.readAllBytes(file)),"REPLAY_NOT_IDENTICAL");else Files.write(file,bytes,StandardOpenOption.CREATE_NEW);
        System.out.println("Certification completed; deterministic report SHA256="+sha(file));
    }
}
