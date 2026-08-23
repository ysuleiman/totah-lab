package totah.lab.prometheus.neural.ferminet.drivers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Collections;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.neural.ferminet.pretraining.FermiNetPretrainingQualification;

/** Frozen, programmatically generated seven-geometry H2O qualification panel. */
public final class FermiNetH2oGeometryManifest {
    public static final String CANONICAL_KEY = "canonical";
    public static final String CANONICAL_IDENTITY =
            "2b5c454215a84de2cfacd6ce7cec2cf018b5b7ee6ab95267332f0fdc26421234";
    public static final double STRETCH_BOHR = 0.020;
    public static final double BEND_DEGREES = 1.0;
    public static final double HALF_BEND_DEGREES = 0.5;

    private static final Vec3 O = new Vec3(0.0, 0.0, 0.0);
    private static final Vec3 H1 = new Vec3(1.7952398191849366, 0.0, 0.0);
    private static final Vec3 H2 = new Vec3(
            -0.46464225035067114, 1.7340684963325879, 0.0);
    private static final Map<String, Entry> ENTRIES = build();

    private FermiNetH2oGeometryManifest() {}

    public static Entry require(String key) {
        Entry entry = ENTRIES.get(Objects.requireNonNull(key, "key"));
        if (entry == null) throw new IllegalArgumentException(
                "unknown frozen H2O geometry key: " + key);
        return entry;
    }

    public static List<Entry> entries() { return List.copyOf(ENTRIES.values()); }

    private static Map<String, Entry> build() {
        LinkedHashMap<String, Entry> result = new LinkedHashMap<>();
        add(result, entry(CANONICAL_KEY, "CANONICAL", 0.0, 0.0, H1, H2));
        Vec3 u1 = H1.subtract(O).unit();
        Vec3 u2 = H2.subtract(O).unit();
        double r1 = H1.subtract(O).norm();
        double r2 = H2.subtract(O).norm();
        add(result, entry("symmetric-minus", "SYMMETRIC_STRETCH", -STRETCH_BOHR,
                0.0, O.add(u1.scale(r1 - STRETCH_BOHR)),
                O.add(u2.scale(r2 - STRETCH_BOHR))));
        add(result, entry("symmetric-plus", "SYMMETRIC_STRETCH", STRETCH_BOHR,
                0.0, O.add(u1.scale(r1 + STRETCH_BOHR)),
                O.add(u2.scale(r2 + STRETCH_BOHR))));
        // Signed asymmetric coordinate: minus means H1+ and H2-.
        add(result, entry("asymmetric-minus", "ASYMMETRIC_STRETCH", -STRETCH_BOHR,
                0.0, O.add(u1.scale(r1 + STRETCH_BOHR)),
                O.add(u2.scale(r2 - STRETCH_BOHR))));
        add(result, entry("asymmetric-plus", "ASYMMETRIC_STRETCH", STRETCH_BOHR,
                0.0, O.add(u1.scale(r1 - STRETCH_BOHR)),
                O.add(u2.scale(r2 + STRETCH_BOHR))));
        double half = Math.toRadians(HALF_BEND_DEGREES);
        add(result, entry("bend-minus", "BEND", -BEND_DEGREES,
                HALF_BEND_DEGREES, O.add(u1.rotateZ(half).scale(r1)),
                O.add(u2.rotateZ(-half).scale(r2))));
        add(result, entry("bend-plus", "BEND", BEND_DEGREES,
                -HALF_BEND_DEGREES, O.add(u1.rotateZ(-half).scale(r1)),
                O.add(u2.rotateZ(half).scale(r2))));
        if (!CANONICAL_IDENTITY.equals(result.get(CANONICAL_KEY).geometryIdentity())) {
            throw new ExceptionInInitializerError("canonical H2O geometry identity changed");
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static void add(Map<String, Entry> entries, Entry entry) {
        if (entries.put(entry.key(), entry) != null) {
            throw new IllegalStateException("duplicate H2O geometry key: " + entry.key());
        }
    }

    private static Entry entry(String key, String transformation, double delta,
            double halfRotationDegrees, Vec3 h1, Vec3 h2) {
        Molecule molecule = molecule(h1, h2);
        return new Entry(key, CANONICAL_IDENTITY, transformation, delta,
                halfRotationDegrees, molecule,
                FermiNetPretrainingQualification.geometryIdentity(molecule));
    }

    private static Molecule molecule(Vec3 h1, Vec3 h2) {
        return new Molecule("ferminet-v1-water", List.of(
                new NuclearCenter(0, "O", new NuclearCharge(8), position(O)),
                new NuclearCenter(1, "H", new NuclearCharge(1), position(h1)),
                new NuclearCenter(2, "H", new NuclearCharge(1), position(h2))),
                new MolecularCharge(0), new ElectronCount(10), new SpinSector(5, 5, 1));
    }

    private static CartesianPosition position(Vec3 value) {
        return new CartesianPosition(value.x(), value.y(), value.z(), LengthUnit.BOHR);
    }

    public record Entry(String key, String parentCanonicalGeometrySha256,
            String transformation, double delta, double halfRotationDegrees,
            Molecule molecule, String geometryIdentity) {
        public Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(parentCanonicalGeometrySha256,
                    "parentCanonicalGeometrySha256");
            Objects.requireNonNull(transformation, "transformation");
            Objects.requireNonNull(molecule, "molecule");
            Objects.requireNonNull(geometryIdentity, "geometryIdentity");
        }
    }

    private record Vec3(double x, double y, double z) {
        Vec3 add(Vec3 other) { return new Vec3(x + other.x, y + other.y, z + other.z); }
        Vec3 subtract(Vec3 other) {
            return new Vec3(x - other.x, y - other.y, z - other.z);
        }
        Vec3 scale(double factor) { return new Vec3(x * factor, y * factor, z * factor); }
        double norm() { return Math.sqrt(x * x + y * y + z * z); }
        Vec3 unit() { return scale(1.0 / norm()); }
        Vec3 rotateZ(double radians) {
            double cosine = Math.cos(radians);
            double sine = Math.sin(radians);
            return new Vec3(cosine * x - sine * y, sine * x + cosine * y, z);
        }
    }
}
