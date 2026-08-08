package totah.lab.web.evidence;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Web-owned representation of Athena pocket evidence. */
public record PocketEvidenceView(
        StructureView structure,
        PocketGeometryView pocket,
        ResidueContextView residueContext,
        ChannelView<List<LigandOccurrenceView>> ligandEvidence,
        ChannelView<Map<String, ComponentChemistryView>> chemistry,
        ProvenanceView provenance
) {
    public record ChannelView<T>(
            String status,
            T value,
            String origin,
            MethodView method,
            String failureCode,
            String reason
    ) {}

    public record MethodView(
            String name,
            String version,
            Map<String, String> parameters
    ) {}

    public record PointView(double x, double y, double z) {}

    public record StructureView(
            String accession,
            String provider,
            String chainId,
            int modelNumber,
            String assemblyId,
            String kind,
            String modelVersion,
            ChannelView<String> experimentalMethod,
            ChannelView<Double> resolutionAngstrom,
            ChannelView<PredictedConfidenceView> predictedConfidence
    ) {}

    public record PredictedConfidenceView(
            String confidenceType,
            Map<String, Double> reportedValues
    ) {}

    public record PocketGeometryView(
            String fpocketId,
            ChannelView<Integer> reportedRank,
            ChannelView<Double> reportedScore,
            ChannelView<Map<String, Double>> reportedDescriptors,
            ChannelView<Double> reportedVolume,
            ChannelView<List<PointView>> alphaSpheres,
            ChannelView<PointView> centroid,
            ChannelView<Map<String, Double>> shapeRepresentation
    ) {}

    public record ResidueContextView(
            ChannelView<List<ResidueObservationView>> pocketResidues,
            ChannelView<List<ResidueStringValueView>> chemistryClasses,
            ChannelView<List<ResidueStringValueView>> sequenceNeighborhoods,
            ChannelView<List<ResidueAttributesView>> conservation,
            ChannelView<List<ResidueAttributesView>> annotations
    ) {}

    public record ResidueStringValueView(ResidueIdView residue, String value) {}

    public record ResidueAttributesView(
            ResidueIdView residue,
            Map<String, String> attributes
    ) {}

    public record ResidueObservationView(
            ResidueIdView id,
            String residueName,
            List<ObservedAtomView> atoms
    ) {}

    public record ResidueIdView(
            String chainId,
            int modelNumber,
            String residueId,
            String insertionCode
    ) {}

    public record AtomIdView(String name, String alternateLocation) {}

    public record ObservedAtomView(
            AtomIdView id,
            String element,
            PointView position,
            Double occupancy,
            Double bFactor,
            Integer formalCharge
    ) {}

    public record LigandOccurrenceView(
            LigandOccurrenceIdView id,
            List<ObservedAtomView> experimentalAtoms,
            ChannelView<String> pocketRelationship,
            ChannelView<List<ResidueIdView>> contactingResidues,
            ChannelView<List<InteractionView>> interactions,
            ChannelView<String> ccdChemistryReference
    ) {}

    public record LigandOccurrenceIdView(
            String pdbId,
            String assemblyId,
            int modelNumber,
            String chainId,
            String componentId,
            String residueId,
            String insertionCode,
            String alternateLocation
    ) {}

    public record InteractionView(
            LigandOccurrenceIdView ligand,
            ResidueIdView residue,
            AtomIdView ligandAtom,
            AtomIdView proteinAtom,
            String type,
            double distanceAngstrom,
            MethodView detectionMethod
    ) {}

    public record ComponentChemistryView(
            String componentId,
            String ccdVersion,
            List<ComponentAtomView> atoms,
            List<ComponentBondView> bonds,
            List<IdealCoordinateView> idealCoordinates,
            ChannelView<Map<String, Double>> molecularDescriptors,
            ChannelView<Map<String, String>> normalizedChemistryClasses,
            ChannelView<List<Double>> featureVector
    ) {}

    public record ComponentAtomView(
            String atomId,
            String element,
            int formalCharge,
            boolean aromatic
    ) {}

    public record ComponentBondView(
            String atomIdA,
            String atomIdB,
            String order,
            boolean aromatic
    ) {}

    public record IdealCoordinateView(String atomId, PointView position) {}

    public record ProvenanceView(
            String sourceProvider,
            String sourceIdentifier,
            String sourceVersion,
            MethodView extractionMethod,
            Instant extractedAt,
            Map<String, String> sourceAttributes
    ) {}
}
