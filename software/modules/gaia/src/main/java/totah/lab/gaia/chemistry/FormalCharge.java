package totah.lab.gaia.chemistry;


public record FormalCharge(int value) {

    public static final FormalCharge NEUTRAL = new FormalCharge(0);
    public static final FormalCharge POSITIVE_ONE = new FormalCharge(1);
    public static final FormalCharge NEGATIVE_ONE = new FormalCharge(-1);

    public static FormalCharge of(int value) {
        return switch (value) {
            case -1 -> NEGATIVE_ONE;
            case 0 -> NEUTRAL;
            case 1 -> POSITIVE_ONE;
            default -> new FormalCharge(value);
        };
    }

    public boolean isNeutral() {
        return value == 0;
    }

    public boolean isPositive() {
        return value > 0;
    }

    public boolean isNegative() {
        return value < 0;
    }

    public FormalCharge add(FormalCharge other) {
        if (other == null) {
            throw new NullPointerException("other");
        }

        return of(Math.addExact(value, other.value));
    }

    public FormalCharge negate() {
        return of(Math.negateExact(value));
    }

    @Override
    public String toString() {
        if (value == 0) {
            return "0";
        }

        return value > 0
                ? "+" + value
                : Integer.toString(value);
    }
}
