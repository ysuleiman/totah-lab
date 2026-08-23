package totah.lab.prometheus.neural.ferminet.drivers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.neural.ferminet.pretraining.FermiNetPretrainingQualification;

final class FermiNetH2oGeometryManifestTest {
    private static final double TOLERANCE = 2.0e-14;

    @Test
    void programmaticPanelAndPersistedHexHaveIdenticalRepositoryIdentities()
            throws Exception {
        JsonNode root;
        try (InputStream input = getClass().getResourceAsStream(
                "/totah/lab/prometheus/neural/ferminet/h2o-seven-geometry-manifest.json")) {
            root = new ObjectMapper().readTree(input);
        }
        assertEquals(7, root.path("geometries").size());
        for (JsonNode persisted : root.path("geometries")) {
            var generated = FermiNetH2oGeometryManifest.require(
                    persisted.path("key").asText());
            assertEquals(generated.geometryIdentity(),
                    persisted.path("geometry_sha256").asText());
            assertEquals(generated.geometryIdentity(),
                    FermiNetPretrainingQualification.geometryIdentity(
                            fromHex(persisted.path("coordinates"))));
            for (int atom = 0; atom < 3; atom++) {
                var expected = generated.molecule().nuclei().get(atom).position().inBohr();
                var hex = persisted.path("coordinates").get(atom).path("hex");
                assertArrayEquals(new long[] {
                        Double.doubleToRawLongBits(expected.x()),
                        Double.doubleToRawLongBits(expected.y()),
                        Double.doubleToRawLongBits(expected.z())}, new long[] {
                        Double.doubleToRawLongBits(Double.valueOf(hex.get(0).asText())),
                        Double.doubleToRawLongBits(Double.valueOf(hex.get(1).asText())),
                        Double.doubleToRawLongBits(Double.valueOf(hex.get(2).asText()))});
            }
        }
        assertEquals(FermiNetH2oGeometryManifest.CANONICAL_IDENTITY,
                FermiNetH2oGeometryManifest.require("canonical").geometryIdentity());
    }

    @Test
    void transformationsPreserveFrozenPhysicalInvariants() {
        Molecule canonical = geometry("canonical");
        double r1 = bond(canonical, 1), r2 = bond(canonical, 2);
        for (String key : List.of("symmetric-minus", "symmetric-plus")) {
            double delta = FermiNetH2oGeometryManifest.require(key).delta();
            assertEquals(r1 + delta, bond(geometry(key), 1), TOLERANCE);
            assertEquals(r2 + delta, bond(geometry(key), 2), TOLERANCE);
        }
        Molecule asymmetricMinus = geometry("asymmetric-minus");
        Molecule asymmetricPlus = geometry("asymmetric-plus");
        assertEquals(r1 + 0.020, bond(asymmetricMinus, 1), TOLERANCE);
        assertEquals(r2 - 0.020, bond(asymmetricMinus, 2), TOLERANCE);
        assertEquals(r1 - 0.020, bond(asymmetricPlus, 1), TOLERANCE);
        assertEquals(r2 + 0.020, bond(asymmetricPlus, 2), TOLERANCE);
        assertEquals(bond(asymmetricMinus, 1) - r1,
                -(bond(asymmetricPlus, 1) - r1), TOLERANCE);
        assertEquals(bond(asymmetricMinus, 2) - r2,
                -(bond(asymmetricPlus, 2) - r2), TOLERANCE);

        double canonicalAngle = angle(canonical);
        assertEquals(canonicalAngle - Math.toRadians(1.0),
                angle(geometry("bend-minus")), TOLERANCE);
        assertEquals(canonicalAngle + Math.toRadians(1.0),
                angle(geometry("bend-plus")), TOLERANCE);
        for (String key : List.of("bend-minus", "bend-plus")) {
            assertEquals(r1, bond(geometry(key), 1), TOLERANCE);
            assertEquals(r2, bond(geometry(key), 2), TOLERANCE);
        }
        for (var entry : FermiNetH2oGeometryManifest.entries()) {
            Molecule molecule = entry.molecule();
            assertEquals("ferminet-v1-water", molecule.moleculeId());
            assertEquals(0, molecule.charge().elementaryCharges());
            assertEquals(10, molecule.electrons().value());
            assertEquals(new SpinSector(5, 5, 1), molecule.spin());
            assertEquals(List.of("O", "H", "H"), molecule.nuclei().stream()
                    .map(NuclearCenter::element).toList());
            assertTrue(molecule.nuclei().stream().allMatch(
                    nucleus -> nucleus.position().inBohr().z() == 0.0));
        }
    }

    @Test
    void unknownGeometryFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> FermiNetH2oGeometryManifest.require("not-frozen"));
    }

    private static Molecule geometry(String key) {
        return FermiNetH2oGeometryManifest.require(key).molecule();
    }

    private static double bond(Molecule molecule, int hydrogen) {
        var o = molecule.nuclei().get(0).position().inBohr();
        var h = molecule.nuclei().get(hydrogen).position().inBohr();
        return Math.sqrt(square(h.x() - o.x()) + square(h.y() - o.y())
                + square(h.z() - o.z()));
    }

    private static double angle(Molecule molecule) {
        var o = molecule.nuclei().get(0).position().inBohr();
        var h1 = molecule.nuclei().get(1).position().inBohr();
        var h2 = molecule.nuclei().get(2).position().inBohr();
        double x1 = h1.x() - o.x(), y1 = h1.y() - o.y();
        double x2 = h2.x() - o.x(), y2 = h2.y() - o.y();
        return Math.acos((x1 * x2 + y1 * y2)
                / (Math.hypot(x1, y1) * Math.hypot(x2, y2)));
    }

    private static Molecule fromHex(JsonNode coordinates) {
        return new Molecule("ferminet-v1-water", List.of(
                nucleus(0, "O", 8, coordinates.get(0).path("hex")),
                nucleus(1, "H", 1, coordinates.get(1).path("hex")),
                nucleus(2, "H", 1, coordinates.get(2).path("hex"))),
                new MolecularCharge(0), new ElectronCount(10), new SpinSector(5, 5, 1));
    }

    private static NuclearCenter nucleus(
            int index, String element, int charge, JsonNode hex) {
        return new NuclearCenter(index, element, new NuclearCharge(charge),
                new CartesianPosition(Double.valueOf(hex.get(0).asText()),
                        Double.valueOf(hex.get(1).asText()),
                        Double.valueOf(hex.get(2).asText()), LengthUnit.BOHR));
    }

    private static double square(double value) { return value * value; }
}
