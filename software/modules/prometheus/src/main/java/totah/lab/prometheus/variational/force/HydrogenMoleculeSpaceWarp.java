package totah.lab.prometheus.variational.force;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.variational.QuantumCoordinates;

/** Filippi--Umrigar space warp for a centered, fixed-axis H2 bond. */
public final class HydrogenMoleculeSpaceWarp {
    private HydrogenMoleculeSpaceWarp() {}

    /**
     * Applies the simultaneous bond displacement R -> R + deltaR.  The nuclei
     * move by -deltaR/2 and +deltaR/2 and f(r)=r^-4 is fixed by the protocol.
     */
    public static Transformation transform(QuantumCoordinates coordinates,
            double bondLengthBohr, double deltaRBohr) {
        Objects.requireNonNull(coordinates, "coordinates");
        if (!Double.isFinite(bondLengthBohr) || bondLengthBohr <= 0
                || !Double.isFinite(deltaRBohr)) {
            throw new IllegalArgumentException("finite positive bond length and finite displacement required");
        }
        double half = bondLengthBohr / 2;
        double jacobian = 1;
        List<QuantumCoordinates.ParticleCoordinate> moved = new ArrayList<>(coordinates.particles().size());
        for (var electron : coordinates.particles()) {
            WeightAndDerivative weight = weightAndDerivative(electron, half);
            double velocity = weight.weightAtPositiveNucleus() - 0.5;
            double blockJacobian = 1 + deltaRBohr * weight.zDerivative();
            if (!(blockJacobian > 0) || !Double.isFinite(blockJacobian)) {
                throw new IllegalArgumentException("non-positive SWCT Jacobian");
            }
            jacobian *= blockJacobian;
            moved.add(new QuantumCoordinates.ParticleCoordinate(electron.particleIndex(),
                    electron.xBohr(), electron.yBohr(), electron.zBohr() + deltaRBohr * velocity,
                    electron.spin()));
        }
        return new Transformation(new QuantumCoordinates(moved), jacobian);
    }

    /** Normalized Eq. 13 weight of the nucleus at +R/2 and its z derivative. */
    public static WeightAndDerivative weightAndDerivative(
            QuantumCoordinates.ParticleCoordinate electron, double halfBondLengthBohr) {
        Objects.requireNonNull(electron, "electron");
        if (!Double.isFinite(halfBondLengthBohr) || halfBondLengthBohr <= 0) {
            throw new IllegalArgumentException("positive half bond length required");
        }
        double minusZ = electron.zBohr() + halfBondLengthBohr;
        double plusZ = electron.zBohr() - halfBondLengthBohr;
        double minusR2 = electron.xBohr() * electron.xBohr()
                + electron.yBohr() * electron.yBohr() + minusZ * minusZ;
        double plusR2 = electron.xBohr() * electron.xBohr()
                + electron.yBohr() * electron.yBohr() + plusZ * plusZ;
        if (minusR2 == 0 || plusR2 == 0) {
            throw new IllegalArgumentException("SWCT weight is singular at a nucleus");
        }
        double minus = 1 / (minusR2 * minusR2);
        double plus = 1 / (plusR2 * plusR2);
        double denominator = minus + plus;
        double minusDerivative = -4 * minusZ / (minusR2 * minusR2 * minusR2);
        double plusDerivative = -4 * plusZ / (plusR2 * plusR2 * plusR2);
        double weight = plus / denominator;
        double derivative = (plusDerivative * denominator
                - plus * (minusDerivative + plusDerivative)) / (denominator * denominator);
        return new WeightAndDerivative(weight, derivative);
    }

    public record Transformation(QuantumCoordinates coordinates, double jacobian) {
        public Transformation {
            Objects.requireNonNull(coordinates, "coordinates");
            if (!(jacobian > 0) || !Double.isFinite(jacobian)) {
                throw new IllegalArgumentException("positive finite Jacobian required");
            }
        }
    }

    public record WeightAndDerivative(double weightAtPositiveNucleus, double zDerivative) {
        public WeightAndDerivative {
            if (!Double.isFinite(weightAtPositiveNucleus) || weightAtPositiveNucleus < 0
                    || weightAtPositiveNucleus > 1 || !Double.isFinite(zDerivative)) {
                throw new IllegalArgumentException("invalid SWCT weight");
            }
        }
    }
}
