package totah.lab.mettl7.recognition;

import totah.lab.athena.interaction.InteractionProfile;
import totah.lab.athena.interaction.InteractionProfiler;
import totah.lab.athena.interaction.InteractionType;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.backend.ocl.OclLigandFeaturePerceiver;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.design.backend.ocl.OclStableAtomOrbitService;
import totah.lab.athena.recognition.EvidenceQuality;
import totah.lab.athena.recognition.InteractionStableFeatureAssigner;
import totah.lab.athena.recognition.RecognitionEdgeAssigner;
import totah.lab.athena.recognition.RecognitionEdgeAssignmentPolicy;
import totah.lab.athena.recognition.RecognitionStateObservation;
import totah.lab.athena.recognition.RecognitionTopologyDistance;
import totah.lab.athena.recognition.FixedFrameHeavyAtomGeometryDistance;
import totah.lab.athena.recognition.StableLigandFeatureMap;
import totah.lab.athena.recognition.StableLigandFeatureMapper;
import totah.lab.athena.surface.differential.ExplicitResidueCorrespondence;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.hermes.file.pdbqt.PdbqtFile;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.hermes.file.pdbqt.meeko.CanonicalSdfMeekoAtomMapper;
import totah.lab.hermes.file.sdf.SdfLigand;
import totah.lab.hermes.file.sdf.reader.SdfLigandReader;
import totah.lab.mettl7.campaign.v2.Mettl7FrozenPoseLigand;
import totah.lab.mettl7.surface.Mettl7FrozenDifferentialSurfaceLoader;
import totah.lab.mettl7.topology.Mettl7SamTopologyRestorer;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.DoubleSummaryStatistics;

/**
 * Bounded METTL7 recognition-state batch execution plumbing.
 *
 * <p>This class deliberately stops at any missing canonical scientific owner.
 * In particular, historical Meeko {@code SMILES IDX} maps are not themselves
 * a proven correspondence to a separately supplied canonical SDF.</p>
 */
public final class Mettl7RecognitionBatchMaterializer {
    public static final String VERSION = "METTL7_RECOGNITION_BATCH_MATERIALIZER_V1";
    public static final String BLOCKER =
            "JAVA_CAPABILITY_GAP_INTERACTION_TO_STABLE_FEATURE_ASSIGNMENT";

    private Mettl7RecognitionBatchMaterializer() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            throw new IllegalArgumentException("usage: <repository-root> <output-directory>");
        }
        run(Path.of(args[0]), Path.of(args[1]));
    }

    public static Summary run(Path repositoryRoot, Path outputDirectory) throws IOException {
        return runWithEvidence(repositoryRoot, outputDirectory).summary();
    }

    public static EvidenceSummary runWithEvidence(Path repositoryRoot, Path outputDirectory) throws IOException {
        return runSources(repositoryRoot, outputDirectory, sources(repositoryRoot.toAbsolutePath().normalize()));
    }

    public static EvidenceSummary runWithEvidence(Path repositoryRoot, Path outputDirectory,
            List<ManifestSource> manifestSources) throws IOException {
        return runSources(repositoryRoot,outputDirectory,manifestSources.stream().map(ManifestSource::toSource).toList());
    }

    private static EvidenceSummary runSources(Path repositoryRoot, Path outputDirectory,
            List<Source> sources) throws IOException {
        Path root = repositoryRoot.toAbsolutePath().normalize();
        Path output = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(output);

        int expectedPoseCount=sources.stream().mapToInt(Source::expectedModels).sum();

        PdbqtReader pdbqtReader = new PdbqtReader();
        SdfLigandReader sdfReader = new SdfLigandReader();
        InteractionProfiler profiler = new InteractionProfiler();
        CanonicalSdfMeekoAtomMapper atomMapper = new CanonicalSdfMeekoAtomMapper();
        InteractionStableFeatureAssigner featureAssigner = new InteractionStableFeatureAssigner();
        Mettl7RecognitionStateAdapter stateAdapter = new Mettl7RecognitionStateAdapter();
        Mettl7FrozenDifferentialSurfaceLoader surfaceLoader=new Mettl7FrozenDifferentialSurfaceLoader();
        var surfaceA=surfaceLoader.load(root,"A");var surfaceB=surfaceLoader.load(root,"B");
        List<Outcome> outcomes = new ArrayList<>(expectedPoseCount);
        List<MaterializedEvidence> materialized = new ArrayList<>(expectedPoseCount);
        List<HalogenAssignmentAudit> halogenAudits = new ArrayList<>();
        for (Source source : sources) {
            PdbqtFile poseFile;
            try {
                poseFile = pdbqtReader.read(source.poseFile());
            } catch (IOException | RuntimeException exception) {
                addFileFailure(outcomes, source, "REJECTED", "POSE_PARSE_FAILED: " + message(exception));
                continue;
            }
            if (poseFile.models().size() != source.expectedModels()) {
                addFileFailure(outcomes, source, "REJECTED", "MODEL_COUNT_MISMATCH: expected "
                        + source.expectedModels() + " observed " + poseFile.models().size());
                continue;
            }

            SdfLigand sdf;
            Structure receptor;
            try {
                sdf = sdfReader.readModel(source.ligandSdf());
                receptor = loadReceptor(root, source);
            } catch (IOException | RuntimeException exception) {
                addFileFailure(outcomes, source, "REJECTED", "CANONICAL_INPUT_FAILED: " + message(exception));
                continue;
            }
            StableContext stable;
            try { stable = stableContext(source.ligandId()!=null?source.ligandId():stableLigandId(source.arm()), sdf); }
            catch (RuntimeException exception) {
                addFileFailure(outcomes, source, "REJECTED", "STABLE_FEATURE_PERCEPTION_FAILED: "+message(exception)); continue;
            }

            for (PdbqtModel model : poseFile.models()) {
                String poseId = source.arm() + ":" + source.seed() + ":model-" + model.modelNumber();
                try {
                    Mapping mapping = source.preparedLigand()!=null
                            ? preparedLigandMapping(source,sdf,model,pdbqtReader,atomMapper)
                            : source.arm().startsWith("NETARSUDIL")
                            ? netarsudilMapping(root, source, sdf, model, pdbqtReader)
                            : dcmbMapping(atomMapper.map(source.ligandSdf(), source.poseFile(), model));
                    if (!mapping.successful()) {
                        outcomes.add(new Outcome(poseId, source.arm(), source.seed(), model.modelNumber(),
                                source.poseFile(), sha256(source.poseFile()), "UNMAPPABLE",
                                mapping.status()+": "+mapping.detail(),0,sdf.atomCount(),0,false,mapping.hash(),"NOT_CREATED"));
                        continue;
                    }
                    Mettl7FrozenPoseLigand ligand=frozenPose(sdf,model,mapping.poseSerialToSdfIndex());
                    EnvironmentParts environment=separateSam(receptor);
                    InteractionProfile completeProfile=environment.cofactor().isEmpty()
                            ?profiler.profile(environment.protein(),ligand.structure(),ligand.formalCharges())
                            :profiler.profile(environment.protein(),ligand.structure(),environment.cofactor(),ligand.formalCharges());
                    InteractionProfile profile=proteinRecognitionProfile(completeProfile);
                    InteractionProfile cofactorProfile=cofactorProfile(completeProfile);
                    Map<Integer,String> assigned=new LinkedHashMap<>(); Map<Integer,EvidenceQuality> qualities=new LinkedHashMap<>();
                    List<String> ambiguities=new ArrayList<>(); boolean unavailable=false;
                    for(int i=0;i<profile.interactions().size();i++){
                        var interaction=profile.interactions().get(i); Map<Integer,String> participating=new LinkedHashMap<>();
                        for(Atom atom:interaction.ligandAtoms()) if(atom.isHeavyAtom()){
                            Integer sdfIndex=mapping.poseSerialToSdfIndex().get(atom.getPdbSerial());
                            if(sdfIndex!=null) participating.put(atom.getPdbSerial(),stable.orbits().get("sdf:"+sdfIndex));
                        }
                        EvidenceQuality q=EvidenceQuality.ADEQUATE;
                        var assignment=featureAssigner.assign(interaction,participating,stable.features(),q); qualities.put(i,q);
                        if (interaction.type() == InteractionType.HALOGEN_BOND) {
                            Set<String> legacyParticipants = new LinkedHashSet<>(participating.values());
                            var legacyAssignment = featureAssigner.assign(interaction, legacyParticipants,
                                    stable.features(), q);
                            if (!legacyAssignment.assigned() && assignment.assigned()) {
                                halogenAudits.add(new HalogenAssignmentAudit(poseId, source.arm(), source.seed(),
                                        model.modelNumber(), atomEvidence(interaction.ligandAtoms()),
                                        atomEvidence(interaction.proteinAtoms()), interaction.residue().toString(),
                                        stable.features().features().stream()
                                                .filter(f -> f.type() == totah.lab.athena.design.feature.LigandFeature.Type.HALOGEN)
                                                .map(f -> f.stableId()+":"+f.canonicalAtomOrbits()).sorted().toList(),
                                        legacyParticipants, legacyAssignment.status().name(), assignment.status().name(),
                                        "ATOM_TO_FEATURE_PARTICIPANT_ROLE: legacy assignment included donor carbon; "
                                                + "canonical Interaction contract identifies ligandAtoms=[halogen, donor carbon]"));
                            }
                        }
                        if(assignment.assigned()) assigned.put(i,assignment.stableFeatureId().orElseThrow());
                        else {unavailable=true;ambiguities.add(i+":"+interaction.type()+":"+assignment.status()
                                +":atom_orbits="+participating+":candidates="+assignment.candidateFeatureIds());}
                    }
                    if(unavailable){outcomes.add(new Outcome(poseId,source.arm(),source.seed(),model.modelNumber(),source.poseFile(),sha256(source.poseFile()),"PENDING_EXTERNAL_EVIDENCE","FEATURE_ASSIGNMENT_UNAVAILABLE: "+ambiguities,mapping.poseSerialToSdfIndex().size(),sdf.atomCount(),profile.interactions().size(),profile.anyPerceptionDegraded(),mapping.hash(),"NOT_CREATED"));continue;}
                    String stableLigandId=source.ligandId()!=null?source.ligandId():stableLigandId(source.arm());
                    var receipt=Mettl7RecognitionStateAdapter.PoseFeatureReceipt.create(stableLigandId,poseId,sha256(source.ligandSdf()),stable.idCode(),mapping.poseSerialToSdfIndex(),model.atoms().size(),sdf.atomCount(),assigned,stable.features(),ambiguities);
                    Map<ResidueId,ResidueId> identity=new LinkedHashMap<>(); profile.interactions().forEach(x->identity.put(x.residue(),x.residue()));
                    EvidenceQuality overall=profile.anyPerceptionDegraded()?EvidenceQuality.DEGRADED:EvidenceQuality.ADEQUATE;
                    String paralog=paralog(source);
                    var surface=paralog.equals("A")?surfaceA:surfaceB;
                    var adapted=stateAdapter.adapt(new Mettl7RecognitionStateAdapter.Input(
                            new Mettl7RecognitionStateAdapter.Artifact(poseId,source.poseFile(),sha256(source.poseFile())),
                            new Mettl7RecognitionStateAdapter.Artifact(paralog,source.receptorFile(),sha256(source.receptorFile())),
                            new Mettl7RecognitionStateAdapter.Artifact(stableLigandId,source.ligandSdf(),sha256(source.ligandSdf())),Optional.empty(),
                            stableLigandId,paralog,poseId,source.seed(),source.poseFile().getFileName().toString(),Optional.empty(),
                            "FROZEN_RECEPTOR_SAM",profile,"ATHENA_INTERACTION_PROFILER",stable.features(),receipt,
                            new ExplicitResidueCorrespondence(identity),Optional.of(surface.map()),
                            "FIXED_RECEPTOR_FRAME;SURFDIFF:"+surface.direction()+":"+surface.sourceSha256(),overall,qualities,List.of(),
                            List.of(mapping.detail(),"SURFDIFF:"+surface.direction()+":"+surface.sourceSha256())));
                    if(!adapted.admitted()){outcomes.add(new Outcome(poseId,source.arm(),source.seed(),model.modelNumber(),source.poseFile(),sha256(source.poseFile()),"REJECTED",adapted.rejections().toString(),mapping.poseSerialToSdfIndex().size(),sdf.atomCount(),profile.interactions().size(),profile.anyPerceptionDegraded(),receipt.receiptSha256(),"NOT_CREATED"));continue;}
                    String admitted=overall==EvidenceQuality.DEGRADED?"ADMITTED_DEGRADED":"ADMITTED_ADEQUATE";
                    materialized.add(new MaterializedEvidence(source.arm(), source.seed(), model.modelNumber(),
                            adapted.accepted().orElseThrow().observation(), heavyCoordinates(sdf, model,
                            mapping.poseSerialToSdfIndex(), mapping.hash()),cofactorProfile,surface.sourceSha256()));
                    outcomes.add(new Outcome(poseId, source.arm(), source.seed(), model.modelNumber(),
                            source.poseFile(),sha256(source.poseFile()),admitted,"ADMITTED",mapping.poseSerialToSdfIndex().size(),sdf.atomCount(),profile.interactions().size(),profile.anyPerceptionDegraded(),receipt.receiptSha256(),adapted.accepted().orElseThrow().observation().poseId()));
                } catch (IOException exception) {
                    outcomes.add(new Outcome(poseId, source.arm(), source.seed(), model.modelNumber(),
                            source.poseFile(), sha256(source.poseFile()), "UNMAPPABLE",
                            BLOCKER + ": " + message(exception),
                            0, sdf.atomCount(), 0, false, "NOT_CREATED", "NOT_CREATED"));
                } catch (RuntimeException exception) {
                    outcomes.add(new Outcome(poseId, source.arm(), source.seed(), model.modelNumber(),
                            source.poseFile(), sha256(source.poseFile()), "REJECTED", message(exception),
                            0, sdf.atomCount(), 0, false, "NOT_CREATED", "NOT_CREATED"));
                }
            }
        }
        outcomes.sort(Comparator.comparing(Outcome::poseId));
        validateAccounting(outcomes,expectedPoseCount);
        Path accounting = output.resolve("MATERIALIZATION_OUTCOMES.csv");
        writeAccounting(accounting, outcomes);
        Path halogenAudit = output.resolve("DCMB_HALOGEN_ASSIGNMENT_AUDIT.csv");
        writeHalogenAudit(halogenAudit, halogenAudits);
        writeDiagnostics(output, materialized);
        Path receipt = output.resolve("BATCH_RECEIPT.txt");
        Files.writeString(receipt, String.join("\n",
                "version=" + VERSION,
                "raw_pose_count=" + outcomes.size(),
                "outcome_counts=" + outcomes.stream().collect(java.util.stream.Collectors.groupingBy(
                        Outcome::status, java.util.TreeMap::new, java.util.stream.Collectors.counting())),
                "arm_outcome_counts=" + outcomes.stream().collect(java.util.stream.Collectors.groupingBy(
                        Outcome::arm, java.util.TreeMap::new, java.util.stream.Collectors.groupingBy(
                                Outcome::status, java.util.TreeMap::new, java.util.stream.Collectors.counting()))),
                "accounting_sha256=" + sha256(accounting),
                "halogen_assignment_audit_pose_count=" + halogenAudits.stream()
                        .map(HalogenAssignmentAudit::poseId).distinct().count(),
                "halogen_assignment_audit_interaction_count=" + halogenAudits.size(),
                "halogen_assignment_audit_sha256=" + sha256(halogenAudit),
                "pairwise_diagnostics_sha256=" + sha256(output.resolve("PAIRWISE_DIAGNOSTICS.csv")),
                "diagnostic_summary_sha256=" + sha256(output.resolve("DIAGNOSTIC_SUMMARY.txt")),
                "blockers=" + outcomes.stream().map(Outcome::reason).distinct().sorted().toList(),
                "scientific_definitions_added=false") + "\n", StandardCharsets.UTF_8);
        return new EvidenceSummary(new Summary(outcomes, accounting, receipt), materialized);
    }

    private static Mapping preparedLigandMapping(Source source,SdfLigand sdf,PdbqtModel pose,
            PdbqtReader reader,CanonicalSdfMeekoAtomMapper mapper)throws IOException{
        PdbqtModel prepared=reader.read(source.preparedLigand()).firstModel();
        var receipt=mapper.map(source.ligandSdf(),source.preparedLigand(),prepared);
        Mapping canonical=receipt.status()==CanonicalSdfMeekoAtomMapper.Status.TOPOLOGY_ABSENT
                ?coordinateElementMapping(source.preparedLigand(),sdf,prepared):dcmbMapping(receipt);
        if(!canonical.successful())return canonical;
        if(pose.atoms().size()!=prepared.atoms().size())return new Mapping(false,Map.of(),canonical.hash(),
                "POSE_ORDER_MISMATCH","atom count");
        for(int i=0;i<prepared.atoms().size();i++){
            var a=prepared.atoms().get(i);var b=pose.atoms().get(i);
            if(a.serial()!=b.serial()||!a.element().equals(b.element())||!a.autodockType().equals(b.autodockType()))
                return new Mapping(false,Map.of(),canonical.hash(),"POSE_ORDER_MISMATCH","serial/type at "+i);
        }
        return new Mapping(true,canonical.poseSerialToSdfIndex(),canonical.hash(),canonical.status(),
                canonical.detail()+"; prepared-to-pose serial/element/type equality");
    }

    private static Mapping coordinateElementMapping(Path preparedPath,SdfLigand sdf,PdbqtModel prepared)throws IOException{
        List<Atom>sdfAtoms=sdf.ligand().structure().getChains().getFirst().residues().getFirst().getAtoms();
        Map<Integer,Integer> map=new LinkedHashMap<>();Set<Integer> used=new LinkedHashSet<>();
        for(var p:prepared.atoms()){int best=-1;double bd=Double.POSITIVE_INFINITY;for(int i=0;i<sdfAtoms.size();i++){
            Atom a=sdfAtoms.get(i);if(used.contains(i)||!a.getElement().symbol().equalsIgnoreCase(p.element()))continue;
            double d=a.getPosition().distance(p.position());if(d<bd){bd=d;best=i;}}
            if(best<0||bd>0.002)return new Mapping(false,Map.of(),sha256(preparedPath),
                    "PREPARED_SDF_CORRESPONDENCE_FAILED","coordinate/element mismatch "+p.serial());
            used.add(best);map.put(p.serial(),best);}
        return new Mapping(true,map,Mettl7RecognitionStateAdapter.mappingHash(map),"UNIQUE_MAPPING",
                "existing prepared/SDF coordinate-element bijection (0.002 A)" );
    }

    private static Structure loadReceptor(Path root, Source source) throws IOException {
        String paralog = paralog(source);
        Path canonical = root.resolve("software/modules/daedalus/src/test/resources/ligand/SAM.sdf");
        Path preparedSam = root.resolve("analysis/dcmb/controlled_campaign/prepared/7" + paralog + "_SAM.sdf");
        Path proteinTopology = "A".equals(paralog)
                ? root.resolve("analysis/mettl7-netarsudil-autodock4-matched-rigid-2026-09-10/results/"
                        + "topology_complete_athena/METTL7A_SAM_TOPOLOGY_COMPLETE_EXACT_AD4_COORDS.pdb")
                : root.resolve("software/modules/athena/src/test/resources/mettl7-v2-regression/netarsudil/"
                        + "METTL7B_SAM_TOPOLOGY_COMPLETE_EXACT_COORDS.pdb");
        return new Mettl7SamTopologyRestorer().restore(
                canonical, preparedSam, source.receptorFile(), proteinTopology).structure();
    }

    private static String paralog(Source source){
        if(source.preparedLigand()!=null)return source.arm().endsWith("_A")?"A":"B";
        return source.arm().contains("_A")?"A":"B";
    }

    private static StableContext stableContext(String ligandId,SdfLigand sdf){
        List<Atom> atoms=sdf.ligand().structure().getChains().getFirst().residues().getFirst().getAtoms();
        Set<Integer> aromatic=new LinkedHashSet<>(); sdf.bonds().stream().filter(b->b.aromatic()||b.order()==totah.lab.gaia.chemistry.BondOrder.AROMATIC).forEach(b->{aromatic.add(b.atomIndexA());aromatic.add(b.atomIndexB());});
        List<MolecularGraph.Atom> graphAtoms=new ArrayList<>();
        for(int i=0;i<atoms.size();i++){Atom a=atoms.get(i);graphAtoms.add(new MolecularGraph.Atom("sdf:"+i,a.getElement().symbol(),null,sdf.formalCharges().get(i),0,aromatic.contains(i),"UNSPECIFIED",new MolecularGraph.Coordinates(a.getPosition().x(),a.getPosition().y(),a.getPosition().z()),Map.of()));}
        List<MolecularGraph.Bond> bonds=new ArrayList<>();int bi=0;for(var b:sdf.bonds()){var order=switch(b.order()){case SINGLE->MolecularGraph.BondOrder.SINGLE;case DOUBLE->MolecularGraph.BondOrder.DOUBLE;case TRIPLE->MolecularGraph.BondOrder.TRIPLE;case AROMATIC->MolecularGraph.BondOrder.AROMATIC;case UNKNOWN->throw new IllegalArgumentException("unknown SDF bond");};bonds.add(new MolecularGraph.Bond("b"+(++bi),"sdf:"+b.atomIndexA(),"sdf:"+b.atomIndexB(),order,b.aromatic(),"UNSPECIFIED",Map.of()));}
        MolecularGraph graph=new MolecularGraph(graphAtoms,bonds,Map.of());
        try{var orbits=new OclStableAtomOrbitService().canonicalOrbits(graph);var perceived=new OclLigandFeaturePerceiver().perceive(graph);var features=new StableLigandFeatureMapper().map(ligandId,perceived.features(),orbits.sourceAtomToCanonicalOrbit(),orbits.provenance(),perceived.backend()+" "+perceived.version(),EvidenceQuality.ADEQUATE,Map.of(),List.of());String id=new OclMolecularBackend().identify(graph).canonicalKey();return new StableContext(features,orbits.sourceAtomToCanonicalOrbit(),id);}catch(Exception e){Throwable root=e;while(root.getCause()!=null)root=root.getCause();throw new IllegalArgumentException(root.getClass().getSimpleName()+": "+root.getMessage(),e);}
    }
    private static Mapping dcmbMapping(CanonicalSdfMeekoAtomMapper.Receipt receipt){
        if(!receipt.successful())return new Mapping(false,Map.of(),receipt.mappingSha256(),receipt.status().name(),receipt.detail());
        Map<Integer,Integer> result=new LinkedHashMap<>();Set<Integer> used=new LinkedHashSet<>();
        for(var e:receipt.meekoSerialToSdfIndexOrOrbit().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()){
            Integer chosen=e.getValue().stream().sorted().filter(i->!used.contains(i)).findFirst().orElse(null);
            if(chosen==null)return new Mapping(false,Map.of(),receipt.mappingSha256(),"AMBIGUOUS_NON_EQUIVALENT_MAPPING","orbit cannot be represented bijectively");used.add(chosen);result.put(e.getKey(),chosen);
        }return new Mapping(true,result,receipt.mappingSha256(),receipt.status().name(),receipt.detail());
    }
    private static Mapping netarsudilMapping(Path root,Source source,SdfLigand sdf,PdbqtModel pose,PdbqtReader reader)throws IOException{
        Path prepared=root.resolve("research/mettl7-netarsudil-sam-mechanism/vina-matched/prepared/netarsudil_neutral.pdbqt");
        if(!sha256(prepared).equals("4c35ff1aebb273aa8bfca60311bd4017b93b7dacc27b57d40e3e2cfc3b00939c"))return new Mapping(false,Map.of(),sha256(prepared),"PROVENANCE_HASH_MISMATCH","prepared ligand hash");
        PdbqtModel prep=reader.read(prepared).firstModel();Mapping preparedMapping=coordinateElementMapping(prepared,sdf,prep);
        if(!preparedMapping.successful())return preparedMapping;Map<Integer,Integer> map=preparedMapping.poseSerialToSdfIndex();
        if(pose.atoms().size()!=prep.atoms().size())return new Mapping(false,Map.of(),sha256(prepared),"POSE_ORDER_MISMATCH","atom count");
        for(int i=0;i<prep.atoms().size();i++){var a=prep.atoms().get(i);var b=pose.atoms().get(i);if(a.serial()!=b.serial()||!a.element().equals(b.element())||!a.autodockType().equals(b.autodockType()))return new Mapping(false,Map.of(),sha256(prepared),"POSE_ORDER_MISMATCH","serial/type at "+i);}
        return new Mapping(true,map,Mettl7RecognitionStateAdapter.mappingHash(map),"UNIQUE_MAPPING","manifest ligand hash + prepared/SDF coordinate-element bijection + pose serial/type equality");
    }
    private static Mettl7FrozenPoseLigand frozenPose(SdfLigand sdf,PdbqtModel model,Map<Integer,Integer> poseToSdf)throws IOException{
        StringBuilder index=new StringBuilder("REMARK INDEX MAP");poseToSdf.entrySet().stream().sorted(Map.Entry.comparingByValue()).forEach(e->index.append(' ').append(e.getValue()+1).append(' ').append(e.getKey()));List<String> remarks=new ArrayList<>(model.remarks());remarks.add(index.toString());return Mettl7FrozenPoseLigand.reconstruct(sdf,new PdbqtModel(model.modelNumber(),model.atoms(),model.torsionTree(),remarks));
    }
    static String stableLigandId(String arm){
        if(arm.startsWith("NETARSUDIL"))return "NETARSUDIL";
        if(arm.endsWith("_R"))return "DCMB_R";
        if(arm.endsWith("_S"))return "DCMB_S";
        throw new IllegalArgumentException("unknown bounded arm "+arm);
    }
    private record StableContext(StableLigandFeatureMap features,Map<String,String>orbits,String idCode){}
    private record Mapping(boolean successful,Map<Integer,Integer>poseSerialToSdfIndex,String hash,String status,String detail){}
    public record MaterializedEvidence(String arm,String seed,int model,RecognitionStateObservation observation,
            FixedFrameHeavyAtomGeometryDistance.PoseGeometry geometry,InteractionProfile cofactorEvidence,
            String surfDiffSha256) { }

    private record EnvironmentParts(Structure protein,Structure cofactor){}
    private static EnvironmentParts separateSam(Structure structure){
        return new EnvironmentParts(subset(structure,false),subset(structure,true));
    }
    private static Structure subset(Structure structure,boolean sam){
        List<Chain> chains=new ArrayList<>();Set<AtomReference> retained=new LinkedHashSet<>();
        for(Chain chain:structure.getChains()){
            List<Residue> residues=chain.residues().stream().filter(r->("SAM".equalsIgnoreCase(r.getName()))==sam).toList();
            if(!residues.isEmpty()){chains.add(new Chain(chain.id(),residues));for(Residue r:residues)for(Atom a:r.getAtoms())retained.add(new AtomReference(chain.id(),r.getNumber(),r.getInsertionCode()==null?' ':r.getInsertionCode(),a.getName()));}
        }
        List<Bond>bonds=structure.bonds().stream().filter(b->retained.contains(b.atom1())&&retained.contains(b.atom2())).toList();
        return new Structure(chains,bonds,structure.getConnectivityMetadata());
    }
    private static InteractionProfile proteinRecognitionProfile(InteractionProfile complete){
        var interactions=complete.interactions().stream().filter(i->!complete.cofactorResidues().contains(i.residue()))
                .toList();
        var raw=complete.rawInteractions().stream().filter(i->!complete.cofactorResidues().contains(i.residue()))
                .toList();
        return new InteractionProfile(interactions,raw,Set.of(),complete.thresholds(),complete.perception());
    }
    private static InteractionProfile cofactorProfile(InteractionProfile complete){
        var interactions=complete.interactions().stream().filter(i->complete.cofactorResidues().contains(i.residue())).toList();
        var raw=complete.rawInteractions().stream().filter(i->complete.cofactorResidues().contains(i.residue())).toList();
        return new InteractionProfile(interactions,raw,complete.cofactorResidues(),complete.thresholds(),complete.perception());
    }

    private static FixedFrameHeavyAtomGeometryDistance.PoseGeometry heavyCoordinates(SdfLigand sdf,PdbqtModel model,
            Map<Integer,Integer> poseToSdf,String mappingHash) {
        List<Atom> sdfAtoms=sdf.ligand().structure().getChains().getFirst().residues().getFirst().getAtoms();
        Map<String,FixedFrameHeavyAtomGeometryDistance.PointAngstrom> result=new LinkedHashMap<>();
        for(var atom:model.atoms()){
            Integer index=poseToSdf.get(atom.serial());
            if(index!=null&&sdfAtoms.get(index).isHeavyAtom()) result.put("sdf:"+index,
                    new FixedFrameHeavyAtomGeometryDistance.PointAngstrom(atom.position().x(),atom.position().y(),atom.position().z()));
        }
        return new FixedFrameHeavyAtomGeometryDistance.PoseGeometry(result,mappingHash,
                "validated canonical SDF/PDBQT correspondence; fixed receptor frame; angstrom");
    }

    private static void writeDiagnostics(Path output,List<MaterializedEvidence> observations)throws IOException{
        Path pairs=output.resolve("PAIRWISE_DIAGNOSTICS.csv");
        Map<String,long[]> policyCounts=new LinkedHashMap<>();
        Map<String,PairAccumulator> aggregate=new LinkedHashMap<>();
        try(BufferedWriter writer=Files.newBufferedWriter(pairs,StandardCharsets.UTF_8)){
            writer.write(csv("arm","pose_1","pose_2","same_seed","fixed_frame_heavy_rmsd_A",
                    "typed_jaccard","one_minus_typed_jaccard","preserved","substituted","rerouted",
                    "unobserved","unavailable","feature_route_changes","residue_route_changes",
                    "ambiguous_assignments","policy_A_g1.5_t0.25","policy_A_g2.0_t0.40",
                    "policy_A_g2.5_t0.55","policy_B_g1.5_t0.25","policy_B_g2.0_t0.40",
                    "policy_B_g2.5_t0.55","policy_C_g1.5_t0.25","policy_C_g2.0_t0.40",
                    "policy_C_g2.5_t0.55","policy_D_status"));
            for(int i=0;i<observations.size();i++)for(int j=i+1;j<observations.size();j++){
                MaterializedEvidence a=observations.get(i),b=observations.get(j);if(!a.arm().equals(b.arm()))continue;
                double rmsd=rmsd(a.geometry(),b.geometry());
                RecognitionTopologyDistance d=topology(a.observation(),b.observation());
                double typedJaccard=typedJaccard(a.observation(),b.observation());
                PairAccumulator accumulator=aggregate.computeIfAbsent(a.arm(),ignored->new PairAccumulator());
                accumulator.add(rmsd,typedJaccard,d,!a.seed().equals(b.seed()));
                boolean[] gates=new boolean[9];double[] gs={1.5,2.0,2.5},ts={0.25,0.40,0.55};
                for(int k=0;k<3;k++){
                    boolean base=rmsd<=gs[k]&&(1.0-typedJaccard)<=ts[k];
                    gates[k]=base;gates[3+k]=base&&d.reroutedEdges()==0;
                    gates[6+k]=gates[3+k]&&d.ambiguousAssignments()==0
                            &&d.preservedEdges()==typedMultisetIntersection(a.observation(),b.observation());
                }
                writer.write(csv(a.arm(),a.observation().poseId(),b.observation().poseId(),
                        a.seed().equals(b.seed()),decimal(rmsd),decimal(typedJaccard),decimal(1.0-typedJaccard),
                        d.preservedEdges(),d.substitutedEdges(),d.reroutedEdges(),d.unobservedEdges(),
                        d.unavailableEdges(),d.ligandFeatureRouteChanges(),d.residueRouteChanges(),
                        d.ambiguousAssignments(),gates[0],gates[1],gates[2],gates[3],gates[4],gates[5],
                        gates[6],gates[7],gates[8],"UNAVAILABLE_NO_CANONICAL_DEFINING_FEATURES"));
                long[] count=policyCounts.computeIfAbsent(a.arm(),ignored->new long[10]);count[0]++;
                for(int k=0;k<9;k++)if(gates[k])count[k+1]++;
            }
        }
        Path summary=output.resolve("DIAGNOSTIC_SUMMARY.txt");
        List<String> lines=new ArrayList<>();lines.add("version="+VERSION);lines.add("observations="+observations.size());
        for(var e:policyCounts.entrySet())lines.add(e.getKey()+" pairs="+e.getValue()[0]
                +" policyA=["+e.getValue()[1]+","+e.getValue()[2]+","+e.getValue()[3]+"]"
                +" policyB=["+e.getValue()[4]+","+e.getValue()[5]+","+e.getValue()[6]+"]"
                +" policyC=["+e.getValue()[7]+","+e.getValue()[8]+","+e.getValue()[9]+"]"
                +" policyD=UNAVAILABLE_NO_CANONICAL_DEFINING_FEATURES");
        for(var e:aggregate.entrySet())lines.add(e.getKey()+" "+e.getValue().render());
        lines.add("historical_family_diagnostics=UNAVAILABLE_NO_FAMILY_PROVENANCE");
        lines.add("evaluated_arm_quality_counts=" + observations.stream().collect(java.util.stream.Collectors.groupingBy(
                MaterializedEvidence::arm, java.util.TreeMap::new, java.util.stream.Collectors.groupingBy(
                        e -> e.observation().quality(), java.util.TreeMap::new, java.util.stream.Collectors.counting()))));
        Files.write(summary,lines,StandardCharsets.UTF_8);
    }

    private static final class PairAccumulator{
        private long total,crossSeed,ambiguous;
        private final long[] geometryPass=new long[3],topologyPass=new long[3],
                topologyWithReroute=new long[3],topologyWithSubstitution=new long[3];
        private final DoubleSummaryStatistics rmsd=new DoubleSummaryStatistics(),
                topology=new DoubleSummaryStatistics();
        void add(double geometry,double typedJaccard,RecognitionTopologyDistance distance,boolean cross){
            total++;if(cross)crossSeed++;if(distance.ambiguousAssignments()>0)ambiguous++;
            rmsd.accept(geometry);double td=1-typedJaccard;topology.accept(td);
            double[] gs={1.5,2,2.5},ts={.25,.40,.55};
            for(int i=0;i<3;i++){if(geometry<=gs[i])geometryPass[i]++;if(td<=ts[i]){
                topologyPass[i]++;if(distance.reroutedEdges()>0)topologyWithReroute[i]++;
                if(distance.substitutedEdges()>0)topologyWithSubstitution[i]++;}}
        }
        String render(){return "cross_seed_pairs="+crossSeed+" rmsd[min="+decimal(rmsd.getMin())+",mean="
                +decimal(rmsd.getAverage())+",max="+decimal(rmsd.getMax())+"] topology_distance[min="+decimal(topology.getMin())
                +",mean="+decimal(topology.getAverage())+",max="+decimal(topology.getMax())+"] geometry_pass="
                +java.util.Arrays.toString(geometryPass)+" topology_pass="+java.util.Arrays.toString(topologyPass)
                +" topology_pass_with_reroute="+java.util.Arrays.toString(topologyWithReroute)
                +" topology_pass_with_substitution="+java.util.Arrays.toString(topologyWithSubstitution)
                +" ambiguous_pairs="+ambiguous+" total="+total;}
    }

    private static double rmsd(FixedFrameHeavyAtomGeometryDistance.PoseGeometry a,
            FixedFrameHeavyAtomGeometryDistance.PoseGeometry b){
        var first=a.canonicalHeavyAtoms();var second=b.canonicalHeavyAtoms();
        if(!first.keySet().equals(second.keySet())||first.isEmpty())throw new IllegalArgumentException("incomparable heavy-atom maps");
        double sum=0;for(String i:first.keySet()){var p=first.get(i);var q=second.get(i);sum+=square(p.x()-q.x())+square(p.y()-q.y())+square(p.z()-q.z());}
        return Math.sqrt(sum/first.size());
    }
    private static double square(double x){return x*x;}

    static double typedJaccard(RecognitionStateObservation a,RecognitionStateObservation b){
        Set<totah.lab.athena.recognition.RecognitionEdge.Key> first=adequateKeys(a),second=adequateKeys(b);
        Set<totah.lab.athena.recognition.RecognitionEdge.Key> union=new LinkedHashSet<>(first);union.addAll(second);
        if(union.isEmpty())return 1.0;Set<totah.lab.athena.recognition.RecognitionEdge.Key> intersection=new LinkedHashSet<>(first);
        intersection.retainAll(second);return (double)intersection.size()/union.size();
    }
    static int typedMultisetIntersection(RecognitionStateObservation a,RecognitionStateObservation b){
        Map<totah.lab.athena.recognition.RecognitionEdge.Key,Long> first=adequateKeyCounts(a),second=adequateKeyCounts(b);
        return first.entrySet().stream().mapToInt(e->(int)Math.min(e.getValue(),second.getOrDefault(e.getKey(),0L))).sum();
    }
    private static Map<totah.lab.athena.recognition.RecognitionEdge.Key,Long> adequateKeyCounts(RecognitionStateObservation o){
        return o.graph().edges().stream().filter(e->e.quality()==EvidenceQuality.ADEQUATE)
                .collect(java.util.stream.Collectors.groupingBy(totah.lab.athena.recognition.RecognitionEdge::key,
                        LinkedHashMap::new,java.util.stream.Collectors.counting()));
    }
    private static Set<totah.lab.athena.recognition.RecognitionEdge.Key> adequateKeys(RecognitionStateObservation o){
        return o.graph().edges().stream().filter(e->e.quality()==EvidenceQuality.ADEQUATE)
                .map(totah.lab.athena.recognition.RecognitionEdge::key)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
    static RecognitionTopologyDistance topology(RecognitionStateObservation a,
            RecognitionStateObservation b){
        Map<String,String> features=new LinkedHashMap<>();a.graph().edges().forEach(e->features.put(e.ligandFeature().id(),e.ligandFeature().id()));
        b.graph().edges().forEach(e->features.putIfAbsent(e.ligandFeature().id(),e.ligandFeature().id()));
        Map<ResidueId,ResidueId> residues=new LinkedHashMap<>();a.graph().edges().forEach(e->residues.put(e.environmentResidue(),e.environmentResidue()));
        b.graph().edges().forEach(e->residues.putIfAbsent(e.environmentResidue(),e.environmentResidue()));
        Map<InteractionType,RecognitionEdgeAssignmentPolicy.GeometryTolerance> tolerances=new LinkedHashMap<>();
        tolerances.put(InteractionType.HYDROGEN_BOND,new RecognitionEdgeAssignmentPolicy.GeometryTolerance(.5,20,180));
        tolerances.put(InteractionType.SALT_BRIDGE,new RecognitionEdgeAssignmentPolicy.GeometryTolerance(1,180,180));
        tolerances.put(InteractionType.HYDROPHOBIC_CONTACT,new RecognitionEdgeAssignmentPolicy.GeometryTolerance(.75,180,180));
        tolerances.put(InteractionType.PI_STACK_PARALLEL,new RecognitionEdgeAssignmentPolicy.GeometryTolerance(.75,20,180));
        tolerances.put(InteractionType.PI_STACK_T_SHAPED,new RecognitionEdgeAssignmentPolicy.GeometryTolerance(.75,20,180));
        tolerances.put(InteractionType.PI_CATION,new RecognitionEdgeAssignmentPolicy.GeometryTolerance(.75,30,180));
        tolerances.put(InteractionType.HALOGEN_BOND,new RecognitionEdgeAssignmentPolicy.GeometryTolerance(.5,20,20));
        var policy=new RecognitionEdgeAssignmentPolicy(features,tolerances,
                "METTL7_EVIDENCE_ADAPTER_AND_POLICY_PROPOSAL.md edge-assignment table");
        var assignment=new RecognitionEdgeAssigner().assign(a.graph(),b.graph(),new ExplicitResidueCorrespondence(residues),policy);
        return RecognitionTopologyDistance.from(assignment);
    }

    private static List<Source> sources(Path root) throws IOException {
        List<Source> result = new ArrayList<>();
        Path netRaw = root.resolve("research/mettl7-netarsudil-sam-mechanism/vina-matched/raw");
        Path netSdf = root.resolve("research/mettl7-netarsudil-sam-mechanism/vina-matched/prepared/netarsudil_CID66599893_neutral.sdf");
        Path netPrepared = root.resolve("research/mettl7-netarsudil-sam-mechanism/vina-matched/prepared");
        for (String paralog : List.of("A", "B")) {
            for (String seed : List.of("172904", "483271", "806519")) {
                result.add(new Source("NETARSUDIL_" + paralog, seed,
                        netRaw.resolve("7" + paralog + "_neutral_seed" + seed + ".pdbqt"), netSdf,
                        netPrepared.resolve("METTL7" + paralog + "_SAM_receptor.pdbqt"), 20,null,null));
            }
        }

        Path dcmb = root.resolve("analysis/dcmb/controlled_campaign");
        for (String paralog : List.of("A", "B")) {
            for (String enantiomer : List.of("R", "S")) {
                for (String seed : List.of("1", "7", "42")) {
                    int models = "B".equals(paralog) && "S".equals(enantiomer) && "1".equals(seed) ? 8 : 9;
                    result.add(new Source("DCMB_" + paralog + "_" + enantiomer, seed,
                            dcmb.resolve("raw/7" + paralog + "_WT_SAM_BOUND_" + enantiomer + "_s" + seed + ".pdbqt"),
                            root.resolve("research/mettl7-selectivity-forensics/dcmb-analog-program/sah-campaign-v1/ligands/DCMB_"
                                    + enantiomer + "_NEUTRAL.sdf"),
                            dcmb.resolve("prepared/7" + paralog + "_WT_SAM_BOUND.pdbqt"), models,null,null));
                }
            }
        }
        for (Source source : result) {
            requireFile(source.poseFile()); requireFile(source.ligandSdf()); requireFile(source.receptorFile());
        }
        return List.copyOf(result);
    }

    private static void addFileFailure(List<Outcome> outcomes, Source source, String status, String reason)
            throws IOException {
        for (int model = 1; model <= source.expectedModels(); model++) {
            outcomes.add(new Outcome(source.arm() + ":" + source.seed() + ":model-" + model,
                    source.arm(), source.seed(), model, source.poseFile(), sha256(source.poseFile()), status,
                    reason, 0, 0, 0, false, "NOT_CREATED", "NOT_CREATED"));
        }
    }

    private static void validateAccounting(List<Outcome> outcomes,int expected) throws IOException {
        if (outcomes.size() != expected) throw new IOException("expected "+expected+" outcomes, observed " + outcomes.size());
        Set<String> ids = new LinkedHashSet<>();
        for (Outcome outcome : outcomes) {
            if (!ids.add(outcome.poseId())) throw new IOException("duplicate pose outcome " + outcome.poseId());
            if (!Set.of("ADMITTED_ADEQUATE", "ADMITTED_DEGRADED", "REJECTED", "UNMAPPABLE",
                    "PENDING_EXTERNAL_EVIDENCE").contains(outcome.status())) {
                throw new IOException("invalid terminal status " + outcome.status());
            }
        }
    }

    private static void writeAccounting(Path path, List<Outcome> outcomes) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(csv("pose_id", "arm", "seed", "model", "source", "source_sha256", "outcome",
                    "reason", "mapped_atoms", "sdf_atoms", "interaction_count", "perception_degraded",
                    "feature_receipt", "recognition_observation"));
            for (Outcome o : outcomes) {
                writer.write(csv(o.poseId(), o.arm(), o.seed(), o.model(), o.source(), o.sourceSha256(),
                        o.status(), o.reason(), o.mappedAtoms(), o.sdfAtoms(), o.interactionCount(),
                        o.perceptionDegraded(), o.featureReceipt(), o.recognitionObservation()));
            }
        }
    }

    private static void writeHalogenAudit(Path path, List<HalogenAssignmentAudit> audits) throws IOException {
        audits.sort(Comparator.comparing(HalogenAssignmentAudit::poseId));
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(csv("pose_id", "arm", "seed", "model", "ligand_atoms", "protein_atoms",
                    "protein_residue", "existing_halogen_features", "legacy_participant_orbits",
                    "legacy_status", "repaired_status", "classified_failure_mode"));
            for (HalogenAssignmentAudit audit : audits) {
                writer.write(csv(audit.poseId(), audit.arm(), audit.seed(), audit.model(), audit.ligandAtoms(),
                        audit.proteinAtoms(), audit.proteinResidue(), audit.existingHalogenFeatures(),
                        audit.legacyParticipantOrbits(), audit.legacyStatus(), audit.repairedStatus(),
                        audit.classifiedFailureMode()));
            }
        }
    }

    private static List<String> atomEvidence(List<Atom> atoms) {
        return atoms.stream().map(atom -> atom.getPdbSerial()+":"+atom.getName()+":"+atom.getElement()).toList();
    }

    private static String csv(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) line.append(',');
            String value = Objects.toString(values[i], "");
            line.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        return line.append('\n').toString();
    }

    static String decimal(double value) {
        return String.format(Locale.ROOT, "%.12f", value);
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0; ) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void requireFile(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("missing bounded input " + path);
    }

    private static String message(Exception exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private record HalogenAssignmentAudit(String poseId, String arm, String seed, int model,
            List<String> ligandAtoms, List<String> proteinAtoms, String proteinResidue,
            List<String> existingHalogenFeatures, Set<String> legacyParticipantOrbits,
            String legacyStatus, String repairedStatus, String classifiedFailureMode) {
        private HalogenAssignmentAudit {
            ligandAtoms = List.copyOf(ligandAtoms);
            proteinAtoms = List.copyOf(proteinAtoms);
            existingHalogenFeatures = List.copyOf(existingHalogenFeatures);
            legacyParticipantOrbits = Set.copyOf(legacyParticipantOrbits);
        }
    }

    private record Source(String arm, String seed, Path poseFile, Path ligandSdf,
                          Path receptorFile, int expectedModels,Path preparedLigand,String ligandId) { }

    public record ManifestSource(String arm,String seed,Path poseFile,Path ligandSdf,Path receptorFile,
            int expectedModels,Path preparedLigand,String ligandId){
        public ManifestSource{Objects.requireNonNull(arm);Objects.requireNonNull(seed);Objects.requireNonNull(poseFile);
            Objects.requireNonNull(ligandSdf);Objects.requireNonNull(receptorFile);Objects.requireNonNull(preparedLigand);
            Objects.requireNonNull(ligandId);if(expectedModels<1)throw new IllegalArgumentException("expectedModels");}
        private Source toSource(){return new Source(arm,seed,poseFile,ligandSdf,receptorFile,expectedModels,preparedLigand,ligandId);}
    }

    public record Outcome(String poseId, String arm, String seed, int model, Path source,
                          String sourceSha256, String status, String reason, int mappedAtoms,
                          int sdfAtoms, int interactionCount, boolean perceptionDegraded,
                          String featureReceipt, String recognitionObservation) { }

    public record Summary(List<Outcome> outcomes, Path accounting, Path receipt) {
        public Summary { outcomes = List.copyOf(outcomes); }
    }
    public record EvidenceSummary(Summary summary,List<MaterializedEvidence> evidence){
        public EvidenceSummary{evidence=List.copyOf(evidence);}
    }
}
