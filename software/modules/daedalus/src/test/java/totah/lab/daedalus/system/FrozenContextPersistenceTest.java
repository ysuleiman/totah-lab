package totah.lab.daedalus.system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.design.backend.ocl.OclMolecularBackend;
import totah.lab.athena.interaction.perception.FormalChargeAssignments;
import totah.lab.athena.system.*;
import totah.lab.gaia.graph.ResidueGraph;
import totah.lab.gaia.structure.*;
import totah.lab.hermes.file.pdbqt.PdbqtGaiaMapper;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;
import totah.lab.mnemosyne.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static totah.lab.mnemosyne.ScientificReference.Kind.*;

/** Full frozen coordinate context only; unavailable molecular chemistry stays unavailable. */
class FrozenContextPersistenceTest {
    private static final Path INPUT = Path.of(
            "software/modules/daedalus/src/test/resources/foundation-frozen-context");
    private static final Instant AT = Instant.parse("2026-10-06T00:00:00Z");
    @TempDir Path output;

    private static ScientificReference ref(ScientificReference.Kind kind, String id) {
        return new ScientificReference(kind, "foundation-frozen-context", id, "1");
    }

    @Test void fullContextSurvivesReadbackWithoutInventedChemicalCoverage() throws Exception {
        exercise(output);
    }

    private static byte[] exercise(Path output) throws Exception {
        var receptorBytes = Files.readAllBytes(INPUT.resolve("receptor.pdbqt"));
        var ligandBytes = Files.readAllBytes(INPUT.resolve("ligand.pdbqt"));
        assertEquals("896c058534c2099f2e12a2356da671cfeb40e7ec9d6a0994f72a5b43371c780d",
                EvidenceExchange.sha256(receptorBytes));
        assertEquals("c4fb304d4da17590c05ccf9bea0281f50d71c4682718c40a66c3dee02f7eaa19",
                EvidenceExchange.sha256(ligandBytes));
        var reader = new PdbqtReader();
        var receptor = PdbqtGaiaMapper.toStructure(reader.read(INPUT.resolve("receptor.pdbqt")));
        var ligand = PdbqtGaiaMapper.toStructure(reader.read(INPUT.resolve("ligand.pdbqt")));
        var ligandResidues = ligand.getChains().stream().flatMap(c -> c.residues().stream()).toList();
        assertEquals(1, ligandResidues.size());
        var chains = new ArrayList<>(receptor.getChains());
        chains.add(new Chain("FROZEN_LIGAND", ligandResidues));
        var graph = ResidueGraph.from(new Structure(chains));
        var cofactors = new HashSet<ResidueId>();
        for (var chain : receptor.getChains()) for (var residue : chain.residues()) {
            if (residue.getName().equals("SAM")) {
                cofactors.add(new ResidueId(chain.id(), residue.getNumber(), residue.getInsertionCode()));
            }
        }
        var state = new SystemStateView(ref(CONTEXT, "1158590-complete-frame"), graph,
                List.of(), List.of(ref(SOURCE, EvidenceExchange.sha256(receptorBytes)),
                ref(SOURCE, EvidenceExchange.sha256(ligandBytes))), cofactors,
                FormalChargeAssignments.EMPTY, true, false,
                List.of("Exact source frame; source chemistry/protonation not established by PDBQT labels",
                        "Engineering acceptance only; no scientific or binding interpretation"));
        int expected = chains.stream().flatMap(c -> c.residues().stream())
                .mapToInt(r -> r.getAtoms().size()).sum();
        assertEquals(expected, state.atoms().size());
        assertEquals(49, graph.view(cofactors).toStructure().getChains().stream()
                .flatMap(c -> c.residues().stream()).mapToInt(r -> r.getAtoms().size()).sum());
        byte[] snapshot = SystemStateView.bytes(state.snapshot());
        var backend = new OclMolecularBackend();
        var pipeline = new SystemQualificationPipeline(new SystemGraphValidation(backend, backend, backend));
        var catalog = new EvidenceSnapshotCatalog(output);
        var activity = ref(ACTIVITY, "frozen-readback");
        var evidence = List.of(
                SystemQualificationPipeline.envelope(activity, "receptor", "source:pdbqt", receptorBytes,
                        ref(METHOD, "source-import"), state.subject(), AT, List.of("unmodified bytes")),
                SystemQualificationPipeline.envelope(activity, "ligand", "source:pdbqt", ligandBytes,
                        ref(METHOD, "source-import"), state.subject(), AT, List.of("unmodified bytes")));
        var residue = ligandResidues.getFirst();
        var selection = Set.of(new ResidueId("FROZEN_LIGAND", residue.getNumber(), residue.getInsertionCode()));
        var result = pipeline.run(catalog, Optional.empty(), state, evidence, Map.of(),
                List.of(ExistingSystemAnalyzers.interactions(selection)), activity, AT);
        assertEquals(SystemGraphCertificate.Status.QUALIFIED,
                result.certificate().capabilities().get(SystemGraphCertificate.Capability.DISTANCE_QUERIES).status());
        assertNotEquals(SystemGraphCertificate.Status.QUALIFIED,
                result.certificate().capabilities().get(SystemGraphCertificate.Capability.HBOND_ANALYSIS).status());
        var stored = catalog.read(result.catalogSnapshot()).orElseThrow().history();
        for (var input : evidence) assertTrue(stored.envelopes().containsValue(input));
        assertArrayEquals(snapshot, SystemStateView.bytes(state.snapshot()));
        assertTrue(stored.interpretations().values().stream()
                .anyMatch(i -> i.status() == EvidenceInterpretation.Status.UNKNOWN_INCONCLUSIVE));
        return SystemStateView.bytes(result.certificate());
    }

    public static void main(String[] args) throws Exception {
        Files.write(Path.of(args[1]), exercise(Path.of(args[0])));
    }
}
