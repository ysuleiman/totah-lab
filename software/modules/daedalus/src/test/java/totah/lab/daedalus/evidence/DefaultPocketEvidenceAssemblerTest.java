package totah.lab.daedalus.evidence;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.evidence.ComponentChemistryEvidence;
import totah.lab.athena.pocket.evidence.EvaluationStatus;
import totah.lab.athena.pocket.evidence.EvidenceChannel;
import totah.lab.athena.pocket.evidence.EvidenceMethod;
import totah.lab.athena.pocket.evidence.EvidenceOrigin;
import totah.lab.athena.pocket.evidence.EvidenceProvenance;
import totah.lab.athena.pocket.evidence.StructureEvidence;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.AlphaSphere;
import totah.lab.gaia.pocket.AlphaSphereSet;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketMetric;
import totah.lab.gaia.pocket.PocketMetricType;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.mmcif.BoundComponentAtom;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPocketEvidenceAssemblerTest {

    private static final EvidenceMethod FPOCKET = new EvidenceMethod("fpocket", "4.2.2");
    private static final EvidenceMethod EXTRACTION =
            new EvidenceMethod("hermes-athena-assembly", "1");

    @Test
    void mapsSourceTruthWithoutInventingRankContactsOrChemistry() {
        var evidence = new DefaultPocketEvidenceAssembler().assemble(request(
                EvidenceChannel.present(List.of(boundSam()),
                        EvidenceOrigin.SOURCE_OBSERVED, EXTRACTION)));

        assertThat(evidence.pocket().reportedRank().status())
                .isEqualTo(EvaluationStatus.NOT_EVALUATED);
        assertThat(((EvidenceChannel.Present<Double>)
                evidence.pocket().reportedScore()).value()).isEqualTo(12.5);
        assertThat(((EvidenceChannel.Present<List<
                totah.lab.athena.pocket.evidence.ResidueObservation>>)
                evidence.residueContext().pocketResidues()).value()).hasSize(1);
        var ligands = ((EvidenceChannel.Present<List<
                totah.lab.athena.pocket.evidence.LigandOccurrenceEvidence>>)
                evidence.ligandEvidence()).value();
        assertThat(ligands).hasSize(1);
        assertThat(ligands.getFirst().experimentalAtoms().getFirst().position())
                .isEqualTo(new Point3D(4, 5, 6));
        assertThat(ligands.getFirst().pocketRelationship().status())
                .isEqualTo(EvaluationStatus.NOT_EVALUATED);
        assertThat(ligands.getFirst().ccdChemistryReference().status())
                .isEqualTo(EvaluationStatus.NOT_APPLICABLE);
    }

    @Test
    void propagatesBoundComponentExtractionFailure() {
        var evidence = new DefaultPocketEvidenceAssembler().assemble(request(
                EvidenceChannel.failed("MMCIF_PARSE_FAILED", "malformed atom-site loop")));

        assertThat(evidence.ligandEvidence().status()).isEqualTo(EvaluationStatus.FAILED);
        var failed = (EvidenceChannel.Failed<List<
                totah.lab.athena.pocket.evidence.LigandOccurrenceEvidence>>)
                evidence.ligandEvidence();
        assertThat(failed.failureCode()).isEqualTo("MMCIF_PARSE_FAILED");
    }

    @Test
    void preservesEvaluatedNoLigandsAsEmptyRatherThanNotEvaluated() {
        var evidence = new DefaultPocketEvidenceAssembler().assemble(request(
                EvidenceChannel.empty(EvidenceOrigin.SOURCE_OBSERVED, EXTRACTION)));

        assertThat(evidence.ligandEvidence().status())
                .isEqualTo(EvaluationStatus.EMPTY);
    }

    private PocketEvidenceAssemblyRequest request(
            EvidenceChannel<List<BoundComponentOccurrence>> components) {
        return new PocketEvidenceAssemblyRequest(structureEvidence(), structure(), pocket(),
                components,
                EvidenceChannel.empty(
                        EvidenceOrigin.SOURCE_REPORTED, EXTRACTION),
                EvidenceChannel.notEvaluated("shape representation not computed"),
                FPOCKET, EXTRACTION,
                new EvidenceProvenance("RCSB", "1ABC", "2026-08-08", EXTRACTION,
                        Instant.parse("2026-08-08T00:00:00Z"), Map.of()));
    }

    private StructureEvidence structureEvidence() {
        return new StructureEvidence("1ABC", "RCSB", "A", 1, "1",
                StructureEvidence.StructureKind.EXPERIMENTAL, null,
                EvidenceChannel.present("X-RAY DIFFRACTION",
                        EvidenceOrigin.SOURCE_REPORTED, EXTRACTION),
                EvidenceChannel.present(1.7,
                        EvidenceOrigin.SOURCE_REPORTED, EXTRACTION),
                EvidenceChannel.notApplicable("experimental structure"));
    }

    private Structure structure() {
        Atom atom = Atom.builder().pdbSerial(1).name("CA")
                .position(new Point3D(1, 2, 3)).occupancy(1.0).bFactor(10.0)
                .charge(0.0).element(Element.C).build();
        return new Structure(List.of(new Chain("A",
                List.of(new Residue("ALA", 10, List.of(atom))))));
    }

    private Pocket pocket() {
        return new Pocket(PocketId.of(1), "Pocket 1", PocketSource.FPOCKET,
                new Point3D(1, 2, 3), List.of(new ResidueId("A", 10, null)),
                List.of(new PocketMetric(PocketMetricType.FPOCKET_SCORE, 12.5),
                        new PocketMetric(PocketMetricType.VOLUME, 450.0)),
                Optional.empty(), Optional.of(new AlphaSphereSet(List.of(
                        new AlphaSphere(1, new Point3D(1, 2, 3), 1.4)))), Map.of());
    }

    private BoundComponentOccurrence boundSam() {
        return new BoundComponentOccurrence("1ABC",
                BoundComponentOccurrence.SourceKind.ASSEMBLY, "1", 1,
                "SAM", "B", null, "B", "501",
                null,
                List.of(new BoundComponentAtom("C1", "C1", "C",
                        new Point3D(4, 5, 6), 1.0, 12.0, 0, null, "100")));
    }
}
