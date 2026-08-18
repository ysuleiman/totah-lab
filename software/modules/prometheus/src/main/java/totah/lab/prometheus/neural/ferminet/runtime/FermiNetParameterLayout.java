package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.molecular.Molecule;

/**
 * Canonical, immutable parameter ordering for the reference-aligned
 * Prometheus FermiNet-v1 ansatz.
 *
 * <p>This layout is aligned to the locked DeepMind FermiNet reference
 * configuration:
 *
 * <ul>
 *   <li>full determinants</li>
 *   <li>16 determinants</li>
 *   <li>4 interaction layers</li>
 *   <li>256 one-electron width</li>
 *   <li>32 two-electron width</li>
 *   <li>useLastLayer = false</li>
 *   <li>biasOrbitals = false</li>
 *   <li>separateSpinChannels = false</li>
 *   <li>isotropic nuclear envelopes</li>
 *   <li>no trainable determinant coefficients</li>
 * </ul>
 */
public final class FermiNetParameterLayout {

    private final FermiNetV1Configuration configuration;
    private final Molecule molecule;
    private final List<Block> blocks;
    private final int parameterCount;

    public FermiNetParameterLayout(
            FermiNetV1Configuration configuration,
            Molecule molecule) {

        this.configuration = Objects.requireNonNull(
                configuration, "configuration");
        this.molecule = Objects.requireNonNull(
                molecule, "molecule");

        List<Block> assembled = new ArrayList<>();
        int offset = 0;

        /*
         * Reference FermiNet input features:
         *
         * one-electron stream:
         *   for each nucleus:
         *     dx, dy, dz, |r_i - R_A|
         *
         * therefore:
         *   4 * numberOfNuclei
         *
         * two-electron stream:
         *   dx, dy, dz, |r_i - r_j|
         *
         * therefore:
         *   4
         */
        int oneInput = 4 * molecule.nuclei().size();
        int twoInput = 4;

        /*
         * For two active spin channels, the reference symmetric feature
         * construction has dimensionality:
         *
         *   3 * oneInput + 2 * twoInput
         *
         * corresponding to:
         *   current one-electron feature
         *   mean alpha one-electron feature
         *   mean beta one-electron feature
         *   mean alpha pair feature
         *   mean beta pair feature
         */
        for (int layer = 0;
             layer < configuration.interactionLayers();
             layer++) {

            int aggregateInput = Math.addExact(
                    Math.multiplyExact(3, oneInput),
                    Math.multiplyExact(2, twoInput));

            offset = add(
                    assembled,
                    "interaction." + layer + ".one.weight",
                    offset,
                    configuration.oneElectronWidth(),
                    aggregateInput);

            offset = add(
                    assembled,
                    "interaction." + layer + ".one.bias",
                    offset,
                    configuration.oneElectronWidth());

            /*
             * Reference behavior:
             *
             * when useLastLayer == false, the final interaction layer does
             * not apply another transformation to the two-electron stream.
             *
             * For the locked 4-layer model, pair-stream transformations
             * therefore exist for layers 0, 1, and 2 only.
             */
            boolean transformTwoElectronStream =
                    layer < configuration.interactionLayers() - 1
                            || configuration.useLastLayer();

            if (transformTwoElectronStream) {
                offset = add(
                        assembled,
                        "interaction." + layer + ".two.weight",
                        offset,
                        configuration.twoElectronWidth(),
                        twoInput);

                offset = add(
                        assembled,
                        "interaction." + layer + ".two.bias",
                        offset,
                        configuration.twoElectronWidth());
            }

            /*
             * After every one-electron transformation, the one-electron
             * stream width becomes the configured hidden width.
             */
            oneInput = configuration.oneElectronWidth();

            /*
             * The pair-stream width changes only when the pair stream is
             * actually transformed.
             */
            if (transformTwoElectronStream) {
                twoInput = configuration.twoElectronWidth();
            }
        }

        int alphaElectrons =
                molecule.spin().alphaElectrons();
        int betaElectrons =
                molecule.spin().betaElectrons();

        int totalElectrons =
                Math.addExact(alphaElectrons, betaElectrons);

        if (totalElectrons < 1) {
            throw new IllegalArgumentException(
                    "FermiNet requires at least one electron");
        }

        int[] spinElectrons = {
                alphaElectrons,
                betaElectrons
        };

        String[] spinLabels = {
                "alpha",
                "beta"
        };

        /*
         * Reference full-det behavior:
         *
         * Each active spin channel produces N total orbitals per determinant,
         * where N is the total number of electrons.
         *
         * The alpha and beta rows are later concatenated into a dense
         * determinant of shape:
         *
         *   determinants x N x N
         *
         * For neutral singlet H2O:
         *
         *   alpha rows = 5
         *   beta rows  = 5
         *   columns    = 10
         *
         * giving 16 dense 10x10 determinants.
         */
        for (int spin = 0; spin < spinElectrons.length; spin++) {

            if (spinElectrons[spin] == 0) {
                continue;
            }

            int orbitalsPerElectron =
                    configuration.fullDeterminants()
                            ? totalElectrons
                            : spinElectrons[spin];

            offset = add(
                    assembled,
                    "orbital." + spinLabels[spin] + ".weight",
                    offset,
                    configuration.determinants(),
                    orbitalsPerElectron,
                    oneInput);

            /*
             * No orbital bias block.
             *
             * Locked reference:
             *   biasOrbitals = false
             */

            offset = add(
                    assembled,
                    "envelope." + spinLabels[spin] + ".pi",
                    offset,
                    configuration.determinants(),
                    orbitalsPerElectron,
                    molecule.nuclei().size());

            offset = add(
                    assembled,
                    "envelope." + spinLabels[spin] + ".sigma",
                    offset,
                    configuration.determinants(),
                    orbitalsPerElectron,
                    molecule.nuclei().size());
        }

        /*
         * No determinant.coefficient block.
         *
         * The locked reference ground-state FermiNet directly sums the
         * determinants in log-domain arithmetic rather than learning an
         * additional coefficient vector.
         */

        this.blocks = List.copyOf(assembled);
        this.parameterCount = offset;
    }

    public FermiNetV1Configuration configuration() {
        return configuration;
    }

    public Molecule molecule() {
        return molecule;
    }

    public List<Block> blocks() {
        return blocks;
    }

    public int parameterCount() {
        return parameterCount;
    }

    public Block block(String name) {
        return blocks.stream()
                .filter(block -> block.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown FermiNet parameter block: " + name));
    }

    private static int add(
            List<Block> blocks,
            String name,
            int offset,
            int... shape) {

        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(shape, "shape");

        long size = 1L;

        for (int dimension : shape) {
            if (dimension < 1) {
                throw new IllegalArgumentException(
                        "invalid parameter block shape for "
                                + name);
            }

            size = Math.multiplyExact(size, dimension);
        }

        int count = Math.toIntExact(size);
        int end = Math.addExact(offset, count);

        blocks.add(new Block(
                name,
                offset,
                end,
                shape));

        return end;
    }

    public record Block(
            String name,
            int startInclusive,
            int endExclusive,
            int[] shape) {

        public Block {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(shape, "shape");

            shape = shape.clone();

            if (name.isBlank()) {
                throw new IllegalArgumentException(
                        "parameter block name must not be blank");
            }

            if (startInclusive < 0) {
                throw new IllegalArgumentException(
                        "parameter block start must be non-negative");
            }

            if (endExclusive <= startInclusive) {
                throw new IllegalArgumentException(
                        "parameter block end must be greater than start");
            }

            long expectedSize = 1L;

            for (int dimension : shape) {
                if (dimension < 1) {
                    throw new IllegalArgumentException(
                            "parameter block dimensions must be positive");
                }

                expectedSize =
                        Math.multiplyExact(expectedSize, dimension);
            }

            if (Math.toIntExact(expectedSize)
                    != endExclusive - startInclusive) {
                throw new IllegalArgumentException(
                        "parameter block shape does not match index range");
            }
        }

        @Override
        public int[] shape() {
            return shape.clone();
        }

        public int size() {
            return endExclusive - startInclusive;
        }
    }
}