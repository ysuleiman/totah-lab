package totah.lab.aether.matrix;

import java.util.List;
import java.util.OptionalDouble;
import totah.lab.aether.model.FragmentPair;
import totah.lab.aether.provenance.ScientificStatus;

/** Five bound RHF calculations and separately typed uncorrected/counterpoise evidence. */
public final class InteractionEnergyResult {
    public enum Status { EVALUATED, SCF_NOT_CONVERGED }
    public enum Role { COMPLEX, A_OWN, B_OWN, A_WITH_GHOST_B, B_WITH_GHOST_A }
    public record Component(Role role, RhfScfRun calculation, String basisContextIdentity) {}
    public record Receipt(String implementation,String protocol,String fragmentAIdentity,String fragmentBIdentity,
                          List<String> componentReceiptHashes,Status evaluationStatus,
                          OptionalDouble uncorrectedHartree,OptionalDouble counterpoiseHartree,
                          String calculationHash,String resultHash,String receiptHash) {
        public Receipt { componentReceiptHashes=List.copyOf(componentReceiptHashes); }
        public ScientificStatus status() { return ScientificStatus.SCREENING_ONLY; }
    }
    private final FragmentPair fragments;
    private final List<Component> components;
    private final Receipt receipt;
    InteractionEnergyResult(FragmentPair fragments,List<Component> components,Receipt receipt) {
        this.fragments=fragments;this.components=List.copyOf(components);this.receipt=receipt;
    }
    public FragmentPair fragments() { return fragments; }
    public List<Component> components() { return components; }
    public Component component(Role role) { return components.stream().filter(c->c.role()==role).findFirst().orElseThrow(); }
    public OptionalDouble uncorrectedHartree() { return receipt.uncorrectedHartree(); }
    public OptionalDouble counterpoiseHartree() { return receipt.counterpoiseHartree(); }
    public Status status() { return receipt.evaluationStatus(); }
    public ScientificStatus scientificStatus() { return ScientificStatus.SCREENING_ONLY; }
    public Receipt receipt() { return receipt; }
}
