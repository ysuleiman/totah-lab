package totah.lab.aether.basis;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.geometry.Point3D;

/** Verified bundled H 1s STO-3G definition; no implied C/N/O or p support. */
public final class Sto3gHydrogen {
    private final String source;
    private final String version;
    private final String contentHash;
    private final List<Double> exponents;
    private final List<Double> coefficients;

    private Sto3gHydrogen(Properties data, String contentHash) throws IOException {
        this.source = required(data, "source");
        this.version = required(data, "version");
        this.contentHash = contentHash;
        if (!"H".equals(required(data, "element")) || !"S".equals(required(data, "shell"))) {
            throw new IOException("Unsupported bundled basis element/shell");
        }
        try {
            exponents = parse(required(data, "exponents"));
            coefficients = parse(required(data, "coefficients"));
            if (exponents.size() != 3 || coefficients.size() != 3) {
                throw new IllegalArgumentException("Expected three STO-3G terms");
            }
            atBohr(new Point3D(0, 0, 0));
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new IOException("Invalid STO-3G resource", e);
        }
    }

    public static Sto3gHydrogen load() throws IOException {
        byte[] bytes = resource("sto-3g-h.properties");
        String hash = ContentHash.sha256(bytes);
        String expected = new String(resource("sto-3g-h.sha256"), java.nio.charset.StandardCharsets.US_ASCII).trim();
        if (!hash.equals(expected)) throw new IOException("STO-3G resource SHA-256 mismatch");
        Properties properties = new Properties();
        try (var input = new ByteArrayInputStream(bytes)) {
            properties.load(input);
        }
        return new Sto3gHydrogen(properties, hash);
    }

    public ContractedGaussian atBohr(Point3D centerBohr) {
        var terms = new ArrayList<GaussianTerm>(exponents.size());
        for (int i = 0; i < exponents.size(); i++) {
            terms.add(new GaussianTerm(new PrimitiveGaussian(centerBohr, exponents.get(i)), coefficients.get(i)));
        }
        return new ContractedGaussian(terms);
    }

    public String source() { return source; }
    public String version() { return version; }
    public String contentHash() { return contentHash; }
    public List<Double> exponents() { return exponents; }
    public List<Double> coefficients() { return coefficients; }

    private static List<Double> parse(String value) {
        return java.util.Arrays.stream(value.split(",", -1)).map(Double::valueOf).toList();
    }

    private static String required(Properties data, String key) throws IOException {
        String value = data.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing basis field: " + key);
        return value;
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream input = Sto3gHydrogen.class.getResourceAsStream(name)) {
            if (input == null) throw new IOException("Missing basis resource: " + name);
            return input.readAllBytes();
        }
    }
}
