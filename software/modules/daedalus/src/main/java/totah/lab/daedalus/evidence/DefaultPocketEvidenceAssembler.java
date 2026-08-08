package totah.lab.daedalus.evidence;

import totah.lab.athena.pocket.evidence.ComponentChemistryEvidence;
import totah.lab.athena.pocket.evidence.EvidenceAtomId;
import totah.lab.athena.pocket.evidence.EvidenceChannel;
import totah.lab.athena.pocket.evidence.EvidenceOrigin;
import totah.lab.athena.pocket.evidence.EvidenceResidueId;
import totah.lab.athena.pocket.evidence.LigandOccurrenceEvidence;
import totah.lab.athena.pocket.evidence.LigandOccurrenceId;
import totah.lab.athena.pocket.evidence.ObservedAtom;
import totah.lab.athena.pocket.evidence.PocketEvidence;
import totah.lab.athena.pocket.evidence.PocketGeometryEvidence;
import totah.lab.athena.pocket.evidence.ResidueContextEvidence;
import totah.lab.athena.pocket.evidence.ResidueObservation;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketMetric;
import totah.lab.gaia.pocket.PocketMetricType;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.AlternateLocationProvenance;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.hermes.file.mmcif.BoundComponentAtom;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Maps source truth without inventing unavailable ranks, contacts, or chemistry. */
public final class DefaultPocketEvidenceAssembler implements PocketEvidenceAssembler {

    private static final EnumSet<PocketMetricType> STANDALONE_METRICS =
            EnumSet.of(PocketMetricType.FPOCKET_SCORE, PocketMetricType.VOLUME);

    @Override
    public PocketEvidence assemble(PocketEvidenceAssemblyRequest request) {
        Pocket pocket = request.pocket();
        if (pocket.source() != PocketSource.FPOCKET) {
            throw new IllegalArgumentException(
                    "Pocket evidence assembler requires an fpocket source");
        }
        return new PocketEvidence(
                request.structureEvidence(),
                geometry(pocket, request),
                residues(pocket, request),
                ligands(request),
                request.chemistry(),
                request.provenance());
    }

    private PocketGeometryEvidence geometry(
            Pocket pocket, PocketEvidenceAssemblyRequest request) {
        EvidenceChannel<Integer> rank = EvidenceChannel.notEvaluated(
                "fpocket output did not provide an explicit rank field");
        EvidenceChannel<Double> score = metric(pocket, PocketMetricType.FPOCKET_SCORE,
                request.fpocketMethod(), "fpocket score was unavailable");
        EvidenceChannel<Double> volume = metric(pocket, PocketMetricType.VOLUME,
                request.fpocketMethod(), "fpocket volume was unavailable");
        Map<String, Double> descriptors = new LinkedHashMap<>();
        for (PocketMetric metric : pocket.metrics()) {
            if (!STANDALONE_METRICS.contains(metric.type())) {
                descriptors.put(metric.type().name().toLowerCase(Locale.ROOT),
                        metric.value());
            }
        }
        EvidenceChannel<Map<String, Double>> descriptorChannel =
                descriptors.isEmpty()
                        ? EvidenceChannel.empty(EvidenceOrigin.SOURCE_REPORTED,
                                request.fpocketMethod())
                        : EvidenceChannel.present(Map.copyOf(descriptors),
                                EvidenceOrigin.SOURCE_REPORTED,
                                request.fpocketMethod());
        EvidenceChannel<List<totah.lab.gaia.geometry.Point3D>> spheres =
                pocket.alphaSphereSet()
                        .<EvidenceChannel<List<totah.lab.gaia.geometry.Point3D>>>map(set ->
                                EvidenceChannel.present(set.spheres().stream()
                                                .map(sphere -> sphere.center()).toList(),
                                        EvidenceOrigin.SOURCE_OBSERVED,
                                        request.fpocketMethod()))
                        .orElseGet(() -> EvidenceChannel.notEvaluated(
                                "fpocket alpha-sphere coordinates were unavailable"));
        return new PocketGeometryEvidence(pocket.id().value(), rank, score,
                descriptorChannel, volume, spheres,
                EvidenceChannel.present(pocket.center(), EvidenceOrigin.DERIVED,
                        request.extractionMethod()),
                request.shapeRepresentation());
    }

    private EvidenceChannel<Double> metric(
            Pocket pocket, PocketMetricType type,
            totah.lab.athena.pocket.evidence.EvidenceMethod method,
            String unavailableReason) {
        var value = pocket.metric(type);
        return value.isPresent()
                ? EvidenceChannel.present(value.getAsDouble(),
                        EvidenceOrigin.SOURCE_REPORTED, method)
                : EvidenceChannel.notEvaluated(unavailableReason);
    }

    private ResidueContextEvidence residues(
            Pocket pocket, PocketEvidenceAssemblyRequest request) {
        List<ResidueObservation> observations = pocket.residues().stream()
                .map(id -> {
                    Residue residue = request.structure().findResidue(id)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Pocket residue is absent from structure: " + id));
                    EvidenceResidueId evidenceId = new EvidenceResidueId(
                            id.chainId(), request.structureEvidence().modelNumber(),
                            Integer.toString(id.residueNumber()),
                            id.insertionCode() == null
                                    ? null : id.insertionCode().toString());
                    return new ResidueObservation(evidenceId, residue.getName(),
                            residue.getAtoms().stream().map(this::observedAtom).toList());
                }).toList();
        EvidenceChannel<List<ResidueObservation>> pocketResidues = observations.isEmpty()
                ? EvidenceChannel.empty(EvidenceOrigin.SOURCE_OBSERVED,
                        request.extractionMethod())
                : EvidenceChannel.present(observations,
                        EvidenceOrigin.SOURCE_OBSERVED, request.extractionMethod());
        return new ResidueContextEvidence(
                pocketResidues,
                EvidenceChannel.notEvaluated("residue chemistry was not classified"),
                EvidenceChannel.notEvaluated("sequence neighborhoods were not evaluated"),
                EvidenceChannel.notEvaluated("conservation was not evaluated"),
                EvidenceChannel.notEvaluated("annotations were not evaluated"));
    }

    private ObservedAtom observedAtom(Atom atom) {
        AlternateLocationProvenance alternate = atom.getAlternateLocationProvenance();
        String alternateLocation = alternate.alternativesPresent()
                ? Character.toString(alternate.selectedAlternateLocation()) : null;
        return new ObservedAtom(new EvidenceAtomId(atom.getName(), alternateLocation),
                atom.getElement() == null ? "UNKNOWN" : atom.getElement().symbol(),
                atom.getPosition(), atom.getOccupancy(), atom.getBFactor(), null);
    }

    private EvidenceChannel<List<LigandOccurrenceEvidence>> ligands(
            PocketEvidenceAssemblyRequest request) {
        if (!(request.boundComponents() instanceof EvidenceChannel.Present<List<
                BoundComponentOccurrence>> evaluated)) {
            return propagateUnavailable(request.boundComponents());
        }
        List<LigandOccurrenceEvidence> evidence = evaluated.value().stream()
                .map(occurrence -> ligand(occurrence, request)).toList();
        return evidence.isEmpty()
                ? EvidenceChannel.empty(EvidenceOrigin.SOURCE_OBSERVED,
                        request.extractionMethod())
                : EvidenceChannel.present(evidence, EvidenceOrigin.SOURCE_OBSERVED,
                        request.extractionMethod());
    }

    private LigandOccurrenceEvidence ligand(
            BoundComponentOccurrence occurrence,
            PocketEvidenceAssemblyRequest request) {
        String residueId = occurrence.authSequenceId() != null
                ? occurrence.authSequenceId()
                : occurrence.sequenceId() == null ? "UNSPECIFIED" : occurrence.sequenceId();
        String chain = occurrence.authAsymId() != null
                ? occurrence.authAsymId() : occurrence.asymId();
        String alternate = commonAlternateLocation(occurrence.atoms());
        LigandOccurrenceId id = new LigandOccurrenceId(occurrence.pdbId(),
                occurrence.assemblyId(), occurrence.modelNumber(), chain,
                occurrence.componentId(), residueId, occurrence.insertionCode(),
                alternate);
        List<ObservedAtom> atoms = occurrence.atoms().stream()
                .map(this::observedAtom).toList();
        EvidenceChannel<String> chemistryReference =
                chemistryReference(occurrence.componentId(), request.chemistry(),
                        request.extractionMethod());
        return new LigandOccurrenceEvidence(id, atoms,
                EvidenceChannel.notEvaluated("ligand-pocket association was not evaluated"),
                EvidenceChannel.notEvaluated("ligand contacts were not evaluated"),
                EvidenceChannel.notEvaluated("interactions were not evaluated"),
                chemistryReference);
    }

    private ObservedAtom observedAtom(BoundComponentAtom atom) {
        return new ObservedAtom(new EvidenceAtomId(atom.name(), atom.alternateLocation()),
                atom.element(), atom.position(), atom.occupancy(), atom.bFactor(),
                atom.formalCharge());
    }

    private String commonAlternateLocation(List<BoundComponentAtom> atoms) {
        List<String> locations = atoms.stream().map(BoundComponentAtom::alternateLocation)
                .filter(value -> value != null && !value.isBlank()).distinct().toList();
        return locations.size() == 1 ? locations.getFirst() : null;
    }

    private EvidenceChannel<String> chemistryReference(
            String componentId,
            EvidenceChannel<Map<String, ComponentChemistryEvidence>> chemistry,
            totah.lab.athena.pocket.evidence.EvidenceMethod method) {
        if (chemistry instanceof EvidenceChannel.Present<Map<String,
                ComponentChemistryEvidence>> evaluated) {
            return evaluated.value().containsKey(componentId)
                    ? EvidenceChannel.present(componentId, EvidenceOrigin.DERIVED, method)
                    : EvidenceChannel.notApplicable(
                            "no CCD chemistry was available for component " + componentId);
        }
        if (chemistry instanceof EvidenceChannel.Empty<Map<String,
                ComponentChemistryEvidence>>) {
            return EvidenceChannel.notApplicable(
                    "CCD chemistry was evaluated but none was available");
        }
        return EvidenceChannel.notEvaluated(
                "CCD chemistry channel was not evaluated");
    }

    private <S, T> EvidenceChannel<T> propagateUnavailable(EvidenceChannel<S> source) {
        if (source instanceof EvidenceChannel.Empty<S> value) {
            return EvidenceChannel.empty(value.origin(), value.method());
        }
        if (source instanceof EvidenceChannel.NotEvaluated<S> value) {
            return EvidenceChannel.notEvaluated(value.reason());
        }
        if (source instanceof EvidenceChannel.NotApplicable<S> value) {
            return EvidenceChannel.notApplicable(value.reason());
        }
        if (source instanceof EvidenceChannel.Failed<S> value) {
            return EvidenceChannel.failed(value.failureCode(), value.reason());
        }
        throw new IllegalArgumentException("Expected an unavailable channel");
    }
}
