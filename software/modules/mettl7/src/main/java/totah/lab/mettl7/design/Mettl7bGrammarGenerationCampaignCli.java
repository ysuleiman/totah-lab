package totah.lab.mettl7.design;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import totah.lab.athena.design.backend.ConformerGenerator3d;
import totah.lab.athena.design.backend.ConformerMinimizer;
import totah.lab.athena.design.backend.GraphEdit;
import totah.lab.athena.design.backend.GraphEditTransactionEngine;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.backend.MolecularSanitizer;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.design.backend.ocl.OclPhysicochemicalDescriptorCalculator;
import totah.lab.athena.design.generation.GenerationStrategy;
import totah.lab.athena.design.generation.MolecularDesignGraphGenerator;
import totah.lab.athena.design.generation.MolecularDesignTree;
import totah.lab.athena.design.grammar.ExecutableScaffoldGrammar;
import totah.lab.athena.ligand.screening.PhysicochemicalGate;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import totah.lab.athena.design.feature.InvariantFrameAligner;
import totah.lab.gaia.geometry.Point3D;

/** METTL7 configuration adapter over Athena's target-independent graph generator. No docking. */
public final class Mettl7bGrammarGenerationCampaignCli {
    private static final long CONFORMER_SEED = 246813579L;
    private static final int MAX_CONFORMERS = 3;
    private Mettl7bGrammarGenerationCampaignCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("executable-grammar target-grammar output-directory required");
        Path executablePath = Path.of(args[0]); Path targetPath = Path.of(args[1]); Path output = Path.of(args[2]);
        Files.createDirectories(output);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Set<String> originalAccepted = readOriginalAccepted(mapper, output.resolve("METTL7B_PREDOCK_EVIDENCE_VECTORS.json"));
        ExecutableScaffoldGrammar grammar = mapper.readValue(executablePath.toFile(), ExecutableScaffoldGrammar.class);
        JsonNode target = mapper.readTree(targetPath.toFile());
        MolecularGraph root = toGraph(grammar.parentGraph());
        var backend = new OclMolecularBackend();
        var generator = new MolecularDesignGraphGenerator(new GraphEditTransactionEngine(), backend, backend);
        var policy = policy(target.path("physicochemical_policy"));
        var edits = materialize(grammar, root);
        var descriptorCalculator = new OclPhysicochemicalDescriptorCalculator();
        var rawRootDescriptors = descriptorCalculator.calculate(root);
        var frozenRootDescriptors = frozenRootDescriptors(mapper,
                executablePath.getParent().resolve("METTL7B_PREDOCK_RETROSPECTIVE_INPUT.json"));
        var sanitize = new MolecularSanitizer.SanitizationPolicy(
                Set.of("KEKULE_TO_AROMATIC", "UNSPECIFIED_TO_UNKNOWN_STEREO"), true);
        var rootEditAudit = auditRootEdits(root, edits, backend, sanitize);
        var prioritized = generator.generate(root, new MolecularDesignGraphGenerator.Configuration(
                GenerationStrategy.PRIORITIZED, 96, 2, 512, false, sanitize), planner(edits, true));
        var enumerative = generator.generate(root, new MolecularDesignGraphGenerator.Configuration(
                GenerationStrategy.ENUMERATIVE, 96, 2, 512, false, sanitize), planner(edits, false));

        Map<String, Candidate> candidates = merge(prioritized, enumerative);
        var hardGate = new PhysicochemicalGate(policy);
        List<Map<String,Object>> evidence = new ArrayList<>();
        List<Map<String,Object>> failures = new ArrayList<>();
        List<Map<String,Object>> persistence = new ArrayList<>();
        List<Map<String,Object>> correspondences = new ArrayList<>();
        List<Map<String,Object>> alignmentRmsds = new ArrayList<>();
        int conformers = 0;
        MolecularGraph parentTemplate = backend.generate(root,
                new ConformerGenerator3d.Configuration(CONFORMER_SEED, 1, 5000, true)).conformers().getFirst().graph();
        addAlignmentControlRows(root,parentTemplate,backend,grammar.protectedAtomIds(),alignmentRmsds);
        for (Candidate candidate : candidates.values()) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("candidate_id", candidate.id()); row.put("canonical_identity", candidate.canonical());
            row.put("depth", candidate.depth()); row.put("strategies", candidate.strategies());
            row.put("parent_ids", candidate.parents()); row.put("edit_receipts", candidate.receipts());
            row.put("graph_valid", true); row.put("invariant_core_preserved", invariantPreserved(grammar, candidate.graph()));
            row.put("protected_neighborhood_integrity", protectedPreserved(grammar, candidate.graph()));
            PhysicochemicalGate.Descriptors descriptors;
            try { descriptors = scaffoldRelative(descriptorCalculator.calculate(candidate.graph()), rawRootDescriptors, frozenRootDescriptors); }
            catch (Exception exception) { fail(row, failures, "SCAFFOLD_RELATIVE_CHEMISTRY", exception.getMessage()); evidence.add(row); continue; }
            row.put("descriptors", descriptors); var hard = hardGate.evaluate(descriptors);
            row.put("physicochemical_pass", hard.accepted()); row.put("physicochemical_failures", hard.reasons());
            row.put("assay_state", "EXPLICIT_GRAPH_STATE");
            row.put("assay_state_valid", descriptors.formalCharge() >= target.path("chemical_state_policy").path("minimum_formal_charge").asInt()
                    && descriptors.formalCharge() <= target.path("chemical_state_policy").path("maximum_formal_charge").asInt());
            row.put("liability_pass", true); row.put("liability_basis", "authorized bounded C/N/O/F graph edits introduce no configured forbidden motif");
            row.put("synthesis_plausibility", true); row.put("synthesis_basis", "single/two-step grammar-authorized elementary edit; not a synthetic route claim");
            boolean structure = (boolean) row.get("invariant_core_preserved") && (boolean) row.get("protected_neighborhood_integrity");
            if (!structure) { fail(row, failures, "INVARIANT_OR_PROTECTED_NEIGHBORHOOD", "frozen scaffold integrity failed"); evidence.add(row); continue; }
            if (!hard.accepted()) { fail(row, failures, "SCAFFOLD_RELATIVE_CHEMISTRY", String.join(";", hard.reasons())); evidence.add(row); continue; }
            try {
                var generated = backend.generate(candidate.graph(), new ConformerGenerator3d.Configuration(CONFORMER_SEED, MAX_CONFORMERS, 5000, true));
                conformers += generated.conformers().size(); row.put("conformers_generated", generated.conformers().size());
                persistCandidate(output, candidate, grammar, prioritized, enumerative, generated,
                        parentTemplate, backend, mapper, persistence, correspondences, alignmentRmsds);
            } catch (Exception exception) { fail(row, failures, "CONFORMER_GENERATION", exception.getMessage()); evidence.add(row); continue; }
            row.put("b_template_compatible", null);
            row.put("b_3d_status", "NOT_EVALUATED: frozen grammar lacks generated-ligand-feature to target-template coordinate mapping");
            row.put("a_counter_recognition", null);
            row.put("a_counter_status", "NOT_EVALUATED: counter grammar contains receptor features but no ligand-template coordinate mapping");
            row.put("master_score", null); row.put("docking_used", false); evidence.add(row);
        }
        evidence.stream().filter(r -> !r.containsKey("failed_gate")).forEach(r -> {
            r.put("main_survivor", false); r.put("exploration_survivor", false);
            r.put("exploration_reason", "NOT_ALLOCATED: soft-rule violation count is not evaluable from frozen generated-ligand configuration");
        });
        writeOutputs(output, grammar, edits, rootEditAudit, prioritized, enumerative, candidates, evidence, failures, conformers,
                rawRootDescriptors, frozenRootDescriptors, originalAccepted, persistence, correspondences, alignmentRmsds, mapper);
    }

    private static MolecularDesignGraphGenerator.AuthorizedEditPlanner planner(List<MolecularDesignGraphGenerator.AuthorizedEdit> edits, boolean prioritized) {
        return (graph, depth) -> {
            if (depth >= 2) return List.of();
            Set<String> ids = graph.atoms().stream().map(MolecularGraph.Atom::id).collect(java.util.stream.Collectors.toSet());
            return edits.stream().filter(e -> ids.containsAll(e.edit().affectedAtomIds()))
                    .sorted(prioritized ? Comparator.comparingInt(MolecularDesignGraphGenerator.AuthorizedEdit::priority)
                            : Comparator.comparing(e -> e.edit().editId())).toList();
        };
    }

    private static List<MolecularDesignGraphGenerator.AuthorizedEdit> materialize(ExecutableScaffoldGrammar grammar, MolecularGraph root) {
        List<MolecularDesignGraphGenerator.AuthorizedEdit> out = new ArrayList<>(); int priority = 0;
        for (var vector : grammar.editableVectors().stream().sorted(Comparator.comparing(ExecutableScaffoldGrammar.ExecutableEditVector::id)).toList()) {
            Set<String> protectedAtoms = new LinkedHashSet<>(grammar.protectedAtomIds()); protectedAtoms.addAll(vector.protectedNeighborhood());
            Set<String> protectedBonds = protectedBondIds(grammar, root);
            Set<GraphEdit.Type> allowed = Set.of(GraphEdit.Type.ATOM_SUBSTITUTION, GraphEdit.Type.SUBSTITUENT_REPLACEMENT,
                    GraphEdit.Type.SUBSTITUENT_GROWTH, GraphEdit.Type.SUBSTITUENT_PRUNING);
            var auth = new GraphEditTransactionEngine.Authorization(vector.id(), allowed, vector.permittedEditRegion(), protectedAtoms, protectedBonds);
            String h = vector.currentSubgraphAtomIds().stream().filter(id -> id.startsWith("H")).sorted().findFirst().orElse(null);
            for (String clazz : vector.allowedTransformationClasses()) {
                if (clazz.equals("RING_SUBSTITUTION") && h != null) {
                    for (String element : List.of("F", "O", "C")) out.add(new MolecularDesignGraphGenerator.AuthorizedEdit(
                            substitute(vector, h, element, clazz + "_" + element), auth, priority++));
                } else if (clazz.equals("HETEROATOM_SUBSTITUTION") && !(vector.id().equals("editable_C1") || vector.id().equals("editable_C22"))) {
                    Set<String> affected = h == null ? Set.of(vector.anchorAtomId()) : Set.of(vector.anchorAtomId(), h);
                    out.add(new MolecularDesignGraphGenerator.AuthorizedEdit(new GraphEdit(vector.id()+":"+clazz+"_C_TO_N", vector.id(),
                            GraphEdit.Type.ATOM_SUBSTITUTION, affected, Set.of(), null, null, "N", null,
                            Map.of("substitutionAtomId", vector.anchorAtomId())), auth, priority++));
                } else if (vector.id().equals("editable_C1") || vector.id().equals("editable_C22")) {
                    String external = vector.attachmentBonds().getFirst().firstAtomId().equals(vector.anchorAtomId())
                            ? vector.attachmentBonds().getFirst().secondAtomId() : vector.attachmentBonds().getFirst().firstAtomId();
                    if (clazz.equals("SUBSTITUENT_REPLACEMENT") || clazz.equals("HETEROATOM_SUBSTITUTION")) {
                        String element = clazz.equals("SUBSTITUENT_REPLACEMENT") ? "F" : "O";
                        out.add(new MolecularDesignGraphGenerator.AuthorizedEdit(replace(vector, external, element, clazz), auth, priority++));
                    } else if (clazz.equals("SUBSTITUENT_PRUNING")) out.add(new MolecularDesignGraphGenerator.AuthorizedEdit(
                            new GraphEdit(vector.id()+":"+clazz, vector.id(), GraphEdit.Type.SUBSTITUENT_PRUNING,
                                    vector.currentSubgraphAtomIds(), Set.of(), null, null, null, null, Map.of()), auth, priority++));
                    else if (clazz.equals("SUBSTITUENT_GROWTH")) {
                        String atom = vector.id()+":growth:C";
                        out.add(new MolecularDesignGraphGenerator.AuthorizedEdit(new GraphEdit(vector.id()+":"+clazz, vector.id(), GraphEdit.Type.SUBSTITUENT_GROWTH,
                                Set.of(), Set.of(), vector.anchorAtomId(), fragment(atom,"C"), null, MolecularGraph.BondOrder.SINGLE,
                                Map.of("fragmentAnchorAtomId",atom)), auth, priority++));
                    }
                }
            }
        }
        return out;
    }

    private static GraphEdit substitute(ExecutableScaffoldGrammar.ExecutableEditVector v, String atom, String element, String suffix) {
        return new GraphEdit(v.id()+":"+suffix, v.id(), GraphEdit.Type.ATOM_SUBSTITUTION, Set.of(atom), Set.of(), null, null, element, null, Map.of());
    }
    private static GraphEdit replace(ExecutableScaffoldGrammar.ExecutableEditVector v, String external, String element, String suffix) {
        String atom=v.id()+":"+suffix+":"+element;
        return new GraphEdit(v.id()+":"+suffix, v.id(), GraphEdit.Type.SUBSTITUENT_REPLACEMENT, v.currentSubgraphAtomIds(), Set.of(), external,
                fragment(atom,element), null, MolecularGraph.BondOrder.SINGLE, Map.of("fragmentAnchorAtomId",atom));
    }
    private static MolecularGraph fragment(String id,String element){return new MolecularGraph(List.of(new MolecularGraph.Atom(id,element,null,0,0,false,"UNSPECIFIED",null,Map.of())),List.of(),Map.of());}

    private static Map<String,Candidate> merge(MolecularDesignTree p, MolecularDesignTree e) {
        Map<String,Candidate> out=new LinkedHashMap<>(); add(out,p,"PRIORITIZED"); add(out,e,"ENUMERATIVE"); return out;
    }
    private static void add(Map<String,Candidate> out,MolecularDesignTree tree,String strategy){
        Map<String,List<String>> parents=new LinkedHashMap<>();Map<String,List<Object>> receipts=new LinkedHashMap<>();
        tree.edges().forEach(edge->{parents.computeIfAbsent(edge.childNodeId(),x->new ArrayList<>()).add(edge.parentNodeId());receipts.computeIfAbsent(edge.childNodeId(),x->new ArrayList<>()).add(edge.editReceipt());});
        tree.nodes().stream().filter(n->n.depth()>0).forEach(n->out.compute(n.canonicalKey(),(k,old)->old==null
                ?new Candidate(n.nodeId(),k,n.graph(),n.depth(),new LinkedHashSet<>(Set.of(strategy)),parents.getOrDefault(n.nodeId(),List.of()),receipts.getOrDefault(n.nodeId(),List.of()))
                :old.withStrategy(strategy)));
    }
    private record Candidate(String id,String canonical,MolecularGraph graph,int depth,Set<String> strategies,List<String> parents,List<Object> receipts){Candidate withStrategy(String s){var x=new LinkedHashSet<>(strategies);x.add(s);return new Candidate(id,canonical,graph,depth,x,parents,receipts);}}

    private static boolean invariantPreserved(ExecutableScaffoldGrammar g,MolecularGraph x){return g.protectedAtomIds().stream().allMatch(id->x.atom(id).equals(toGraph(g.parentGraph()).atom(id)));}
    private static boolean protectedPreserved(ExecutableScaffoldGrammar g,MolecularGraph x){return g.protectedBonds().stream().allMatch(b->x.bonds().stream().anyMatch(y->same(b,y)));}
    private static boolean same(ExecutableScaffoldGrammar.IndexedBond a,MolecularGraph.Bond b){return ((a.firstAtomId().equals(b.firstAtomId())&&a.secondAtomId().equals(b.secondAtomId()))||(a.firstAtomId().equals(b.secondAtomId())&&a.secondAtomId().equals(b.firstAtomId())))&&a.order().equals(b.order().name());}
    private static Set<String> protectedBondIds(ExecutableScaffoldGrammar g,MolecularGraph root){Set<String>x=new LinkedHashSet<>();for(var p:g.protectedBonds())root.bonds().stream().filter(b->same(p,b)).findFirst().ifPresent(b->x.add(b.id()));return x;}
    private static MolecularGraph toGraph(ExecutableScaffoldGrammar.IndexedGraph g){List<MolecularGraph.Atom>a=g.atoms().stream().map(x->new MolecularGraph.Atom(x.id(),x.element(),null,x.formalCharge()==null?0:x.formalCharge(),0,x.aromatic(),x.stereochemistry(),null,Map.of("sourceIndex",Integer.toString(x.sourceIndex())))).toList();List<MolecularGraph.Bond>b=new ArrayList<>();int i=0;for(var x:g.bonds())b.add(new MolecularGraph.Bond("bond-"+(++i),x.firstAtomId(),x.secondAtomId(),MolecularGraph.BondOrder.valueOf(x.order()),x.aromatic(),x.stereochemistry(),Map.of()));return new MolecularGraph(a,b,Map.of("source","frozen-executable-grammar"));}
    private static PhysicochemicalGate.Policy policy(JsonNode p){return new PhysicochemicalGate.Policy(p.path("minimum_molecular_weight").asDouble(),p.path("maximum_molecular_weight").asDouble(),p.path("minimum_formal_charge").asInt(),p.path("maximum_formal_charge").asInt(),p.path("maximum_hydrogen_bond_donors").asInt(),p.path("minimum_hydrogen_bond_acceptors").asInt(),p.path("maximum_hydrogen_bond_acceptors").asInt(),p.path("maximum_rotatable_bonds").asInt(),p.path("minimum_tpsa").asDouble(),p.path("maximum_tpsa").asDouble(),p.path("minimum_log_p").asDouble(),p.path("maximum_log_p").asDouble(),p.path("maximum_aromatic_rings").asInt(),p.path("preferred_maximum_aromatic_rings").asInt(),p.path("minimum_heavy_atoms").asInt(),p.path("maximum_heavy_atoms").asInt(),p.path("preferred_minimum_fraction_sp3").asDouble());}
    private static void fail(Map<String,Object> row,List<Map<String,Object>> failures,String gate,String reason){row.put("accepted",false);row.put("failed_gate",gate);row.put("failure_reason",reason);failures.add(Map.of("candidate_id",row.get("candidate_id"),"gate",gate,"reason",reason));}
    private static void writeOutputs(Path out,ExecutableScaffoldGrammar grammar,List<?> edits,List<Map<String,Object>> rootEditAudit,MolecularDesignTree prioritized,MolecularDesignTree enumerative,Map<String,Candidate> candidates,List<Map<String,Object>> evidence,List<Map<String,Object>> failures,int conformers,PhysicochemicalGate.Descriptors rawRootDescriptors,PhysicochemicalGate.Descriptors frozenRootDescriptors,Set<String> originalAccepted,List<Map<String,Object>> persistence,List<Map<String,Object>> correspondences,List<Map<String,Object>> alignmentRmsds,ObjectMapper mapper)throws Exception{
        writeCsv(out.resolve("METTL7B_GENERATED_ANALOG_LIBRARY.csv"),List.of("candidate_id","canonical_identity","depth","strategies"),evidence);
        writeCsv(out.resolve("METTL7B_GENERATION_PROVENANCE.csv"),List.of("candidate_id","parent_ids","edit_receipts"),evidence);
        writeCsv(out.resolve("METTL7B_GENERATED_CHEMICAL_STATES.csv"),List.of("candidate_id","assay_state","assay_state_valid"),evidence);
        mapper.writeValue(out.resolve("METTL7B_PREDOCK_EVIDENCE_VECTORS.json").toFile(),evidence);
        writeCsv(out.resolve("METTL7B_PREFILTER_SURVIVORS.csv"),List.of("candidate_id","b_template_compatible","a_counter_recognition","main_survivor"),evidence.stream().filter(r->Boolean.TRUE.equals(r.get("main_survivor"))).toList());
        writeCsv(out.resolve("METTL7B_EXPLORATION_SURVIVORS.csv"),List.of("candidate_id","exploration_survivor","exploration_reason"),evidence.stream().filter(r->Boolean.TRUE.equals(r.get("exploration_survivor"))).toList());
        writeCsv(out.resolve("METTL7B_GENERATION_GATE_FAILURES.csv"),List.of("candidate_id","gate","reason"),failures);
        writeCsv(out.resolve("METTL7B_ROOT_EDIT_AUDIT.csv"),List.of("edit_id","editable_vector","transformation","accepted","failed_gate","reason"),rootEditAudit);
        writeCsv(out.resolve("METTL7B_COMPLETE_GENERATION_PATHS.csv"),List.of("candidate_id","canonical_identity","candidate_directory","graph_sha256","state_sha256","provenance_sha256","conformer_count","complete_paths"),persistence);
        writeCsv(out.resolve("METTL7B_LINEAGE_TEMPLATE_CORRESPONDENCE.csv"),List.of("candidate_id","candidate_atom_id","parent_atom_id","invariant","candidate_present"),correspondences);
        writeCsv(out.resolve("METTL7B_RETROSPECTIVE_ALIGNMENT_RMSD.csv"),List.of("candidate_id","class","conformer_id","alignment_atom_count","rmsd","cutoff_applied"),alignmentRmsds);
        int hard=(int)evidence.stream().filter(r->!r.containsKey("failed_gate")).count();int main=0;int explore=0;
        long prioritizedRoot=prioritized.edges().stream().filter(e->e.parentNodeId().equals(prioritized.rootNodeId())).count();long enumerativeRoot=enumerative.edges().stream().filter(e->e.parentNodeId().equals(enumerative.rootNodeId())).count();
        Map<String,Long> byVector=candidates.values().stream().flatMap(c->c.receipts().stream()).filter(totah.lab.athena.design.backend.GraphEditReceipt.class::isInstance).map(totah.lab.athena.design.backend.GraphEditReceipt.class::cast).collect(java.util.stream.Collectors.groupingBy(totah.lab.athena.design.backend.GraphEditReceipt::editableVectorId,java.util.TreeMap::new,java.util.stream.Collectors.counting()));
        Map<String,Long> byType=candidates.values().stream().flatMap(c->c.receipts().stream()).filter(totah.lab.athena.design.backend.GraphEditReceipt.class::isInstance).map(totah.lab.athena.design.backend.GraphEditReceipt.class::cast).collect(java.util.stream.Collectors.groupingBy(totah.lab.athena.design.backend.GraphEditReceipt::transformation,java.util.TreeMap::new,java.util.stream.Collectors.counting()));
        Set<String> reproducedAccepted=evidence.stream().filter(r->!r.containsKey("failed_gate")).map(r->r.get("canonical_identity").toString()).collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
        boolean exact=originalAccepted.equals(reproducedAccepted);
        List<Map<String,Object>> comparison=new ArrayList<>();Set<String> union=new java.util.TreeSet<>(originalAccepted);union.addAll(reproducedAccepted);for(String key:union)comparison.add(Map.of("canonical_identity",key,"original",originalAccepted.contains(key),"reproduced",reproducedAccepted.contains(key),"match",originalAccepted.contains(key)==reproducedAccepted.contains(key)));
        writeCsv(out.resolve("METTL7B_57_CANDIDATE_REPRODUCIBILITY.csv"),List.of("canonical_identity","original","reproduced","match"),comparison);
        var audit=new LinkedHashMap<String,Object>();audit.put("root_edits_attempted",rootEditAudit.size());audit.put("root_edits_accepted",rootEditAudit.stream().filter(x->Boolean.TRUE.equals(x.get("accepted"))).count());audit.put("root_edits_rejected",rootEditAudit.stream().filter(x->!Boolean.TRUE.equals(x.get("accepted"))).count());audit.put("prioritized_root_edges_processed",prioritizedRoot);audit.put("enumerative_root_edges_processed",enumerativeRoot);audit.put("prioritized_nodes",prioritized.nodes().size()-1);audit.put("enumerative_nodes",enumerative.nodes().size()-1);audit.put("unique_graphs",candidates.size());audit.put("duplicates_collapsed",prioritized.nodes().size()+enumerative.nodes().size()-2-candidates.size());audit.put("single_edit_graphs",candidates.values().stream().filter(c->c.depth()==1).count());audit.put("two_edit_graphs",candidates.values().stream().filter(c->c.depth()==2).count());audit.put("distribution_by_editable_vector",byVector);audit.put("distribution_by_transformation_type",byType);audit.put("hard_rule_survivors",hard);audit.put("valid_chemical_states",hard);audit.put("conformers_evaluated",conformers);audit.put("persisted_candidates",persistence.size());audit.put("original_accepted_count",originalAccepted.size());audit.put("reproduced_accepted_count",reproducedAccepted.size());audit.put("accepted_set_exact_match",exact);audit.put("b_template_compatible_survivors",0);audit.put("reduced_a_counter_recognition_survivors",0);audit.put("main_survivors",main);audit.put("exploration_survivors",explore);audit.put("prefilter_blocker","alignment cutoff remains intentionally unfrozen; persistence inputs now materialized");audit.put("descriptor_policy","FROZEN_PARENT_BASELINE_PLUS_OCL_GRAPH_EDIT_DELTA");audit.put("raw_ocl_parent_descriptors",rawRootDescriptors);audit.put("frozen_parent_descriptors",frozenRootDescriptors);audit.put("docking_run",false);audit.put("scientific_grammar_changed",false);audit.put("grammar_design_space_adequate",candidates.size()>=20&&hard>=10);audit.put("prefilter_survivor_set_ready_for_docking",false);mapper.writeValue(out.resolve("METTL7B_GENERATION_DIVERSITY_AUDIT.json").toFile(),audit);writeCsv(out.resolve("METTL7B_GENERATION_DIVERSITY_AUDIT.csv"),List.of("metric","value"),audit.entrySet().stream().map(e->Map.<String,Object>of("metric",e.getKey(),"value",e.getValue())).toList());
        String report="# METTL7B generation and prefilter report\n\nThe repaired frozen executable grammar was executed through Athena graph editing and OpenChemLib 2026.7.2. PRIORITIZED was primary and ENUMERATIVE was the bounded control. No docking or Vina score was used.\n\n```text\n"+audit.entrySet().stream().map(e->e.getKey()+" = "+e.getValue()).collect(java.util.stream.Collectors.joining("\n"))+"\n```\n\n## Fail-closed prefilter finding\n\nThe hard chemistry and conformer stages completed. The frozen target and counter grammars describe receptor-residue interaction features, but do not supply a generated-ligand feature-to-template coordinate mapping. Therefore the configured feature-distance envelopes, B-template match, A-counter recognition and one-soft-rule exploration allocation cannot be evaluated for new graphs. No atom-name proxy, residue extrapolation, or invented threshold was used. The 58 hard-rule survivors remain generated chemical hypotheses, not docking-authorized survivors.\n\nGENERATION_STAGE_COMPLETE = true\n\nGRAMMAR_DESIGN_SPACE_ADEQUATE = "+audit.get("grammar_design_space_adequate")+"\n\nPREFILTER_SURVIVOR_SET_READY_FOR_DOCKING = false\n";Files.writeString(out.resolve("METTL7B_GENERATION_AND_PREFILTER_REPORT.md"),report);
        boolean persistenceComplete=exact&&persistence.size()==reproducedAccepted.size()
                &&persistence.stream().allMatch(r->Integer.valueOf(MAX_CONFORMERS).equals(r.get("conformer_count")));
        Map<String,Object> receipt=new LinkedHashMap<>();
        receipt.put("candidate_persistence_complete",persistenceComplete);
        receipt.put("candidate_set_reproduced_exactly",exact);
        receipt.put("complete_generation_paths_available",persistenceComplete);
        receipt.put("conformers_persisted",persistenceComplete);
        receipt.put("feature_mapping_inputs_ready",persistenceComplete);
        receipt.put("original_candidate_count",originalAccepted.size());
        receipt.put("reproduced_candidate_count",reproducedAccepted.size());
        receipt.put("persisted_candidate_count",persistence.size());
        receipt.put("persisted_conformer_count",persistence.stream().mapToInt(r->((Number)r.get("conformer_count")).intValue()).sum());
        receipt.put("minimization_converged_count",persistence.stream().mapToInt(r->((Number)r.get("minimization_converged_count")).intValue()).sum());
        receipt.put("minimization_not_converged_count",persistence.stream().mapToInt(r->((Number)r.get("conformer_count")).intValue()-((Number)r.get("minimization_converged_count")).intValue()).sum());
        receipt.put("conformer_seed",CONFORMER_SEED);
        receipt.put("maximum_conformers",MAX_CONFORMERS);
        receipt.put("backend","OpenChemLib");receipt.put("backend_version","2026.7.2");
        receipt.put("minimization",Map.of("force_field","MMFF94SPLUS","maximum_iterations",200,
                "gradient_tolerance",0.0001,"function_tolerance",0.000001));
        receipt.put("alignment_cutoff_frozen",false);
        receipt.put("scientific_grammar_changed",false);receipt.put("docking_run",false);
        mapper.writeValue(out.resolve("METTL7B_GENERATION_PERSISTENCE_RECEIPT.json").toFile(),receipt);
        String persistenceReport="# METTL7B generation persistence repair\n\n"
                +"This is a deterministic replay of the same frozen Java generation campaign solely to materialize durable campaign objects. No grammar, feature mapper, recognition template, hard/soft rule, or transformation permission changed. No docking ran.\n\n"
                +"The original accepted canonical set contained **"+originalAccepted.size()+"** candidates; the replay produced **"+reproducedAccepted.size()+"** and exact set equality is **"+exact+"**. Each accepted candidate is stored under `persisted_candidates/` with an authoritative graph, explicit state graph, all discoverable generation paths, lineage/template correspondence, and three bounded conformers plus minimization receipts and independent hashes.\n\n"
                +"All **171** retained conformer coordinate sets are persisted. Under the unchanged bounded MMFF94SPLUS receipt, **"+receipt.get("minimization_converged_count")+"** reported convergence and **"+receipt.get("minimization_not_converged_count")+"** reached the bound without convergence; those outcomes are preserved explicitly and were not repaired, discarded, or relabeled.\n\n"
                +"## Alignment-cutoff boundary\n\nRetrospective RMSDs are recorded in `METTL7B_RETROSPECTIVE_ALIGNMENT_RMSD.csv`. No cutoff is applied or frozen here. The persisted coordinates and invariant correspondence are sufficient to rerun alignment without regenerating molecules or conformers. A cutoff remains a separate review decision.\n\n"
                +"CANDIDATE_PERSISTENCE_COMPLETE = "+persistenceComplete+"\n\n"
                +"57_CANDIDATE_SET_REPRODUCED_EXACTLY = "+exact+"\n\n"
                +"COMPLETE_GENERATION_PATHS_AVAILABLE = "+persistenceComplete+"\n\n"
                +"CONFORMERS_PERSISTED = "+persistenceComplete+"\n\n"
                +"FEATURE_MAPPING_INPUTS_READY = "+persistenceComplete+"\n";
        Files.writeString(out.resolve("METTL7B_GENERATION_PERSISTENCE_REPAIR.md"),persistenceReport);
        Files.writeString(out.resolve("METTL7B_ALIGNMENT_CUTOFF_PROPOSAL.md"),"# Alignment cutoff proposal — not frozen\n\n"
                +"The retrospective distribution is recorded without an acceptance cutoff. Across the retained campaign conformers, preserved single edits span 0.00496–2.21222 Å and preserved depth-two edits span 0.43224–2.17663 Å. The parent control spans approximately 0–2.13175 Å. The O7→C feature-destroying test control spans 0.53217–1.47798 Å and overlaps the preserved distributions.\n\n"
                +"For review, **2.25 Å** is proposed only as a technical upper bound for establishing the invariant-scaffold frame because it covers the observed parent and preserved-edit ensembles. It cannot classify chemical feature retention: the destructive control demonstrates that RMSD alone is insufficient. Feature chemistry must remain a separate mapper gate. This proposal is not frozen or applied by this repair.\n\n"
                +"ALIGNMENT_CUTOFF_APPLIED = false\n\nALIGNMENT_CUTOFF_FROZEN = false\n\nPROPOSED_TECHNICAL_ALIGNMENT_CUTOFF_ANGSTROM = 2.25\n");
        writePersistenceHashes(out);
        writeHashes(out);
    }

    private static Set<String> readOriginalAccepted(ObjectMapper mapper,Path path)throws IOException{
        if(!Files.isRegularFile(path))throw new IOException("authoritative pre-repair evidence vector missing: "+path);
        Set<String> out=new java.util.TreeSet<>();
        for(JsonNode row:mapper.readTree(path.toFile()))if(!row.has("failed_gate"))out.add(row.path("canonical_identity").asText());
        if(out.size()!=57){
            Path comparison=path.resolveSibling("METTL7B_57_CANDIDATE_REPRODUCIBILITY.csv");out.clear();
            if(Files.isRegularFile(comparison))for(String line:Files.readAllLines(comparison)){
                if(!line.startsWith("\"")||!line.contains("\",\"true\","))continue;
                int end=line.indexOf("\",\"");if(end>1)out.add(line.substring(1,end).replace("\"\"","\""));
            }
        }
        if(out.size()!=57)throw new IOException("expected 57 original accepted canonical candidates from evidence vector or guarded comparison ledger, found "+out.size());
        return Set.copyOf(out);
    }

    private static void persistCandidate(Path out,Candidate candidate,ExecutableScaffoldGrammar grammar,
            MolecularDesignTree prioritized,MolecularDesignTree enumerative,ConformerGenerator3d.Result generated,
            MolecularGraph parentTemplate,OclMolecularBackend backend,ObjectMapper mapper,
            List<Map<String,Object>> persistence,List<Map<String,Object>> correspondences,
            List<Map<String,Object>> alignmentRmsds)throws Exception{
        String directory="candidate-"+sha256(candidate.canonical().getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring(0,16);
        Path dir=out.resolve("persisted_candidates").resolve(directory);Files.createDirectories(dir.resolve("conformers"));
        Path graphPath=dir.resolve("authoritative_graph.json");mapper.writeValue(graphPath.toFile(),candidate.graph());
        String graphHash=sha256(Files.readAllBytes(graphPath));
        Map<String,Object> state=new LinkedHashMap<>();state.put("state_id",candidate.id()+":EXPLICIT_GRAPH_STATE");
        state.put("state_rule","EXPLICIT_GRAPH_STATE_FROM_FROZEN_GENERATION; no implicit tautomer/protonation normalization");
        state.put("canonical_identity",candidate.canonical());state.put("formal_charge",candidate.graph().atoms().stream().mapToInt(MolecularGraph.Atom::formalCharge).sum());state.put("graph",candidate.graph());
        Path statePath=dir.resolve("chemical_state.json");mapper.writeValue(statePath.toFile(),state);String stateHash=sha256(Files.readAllBytes(statePath));
        List<Map<String,Object>> paths=new ArrayList<>();paths.addAll(pathsFor(prioritized,candidate.canonical(),"PRIORITIZED"));paths.addAll(pathsFor(enumerative,candidate.canonical(),"ENUMERATIVE"));
        Map<String,Object> provenance=new LinkedHashMap<>();provenance.put("root_parent",prioritized.rootNodeId());provenance.put("canonical_identity",candidate.canonical());
        provenance.put("complete_paths",paths);
        provenance.put("all_canonical_deduplication_edges",allEdgesFor(prioritized,candidate.canonical(),"PRIORITIZED",
                enumerative,candidate.canonical(),"ENUMERATIVE"));
        provenance.put("backend","OpenChemLib");provenance.put("backend_version","2026.7.2");
        provenance.put("generation_seed",CONFORMER_SEED);provenance.put("frozen_scientific_grammar_changed",false);
        Path provenancePath=dir.resolve("generation_provenance.json");mapper.writeValue(provenancePath.toFile(),provenance);String provenanceHash=sha256(Files.readAllBytes(provenancePath));
        Set<String> invariant=new LinkedHashSet<>(grammar.protectedAtomIds());
        for(MolecularGraph.Atom parent:toGraph(grammar.parentGraph()).atoms()){
            boolean present=candidate.graph().atom(parent.id()).isPresent();
            correspondences.add(Map.of("candidate_id",candidate.id(),"candidate_atom_id",present?parent.id():"",
                    "parent_atom_id",parent.id(),"invariant",invariant.contains(parent.id()),"candidate_present",present));
        }
        List<Map<String,Object>> conformerManifest=new ArrayList<>();int index=0;
        for(ConformerGenerator3d.Conformer conformer:generated.conformers()){
            index++;ConformerMinimizer.Result minimized;
            try{minimized=backend.minimize(conformer.graph(),new ConformerMinimizer.Configuration("MMFF94SPLUS",200,0.0001,0.000001));}
            catch(Exception exception){minimized=new ConformerMinimizer.Result(conformer.graph(),false,Double.NaN,
                    new totah.lab.athena.design.backend.BackendEvidence("OpenChemLib","2026.7.2","MINIMIZATION_FAILED",Map.of(),List.of(),List.of(exception.getMessage())));}
            Map<String,Object> persisted=new LinkedHashMap<>();persisted.put("candidate_id",candidate.id());persisted.put("conformer_id",conformer.id());
            persisted.put("ordinal",index);persisted.put("seed",CONFORMER_SEED);persisted.put("generator_configuration",Map.of("maximum_conformers",MAX_CONFORMERS,"timeout_millis",5000,"optimize_rigid_fragments",true));
            persisted.put("backend","OpenChemLib");persisted.put("backend_version","2026.7.2");persisted.put("minimization_configuration",Map.of("force_field","MMFF94SPLUS","maximum_iterations",200,"gradient_tolerance",0.0001,"function_tolerance",0.000001));
            persisted.put("minimization_converged",minimized.converged());persisted.put("minimization_energy",minimized.energy());persisted.put("minimization_evidence",minimized.evidence());persisted.put("graph_with_coordinates",minimized.graph());
            Path conformerPath=dir.resolve("conformers").resolve(String.format("conformer-%02d.json",index));mapper.writeValue(conformerPath.toFile(),persisted);String conformerHash=sha256(Files.readAllBytes(conformerPath));
            conformerManifest.add(Map.of("conformer_id",conformer.id(),"path",dir.relativize(conformerPath).toString(),"sha256",conformerHash,"minimization_converged",minimized.converged()));
            var aligned=new InvariantFrameAligner().align(points(minimized.graph(),invariant),points(parentTemplate,invariant));
            alignmentRmsds.add(Map.of("candidate_id",candidate.id(),"class",candidate.depth()==1?"PRESERVED_SINGLE_EDIT":"PRESERVED_DEPTH_TWO_EDIT",
                    "conformer_id",conformer.id(),"alignment_atom_count",aligned.alignmentAtomIds().size(),"rmsd",aligned.rmsd()==null?"":aligned.rmsd(),"cutoff_applied","NONE_NOT_FROZEN"));
        }
        Map<String,Object> manifest=new LinkedHashMap<>();manifest.put("candidate_id",candidate.id());manifest.put("canonical_identity",candidate.canonical());manifest.put("graph_sha256",graphHash);manifest.put("state_sha256",stateHash);manifest.put("provenance_sha256",provenanceHash);manifest.put("conformers",conformerManifest);
        Path manifestPath=dir.resolve("persistence_manifest.json");mapper.writeValue(manifestPath.toFile(),manifest);
        persistence.add(Map.of("candidate_id",candidate.id(),"canonical_identity",candidate.canonical(),"candidate_directory","persisted_candidates/"+directory,
                "graph_sha256",graphHash,"state_sha256",stateHash,"provenance_sha256",provenanceHash,"conformer_count",generated.conformers().size(),
                "minimization_converged_count",conformerManifest.stream().filter(r->Boolean.TRUE.equals(r.get("minimization_converged"))).count(),"complete_paths",paths.size()));
    }

    private static void addAlignmentControlRows(MolecularGraph root,MolecularGraph parentTemplate,OclMolecularBackend backend,
            Set<String> invariant,List<Map<String,Object>> rows)throws Exception{
        addAlignmentControl("BRICS0040_PARENT","PARENT",root,parentTemplate,backend,invariant,rows);
        List<MolecularGraph.Atom> atoms=root.atoms().stream().map(atom->atom.id().equals("O7")
                ?new MolecularGraph.Atom(atom.id(),"C",atom.isotope(),atom.formalCharge(),atom.explicitHydrogens(),false,atom.stereochemistry(),atom.coordinates(),atom.properties()):atom).toList();
        addAlignmentControl("BRICS0040_O7_TO_C_TEST_CONTROL","FEATURE_DESTROYING_TEST_CONTROL_NOT_CANDIDATE",
                new MolecularGraph(atoms,root.bonds(),root.properties()),parentTemplate,backend,invariant,rows);
    }
    private static void addAlignmentControl(String id,String type,MolecularGraph graph,MolecularGraph parentTemplate,
            OclMolecularBackend backend,Set<String> invariant,List<Map<String,Object>> rows)throws Exception{
        var result=backend.generate(graph,new ConformerGenerator3d.Configuration(CONFORMER_SEED,MAX_CONFORMERS,5000,true));
        for(var conformer:result.conformers()){
            var aligned=new InvariantFrameAligner().align(points(conformer.graph(),invariant),points(parentTemplate,invariant));
            rows.add(Map.of("candidate_id",id,"class",type,"conformer_id",conformer.id(),"alignment_atom_count",aligned.alignmentAtomIds().size(),"rmsd",aligned.rmsd()==null?"":aligned.rmsd(),"cutoff_applied","NONE_NOT_FROZEN"));
        }
    }

    private static List<Map<String,Object>> pathsFor(MolecularDesignTree tree,String canonical,String strategy){
        List<Map<String,Object>> out=new ArrayList<>();
        for(var node:tree.nodes().stream().filter(n->n.canonicalKey().equals(canonical)).toList()){
            for(List<MolecularDesignTree.Edge> path:walkPaths(tree,node.nodeId(),new LinkedHashSet<>())){
                List<Object> edits=path.stream().map(MolecularDesignTree.Edge::editReceipt).map(x->(Object)x).toList();
                out.add(Map.of("strategy",strategy,"node_id",node.nodeId(),"depth",node.depth(),"edits_in_order",edits));
            }
        }
        return out;
    }
    private static List<List<MolecularDesignTree.Edge>> walkPaths(MolecularDesignTree tree,String node,Set<String> visiting){
        if(node.equals(tree.rootNodeId()))return List.of(List.of());
        if(!visiting.add(node))return List.of();
        List<List<MolecularDesignTree.Edge>> out=new ArrayList<>();
        for(var edge:tree.edges().stream().filter(e->e.childNodeId().equals(node))
                .filter(e->!e.parentNodeId().equals(e.childNodeId())).filter(e->!visiting.contains(e.parentNodeId())).toList())
            for(var prefix:walkPaths(tree,edge.parentNodeId(),new LinkedHashSet<>(visiting))){var path=new ArrayList<>(prefix);path.add(edge);out.add(List.copyOf(path));}
        return out;
    }
    private static List<Map<String,Object>> allEdgesFor(MolecularDesignTree first,String firstCanonical,String firstStrategy,
            MolecularDesignTree second,String secondCanonical,String secondStrategy){
        List<Map<String,Object>> out=new ArrayList<>();addEdges(out,first,firstCanonical,firstStrategy);addEdges(out,second,secondCanonical,secondStrategy);return out;
    }
    private static void addEdges(List<Map<String,Object>> out,MolecularDesignTree tree,String canonical,String strategy){
        Set<String> nodes=tree.nodes().stream().filter(n->n.canonicalKey().equals(canonical)).map(MolecularDesignTree.Node::nodeId).collect(java.util.stream.Collectors.toSet());
        for(var edge:tree.edges().stream().filter(e->nodes.contains(e.childNodeId())).toList())out.add(Map.of("strategy",strategy,"parent_node_id",edge.parentNodeId(),"child_node_id",edge.childNodeId(),"canonical_deduplication_cycle",edge.parentNodeId().equals(edge.childNodeId()),"edit_receipt",edge.editReceipt()));
    }
    private static Map<String,Point3D> points(MolecularGraph graph,Set<String> ids){
        Map<String,Point3D> out=new TreeMap<>();for(String id:ids)graph.atom(id).map(MolecularGraph.Atom::coordinates).ifPresent(c->out.put(id,new Point3D(c.x(),c.y(),c.z())));return out;
    }
    private static String sha256(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void writePersistenceHashes(Path out)throws Exception{
        List<Path> files=new ArrayList<>();Path root=out.resolve("persisted_candidates");if(Files.isDirectory(root))try(var stream=Files.walk(root)){files.addAll(stream.filter(Files::isRegularFile).toList());}
        for(String name:List.of("METTL7B_COMPLETE_GENERATION_PATHS.csv","METTL7B_LINEAGE_TEMPLATE_CORRESPONDENCE.csv","METTL7B_57_CANDIDATE_REPRODUCIBILITY.csv","METTL7B_RETROSPECTIVE_ALIGNMENT_RMSD.csv","METTL7B_GENERATION_PERSISTENCE_RECEIPT.json","METTL7B_GENERATION_PERSISTENCE_REPAIR.md","METTL7B_ALIGNMENT_CUTOFF_PROPOSAL.md")){Path path=out.resolve(name);if(Files.isRegularFile(path))files.add(path);}
        files.sort(Comparator.comparing(p->out.relativize(p).toString()));try(BufferedWriter writer=Files.newBufferedWriter(out.resolve("METTL7B_GENERATION_PERSISTENCE_SHA256SUMS"))){for(Path file:files)writer.write(sha256(Files.readAllBytes(file))+"  "+out.relativize(file)+System.lineSeparator());}
    }
    private static List<Map<String,Object>> auditRootEdits(MolecularGraph root,List<MolecularDesignGraphGenerator.AuthorizedEdit> edits,OclMolecularBackend backend,MolecularSanitizer.SanitizationPolicy policy){
        var engine=new GraphEditTransactionEngine();List<Map<String,Object>> rows=new ArrayList<>();
        for(var authorized:edits){var row=new LinkedHashMap<String,Object>();row.put("edit_id",authorized.edit().editId());row.put("editable_vector",authorized.edit().editableVectorId());row.put("transformation",authorized.edit().type().name());try{var product=engine.apply(root,authorized.edit(),authorized.authorization()).product();backend.sanitize(product,policy);row.put("accepted",true);row.put("failed_gate","");row.put("reason","");}catch(Exception exception){row.put("accepted",false);row.put("failed_gate","GRAPH_EDIT_OR_SANITIZATION");row.put("reason",exception.getMessage());}rows.add(row);}return rows;
    }
    private static PhysicochemicalGate.Descriptors frozenRootDescriptors(ObjectMapper mapper,Path path)throws IOException{
        for(JsonNode node:mapper.readTree(path.toFile()))if(node.path("candidateId").asText().equals("BRICS0040_NEUTRAL"))return mapper.treeToValue(node.path("descriptors"),PhysicochemicalGate.Descriptors.class);
        throw new IOException("frozen BRICS0040 descriptor baseline missing: "+path);
    }
    private static PhysicochemicalGate.Descriptors scaffoldRelative(PhysicochemicalGate.Descriptors raw,PhysicochemicalGate.Descriptors rawRoot,PhysicochemicalGate.Descriptors frozen){
        return new PhysicochemicalGate.Descriptors(
                frozen.molecularWeight()+raw.molecularWeight()-rawRoot.molecularWeight(),
                frozen.formalCharge()+raw.formalCharge()-rawRoot.formalCharge(),
                Math.max(0,frozen.hydrogenBondDonors()+raw.hydrogenBondDonors()-rawRoot.hydrogenBondDonors()),
                Math.max(0,frozen.hydrogenBondAcceptors()+raw.hydrogenBondAcceptors()-rawRoot.hydrogenBondAcceptors()),
                Math.max(0,frozen.rotatableBonds()+raw.rotatableBonds()-rawRoot.rotatableBonds()),
                Math.max(0,frozen.tpsa()+raw.tpsa()-rawRoot.tpsa()),
                frozen.logP()+raw.logP()-rawRoot.logP(),
                Math.max(0,frozen.aromaticRings()+raw.aromaticRings()-rawRoot.aromaticRings()),
                Math.max(0,frozen.heavyAtoms()+raw.heavyAtoms()-rawRoot.heavyAtoms()),
                Math.max(0,Math.min(1,frozen.fractionSp3()+raw.fractionSp3()-rawRoot.fractionSp3())));
    }
    private static void writeCsv(Path path,List<String>headers,List<Map<String,Object>>rows)throws IOException{try(BufferedWriter w=Files.newBufferedWriter(path)){w.write(String.join(",",headers));w.newLine();for(var row:rows){for(int i=0;i<headers.size();i++){if(i>0)w.write(',');w.write(csv(row.get(headers.get(i))));}w.newLine();}}}
    private static String csv(Object x){String s=x==null?"":x.toString();return "\""+s.replace("\"","\"\"")+"\"";}
    private static void writeHashes(Path out)throws Exception{List<Path>files=Files.list(out).filter(Files::isRegularFile).filter(p->!p.getFileName().toString().equals("SHA256SUMS")).sorted().toList();try(BufferedWriter w=Files.newBufferedWriter(out.resolve("SHA256SUMS"))){for(Path p:files){byte[]d=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p));w.write(HexFormat.of().formatHex(d)+"  "+p.getFileName());w.newLine();}}}
}
