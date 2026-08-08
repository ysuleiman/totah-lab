package totah.lab.web.evidence;

import totah.lab.athena.pocket.evidence.*;
import totah.lab.gaia.geometry.Point3D;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static totah.lab.web.evidence.PocketEvidenceView.*;

/** Explicit anti-corruption boundary between Athena evidence and web JSON. */
public final class PocketEvidenceViewMapper {
    public PocketEvidenceView toView(PocketEvidence evidence) {
        return new PocketEvidenceView(
                structure(evidence.structure()),
                pocket(evidence.pocket()),
                residueContext(evidence.residueContext()),
                channel(evidence.ligandEvidence(), values -> values.stream()
                        .map(this::ligandOccurrence).toList()),
                channel(evidence.chemistry(), values -> values.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                entry -> chemistry(entry.getValue()),
                                (left, right) -> left, LinkedHashMap::new))),
                provenance(evidence.provenance()));
    }

    private StructureView structure(StructureEvidence value) {
        return new StructureView(value.accession(), value.provider(),
                value.chainId(), value.modelNumber(), value.assemblyId(),
                value.kind().name(), value.modelVersion(),
                channel(value.experimentalMethod(), Function.identity()),
                channel(value.resolutionAngstrom(), Function.identity()),
                channel(value.predictedConfidence(), confidence ->
                        new PredictedConfidenceView(confidence.confidenceType(),
                                confidence.reportedValues())));
    }

    private PocketGeometryView pocket(PocketGeometryEvidence value) {
        return new PocketGeometryView(value.fpocketId(),
                channel(value.reportedRank(), Function.identity()),
                channel(value.reportedScore(), Function.identity()),
                channel(value.reportedDescriptors(), Function.identity()),
                channel(value.reportedVolume(), Function.identity()),
                channel(value.alphaSpheres(), points -> points.stream()
                        .map(PocketEvidenceViewMapper::point).toList()),
                channel(value.centroid(), PocketEvidenceViewMapper::point),
                channel(value.shapeRepresentation(), Function.identity()));
    }

    private ResidueContextView residueContext(ResidueContextEvidence value) {
        return new ResidueContextView(
                channel(value.pocketResidues(), residues -> residues.stream()
                        .map(this::residueObservation).toList()),
                channel(value.chemistryClasses(), this::residueStringValues),
                channel(value.sequenceNeighborhoods(), this::residueStringValues),
                channel(value.conservation(), this::residueAttributes),
                channel(value.annotations(), this::residueAttributes));
    }

    private ResidueObservationView residueObservation(ResidueObservation value) {
        return new ResidueObservationView(residueId(value.id()),
                value.residueName(), value.atoms().stream()
                        .map(this::observedAtom).toList());
    }

    private LigandOccurrenceView ligandOccurrence(LigandOccurrenceEvidence value) {
        return new LigandOccurrenceView(ligandId(value.id()),
                value.experimentalAtoms().stream().map(this::observedAtom).toList(),
                channel(value.pocketRelationship(), Enum::name),
                channel(value.contactingResidues(), residues -> residues.stream()
                        .map(PocketEvidenceViewMapper::residueId).toList()),
                channel(value.interactions(), interactions -> interactions.stream()
                        .map(this::interaction).toList()),
                channel(value.ccdChemistryReference(), Function.identity()));
    }

    private InteractionView interaction(InteractionObservation value) {
        return new InteractionView(ligandId(value.ligand()),
                residueId(value.residue()), atomId(value.ligandAtom()),
                atomId(value.proteinAtom()), value.type().name(),
                value.distanceAngstrom(), method(value.detectionMethod()));
    }

    private ComponentChemistryView chemistry(ComponentChemistryEvidence value) {
        return new ComponentChemistryView(value.componentId(), value.ccdVersion(),
                value.atoms().stream().map(atom -> new ComponentAtomView(
                        atom.atomId(), atom.element(), atom.formalCharge(),
                        atom.aromatic())).toList(),
                value.bonds().stream().map(bond -> new ComponentBondView(
                        bond.atomIdA(), bond.atomIdB(), bond.order().name(),
                        bond.aromatic())).toList(),
                value.idealCoordinates().stream().map(coordinate ->
                        new IdealCoordinateView(coordinate.atomId(),
                                point(coordinate.position()))).toList(),
                channel(value.molecularDescriptors(), Function.identity()),
                channel(value.normalizedChemistryClasses(), Function.identity()),
                channel(value.featureVector(), Function.identity()));
    }

    private ProvenanceView provenance(EvidenceProvenance value) {
        return new ProvenanceView(value.sourceProvider(), value.sourceIdentifier(),
                value.sourceVersion(), method(value.extractionMethod()),
                value.extractedAt(), value.sourceAttributes());
    }

    private <T, V> ChannelView<V> channel(
            EvidenceChannel<T> source, Function<T, V> valueMapper) {
        return switch (source) {
            case EvidenceChannel.Present<T> present -> new ChannelView<>(
                    present.status().name(), valueMapper.apply(present.value()),
                    present.origin().name(), method(present.method()),
                    null, null);
            case EvidenceChannel.Empty<T> empty -> new ChannelView<>(
                    empty.status().name(), null, empty.origin().name(),
                    method(empty.method()), null, null);
            case EvidenceChannel.NotEvaluated<T> unavailable -> new ChannelView<>(
                    unavailable.status().name(), null, null, null, null,
                    unavailable.reason());
            case EvidenceChannel.NotApplicable<T> unavailable -> new ChannelView<>(
                    unavailable.status().name(), null, null, null, null,
                    unavailable.reason());
            case EvidenceChannel.Failed<T> failed -> new ChannelView<>(
                    failed.status().name(), null, null, null,
                    failed.failureCode(), failed.reason());
        };
    }

    private List<ResidueStringValueView> residueStringValues(
            Map<EvidenceResidueId, String> source) {
        return source.entrySet().stream().map(entry ->
                new ResidueStringValueView(residueId(entry.getKey()),
                        entry.getValue())).toList();
    }

    private List<ResidueAttributesView> residueAttributes(
            Map<EvidenceResidueId, Map<String, String>> source) {
        return source.entrySet().stream().map(entry ->
                new ResidueAttributesView(residueId(entry.getKey()),
                        entry.getValue())).toList();
    }

    private ObservedAtomView observedAtom(ObservedAtom value) {
        return new ObservedAtomView(atomId(value.id()), value.element(),
                point(value.position()), value.occupancy(), value.bFactor(),
                value.formalCharge());
    }

    private static PointView point(Point3D value) {
        return new PointView(value.x(), value.y(), value.z());
    }

    private static ResidueIdView residueId(EvidenceResidueId value) {
        return new ResidueIdView(value.chainId(), value.modelNumber(),
                value.residueId(), value.insertionCode());
    }

    private static AtomIdView atomId(EvidenceAtomId value) {
        return new AtomIdView(value.name(), value.alternateLocation());
    }

    private static LigandOccurrenceIdView ligandId(LigandOccurrenceId value) {
        return new LigandOccurrenceIdView(value.pdbId(), value.assemblyId(),
                value.modelNumber(), value.chainId(), value.componentId(),
                value.residueId(), value.insertionCode(),
                value.alternateLocation());
    }

    private static MethodView method(EvidenceMethod value) {
        return new MethodView(value.name(), value.version(), value.parameters());
    }
}
