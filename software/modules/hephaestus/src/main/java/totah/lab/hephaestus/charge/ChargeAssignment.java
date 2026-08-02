package totah.lab.hephaestus.charge;

import java.util.List;
import java.util.Objects;

public final class ChargeAssignment {
    private final String source;
    private final List<AssignedCharge> charges;
    private final double totalCharge;

    public ChargeAssignment(
            String source,
            List<AssignedCharge> charges) {
        this.source = Objects.requireNonNull(source, "source");
        this.charges = List.copyOf(charges);
        this.totalCharge = this.charges.stream()
                .mapToDouble(AssignedCharge::charge)
                .sum();
    }

    public String source() { return source; }
    public List<AssignedCharge> charges() { return charges; }
    public double totalCharge() { return totalCharge; }
    public int atomCount() { return charges.size(); }
}
