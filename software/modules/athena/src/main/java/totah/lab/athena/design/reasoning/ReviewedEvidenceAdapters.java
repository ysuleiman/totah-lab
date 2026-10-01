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
}
