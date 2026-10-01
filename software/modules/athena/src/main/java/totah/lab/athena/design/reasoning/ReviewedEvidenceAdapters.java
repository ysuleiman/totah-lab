package totah.lab.athena.design.reasoning;

import totah.lab.aether.matrix.FragmentInteractionCalculator;
import totah.lab.aether.provenance.ScientificStatus;
import totah.lab.athena.design.backend.BackendEvidence;
import totah.lab.athena.design.backend.MolecularSanitizer;
import totah.lab.athena.design.generation.MolecularDesignTree.DesignState;
import totah.lab.athena.design.generation.MolecularDesignTree.Reference;
import totah.lab.athena.pocket.evidence.EvidenceMethod;

import java.util.Map;
import java.util.TreeMap;

import static totah.lab.athena.design.reasoning.DesignKnowledge.*;

/** Explicit reviewed association of existing receipts, never automatic ingestion or biological interpretation. */
public final class ReviewedEvidenceAdapters {
    private ReviewedEvidenceAdapters() { }

    /** The caller reviews receipt attribution. Valid toolkit chemistry does not establish function or potency. */
    public static Evidence chemistryValidation(Reference id, Reference source, Reference review,
                                               DesignState subject, Reference system, String context,
                                               Map<String, String> conditions, MolecularSanitizer.Result result) {
        if (!subject.graph().equals(result.graph())) throw new IllegalArgumentException("receipt graph differs from reviewed subject");
        var backend = result.evidence();
        var method = new EvidenceMethod(backend.backend() + ":" + backend.operation(), backend.version(), Map.of());
        var semantics = new Semantics(method, new Reference("toolkit-chemical-validation", "1"), system,
                conditions, "categorical", ValueKind.QUALITATIVE);
        boolean valid = result.valid() && backend.graphChanges().stream()
                .noneMatch(c -> c.disposition() == BackendEvidence.Disposition.UNAUTHORIZED_MEANINGFUL_CHANGE);
        return new Evidence(id, EvidenceKind.COMPUTATIONAL, ReviewStatus.REVIEWED,
                "Toolkit chemical validation only", context, source,
                "No biological or geometry inference. Source valid=" + result.valid() + "; Backend receipt: " + backend,
                new Qualification(semantics, valid ? ScientificStatus.SCREENING_ONLY : ScientificStatus.UNAVAILABLE,
                        subject, review, "categorical toolkit result; no experimental uncertainty estimate"),
                valid ? new Value(null, null, "valid") : null);
    }

    /** Fixed PBE-D3 counterpoise endpoint. Original protocol, component attribution and screening limit survive. */
    public static Evidence fragmentEnergy(Reference id, Reference review, DesignState subject, Reference system,
                                          String context, Map<String, String> conditions,
                                          FragmentInteractionCalculator.Result result) {
        var parameters = new TreeMap<String, String>();
        parameters.put("protocol", FragmentInteractionCalculator.PROTOCOL);
        parameters.put("receiptHash", result.receiptHash());
        for (int i = 0; i < result.components().size(); i++) parameters.put("component." + i, result.components().get(i).toString());
        parameters.put("dispersionReceipts", result.dispersionReceipts().toString());
        var semantics = new Semantics(new EvidenceMethod("Aether-fragment-PBE-D3-CP", "18.1", Map.of("protocol", FragmentInteractionCalculator.PROTOCOL)),
                new Reference("electronic-fragment-interaction-energy", "1"), system, conditions, "hartree", ValueKind.QUANTITATIVE);
        boolean available = result.pbeD3CpHartree().isPresent() && Double.isFinite(result.pbeD3CpHartree().getAsDouble())
                && result.failures().isEmpty() && result.components().size() == 6
                && result.components().stream().map(c -> c.method() + ":" + c.role()).collect(java.util.stream.Collectors.toSet())
                        .equals(java.util.Set.of("RHF:AB", "RHF:A_GHOST_B", "RHF:B_GHOST_A", "PBE:AB", "PBE:A_GHOST_B", "PBE:B_GHOST_A"))
                && result.dispersionReceipts().size() == 3
                && result.components().stream().allMatch(c -> c.scfStatus().equals("CONVERGED") && c.energyHartree().isPresent() && Double.isFinite(c.energyHartree().getAsDouble()));
        Double value = available ? result.pbeD3CpHartree().getAsDouble() : null;
        return new Evidence(id, EvidenceKind.COMPUTATIONAL, ReviewStatus.REVIEWED,
                "Frozen fragment electronic interaction energy; not binding free energy or biological potency", context,
                new Reference(result.receiptHash(), "aether-fragment-interface-18-1"),
                "SCREENING_ONLY; reviewed subject/system association is supplied externally. No uncertainty estimate. Failures: " + result.failures() + "; receipt attribution: " + parameters,
                new Qualification(semantics, available ? result.status() : ScientificStatus.UNAVAILABLE,
                        subject, review, "point calculation only; zero interval width is not zero physical uncertainty"),
                available ? new Value(value, value, "") : null);
    }

    /** The raw source receipt is not evidence. The host must verify it with its named importer before review. */
    public record SourceReceipt(String schema, String provider, String identifier, String sourceVersion,
                                String uri, String title, String importedAt, String rawBase64, String sha256,
                                Reference importer, Reference endpoint, EvidenceMethod method, Reference system,
                                String context, Map<String,String> conditions, String unit, String valueText,
                                ValueKind valueKind, UncertaintyKind uncertaintyKind, String uncertaintyText,
                                String claimBoundary, Map<String,String> sourceUse, java.util.List<String> warnings,
                                java.util.List<String> errors) {
        public SourceReceipt {
            if (!"pubchem-molecular-weight-source/1".equals(schema)) throw new IllegalArgumentException("unsupported source receipt schema");
            java.time.Instant.parse(importedAt);
            conditions = Map.copyOf(conditions); sourceUse = Map.copyOf(sourceUse);
            warnings = java.util.List.copyOf(warnings); errors = java.util.List.copyOf(errors);
            byte[] raw = java.util.Base64.getDecoder().decode(rawBase64);
            if (!totah.lab.aether.provenance.ContentHash.sha256(raw).equals(sha256))
                throw new IllegalArgumentException("raw source hash mismatch");
            java.util.Objects.requireNonNull(importer); java.util.Objects.requireNonNull(endpoint);
        }
    }
    public enum UncertaintyKind { POINT_UNKNOWN, REPORTED_INTERVAL, REPORTED_ERROR, QUALITATIVE, UNAVAILABLE }

    /** Explicit scientific association, never a parser default. Missing qualifications cause a recorded rejection. */
    public record SourceReview(Reference reference, Reference reviewer, Reference process, String reviewedAt,
                               boolean approve, String reason, String scientificSubject, DesignState subject,
                               Semantics semantics, ScientificStatus status, UncertaintyKind uncertaintyKind,
                               String uncertaintyNote, String claimBoundary, String limitations) {
        public SourceReview {
            java.util.Objects.requireNonNull(reference); java.util.Objects.requireNonNull(reviewer);
            java.util.Objects.requireNonNull(process); java.time.Instant.parse(reviewedAt);
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("review reason required");
        }
    }
    /** Full structural source and backend proof; molecular relevance still requires a separate review. */
    public record SourceAssociation(String rawBase64, String sha256, String uri, String parser,
                                    totah.lab.athena.design.backend.MolecularGraph graph,
                                    totah.lab.athena.design.backend.CanonicalIdentityService.Association proof) {
        public SourceAssociation {
            if (!totah.lab.aether.provenance.ContentHash.sha256(java.util.Base64.getDecoder().decode(rawBase64)).equals(sha256))
                throw new IllegalArgumentException("structure source hash mismatch");
            java.util.Objects.requireNonNull(graph); java.util.Objects.requireNonNull(proof);
        }
    }
    /** Opaque downstream interpretation: Athena does not implement the domain policy or parse its raw record. */
    public record Interpretation(SourceReview review, Reference policy, String inputType, String rawJson, String sha256,
                                 String claimBoundary, Evidence evidence) {
        public Interpretation {
            java.util.Objects.requireNonNull(review); java.util.Objects.requireNonNull(policy);
            if (inputType == null || inputType.isBlank() || claimBoundary == null || claimBoundary.isBlank()
                    || !totah.lab.aether.provenance.ContentHash.sha256(rawJson).equals(sha256)
                    || !review.approve() || evidence.qualification() == null || !evidence.qualification().review().equals(review.reference())
                    || !evidence.qualification().subject().equals(review.subject())
                    || !evidence.qualification().semantics().equals(review.semantics())
                    || evidence.qualification().status() != review.status() || !claimBoundary.equals(review.claimBoundary())
                    || !evidence.source().version().equals(sha256) || !evidence.claim().equals(claimBoundary))
                throw new IllegalArgumentException("interpreted evidence must preserve source/review/claim attribution");
        }
    }

    /** Both approvals and rejections carry the original bytes and complete review, ready for durable persistence. */
    public record SourceDecision(SourceReceipt receipt, SourceReview review, Reference evidenceReference,
                                 Evidence evidence, java.util.List<String> reasons, SourceAssociation association) {
        public SourceDecision {
            java.util.Objects.requireNonNull(receipt); java.util.Objects.requireNonNull(review);
            java.util.Objects.requireNonNull(evidenceReference); reasons = java.util.List.copyOf(reasons);
            var expected = rejectionReasons(receipt, review, association);
            if (!reasons.equals(expected) || !java.util.Objects.equals(evidence, qualified(receipt, review, evidenceReference, expected)))
                throw new IllegalArgumentException("source decision must preserve reviewed qualification");
        }
        public SourceDecision(SourceReceipt receipt, SourceReview review, Reference evidenceReference,
                              Evidence evidence, java.util.List<String> reasons) {
            this(receipt, review, evidenceReference, evidence, reasons, null);
        }
    }
    public static SourceDecision reviewSource(SourceReceipt receipt, SourceReview review, Reference evidenceReference) {
        return reviewSource(receipt, review, evidenceReference, null);
    }
    public static SourceDecision reviewSource(SourceReceipt receipt, SourceReview review, Reference evidenceReference, SourceAssociation association) {
        var reasons = rejectionReasons(receipt, review, association);
        return new SourceDecision(receipt, review, evidenceReference, qualified(receipt, review, evidenceReference, reasons), reasons, association);
    }
    private static java.util.List<String> rejectionReasons(SourceReceipt source, SourceReview review, SourceAssociation association) {
        var reasons = new java.util.ArrayList<String>();
        if (association != null && !association.proof().proven()) reasons.add("structural association not proven: " + association.proof().reason());
        if (!review.approve()) reasons.add("reviewer rejected: " + review.reason());
        if (!source.errors().isEmpty()) reasons.add("source parse errors: " + source.errors());
        if (!"PubChem".equals(source.provider()) || !source.importer().equals(new Reference("PubChemMolecularWeightImporter", "1"))
                || !source.endpoint().equals(new Reference("pubchem:MolecularWeight", "1"))
                || source.method() == null || source.system() == null || !"g/mol".equals(source.unit())
                || source.valueKind() != ValueKind.QUANTITATIVE || source.sourceVersion().isBlank())
            reasons.add("unsupported or missing source semantics");
        var semantics = review.semantics();
        if (semantics == null || !java.util.Objects.equals(source.method(), semantics.method())
                || !source.endpoint().equals(semantics.endpoint()) || !java.util.Objects.equals(source.system(), semantics.system())
                || !source.conditions().equals(semantics.conditions()) || !source.unit().equals(semantics.unit())
                || source.valueKind() != semantics.valueKind()) reasons.add("review may not invent or convert source semantics");
        if (!java.util.Objects.equals(source.identifier(), review.scientificSubject()) || review.subject() == null)
            reasons.add("explicit matching source subject and design-state association required");
        if (review.status() != ScientificStatus.SCREENING_ONLY) reasons.add("computed source is screening-only");
        if (source.uncertaintyKind() != UncertaintyKind.POINT_UNKNOWN || review.uncertaintyKind() != source.uncertaintyKind())
            reasons.add("unsupported or strengthened uncertainty interpretation");
        if (!java.util.Objects.equals(source.claimBoundary(), review.claimBoundary())
                || review.limitations() == null || review.limitations().isBlank()
                || review.uncertaintyNote() == null || review.uncertaintyNote().isBlank())
            reasons.add("explicit source claim boundary, uncertainty note and review limitations required");
        try {
            if (!Double.isFinite(Double.parseDouble(source.valueText()))) reasons.add("nonfinite source value");
        } catch (NumberFormatException error) { reasons.add("unusable source value"); }
        return java.util.List.copyOf(reasons);
    }
    private static Evidence qualified(SourceReceipt source, SourceReview review, Reference id, java.util.List<String> reasons) {
        if (!reasons.isEmpty()) return null;
        double value = Double.parseDouble(source.valueText());
        return new Evidence(id, EvidenceKind.COMPUTATIONAL, ReviewStatus.REVIEWED, source.claimBoundary(), source.context(),
                new Reference(source.provider() + ":" + source.identifier(), source.sha256()),
                "Source release=" + source.sourceVersion() + "; importer=" + source.importer() + "; review process=" + review.process()
                        + "; warnings=" + source.warnings() + "; source use=" + new TreeMap<>(source.sourceUse()) + "; " + review.limitations(),
                new Qualification(review.semantics(), review.status(), review.subject(), review.reference(),
                        "POINT_UNKNOWN: no source uncertainty reported; equal bounds are a point representation, not zero physical uncertainty. " + review.uncertaintyNote()),
                new Value(value, value, ""));
    }
}
