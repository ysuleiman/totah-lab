package totah.lab.prometheus.neural.ferminet.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Reference-aligned Prometheus FermiNet-v1 molecular wavefunction.
 *
 * <p>Architecture locked against DeepMind FermiNet commit
 * c4312c315dda1c5728994ba89629744f71c6eb66.
 */
public final class FermiNetV1State {

    private static final double INV_SQRT_2 =
            1.0 / Math.sqrt(2.0);

    private static final double SINGULAR_THRESHOLD =
            1.0e-14;

    private final Molecule molecule;
    private final FermiNetV1Configuration configuration;
    private final FermiNetParameterLayout layout;
    private final double[] parameters;

    public FermiNetV1State(
            Molecule molecule,
            FermiNetV1Configuration configuration,
            FermiNetParameters parameters) {

        this(
                molecule,
                configuration,
                Objects.requireNonNull(
                                parameters,
                                "parameters")
                        .toArray());

        if (!parameters.layout()
                .configuration()
                .equals(configuration)
                || !parameters.layout()
                .molecule()
                .scientificIdentity()
                .equals(
                        molecule.scientificIdentity())
                || parameters.layout()
                .parameterCount()
                != layout.parameterCount()) {

            throw new IllegalArgumentException(
                    "parameter layout mismatch");
        }
    }

    private FermiNetV1State(
            Molecule molecule,
            FermiNetV1Configuration configuration,
            double[] parameters) {

        this(molecule, configuration, parameters, true);
    }

    private FermiNetV1State(
            Molecule molecule,
            FermiNetV1Configuration configuration,
            double[] parameters,
            boolean copyParameters) {

        this.molecule =
                Objects.requireNonNull(
                        molecule,
                        "molecule");

        this.configuration =
                Objects.requireNonNull(
                        configuration,
                        "configuration");

        if (!configuration.fullDeterminants()) {
            throw new IllegalArgumentException(
                    "reference-aligned FermiNet-v1 requires full determinants");
        }

        if (configuration.useLastLayer()) {
            throw new IllegalArgumentException(
                    "reference-aligned FermiNet-v1 requires useLastLayer=false");
        }

        if (configuration.biasOrbitals()) {
            throw new IllegalArgumentException(
                    "reference-aligned FermiNet-v1 requires biasOrbitals=false");
        }

        if (configuration.separateSpinChannels()) {
            throw new IllegalArgumentException(
                    "reference-aligned FermiNet-v1 requires shared pair-stream parameters");
        }

        if (configuration.jastrowEnabled()) {
            throw new IllegalArgumentException(
                    "reference-aligned FermiNet-v1 requires no Jastrow");
        }

        this.layout =
                new FermiNetParameterLayout(
                        configuration,
                        molecule);

        this.parameters = copyParameters
                ? Objects.requireNonNull(parameters, "parameters").clone()
                : Objects.requireNonNull(parameters, "parameters");

        if (parameters.length
                != layout.parameterCount()
                || Arrays.stream(parameters)
                .anyMatch(
                        x -> !Double.isFinite(x))) {

            throw new IllegalArgumentException(
                    "invalid FermiNet parameter vector");
        }
    }

    public Molecule molecule() {
        return molecule;
    }

    public FermiNetV1Configuration configuration() {
        return configuration;
    }

    public int parameterCount() {
        return parameters.length;
    }

    public double parameter(int index) {
        return parameters[index];
    }

    public FermiNetV1State withParameter(
            int index,
            double value) {

        if (index < 0
                || index >= parameters.length
                || !Double.isFinite(value)) {

            throw new IllegalArgumentException(
                    "invalid parameter update");
        }

        double[] next =
                parameters.clone();

        next[index] =
                value;

        return new FermiNetV1State(
                molecule,
                configuration,
                next);
    }

    FermiNetV1State withParameters(
            double[] values) {

        return new FermiNetV1State(
                molecule,
                configuration,
                values);
    }

    FermiNetV1State withGeometry(
            Molecule geometry) {

        requireCompatibleGeometry(
                geometry);

        return new FermiNetV1State(
                geometry,
                configuration,
                parameters,
                false);
    }

    private void requireCompatibleGeometry(
            Molecule geometry) {

        Objects.requireNonNull(
                geometry,
                "geometry");

        if (!molecule.moleculeId().equals(geometry.moleculeId())
                || !molecule.charge().equals(geometry.charge())
                || !molecule.electrons().equals(geometry.electrons())
                || !molecule.spin().equals(geometry.spin())
                || molecule.nuclei().size() != geometry.nuclei().size()) {

            throw new IllegalArgumentException(
                    "incompatible FermiNet molecular geometry");
        }

        for (int nucleus = 0;
             nucleus < molecule.nuclei().size();
             nucleus++) {

            var expected =
                    molecule.nuclei().get(nucleus);

            var actual =
                    geometry.nuclei().get(nucleus);

            if (expected.orderedIndex() != actual.orderedIndex()
                    || !expected.element().equals(actual.element())
                    || !expected.charge().equals(actual.charge())) {

                throw new IllegalArgumentException(
                        "incompatible FermiNet nuclear topology");
            }
        }
    }

    double[] parameterArray() {
        return parameters.clone();
    }

    public Evaluation evaluate(
            QuantumCoordinates coordinates) {

        validate(coordinates);

        int dimensions =
                3 * molecule.electrons().value();

        Interaction interaction =
                interaction(
                        coordinates,
                        dimensions);

        Output output =
                output(
                        interaction.one(),
                        coordinates,
                        dimensions);

        double[] parameterDerivative =
                backprop(
                        output,
                        interaction.caches());

        return new Evaluation(
                output.sign(),
                output.logAbsolute(),
                output.logGradient(),
                output.laplacianOverWavefunction(),
                parameterDerivative);
    }

    StructuredSrEvaluation structuredSrEvaluation(
            QuantumCoordinates coordinates) {

        validate(coordinates);

        int dimensions =
                3 * molecule.electrons().value();

        Interaction interaction =
                interaction(
                        coordinates,
                        dimensions);

        Output output =
                output(
                        interaction.one(),
                        coordinates,
                        dimensions);

        FermiNetStructuredSrStatistics.Builder statistics =
                new FermiNetStructuredSrStatistics.Builder(
                        new FermiNetStructuredSrStatistics.Schema(
                                layout));

        backprop(
                output,
                interaction.caches(),
                new ParameterGradientAccumulator(
                        0,
                        0,
                        new double[0]),
                statistics);

        return new StructuredSrEvaluation(
                output.sign(),
                output.logAbsolute(),
                output.logGradient(),
                output.laplacianOverWavefunction(),
                statistics.build());
    }

    /**
     * Writes a canonical contiguous range of parameter log derivatives into
     * caller-provided storage without materializing the full parameter vector.
     */
    public void parameterLogDerivatives(
            QuantumCoordinates coordinates,
            int parameterStart,
            int parameterLength,
            double[] destination) {

        validateParameterDerivativeRange(
                parameterStart,
                parameterLength,
                destination);

        validate(coordinates);

        int dimensions =
                3 * molecule.electrons().value();

        Interaction interaction =
                interaction(
                        coordinates,
                        dimensions);

        Output output =
                output(
                        interaction.one(),
                        coordinates,
                        dimensions);

        Arrays.fill(
                destination,
                0,
                parameterLength,
                0.0);

        backprop(
                output,
                interaction.caches(),
                new ParameterGradientAccumulator(
                        parameterStart,
                        parameterLength,
                destination));
    }

    private void validateParameterDerivativeRange(
            int parameterStart,
            int parameterLength,
            double[] destination) {

        Objects.requireNonNull(
                destination,
                "destination");

        if (parameterStart < 0
                || parameterLength < 1
                || parameterStart > parameters.length - parameterLength) {

            throw new IllegalArgumentException(
                    "invalid parameter derivative range");
        }

        if (destination.length < parameterLength) {
            throw new IllegalArgumentException(
                    "parameter derivative destination too small");
        }
    }

    SpatialEvaluation spatialEvaluation(
            QuantumCoordinates coordinates) {

        validate(coordinates);

        int dimensions =
                3 * molecule.electrons().value();

        Interaction interaction =
                interaction(
                        coordinates,
                        dimensions);

        Output output =
                output(
                        interaction.one(),
                        coordinates,
                        dimensions);

        return new SpatialEvaluation(
                output.sign(),
                output.logAbsolute(),
                output.logGradient(),
                output.laplacianOverWavefunction());
    }

    NuclearEvaluation nuclearEvaluation(
            QuantumCoordinates coordinates) {

        validate(coordinates);

        int dimensions =
                3 * molecule.nuclei().size();

        Interaction interaction =
                interaction(
                        coordinates,
                        dimensions,
                        DerivativeDomain.NUCLEAR);

        Output output =
                output(
                        interaction.one(),
                        coordinates,
                        dimensions,
                        DerivativeDomain.NUCLEAR);

        return new NuclearEvaluation(
                output.sign(),
                output.logAbsolute(),
                output.logGradient());
    }

    DirectionalEvaluation directionalEvaluation(
            QuantumCoordinates coordinates,
            double[] nuclearDirection,
            double[] electronDirection) {

        validate(coordinates);
        Objects.requireNonNull(nuclearDirection, "nuclearDirection");
        Objects.requireNonNull(electronDirection, "electronDirection");
        int dimensions = 3 * molecule.electrons().value();
        if (nuclearDirection.length != 3 * molecule.nuclei().size()
                || electronDirection.length != dimensions
                || Arrays.stream(nuclearDirection).anyMatch(x -> !Double.isFinite(x))
                || Arrays.stream(electronDirection).anyMatch(x -> !Double.isFinite(x))) {
            throw new IllegalArgumentException("invalid FermiNet coordinate direction");
        }

        Interaction interaction = interaction(
                coordinates, dimensions, DerivativeDomain.DIRECTIONAL,
                nuclearDirection, electronDirection);
        Output output = output(
                interaction.one(), coordinates, dimensions,
                DerivativeDomain.DIRECTIONAL, nuclearDirection, electronDirection);
        FermiNetSpatialJet wavefunction = output.wavefunction();
        return new DirectionalEvaluation(
                output.sign(), output.logAbsolute(),
                wavefunction.directionalValue() / wavefunction.value(),
                output.laplacianOverWavefunction(),
                (wavefunction.directionalLaplacian() * wavefunction.value()
                        - wavefunction.laplacian() * wavefunction.directionalValue())
                        / (wavefunction.value() * wavefunction.value()));
    }

    BatchedDirectionalEvaluation batchedDirectionalEvaluation(
            QuantumCoordinates coordinates,
            double[][] nuclearDirections,
            double[][] electronDirections) {
        return batchedDirectionalEvaluation(coordinates, nuclearDirections,
                electronDirections, new FermiNetBatchedJetWorkspace());
    }

    BatchedDirectionalEvaluation batchedDirectionalEvaluation(
            QuantumCoordinates coordinates,
            double[][] nuclearDirections,
            double[][] electronDirections,
            FermiNetBatchedJetWorkspace workspace) {
        validate(coordinates);
        DirectionBatch directions = validateDirectionBatch(
                nuclearDirections, electronDirections,
                Objects.requireNonNull(workspace, "workspace"));
        int dimensions = 3 * molecule.electrons().value();
        Interaction interaction = interaction(coordinates, dimensions,
                DerivativeDomain.BATCHED_DIRECTIONAL, null, null, directions);
        Output output = output(interaction.one(), coordinates, dimensions,
                DerivativeDomain.BATCHED_DIRECTIONAL, null, null, directions);
        if (!(output.wavefunction() instanceof FermiNetBatchedSpatialJet wavefunction)) {
            throw new IllegalStateException("batched derivative wavefunction is absent");
        }
        double[] logDirections = new double[directions.size()];
        double[] laplacianDirections = new double[directions.size()];
        double value = wavefunction.value();
        double valueSquared = value * value;
        for (int direction = 0; direction < directions.size(); direction++) {
            double directionalValue = wavefunction.directionalValue(direction);
            logDirections[direction] = directionalValue / value;
            laplacianDirections[direction] =
                    (wavefunction.directionalLaplacian(direction) * value
                            - wavefunction.laplacian() * directionalValue)
                            / valueSquared;
        }
        return new BatchedDirectionalEvaluation(
                output.sign(), output.logAbsolute(), output.logGradient(),
                output.laplacianOverWavefunction(), logDirections,
                laplacianDirections);
    }

    private DirectionBatch validateDirectionBatch(
            double[][] nuclearDirections,
            double[][] electronDirections,
            FermiNetBatchedJetWorkspace workspace) {
        Objects.requireNonNull(nuclearDirections, "nuclearDirections");
        Objects.requireNonNull(electronDirections, "electronDirections");
        int size = nuclearDirections.length;
        if (size < 1 || electronDirections.length != size) {
            throw new IllegalArgumentException("invalid derivative direction batch");
        }
        int nuclearDimensions = 3 * molecule.nuclei().size();
        int electronDimensions = 3 * molecule.electrons().value();
        double[][] nuclear = new double[size][];
        double[][] electron = new double[size][];
        for (int direction = 0; direction < size; direction++) {
            nuclear[direction] = requireDirection(
                    nuclearDirections[direction], nuclearDimensions);
            electron[direction] = requireDirection(
                    electronDirections[direction], electronDimensions);
        }
        return new DirectionBatch(nuclear, electron, workspace);
    }

    private static double[] requireDirection(double[] values, int length) {
        Objects.requireNonNull(values, "direction");
        if (values.length != length
                || Arrays.stream(values).anyMatch(x -> !Double.isFinite(x))) {
            throw new IllegalArgumentException("invalid FermiNet coordinate direction");
        }
        return values.clone();
    }

    /**
     * Package-private diagnostic snapshot used exclusively by reference-parity
     * tests.
     *
     * <p>This deliberately does not form part of the public FermiNet API.
     */
    ReferenceSnapshot referenceSnapshot(
            QuantumCoordinates coordinates) {

        validate(coordinates);

        /*
         * Value path gives us the exact scalar arrays entering and leaving
         * every interaction layer.
         */
        ValueInputs raw =
                valueInputs(coordinates);

        ValueInteraction valueInteraction =
                valueInteraction(coordinates);

        List<LayerReferenceSnapshot> layers =
                new ArrayList<>();

        List<LayerCache> caches =
                valueInteraction.caches();

        for (int layer = 0;
             layer < caches.size();
             layer++) {

            LayerCache cache =
                    caches.get(layer);

            /*
             * The next layer's input is this layer's output.
             *
             * For the final layer, the final one-electron result is stored
             * directly in ValueInteraction.one().
             */
            double[][] postOne =
                    layer + 1 < caches.size()
                            ? caches.get(layer + 1).one()
                            : valueInteraction.one();

            /*
             * With useLastLayer=false, the terminal layer does not transform
             * the two-electron stream. Therefore its post-two value equals
             * its incoming pair stream.
             */
            double[][][] postTwo =
                    layer + 1 < caches.size()
                            ? caches.get(layer + 1).two()
                            : cache.two();

            layers.add(
                    new LayerReferenceSnapshot(
                            layer,
                            cache.aggregate(),
                            postOne,
                            postTwo,
                            cache.transformTwo()));
        }

        /*
         * Spatial path is used for determinant output because that is the
         * actual production wavefunction evaluator.
         */
        int dimensions =
                3 * molecule.electrons().value();

        Interaction spatialInteraction =
                interaction(
                        coordinates,
                        dimensions);

        Output output =
                output(
                        spatialInteraction.one(),
                        coordinates,
                        dimensions);

        List<DeterminantReferenceSnapshot> determinants =
                new ArrayList<>();

        for (int d = 0;
             d < output.data().length;
             d++) {

            DeterminantData determinant =
                    output.data()[d];

            determinants.add(
                    new DeterminantReferenceSnapshot(
                            d,
                            determinant.matrix().values(),
                            determinant.sign(),
                            determinant.logMagnitude()));
        }

        return new ReferenceSnapshot(
                raw.one(),
                raw.two(),
                layers,
                determinants,
                output.sign(),
                output.logAbsolute());
    }

    /**
     * HF orbital-pretraining objective.
     *
     * <p>For full determinants, spin-separated HF matrices are embedded into
     * a block-diagonal N x N target.
     */
    public PretrainingEvaluation pretrainingEvaluation(
            QuantumCoordinates coordinates,
            double[][] targetAlpha,
            double[][] targetBeta) {

        validate(coordinates);

        int alpha =
                molecule.spin().alphaElectrons();

        int beta =
                molecule.spin().betaElectrons();

        validateTarget(
                targetAlpha,
                alpha,
                "alpha");

        validateTarget(
                targetBeta,
                beta,
                "beta");

        int n =
                alpha + beta;

        double[][] target =
                new double[n][n];

        for (int i = 0;
             i < alpha;
             i++) {

            System.arraycopy(
                    targetAlpha[i],
                    0,
                    target[i],
                    0,
                    alpha);
        }

        for (int i = 0;
             i < beta;
             i++) {

            System.arraycopy(
                    targetBeta[i],
                    0,
                    target[alpha + i],
                    alpha,
                    beta);
        }

        ValueInteraction interaction =
                valueInteraction(coordinates);

        double[] gradient =
                new double[parameters.length];

        double[][] adjOne =
                new double[n]
                        [configuration.oneElectronWidth()];

        double loss =
                0.0;

        int determinants =
                configuration.determinants();

        for (int d = 0;
             d < determinants;
             d++) {

            DenseOrbitalMatrix matrix =
                    denseOrbitalValueMatrix(
                            interaction.one(),
                            coordinates,
                            d);

            loss +=
                    accumulateOrbitalMse(
                            matrix,
                            target,
                            d,
                            adjOne,
                            gradient);
        }

        backpropLayers(
                adjOne,
                new ParameterGradientAccumulator(
                        0,
                        gradient.length,
                        gradient),
                interaction.caches());

        return new PretrainingEvaluation(
                loss,
                gradient);
    }

    /*
     * =====================================================================
     * Value interaction stack
     * =====================================================================
     */

    private ValueInteraction valueInteraction(
            QuantumCoordinates coordinates) {

        ValueInputs input =
                valueInputs(coordinates);

        double[][] one =
                input.one();

        double[][][] two =
                input.two();

        List<LayerCache> caches =
                new ArrayList<>();

        for (int layer = 0;
             layer < configuration.interactionLayers();
             layer++) {

            double[][] aggregate =
                    aggregateValues(
                            one,
                            two);

            var oneWeight =
                    layout.block(
                            "interaction."
                                    + layer
                                    + ".one.weight");

            var oneBias =
                    layout.block(
                            "interaction."
                                    + layer
                                    + ".one.bias");

            double[][] oneTanh =
                    new double[one.length]
                            [configuration.oneElectronWidth()];

            double[][] nextOne =
                    new double[one.length]
                            [configuration.oneElectronWidth()];

            boolean oneResidual =
                    one[0].length
                            == configuration.oneElectronWidth();

            for (int i = 0;
                 i < one.length;
                 i++) {

                for (int o = 0;
                     o < configuration.oneElectronWidth();
                     o++) {

                    double z =
                            parameters[
                                    oneBias.startInclusive()
                                            + o];

                    int base =
                            oneWeight.startInclusive()
                                    + o
                                    * aggregate[i].length;

                    for (int k = 0;
                         k < aggregate[i].length;
                         k++) {

                        z +=
                                parameters[base + k]
                                        * aggregate[i][k];
                    }

                    double transformed =
                            Math.tanh(z);

                    oneTanh[i][o] =
                            transformed;

                    nextOne[i][o] =
                            oneResidual
                                    ? (one[i][o]
                                    + transformed)
                                    * INV_SQRT_2
                                    : transformed;
                }
            }

            boolean transformTwo =
                    layer
                            < configuration.interactionLayers()
                            - 1
                            || configuration.useLastLayer();

            double[][][] twoTanh =
                    null;

            double[][][] nextTwo =
                    two;

            if (transformTwo) {

                var twoWeight =
                        layout.block(
                                "interaction."
                                        + layer
                                        + ".two.weight");

                var twoBias =
                        layout.block(
                                "interaction."
                                        + layer
                                        + ".two.bias");

                twoTanh =
                        new double[two.length]
                                [two.length]
                                [configuration.twoElectronWidth()];

                nextTwo =
                        new double[two.length]
                                [two.length]
                                [configuration.twoElectronWidth()];

                boolean twoResidual =
                        two[0][0].length
                                == configuration.twoElectronWidth();

                for (int i = 0;
                     i < two.length;
                     i++) {

                    for (int j = 0;
                         j < two.length;
                         j++) {

                        for (int o = 0;
                             o < configuration.twoElectronWidth();
                             o++) {

                            double z =
                                    parameters[
                                            twoBias.startInclusive()
                                                    + o];

                            int base =
                                    twoWeight.startInclusive()
                                            + o
                                            * two[i][j].length;

                            for (int k = 0;
                                 k < two[i][j].length;
                                 k++) {

                                z +=
                                        parameters[base + k]
                                                * two[i][j][k];
                            }

                            double transformed =
                                    Math.tanh(z);

                            twoTanh[i][j][o] =
                                    transformed;

                            nextTwo[i][j][o] =
                                    twoResidual
                                            ? (two[i][j][o]
                                            + transformed)
                                            * INV_SQRT_2
                                            : transformed;
                        }
                    }
                }
            }

            caches.add(
                    new LayerCache(
                            deepCopy(one),
                            deepCopy(two),
                            deepCopy(aggregate),
                            deepCopy(oneTanh),
                            twoTanh == null
                                    ? null
                                    : deepCopy(twoTanh),
                            transformTwo));

            one =
                    nextOne;

            two =
                    nextTwo;
        }

        return new ValueInteraction(
                one,
                List.copyOf(caches));
    }

    /*
     * =====================================================================
     * Spatial interaction stack
     * =====================================================================
     */

    private Interaction interaction(
            QuantumCoordinates coordinates,
            int dimensions) {

        return interaction(
                coordinates,
                dimensions,
                DerivativeDomain.ELECTRON);
    }

    private Interaction interaction(
            QuantumCoordinates coordinates,
            int dimensions,
            DerivativeDomain derivativeDomain) {

        return interaction(coordinates, dimensions, derivativeDomain,
                null, null, null);
    }

    private Interaction interaction(
            QuantumCoordinates coordinates,
            int dimensions,
            DerivativeDomain derivativeDomain,
            double[] nuclearDirection,
            double[] electronDirection) {

        return interaction(coordinates, dimensions, derivativeDomain,
                nuclearDirection, electronDirection, null);
    }

    private Interaction interaction(
            QuantumCoordinates coordinates,
            int dimensions,
            DerivativeDomain derivativeDomain,
            double[] nuclearDirection,
            double[] electronDirection,
            DirectionBatch directionBatch) {

        Inputs input =
                inputs(
                        coordinates,
                        dimensions,
                        derivativeDomain,
                        nuclearDirection,
                        electronDirection,
                        directionBatch);

        FermiNetSpatialJet[][] one =
                input.one();

        FermiNetSpatialJet[][][] two =
                input.two();

        List<LayerCache> caches =
                new ArrayList<>();

        for (int layer = 0;
             layer < configuration.interactionLayers();
             layer++) {

            FermiNetSpatialJet[][] aggregate =
                    aggregate(
                            one,
                            two,
                            directionBatch);

            var oneWeight =
                    layout.block(
                            "interaction."
                                    + layer
                                    + ".one.weight");

            var oneBias =
                    layout.block(
                            "interaction."
                                    + layer
                                    + ".one.bias");

            FermiNetSpatialJet[][] oneTanh =
                    new FermiNetSpatialJet[one.length]
                            [configuration.oneElectronWidth()];

            FermiNetSpatialJet[][] nextOne =
                    new FermiNetSpatialJet[one.length]
                            [configuration.oneElectronWidth()];

            boolean oneResidual =
                    one[0].length
                            == configuration.oneElectronWidth();

            for (int i = 0;
                 i < one.length;
                 i++) {

                for (int o = 0;
                     o < configuration.oneElectronWidth();
                     o++) {

                    FermiNetSpatialJet z =
                            FermiNetSpatialJet.affine(
                                    aggregate[i],
                                    parameters,
                                    oneWeight.startInclusive()
                                            + o
                                            * aggregate[i].length,
                                    parameters[
                                            oneBias.startInclusive()
                                                    + o]);

                    FermiNetSpatialJet transformed =
                            z.tanh();

                    releaseBatched(directionBatch, z);

                    oneTanh[i][o] =
                            transformed;

                    if (oneResidual) {
                        FermiNetSpatialJet residual = transformed.add(one[i][o]);
                        nextOne[i][o] = residual.multiply(INV_SQRT_2);
                        releaseBatched(directionBatch, residual);
                    } else {
                        nextOne[i][o] = transformed;
                    }
                }
            }

            boolean transformTwo =
                    layer
                            < configuration.interactionLayers()
                            - 1
                            || configuration.useLastLayer();

            FermiNetSpatialJet[][][] twoTanh =
                    null;

            FermiNetSpatialJet[][][] nextTwo =
                    two;

            if (transformTwo) {

                var twoWeight =
                        layout.block(
                                "interaction."
                                        + layer
                                        + ".two.weight");

                var twoBias =
                        layout.block(
                                "interaction."
                                        + layer
                                        + ".two.bias");

                twoTanh =
                        new FermiNetSpatialJet[two.length]
                                [two.length]
                                [configuration.twoElectronWidth()];

                nextTwo =
                        new FermiNetSpatialJet[two.length]
                                [two.length]
                                [configuration.twoElectronWidth()];

                boolean twoResidual =
                        two[0][0].length
                                == configuration.twoElectronWidth();

                for (int i = 0;
                     i < two.length;
                     i++) {

                    for (int j = 0;
                         j < two.length;
                         j++) {

                        for (int o = 0;
                             o < configuration.twoElectronWidth();
                             o++) {

                            FermiNetSpatialJet z =
                                    FermiNetSpatialJet.affine(
                                            two[i][j],
                                            parameters,
                                            twoWeight.startInclusive()
                                                    + o
                                                    * two[i][j].length,
                                            parameters[
                                                    twoBias.startInclusive()
                                                            + o]);

                            FermiNetSpatialJet transformed =
                                    z.tanh();

                            releaseBatched(directionBatch, z);

                            twoTanh[i][j][o] =
                                    transformed;

                            if (twoResidual) {
                                FermiNetSpatialJet residual =
                                        transformed.add(two[i][j][o]);
                                nextTwo[i][j][o] =
                                        residual.multiply(INV_SQRT_2);
                                releaseBatched(directionBatch, residual);
                            } else {
                                nextTwo[i][j][o] = transformed;
                            }
                        }
                    }
                }
            }

            caches.add(
                    new LayerCache(
                            values(one),
                            values(two),
                            values(aggregate),
                            values(oneTanh),
                            twoTanh == null
                                    ? null
                                    : values(twoTanh),
                            transformTwo));

            if (directionBatch != null) {
                var retained = java.util.Collections.newSetFromMap(
                        new java.util.IdentityHashMap<FermiNetSpatialJet, Boolean>());
                retainAll(retained, nextOne);
                retainAll(retained, nextTwo);
                releaseAll(directionBatch, retained, one);
                releaseAll(directionBatch, retained, two);
                releaseAll(directionBatch, retained, aggregate);
                releaseAll(directionBatch, retained, oneTanh);
                if (twoTanh != null) {
                    releaseAll(directionBatch, retained, twoTanh);
                }
            }

            one =
                    nextOne;

            two =
                    nextTwo;
        }

        return new Interaction(
                one,
                List.copyOf(caches));
    }

    /*
     * =====================================================================
     * Input features
     * =====================================================================
     */

    private ValueInputs valueInputs(
            QuantumCoordinates coordinates) {

        int n =
                coordinates.particles().size();

        int nuclei =
                molecule.nuclei().size();

        double[][] one =
                new double[n][4 * nuclei];

        double[][][] two =
                new double[n][n][4];

        for (int i = 0;
             i < n;
             i++) {

            var electron =
                    coordinates.particles().get(i);

            for (int a = 0;
                 a < nuclei;
                 a++) {

                var nucleus =
                        molecule.nuclei()
                                .get(a)
                                .position()
                                .inBohr();

                double dx =
                        electron.xBohr()
                                - nucleus.x();

                double dy =
                        electron.yBohr()
                                - nucleus.y();

                double dz =
                        electron.zBohr()
                                - nucleus.z();

                int base =
                        4 * a;

                one[i][base] =
                        distance(
                                dx,
                                dy,
                                dz);

                one[i][base + 1] =
                        dx;

                one[i][base + 2] =
                        dy;

                one[i][base + 3] =
                        dz;
            }
        }

        for (int i = 0;
             i < n;
             i++) {

            var ri =
                    coordinates.particles().get(i);

            for (int j = 0;
                 j < n;
                 j++) {

                if (i == j) {
                    continue;
                }

                var rj =
                        coordinates.particles().get(j);

                double dx =
                        rj.xBohr()
                                - ri.xBohr();

                double dy =
                        rj.yBohr()
                                - ri.yBohr();

                double dz =
                        rj.zBohr()
                                - ri.zBohr();

                two[i][j][0] =
                        distance(
                                dx,
                                dy,
                                dz);

                two[i][j][1] =
                        dx;

                two[i][j][2] =
                        dy;

                two[i][j][3] =
                        dz;
            }
        }

        return new ValueInputs(
                one,
                two);
    }

    private Inputs inputs(
            QuantumCoordinates coordinates,
            int dimensions) {

        return inputs(
                coordinates,
                dimensions,
                DerivativeDomain.ELECTRON);
    }

    private Inputs inputs(
            QuantumCoordinates coordinates,
            int dimensions,
            DerivativeDomain derivativeDomain) {

        return inputs(coordinates, dimensions, derivativeDomain,
                null, null, null);
    }

    private Inputs inputs(
            QuantumCoordinates coordinates,
            int dimensions,
            DerivativeDomain derivativeDomain,
            double[] nuclearDirection,
            double[] electronDirection) {

        return inputs(coordinates, dimensions, derivativeDomain,
                nuclearDirection, electronDirection, null);
    }

    private Inputs inputs(
            QuantumCoordinates coordinates,
            int dimensions,
            DerivativeDomain derivativeDomain,
            double[] nuclearDirection,
            double[] electronDirection,
            DirectionBatch directionBatch) {

        int n =
                coordinates.particles().size();

        int nuclei =
                molecule.nuclei().size();

        FermiNetSpatialJet[][] xyz =
                new FermiNetSpatialJet[n][3];

        FermiNetSpatialJet[][] one =
                new FermiNetSpatialJet[n]
                        [4 * nuclei];

        FermiNetSpatialJet[][][] two =
                new FermiNetSpatialJet[n][n][4];

        for (int i = 0;
             i < n;
             i++) {

            var particle =
                    coordinates.particles().get(i);

            xyz[i][0] =
                    derivativeDomain == DerivativeDomain.BATCHED_DIRECTIONAL
                            ? FermiNetBatchedSpatialJet.variable(
                                    directionBatch.workspace(), particle.xBohr(),
                                    dimensions, 3 * i,
                                    directionBatch.electronValues(3 * i))
                            : derivativeDomain == DerivativeDomain.DIRECTIONAL
                            ? FermiNetSpatialJet.directionalVariable(
                                    particle.xBohr(), dimensions, 3 * i,
                                    electronDirection[3 * i])
                            : derivativeDomain == DerivativeDomain.ELECTRON
                            ? FermiNetSpatialJet.variable(
                                    particle.xBohr(),
                                    dimensions,
                                    3 * i)
                            : FermiNetSpatialJet.constant(
                                    particle.xBohr(),
                                    dimensions);

            xyz[i][1] =
                    derivativeDomain == DerivativeDomain.BATCHED_DIRECTIONAL
                            ? FermiNetBatchedSpatialJet.variable(
                                    directionBatch.workspace(), particle.yBohr(),
                                    dimensions, 3 * i + 1,
                                    directionBatch.electronValues(3 * i + 1))
                            : derivativeDomain == DerivativeDomain.DIRECTIONAL
                            ? FermiNetSpatialJet.directionalVariable(
                                    particle.yBohr(), dimensions, 3 * i + 1,
                                    electronDirection[3 * i + 1])
                            : derivativeDomain == DerivativeDomain.ELECTRON
                            ? FermiNetSpatialJet.variable(
                                    particle.yBohr(),
                                    dimensions,
                                    3 * i + 1)
                            : FermiNetSpatialJet.constant(
                                    particle.yBohr(),
                                    dimensions);

            xyz[i][2] =
                    derivativeDomain == DerivativeDomain.BATCHED_DIRECTIONAL
                            ? FermiNetBatchedSpatialJet.variable(
                                    directionBatch.workspace(), particle.zBohr(),
                                    dimensions, 3 * i + 2,
                                    directionBatch.electronValues(3 * i + 2))
                            : derivativeDomain == DerivativeDomain.DIRECTIONAL
                            ? FermiNetSpatialJet.directionalVariable(
                                    particle.zBohr(), dimensions, 3 * i + 2,
                                    electronDirection[3 * i + 2])
                            : derivativeDomain == DerivativeDomain.ELECTRON
                            ? FermiNetSpatialJet.variable(
                                    particle.zBohr(),
                                    dimensions,
                                    3 * i + 2)
                            : FermiNetSpatialJet.constant(
                                    particle.zBohr(),
                                    dimensions);

            for (int a = 0;
                 a < nuclei;
                 a++) {

                var nucleus =
                        molecule.nuclei()
                                .get(a)
                                .position()
                                .inBohr();

                FermiNetSpatialJet nucleusX =
                        derivativeDomain == DerivativeDomain.BATCHED_DIRECTIONAL
                                ? FermiNetBatchedSpatialJet.constant(
                                        directionBatch.workspace(), nucleus.x(), dimensions,
                                        directionBatch.nuclearValues(3 * a))
                                : derivativeDomain == DerivativeDomain.DIRECTIONAL
                                ? FermiNetSpatialJet.directionalConstant(
                                        nucleus.x(), dimensions, nuclearDirection[3 * a])
                                : derivativeDomain == DerivativeDomain.NUCLEAR
                                ? FermiNetSpatialJet.variable(
                                        nucleus.x(),
                                        dimensions,
                                        3 * a)
                                : FermiNetSpatialJet.constant(
                                        nucleus.x(),
                                        dimensions);

                FermiNetSpatialJet nucleusY =
                        derivativeDomain == DerivativeDomain.BATCHED_DIRECTIONAL
                                ? FermiNetBatchedSpatialJet.constant(
                                        directionBatch.workspace(), nucleus.y(), dimensions,
                                        directionBatch.nuclearValues(3 * a + 1))
                                : derivativeDomain == DerivativeDomain.DIRECTIONAL
                                ? FermiNetSpatialJet.directionalConstant(
                                        nucleus.y(), dimensions, nuclearDirection[3 * a + 1])
                                : derivativeDomain == DerivativeDomain.NUCLEAR
                                ? FermiNetSpatialJet.variable(
                                        nucleus.y(),
                                        dimensions,
                                        3 * a + 1)
                                : FermiNetSpatialJet.constant(
                                        nucleus.y(),
                                        dimensions);

                FermiNetSpatialJet nucleusZ =
                        derivativeDomain == DerivativeDomain.BATCHED_DIRECTIONAL
                                ? FermiNetBatchedSpatialJet.constant(
                                        directionBatch.workspace(), nucleus.z(), dimensions,
                                        directionBatch.nuclearValues(3 * a + 2))
                                : derivativeDomain == DerivativeDomain.DIRECTIONAL
                                ? FermiNetSpatialJet.directionalConstant(
                                        nucleus.z(), dimensions, nuclearDirection[3 * a + 2])
                                : derivativeDomain == DerivativeDomain.NUCLEAR
                                ? FermiNetSpatialJet.variable(
                                        nucleus.z(),
                                        dimensions,
                                        3 * a + 2)
                                : FermiNetSpatialJet.constant(
                                        nucleus.z(),
                                        dimensions);

                var dx =
                        xyz[i][0]
                                .subtract(
                                        nucleusX);

                var dy =
                        xyz[i][1]
                                .subtract(
                                        nucleusY);

                var dz =
                        xyz[i][2]
                                .subtract(
                                        nucleusZ);

                int base =
                        4 * a;

                one[i][base] =
                        norm(
                                dx,
                                dy,
                                dz);

                one[i][base + 1] =
                        dx;

                one[i][base + 2] =
                        dy;

                one[i][base + 3] =
                        dz;
            }
        }

        for (int i = 0;
             i < n;
             i++) {

            for (int j = 0;
                 j < n;
                 j++) {

                if (i == j) {

                    for (int k = 0;
                         k < 4;
                         k++) {

                        two[i][j][k] =
                                FermiNetSpatialJet.constant(
                                        0.0,
                                        dimensions);
                    }

                    continue;
                }

                var dx =
                        xyz[j][0]
                                .subtract(
                                        xyz[i][0]);

                var dy =
                        xyz[j][1]
                                .subtract(
                                        xyz[i][1]);

                var dz =
                        xyz[j][2]
                                .subtract(
                                        xyz[i][2]);

                two[i][j][0] =
                        norm(
                                dx,
                                dy,
                                dz);

                two[i][j][1] =
                        dx;

                two[i][j][2] =
                        dy;

                two[i][j][3] =
                        dz;
            }
        }

        if (directionBatch != null) {
            releaseAll(directionBatch, java.util.Set.of(), xyz);
        }

        return new Inputs(
                one,
                two);
    }

    /*
     * =====================================================================
     * Symmetric aggregation
     * =====================================================================
     */

    private double[][] aggregateValues(
            double[][] one,
            double[][][] two) {

        int n =
                one.length;

        int oneWidth =
                one[0].length;

        int twoWidth =
                two[0][0].length;

        int alpha =
                molecule.spin()
                        .alphaElectrons();

        int beta =
                molecule.spin()
                        .betaElectrons();

        int activeChannels =
                (alpha > 0 ? 1 : 0)
                        + (beta > 0 ? 1 : 0);

        double[][] result =
                new double[n][
                        (activeChannels + 1)
                                * oneWidth
                                + activeChannels
                                * twoWidth];

        double[] meanAlpha =
                alpha > 0
                        ? meanRows(
                        one,
                        0,
                        alpha)
                        : null;

        double[] meanBeta =
                beta > 0
                        ? meanRows(
                        one,
                        alpha,
                        alpha + beta)
                        : null;

        for (int electron = 0;
             electron < n;
             electron++) {

            int at =
                    0;

            System.arraycopy(
                    one[electron],
                    0,
                    result[electron],
                    at,
                    oneWidth);

            at +=
                    oneWidth;

            if (alpha > 0) {

                System.arraycopy(
                        meanAlpha,
                        0,
                        result[electron],
                        at,
                        oneWidth);

                at +=
                        oneWidth;
            }

            if (beta > 0) {

                System.arraycopy(
                        meanBeta,
                        0,
                        result[electron],
                        at,
                        oneWidth);

                at +=
                        oneWidth;
            }

            if (alpha > 0) {

                for (int k = 0;
                     k < twoWidth;
                     k++) {

                    double sum =
                            0.0;

                    for (int source = 0;
                         source < alpha;
                         source++) {

                        sum +=
                                two[source]
                                        [electron]
                                        [k];
                    }

                    result[electron][at++] =
                            sum / alpha;
                }
            }

            if (beta > 0) {

                for (int k = 0;
                     k < twoWidth;
                     k++) {

                    double sum =
                            0.0;

                    for (int source = alpha;
                         source < alpha + beta;
                         source++) {

                        sum +=
                                two[source]
                                        [electron]
                                        [k];
                    }

                    result[electron][at++] =
                            sum / beta;
                }
            }
        }

        return result;
    }

    private FermiNetSpatialJet[][] aggregate(
            FermiNetSpatialJet[][] one,
            FermiNetSpatialJet[][][] two,
            DirectionBatch directionBatch) {

        int n =
                one.length;

        int oneWidth =
                one[0].length;

        int twoWidth =
                two[0][0].length;

        int alpha =
                molecule.spin()
                        .alphaElectrons();

        int beta =
                molecule.spin()
                        .betaElectrons();

        int activeChannels =
                (alpha > 0 ? 1 : 0)
                        + (beta > 0 ? 1 : 0);

        int dimensions =
                one[0][0].dimensions();

        FermiNetSpatialJet[][] result =
                new FermiNetSpatialJet[n][
                        (activeChannels + 1)
                                * oneWidth
                                + activeChannels
                                * twoWidth];

        FermiNetSpatialJet[] meanAlpha =
                alpha > 0
                        ? meanRows(
                        one,
                        0,
                        alpha,
                        directionBatch)
                        : null;

        FermiNetSpatialJet[] meanBeta =
                beta > 0
                        ? meanRows(
                        one,
                        alpha,
                        alpha + beta,
                        directionBatch)
                        : null;

        for (int electron = 0;
             electron < n;
             electron++) {

            int at =
                    0;

            at =
                    copy(
                            result[electron],
                            at,
                            one[electron]);

            if (alpha > 0) {

                at =
                        copy(
                                result[electron],
                                at,
                                meanAlpha);
            }

            if (beta > 0) {

                at =
                        copy(
                                result[electron],
                                at,
                                meanBeta);
            }

            if (alpha > 0) {

                for (int k = 0;
                     k < twoWidth;
                     k++) {

                    FermiNetSpatialJet sum =
                            FermiNetSpatialJet.constant(
                                    0.0,
                                    dimensions);

                    for (int source = 0;
                         source < alpha;
                         source++) {

                        FermiNetSpatialJet next = sum.add(
                                two[source][electron][k]);
                        releaseBatched(directionBatch, sum);
                        sum = next;
                    }

                    result[electron][at++] = sum.multiply(1.0 / alpha);
                    releaseBatched(directionBatch, sum);
                }
            }

            if (beta > 0) {

                for (int k = 0;
                     k < twoWidth;
                     k++) {

                    FermiNetSpatialJet sum =
                            FermiNetSpatialJet.constant(
                                    0.0,
                                    dimensions);

                    for (int source = alpha;
                         source < alpha + beta;
                         source++) {

                        FermiNetSpatialJet next = sum.add(
                                two[source][electron][k]);
                        releaseBatched(directionBatch, sum);
                        sum = next;
                    }

                    result[electron][at++] = sum.multiply(1.0 / beta);
                    releaseBatched(directionBatch, sum);
                }
            }
        }

        return result;
    }

    /*
     * =====================================================================
     * Dense orbitals
     * =====================================================================
     */

    private DenseOrbitalMatrix denseOrbitalValueMatrix(
            double[][] one,
            QuantumCoordinates coordinates,
            int determinant) {

        int n =
                molecule.electrons()
                        .value();

        int alpha =
                molecule.spin()
                        .alphaElectrons();

        int width =
                one[0].length;

        int nuclei =
                molecule.nuclei()
                        .size();

        double[][] values =
                new double[n][n];

        double[][] raw =
                new double[n][n];

        double[][] envelope =
                new double[n][n];

        double[][] distances =
                new double[n][nuclei];

        double[][] features =
                new double[n][width];

        for (int i = 0;
             i < n;
             i++) {

            System.arraycopy(
                    one[i],
                    0,
                    features[i],
                    0,
                    width);

            for (int a = 0;
                 a < nuclei;
                 a++) {

                var electron =
                        coordinates.particles()
                                .get(i);

                var nucleus =
                        molecule.nuclei()
                                .get(a)
                                .position()
                                .inBohr();

                distances[i][a] =
                        distance(
                                electron.xBohr()
                                        - nucleus.x(),
                                electron.yBohr()
                                        - nucleus.y(),
                                electron.zBohr()
                                        - nucleus.z());
            }

            String spin =
                    i < alpha
                            ? "alpha"
                            : "beta";

            var weight =
                    layout.block(
                            "orbital."
                                    + spin
                                    + ".weight");

            var pi =
                    layout.block(
                            "envelope."
                                    + spin
                                    + ".pi");

            var sigma =
                    layout.block(
                            "envelope."
                                    + spin
                                    + ".sigma");

            for (int orbital = 0;
                 orbital < n;
                 orbital++) {

                int head =
                        determinant
                                * n
                                + orbital;

                int weightBase =
                        weight.startInclusive()
                                + head
                                * width;

                double r =
                        0.0;

                for (int k = 0;
                     k < width;
                     k++) {

                    r +=
                            parameters[
                                    weightBase
                                            + k]
                                    * one[i][k];
                }

                raw[i][orbital] =
                        r;

                double env =
                        0.0;

                for (int a = 0;
                     a < nuclei;
                     a++) {

                    int index =
                            head
                                    * nuclei
                                    + a;

                    env +=
                            parameters[
                                    pi.startInclusive()
                                            + index]
                                    * Math.exp(
                                    -parameters[
                                            sigma.startInclusive()
                                                    + index]
                                            * distances[i][a]);
                }

                envelope[i][orbital] =
                        env;

                values[i][orbital] =
                        r * env;
            }
        }

        return new DenseOrbitalMatrix(
                null,
                values,
                raw,
                envelope,
                distances,
                features);
    }

    private DenseOrbitalMatrix denseOrbitalMatrix(
            FermiNetSpatialJet[][] one,
            QuantumCoordinates coordinates,
            int determinant,
            int dimensions) {

        return denseOrbitalMatrix(
                one,
                coordinates,
                determinant,
                dimensions,
                DerivativeDomain.ELECTRON);
    }

    private DenseOrbitalMatrix denseOrbitalMatrix(
            FermiNetSpatialJet[][] one,
            QuantumCoordinates coordinates,
            int determinant,
            int dimensions,
            DerivativeDomain derivativeDomain) {

        return denseOrbitalMatrix(one, coordinates, determinant, dimensions,
                derivativeDomain, null, null, null);
    }

    private DenseOrbitalMatrix denseOrbitalMatrix(
            FermiNetSpatialJet[][] one,
            QuantumCoordinates coordinates,
            int determinant,
            int dimensions,
            DerivativeDomain derivativeDomain,
            double[] nuclearDirection,
            double[] electronDirection) {

        return denseOrbitalMatrix(one, coordinates, determinant, dimensions,
                derivativeDomain, nuclearDirection, electronDirection, null);
    }

    private DenseOrbitalMatrix denseOrbitalMatrix(
            FermiNetSpatialJet[][] one,
            QuantumCoordinates coordinates,
            int determinant,
            int dimensions,
            DerivativeDomain derivativeDomain,
            double[] nuclearDirection,
            double[] electronDirection,
            DirectionBatch directionBatch) {

        int n =
                molecule.electrons()
                        .value();

        int alpha =
                molecule.spin()
                        .alphaElectrons();

        int width =
                one[0].length;

        int nuclei =
                molecule.nuclei()
                        .size();

        FermiNetSpatialJet[][] jets =
                new FermiNetSpatialJet[n][n];

        double[][] raw =
                new double[n][n];

        double[][] envelope =
                new double[n][n];

        double[][] distances =
                new double[n][nuclei];

        double[][] features =
                new double[n][width];

        for (int i = 0;
             i < n;
             i++) {

            for (int k = 0;
                 k < width;
                 k++) {

                features[i][k] =
                        one[i][k].value();
            }

            for (int a = 0;
                 a < nuclei;
                 a++) {

                var electron =
                        coordinates.particles()
                                .get(i);

                var nucleus =
                        molecule.nuclei()
                                .get(a)
                                .position()
                                .inBohr();

                distances[i][a] =
                        distance(
                                electron.xBohr()
                                        - nucleus.x(),
                                electron.yBohr()
                                        - nucleus.y(),
                                electron.zBohr()
                                        - nucleus.z());
            }

            String spin =
                    i < alpha
                            ? "alpha"
                            : "beta";

            var weight =
                    layout.block(
                            "orbital."
                                    + spin
                                    + ".weight");

            var pi =
                    layout.block(
                            "envelope."
                                    + spin
                                    + ".pi");

            var sigma =
                    layout.block(
                            "envelope."
                                    + spin
                                    + ".sigma");

            for (int orbital = 0;
                 orbital < n;
                 orbital++) {

                int head =
                        determinant
                                * n
                                + orbital;

                FermiNetSpatialJet r =
                        FermiNetSpatialJet.affine(
                                one[i],
                                parameters,
                                weight.startInclusive()
                                        + head
                                        * width,
                                0.0);

                raw[i][orbital] =
                        r.value();

                FermiNetSpatialJet env =
                        FermiNetSpatialJet.constant(
                                0.0,
                                dimensions);

                for (int a = 0;
                     a < nuclei;
                     a++) {

                    FermiNetSpatialJet d =
                            electronNuclearDistance(
                                    coordinates,
                                    i,
                                    a,
                                    dimensions,
                                    derivativeDomain,
                                    nuclearDirection,
                                    electronDirection,
                                    directionBatch);

                    int index =
                            head
                                    * nuclei
                                    + a;

                    env =
                            env.add(
                                    d.multiply(
                                                    -parameters[
                                                            sigma.startInclusive()
                                                                    + index])
                                            .exp()
                                            .multiply(
                                                    parameters[
                                                            pi.startInclusive()
                                                                    + index]));
                }

                envelope[i][orbital] =
                        env.value();

                jets[i][orbital] =
                        r.multiply(env);
            }
        }

        return new DenseOrbitalMatrix(
                jets,
                values(jets),
                raw,
                envelope,
                distances,
                features);
    }

    /*
     * =====================================================================
     * Wavefunction
     * =====================================================================
     */

    private Output output(
            FermiNetSpatialJet[][] one,
            QuantumCoordinates coordinates,
            int dimensions) {

        return output(
                one,
                coordinates,
                dimensions,
                DerivativeDomain.ELECTRON);
    }

    private Output output(
            FermiNetSpatialJet[][] one,
            QuantumCoordinates coordinates,
            int dimensions,
            DerivativeDomain derivativeDomain) {

        return output(one, coordinates, dimensions, derivativeDomain,
                null, null, null);
    }

    private Output output(
            FermiNetSpatialJet[][] one,
            QuantumCoordinates coordinates,
            int dimensions,
            DerivativeDomain derivativeDomain,
            double[] nuclearDirection,
            double[] electronDirection) {

        return output(one, coordinates, dimensions, derivativeDomain,
                nuclearDirection, electronDirection, null);
    }

    private Output output(
            FermiNetSpatialJet[][] one,
            QuantumCoordinates coordinates,
            int dimensions,
            DerivativeDomain derivativeDomain,
            double[] nuclearDirection,
            double[] electronDirection,
            DirectionBatch directionBatch) {

        int determinants =
                configuration.determinants();

        DeterminantData[] data =
                new DeterminantData[
                        determinants];

        double maxLog =
                Double.NEGATIVE_INFINITY;

        for (int d = 0;
             d < determinants;
             d++) {

            DenseOrbitalMatrix matrix =
                    denseOrbitalMatrix(
                            one,
                            coordinates,
                            d,
                            dimensions,
                            derivativeDomain,
                            nuclearDirection,
                            electronDirection,
                            directionBatch);

            FermiNetSpatialJet det =
                    determinant(
                            matrix.jets());

            double detValue =
                    det.value();

            if (!Double.isFinite(detValue)
                    || Math.abs(detValue)
                    < SINGULAR_THRESHOLD) {

                throw new IllegalStateException(
                        "singular FermiNet determinant");
            }

            int sign =
                    detValue > 0.0
                            ? 1
                            : -1;

            double logMagnitude =
                    Math.log(
                            Math.abs(
                                    detValue));

            data[d] =
                    new DeterminantData(
                            matrix,
                            det,
                            sign,
                            logMagnitude);

            maxLog =
                    Math.max(
                            maxLog,
                            logMagnitude);
        }

        FermiNetSpatialJet scaled =
                FermiNetSpatialJet.constant(
                        0.0,
                        dimensions);

        for (DeterminantData determinant :
                data) {

            scaled =
                    scaled.add(
                            determinant.det()
                                    .multiply(
                                            Math.exp(
                                                    -maxLog)));
        }

        if (!Double.isFinite(
                scaled.value())
                || Math.abs(
                scaled.value())
                < SINGULAR_THRESHOLD) {

            throw new IllegalStateException(
                    "FermiNet determinant sum is numerically singular");
        }

        double[] logGradient =
                scaled.gradient();

        for (int i = 0;
             i < logGradient.length;
             i++) {

            logGradient[i] /=
                    scaled.value();
        }

        double[] mixture =
                new double[
                        data.length];

        for (int d = 0;
             d < data.length;
             d++) {

            mixture[d] =
                    data[d].sign()
                            * Math.exp(
                            data[d].logMagnitude()
                                    - maxLog)
                            / scaled.value();
        }

        return new Output(
                scaled.value() > 0.0
                        ? 1
                        : -1,
                maxLog
                        + Math.log(
                        Math.abs(
                                scaled.value())),
                logGradient,
                scaled.laplacian()
                        / scaled.value(),
                data,
                mixture,
                scaled);
    }

    /*
     * =====================================================================
     * Reverse mode
     * =====================================================================
     */

    private double[] backprop(
            Output output,
            List<LayerCache> caches) {

        double[] gradient =
                new double[
                        parameters.length];

        backprop(
                output,
                caches,
                new ParameterGradientAccumulator(
                        0,
                        gradient.length,
                        gradient));

        return gradient;
    }

    private void backprop(
            Output output,
            List<LayerCache> caches,
            ParameterGradientAccumulator gradient) {

        backprop(
                output,
                caches,
                gradient,
                null);
    }

    private void backprop(
            Output output,
            List<LayerCache> caches,
            ParameterGradientAccumulator gradient,
            FermiNetStructuredSrStatistics.Builder statistics) {

        double[][] adjOne =
                new double[
                        molecule.electrons()
                                .value()]
                        [configuration.oneElectronWidth()];

        for (int d = 0;
             d < output.data().length;
             d++) {

            accumulateOrbitals(
                    output.data()[d]
                            .matrix(),
                    output.mixture()[d],
                    d,
                    adjOne,
                    gradient,
                    statistics);
        }

        backpropLayers(
                adjOne,
                gradient,
                caches,
                statistics);
    }

    private void accumulateOrbitals(
            DenseOrbitalMatrix matrix,
            double mix,
            int determinant,
            double[][] adjOne,
            ParameterGradientAccumulator gradient,
            FermiNetStructuredSrStatistics.Builder statistics) {

        int n =
                molecule.electrons()
                        .value();

        int alpha =
                molecule.spin()
                        .alphaElectrons();

        int width =
                configuration.oneElectronWidth();

        int nuclei =
                molecule.nuclei()
                        .size();

        double[][] inverse =
                inverse(
                        matrix.values());

        if (statistics != null
                && determinant == 0) {
            if (alpha > 0) {
                statistics.denseInputs(
                        "orbital.alpha.weight",
                        Arrays.copyOfRange(
                                matrix.features(),
                                0,
                                alpha));
            }
            if (alpha < n) {
                statistics.denseInputs(
                        "orbital.beta.weight",
                        Arrays.copyOfRange(
                                matrix.features(),
                                alpha,
                                n));
            }
        }

        for (int i = 0;
             i < n;
             i++) {

            String spin =
                    i < alpha
                            ? "alpha"
                            : "beta";

            var weight =
                    layout.block(
                            "orbital."
                                    + spin
                                    + ".weight");

            var pi =
                    layout.block(
                            "envelope."
                                    + spin
                                    + ".pi");

            var sigma =
                    layout.block(
                            "envelope."
                                    + spin
                                    + ".sigma");

            for (int orbital = 0;
                 orbital < n;
                 orbital++) {

                double adjPhi =
                        mix
                                * inverse[
                                orbital]
                                [i];

                double raw =
                        matrix.raw()
                                [i]
                                [orbital];

                double env =
                        matrix.envelope()
                                [i]
                                [orbital];

                double adjRaw =
                        adjPhi
                                * env;

                double adjEnvelope =
                        adjPhi
                                * raw;

                int head =
                        determinant
                                * n
                                + orbital;

                int weightBase =
                        weight.startInclusive()
                                + head
                                * width;

                if (statistics != null) {
                    statistics.denseAdjoint(
                            weight.name(),
                            i < alpha ? i : i - alpha,
                            head,
                            adjRaw);
                }

                for (int k = 0;
                     k < width;
                     k++) {

                    gradient.add(
                            weightBase + k,
                            adjRaw
                                    * matrix.features()
                                    [i][k]);

                    adjOne[i][k] +=
                            adjRaw
                                    * parameters[
                                    weightBase
                                            + k];
                }

                for (int a = 0;
                     a < nuclei;
                     a++) {

                    int index =
                            head
                                    * nuclei
                                    + a;

                    double sigmaValue =
                            parameters[
                                    sigma.startInclusive()
                                            + index];

                    double piValue =
                            parameters[
                                    pi.startInclusive()
                                            + index];

                    double r =
                            matrix.distances()
                                    [i][a];

                    double decay =
                            Math.exp(
                                    -sigmaValue
                                            * r);

                    gradient.add(
                            pi.startInclusive()
                                    + index,
                            adjEnvelope
                                    * decay);

                    gradient.add(
                            sigma.startInclusive()
                                    + index,
                            adjEnvelope
                                    * piValue
                                    * decay
                                    * (-r));

                    if (statistics != null) {
                        statistics.addExplicit(
                                pi.name(),
                                index,
                                adjEnvelope * decay);
                        statistics.addExplicit(
                                sigma.name(),
                                index,
                                adjEnvelope
                                        * piValue
                                        * decay
                                        * (-r));
                    }
                }
            }
        }
    }

    private double accumulateOrbitalMse(
            DenseOrbitalMatrix matrix,
            double[][] target,
            int determinant,
            double[][] adjOne,
            double[] gradient) {

        int n =
                molecule.electrons()
                        .value();

        int alpha =
                molecule.spin()
                        .alphaElectrons();

        int determinants =
                configuration.determinants();

        int width =
                configuration.oneElectronWidth();

        int nuclei =
                molecule.nuclei()
                        .size();

        double scale =
                1.0
                        / (determinants
                        * (double) n
                        * (double) n);

        double loss =
                0.0;

        for (int i = 0;
             i < n;
             i++) {

            String spin =
                    i < alpha
                            ? "alpha"
                            : "beta";

            var weight =
                    layout.block(
                            "orbital."
                                    + spin
                                    + ".weight");

            var pi =
                    layout.block(
                            "envelope."
                                    + spin
                                    + ".pi");

            var sigma =
                    layout.block(
                            "envelope."
                                    + spin
                                    + ".sigma");

            for (int orbital = 0;
                 orbital < n;
                 orbital++) {

                double difference =
                        matrix.values()
                                [i]
                                [orbital]
                                - target[i]
                                [orbital];

                loss +=
                        scale
                                * difference
                                * difference;

                double adjPhi =
                        2.0
                                * scale
                                * difference;

                double raw =
                        matrix.raw()
                                [i]
                                [orbital];

                double env =
                        matrix.envelope()
                                [i]
                                [orbital];

                double adjRaw =
                        adjPhi
                                * env;

                double adjEnvelope =
                        adjPhi
                                * raw;

                int head =
                        determinant
                                * n
                                + orbital;

                int weightBase =
                        weight.startInclusive()
                                + head
                                * width;

                for (int k = 0;
                     k < width;
                     k++) {

                    gradient[
                            weightBase + k] +=
                            adjRaw
                                    * matrix.features()
                                    [i][k];

                    adjOne[i][k] +=
                            adjRaw
                                    * parameters[
                                    weightBase
                                            + k];
                }

                for (int a = 0;
                     a < nuclei;
                     a++) {

                    int index =
                            head
                                    * nuclei
                                    + a;

                    double sigmaValue =
                            parameters[
                                    sigma.startInclusive()
                                            + index];

                    double piValue =
                            parameters[
                                    pi.startInclusive()
                                            + index];

                    double r =
                            matrix.distances()
                                    [i][a];

                    double decay =
                            Math.exp(
                                    -sigmaValue
                                            * r);

                    gradient[
                            pi.startInclusive()
                                    + index] +=
                            adjEnvelope
                                    * decay;

                    gradient[
                            sigma.startInclusive()
                                    + index] +=
                            adjEnvelope
                                    * piValue
                                    * decay
                                    * (-r);
                }
            }
        }

        return loss;
    }

    private void backpropLayers(
            double[][] adjOne,
            ParameterGradientAccumulator gradient,
            List<LayerCache> caches) {

        backpropLayers(
                adjOne,
                gradient,
                caches,
                null);
    }

    private void backpropLayers(
            double[][] adjOne,
            ParameterGradientAccumulator gradient,
            List<LayerCache> caches,
            FermiNetStructuredSrStatistics.Builder statistics) {

        double[][][] adjTwoCarry =
                null;

        for (int layer =
             caches.size() - 1;
             layer >= 0;
             layer--) {

            LayerCache cache =
                    caches.get(layer);

            var oneWeight =
                    layout.block(
                            "interaction."
                                    + layer
                                    + ".one.weight");

            var oneBias =
                    layout.block(
                            "interaction."
                                    + layer
                                    + ".one.bias");

            double[][] adjAggregate =
                    new double[
                            cache.aggregate()
                                    .length]
                            [cache.aggregate()
                            [0]
                            .length];

            double[][] adjPreviousOne =
                    new double[
                            cache.one()
                                    .length]
                            [cache.one()
                            [0]
                            .length];

            double[][][] adjPreviousTwo =
                    new double[
                            cache.two()
                                    .length]
                            [cache.two()
                            .length]
                            [cache.two()
                            [0][0]
                            .length];

            boolean oneResidual =
                    cache.one()
                            [0]
                            .length
                            == adjOne[0]
                            .length;

            double[][] oneAdjoints =
                    statistics == null
                            ? null
                            : new double[adjOne.length]
                            [adjOne[0].length];

            for (int i = 0;
                 i < adjOne.length;
                 i++) {

                for (int o = 0;
                     o < adjOne[i].length;
                     o++) {

                    double upstream =
                            adjOne[i][o];

                    double branchScale =
                            oneResidual
                                    ? INV_SQRT_2
                                    : 1.0;

                    double tanh =
                            cache.oneTanh()
                                    [i][o];

                    double dz =
                            upstream
                                    * branchScale
                                    * (1.0
                                    - tanh
                                    * tanh);

                    if (statistics != null) {
                        oneAdjoints[i][o] = dz;
                        statistics.addExplicit(
                                oneBias.name(),
                                o,
                                dz);
                    }

                    gradient.add(
                            oneBias.startInclusive()
                                    + o,
                            dz);

                    int base =
                            oneWeight.startInclusive()
                                    + o
                                    * cache.aggregate()
                                    [i]
                                    .length;

                    for (int k = 0;
                         k < cache.aggregate()
                                 [i]
                                 .length;
                         k++) {

                        gradient.add(
                                base + k,
                                dz
                                        * cache.aggregate()
                                        [i][k]);

                        adjAggregate[i][k] +=
                                dz
                                        * parameters[
                                        base
                                                + k];
                    }

                    if (oneResidual) {

                        adjPreviousOne[i][o] +=
                                upstream
                                        * INV_SQRT_2;
                    }
                }
            }

            if (statistics != null) {
                statistics.dense(
                        oneWeight.name(),
                        cache.aggregate(),
                        oneAdjoints);
            }

            scatterAggregate(
                    adjAggregate,
                    adjPreviousOne,
                    adjPreviousTwo);

            if (cache.transformTwo()) {

                var twoWeight =
                        layout.block(
                                "interaction."
                                        + layer
                                        + ".two.weight");

                var twoBias =
                        layout.block(
                                "interaction."
                                        + layer
                                        + ".two.bias");

                int outWidth =
                        configuration.twoElectronWidth();

                double[][][] incoming =
                        adjTwoCarry != null
                                ? adjTwoCarry
                                : new double[
                                cache.two()
                                        .length]
                                [cache.two()
                                .length]
                                [outWidth];

                boolean twoResidual =
                        cache.two()
                                [0][0]
                                .length
                                == outWidth;

                double[][] twoInputs =
                        statistics == null
                                ? null
                                : new double[cache.two().length
                                * cache.two().length][];

                double[][] twoAdjoints =
                        statistics == null
                                ? null
                                : new double[cache.two().length
                                * cache.two().length]
                                [outWidth];

                for (int i = 0;
                     i < cache.two()
                             .length;
                     i++) {

                    for (int j = 0;
                         j < cache.two()
                                 .length;
                         j++) {

                        for (int o = 0;
                             o < outWidth;
                             o++) {

                            double upstream =
                                    incoming[i]
                                            [j]
                                            [o];

                            double branchScale =
                                    twoResidual
                                            ? INV_SQRT_2
                                            : 1.0;

                            double tanh =
                                    cache.twoTanh()
                                            [i]
                                            [j]
                                            [o];

                            double dz =
                                    upstream
                                            * branchScale
                                            * (1.0
                                            - tanh
                                            * tanh);

                            if (statistics != null) {
                                int occurrence =
                                        i * cache.two().length + j;
                                twoInputs[occurrence] =
                                        cache.two()[i][j];
                                twoAdjoints[occurrence][o] = dz;
                                statistics.addExplicit(
                                        twoBias.name(),
                                        o,
                                        dz);
                            }

                            gradient.add(
                                    twoBias.startInclusive()
                                            + o,
                                    dz);

                            int base =
                                    twoWeight.startInclusive()
                                            + o
                                            * cache.two()
                                            [i][j]
                                            .length;

                            for (int k = 0;
                                 k < cache.two()
                                         [i][j]
                                         .length;
                                 k++) {

                                gradient.add(
                                        base + k,
                                        dz
                                                * cache.two()
                                                [i][j][k]);

                                adjPreviousTwo
                                        [i][j][k] +=
                                        dz
                                                * parameters[
                                                base
                                                        + k];
                            }

                            if (twoResidual) {

                                adjPreviousTwo
                                        [i][j][o] +=
                                        upstream
                                                * INV_SQRT_2;
                            }
                        }
                    }
                }

                if (statistics != null) {
                    statistics.dense(
                            twoWeight.name(),
                            twoInputs,
                            twoAdjoints);
                }

            } else if (adjTwoCarry != null) {

                throw new IllegalStateException(
                        "unexpected pair-stream adjoint at untransformed final layer");
            }

            adjOne =
                    adjPreviousOne;

            adjTwoCarry =
                    adjPreviousTwo;
        }
    }

    private void scatterAggregate(
            double[][] adj,
            double[][] one,
            double[][][] two) {

        int n =
                one.length;

        int oneWidth =
                one[0].length;

        int twoWidth =
                two[0][0].length;

        int alpha =
                molecule.spin()
                        .alphaElectrons();

        int beta =
                molecule.spin()
                        .betaElectrons();

        for (int electron = 0;
             electron < n;
             electron++) {

            int at =
                    0;

            for (int k = 0;
                 k < oneWidth;
                 k++) {

                one[electron][k] +=
                        adj[electron]
                                [at++];
            }

            if (alpha > 0) {

                for (int k = 0;
                     k < oneWidth;
                     k++) {

                    double contribution =
                            adj[electron]
                                    [at++]
                                    / alpha;

                    for (int source = 0;
                         source < alpha;
                         source++) {

                        one[source][k] +=
                                contribution;
                    }
                }
            }

            if (beta > 0) {

                for (int k = 0;
                     k < oneWidth;
                     k++) {

                    double contribution =
                            adj[electron]
                                    [at++]
                                    / beta;

                    for (int source = alpha;
                         source < alpha + beta;
                         source++) {

                        one[source][k] +=
                                contribution;
                    }
                }
            }

            if (alpha > 0) {

                for (int k = 0;
                     k < twoWidth;
                     k++) {

                    double contribution =
                            adj[electron]
                                    [at++]
                                    / alpha;

                    for (int source = 0;
                         source < alpha;
                         source++) {

                        two[source]
                                [electron]
                                [k] +=
                                contribution;
                    }
                }
            }

            if (beta > 0) {

                for (int k = 0;
                     k < twoWidth;
                     k++) {

                    double contribution =
                            adj[electron]
                                    [at++]
                                    / beta;

                    for (int source = alpha;
                         source < alpha + beta;
                         source++) {

                        two[source]
                                [electron]
                                [k] +=
                                contribution;
                    }
                }
            }
        }
    }

    /*
     * =====================================================================
     * Numerical helpers
     * =====================================================================
     */

    private FermiNetSpatialJet electronNuclearDistance(
            QuantumCoordinates coordinates,
            int electron,
            int nucleus,
            int dimensions) {

        return electronNuclearDistance(
                coordinates,
                electron,
                nucleus,
                dimensions,
                DerivativeDomain.ELECTRON);
    }

    private FermiNetSpatialJet electronNuclearDistance(
            QuantumCoordinates coordinates,
            int electron,
            int nucleus,
            int dimensions,
            DerivativeDomain derivativeDomain) {

        return electronNuclearDistance(coordinates, electron, nucleus, dimensions,
                derivativeDomain, null, null, null);
    }

    private FermiNetSpatialJet electronNuclearDistance(
            QuantumCoordinates coordinates,
            int electron,
            int nucleus,
            int dimensions,
            DerivativeDomain derivativeDomain,
            double[] nuclearDirection,
            double[] electronDirection) {

        return electronNuclearDistance(coordinates, electron, nucleus,
                dimensions, derivativeDomain, nuclearDirection,
                electronDirection, null);
    }

    private FermiNetSpatialJet electronNuclearDistance(
            QuantumCoordinates coordinates,
            int electron,
            int nucleus,
            int dimensions,
            DerivativeDomain derivativeDomain,
            double[] nuclearDirection,
            double[] electronDirection,
            DirectionBatch directionBatch) {

        var e =
                coordinates.particles()
                        .get(electron);

        var n =
                molecule.nuclei()
                        .get(nucleus)
                        .position()
                        .inBohr();

        FermiNetSpatialJet electronX =
                derivativeDomain == DerivativeDomain.BATCHED_DIRECTIONAL
                        ? FermiNetBatchedSpatialJet.variable(
                                directionBatch.workspace(), e.xBohr(), dimensions,
                                3 * electron,
                                directionBatch.electronValues(3 * electron))
                        : derivativeDomain == DerivativeDomain.DIRECTIONAL
                        ? FermiNetSpatialJet.directionalVariable(
                                e.xBohr(), dimensions, 3 * electron,
                                electronDirection[3 * electron])
                        : derivativeDomain == DerivativeDomain.ELECTRON
                        ? FermiNetSpatialJet.variable(
                                e.xBohr(), dimensions, 3 * electron)
                        : FermiNetSpatialJet.constant(
                                e.xBohr(), dimensions);

        FermiNetSpatialJet electronY =
                derivativeDomain == DerivativeDomain.BATCHED_DIRECTIONAL
                        ? FermiNetBatchedSpatialJet.variable(
                                directionBatch.workspace(), e.yBohr(), dimensions,
                                3 * electron + 1,
                                directionBatch.electronValues(3 * electron + 1))
                        : derivativeDomain == DerivativeDomain.DIRECTIONAL
                        ? FermiNetSpatialJet.directionalVariable(
                                e.yBohr(), dimensions, 3 * electron + 1,
                                electronDirection[3 * electron + 1])
                        : derivativeDomain == DerivativeDomain.ELECTRON
                        ? FermiNetSpatialJet.variable(
                                e.yBohr(), dimensions, 3 * electron + 1)
                        : FermiNetSpatialJet.constant(
                                e.yBohr(), dimensions);

        FermiNetSpatialJet electronZ =
                derivativeDomain == DerivativeDomain.BATCHED_DIRECTIONAL
                        ? FermiNetBatchedSpatialJet.variable(
                                directionBatch.workspace(), e.zBohr(), dimensions,
                                3 * electron + 2,
                                directionBatch.electronValues(3 * electron + 2))
                        : derivativeDomain == DerivativeDomain.DIRECTIONAL
                        ? FermiNetSpatialJet.directionalVariable(
                                e.zBohr(), dimensions, 3 * electron + 2,
                                electronDirection[3 * electron + 2])
                        : derivativeDomain == DerivativeDomain.ELECTRON
                        ? FermiNetSpatialJet.variable(
                                e.zBohr(), dimensions, 3 * electron + 2)
                        : FermiNetSpatialJet.constant(
                                e.zBohr(), dimensions);

        FermiNetSpatialJet nucleusX =
                derivativeDomain == DerivativeDomain.BATCHED_DIRECTIONAL
                        ? FermiNetBatchedSpatialJet.constant(
                                directionBatch.workspace(), n.x(), dimensions,
                                directionBatch.nuclearValues(3 * nucleus))
                        : derivativeDomain == DerivativeDomain.DIRECTIONAL
                        ? FermiNetSpatialJet.directionalConstant(
                                n.x(), dimensions, nuclearDirection[3 * nucleus])
                        : derivativeDomain == DerivativeDomain.NUCLEAR
                        ? FermiNetSpatialJet.variable(
                                n.x(), dimensions, 3 * nucleus)
                        : FermiNetSpatialJet.constant(
                                n.x(), dimensions);

        FermiNetSpatialJet nucleusY =
                derivativeDomain == DerivativeDomain.BATCHED_DIRECTIONAL
                        ? FermiNetBatchedSpatialJet.constant(
                                directionBatch.workspace(), n.y(), dimensions,
                                directionBatch.nuclearValues(3 * nucleus + 1))
                        : derivativeDomain == DerivativeDomain.DIRECTIONAL
                        ? FermiNetSpatialJet.directionalConstant(
                                n.y(), dimensions, nuclearDirection[3 * nucleus + 1])
                        : derivativeDomain == DerivativeDomain.NUCLEAR
                        ? FermiNetSpatialJet.variable(
                                n.y(), dimensions, 3 * nucleus + 1)
                        : FermiNetSpatialJet.constant(
                                n.y(), dimensions);

        FermiNetSpatialJet nucleusZ =
                derivativeDomain == DerivativeDomain.BATCHED_DIRECTIONAL
                        ? FermiNetBatchedSpatialJet.constant(
                                directionBatch.workspace(), n.z(), dimensions,
                                directionBatch.nuclearValues(3 * nucleus + 2))
                        : derivativeDomain == DerivativeDomain.DIRECTIONAL
                        ? FermiNetSpatialJet.directionalConstant(
                                n.z(), dimensions, nuclearDirection[3 * nucleus + 2])
                        : derivativeDomain == DerivativeDomain.NUCLEAR
                        ? FermiNetSpatialJet.variable(
                                n.z(), dimensions, 3 * nucleus + 2)
                        : FermiNetSpatialJet.constant(
                                n.z(), dimensions);

        var dx =
                electronX.subtract(nucleusX);

        var dy =
                electronY.subtract(nucleusY);

        var dz =
                electronZ.subtract(nucleusZ);

        return norm(
                dx,
                dy,
                dz);
    }

    private static FermiNetSpatialJet norm(
            FermiNetSpatialJet x,
            FermiNetSpatialJet y,
            FermiNetSpatialJet z) {

        return x.multiply(x)
                .add(
                        y.multiply(y))
                .add(
                        z.multiply(z))
                .sqrt();
    }

    private static void releaseBatched(
            DirectionBatch directions, FermiNetSpatialJet jet) {
        if (directions != null) directions.workspace().release(jet);
    }

    private static void retainAll(
            java.util.Set<FermiNetSpatialJet> retained,
            FermiNetSpatialJet[][] values) {
        for (FermiNetSpatialJet[] row : values) {
            for (FermiNetSpatialJet value : row) retained.add(value);
        }
    }

    private static void retainAll(
            java.util.Set<FermiNetSpatialJet> retained,
            FermiNetSpatialJet[][][] values) {
        for (FermiNetSpatialJet[][] matrix : values) retainAll(retained, matrix);
    }

    private static void releaseAll(
            DirectionBatch directions,
            java.util.Set<FermiNetSpatialJet> retained,
            FermiNetSpatialJet[][] values) {
        for (FermiNetSpatialJet[] row : values) {
            for (FermiNetSpatialJet value : row) {
                if (!retained.contains(value)) directions.workspace().release(value);
            }
        }
    }

    private static void releaseAll(
            DirectionBatch directions,
            java.util.Set<FermiNetSpatialJet> retained,
            FermiNetSpatialJet[][][] values) {
        for (FermiNetSpatialJet[][] matrix : values) {
            releaseAll(directions, retained, matrix);
        }
    }

    private static double[] meanRows(
            double[][] values,
            int start,
            int end) {

        double[] result =
                new double[
                        values[0].length];

        int count =
                end - start;

        for (int i = start;
             i < end;
             i++) {

            for (int k = 0;
                 k < result.length;
                 k++) {

                result[k] +=
                        values[i][k];
            }
        }

        for (int k = 0;
             k < result.length;
             k++) {

            result[k] /=
                    count;
        }

        return result;
    }

    private static FermiNetSpatialJet[] meanRows(
            FermiNetSpatialJet[][] values,
            int start,
            int end,
            DirectionBatch directionBatch) {

        int dimensions =
                values[0][0]
                        .dimensions();

        FermiNetSpatialJet[] result =
                new FermiNetSpatialJet[
                        values[0].length];

        int count =
                end - start;

        for (int k = 0;
             k < result.length;
             k++) {

            FermiNetSpatialJet sum =
                    FermiNetSpatialJet.constant(
                            0.0,
                            dimensions);

            for (int i = start;
                 i < end;
                 i++) {

                FermiNetSpatialJet next = sum.add(values[i][k]);
                releaseBatched(directionBatch, sum);
                sum = next;
            }

            result[k] = sum.multiply(1.0 / count);
            releaseBatched(directionBatch, sum);
        }

        return result;
    }

    private static int copy(
            FermiNetSpatialJet[] target,
            int at,
            FermiNetSpatialJet[] source) {

        System.arraycopy(
                source,
                0,
                target,
                at,
                source.length);

        return at
                + source.length;
    }

    private static FermiNetSpatialJet determinant(
            FermiNetSpatialJet[][] input) {

        int n =
                input.length;

        for (FermiNetSpatialJet[] row :
                input) {

            if (row.length != n) {

                throw new IllegalArgumentException(
                        "determinant matrix must be square");
            }
        }

        FermiNetSpatialJet[][] a =
                new FermiNetSpatialJet[n][n];

        for (int i = 0;
             i < n;
             i++) {

            a[i] =
                    input[i].clone();
        }

        FermiNetSpatialJet det =
                FermiNetSpatialJet.constant(
                        1.0,
                        input[0][0]
                                .dimensions());

        int sign =
                1;

        for (int k = 0;
             k < n;
             k++) {

            int pivot =
                    k;

            for (int i = k + 1;
                 i < n;
                 i++) {

                if (Math.abs(
                        a[i][k]
                                .value())
                        > Math.abs(
                        a[pivot][k]
                                .value())) {

                    pivot =
                            i;
                }
            }

            if (Math.abs(
                    a[pivot][k]
                            .value())
                    < SINGULAR_THRESHOLD) {

                throw new IllegalStateException(
                        "singular FermiNet determinant");
            }

            if (pivot != k) {

                FermiNetSpatialJet[] row =
                        a[k];

                a[k] =
                        a[pivot];

                a[pivot] =
                        row;

                sign =
                        -sign;
            }

            FermiNetSpatialJet p =
                    a[k][k];

            det =
                    det.multiply(p);

            for (int i = k + 1;
                 i < n;
                 i++) {

                FermiNetSpatialJet factor =
                        a[i][k]
                                .divide(p);

                for (int j = k + 1;
                     j < n;
                     j++) {

                    a[i][j] =
                            a[i][j]
                                    .subtract(
                                            factor.multiply(
                                                    a[k][j]));
                }
            }
        }

        return det.multiply(
                sign);
    }

    private static double[][] inverse(
            double[][] input) {

        int n =
                input.length;

        double[][] a =
                new double[n]
                        [2 * n];

        for (int i = 0;
             i < n;
             i++) {

            if (input[i].length
                    != n) {

                throw new IllegalArgumentException(
                        "matrix must be square");
            }

            System.arraycopy(
                    input[i],
                    0,
                    a[i],
                    0,
                    n);

            a[i][n + i] =
                    1.0;
        }

        for (int k = 0;
             k < n;
             k++) {

            int pivot =
                    k;

            for (int i = k + 1;
                 i < n;
                 i++) {

                if (Math.abs(
                        a[i][k])
                        > Math.abs(
                        a[pivot][k])) {

                    pivot =
                            i;
                }
            }

            if (Math.abs(
                    a[pivot][k])
                    < SINGULAR_THRESHOLD) {

                throw new IllegalStateException(
                        "singular FermiNet determinant");
            }

            double[] row =
                    a[k];

            a[k] =
                    a[pivot];

            a[pivot] =
                    row;

            double p =
                    a[k][k];

            for (int j = 0;
                 j < 2 * n;
                 j++) {

                a[k][j] /=
                        p;
            }

            for (int i = 0;
                 i < n;
                 i++) {

                if (i == k) {
                    continue;
                }

                double factor =
                        a[i][k];

                for (int j = 0;
                     j < 2 * n;
                     j++) {

                    a[i][j] -=
                            factor
                                    * a[k][j];
                }
            }
        }

        double[][] result =
                new double[n][n];

        for (int i = 0;
             i < n;
             i++) {

            System.arraycopy(
                    a[i],
                    n,
                    result[i],
                    0,
                    n);
        }

        return result;
    }

    private void validate(
            QuantumCoordinates coordinates) {

        if (coordinates.particles()
                .size()
                != molecule.electrons()
                .value()) {

            throw new IllegalArgumentException(
                    "electron count mismatch");
        }

        for (int i = 0;
             i < coordinates.particles()
                     .size();
             i++) {

            SpinProjection expected =
                    i
                            < molecule.spin()
                            .alphaElectrons()
                            ? SpinProjection.ALPHA
                            : SpinProjection.BETA;

            if (coordinates.particles()
                    .get(i)
                    .spin()
                    != expected) {

                throw new IllegalArgumentException(
                        "electrons must be ordered alpha then beta");
            }
        }
    }

    private static void validateTarget(
            double[][] target,
            int count,
            String spin) {

        Objects.requireNonNull(
                target,
                spin + " target");

        if (target.length
                != count) {

            throw new IllegalArgumentException(
                    spin
                            + " target row count");
        }

        for (double[] row :
                target) {

            if (row == null
                    || row.length
                    != count
                    || Arrays.stream(row)
                    .anyMatch(
                            x -> !Double.isFinite(x))) {

                throw new IllegalArgumentException(
                        spin
                                + " target matrix");
            }
        }
    }

    private static double distance(
            double x,
            double y,
            double z) {

        return Math.sqrt(
                x * x
                        + y * y
                        + z * z);
    }

    private static double[][] values(
            FermiNetSpatialJet[][] input) {

        double[][] result =
                new double[input.length]
                        [input[0].length];

        for (int i = 0;
             i < input.length;
             i++) {

            for (int j = 0;
                 j < input[i].length;
                 j++) {

                result[i][j] =
                        input[i][j]
                                .value();
            }
        }

        return result;
    }

    private static double[][][] values(
            FermiNetSpatialJet[][][] input) {

        double[][][] result =
                new double[input.length]
                        [input[0].length]
                        [input[0][0].length];

        for (int i = 0;
             i < input.length;
             i++) {

            for (int j = 0;
                 j < input[i].length;
                 j++) {

                for (int k = 0;
                     k < input[i][j].length;
                     k++) {

                    result[i][j][k] =
                            input[i][j][k]
                                    .value();
                }
            }
        }

        return result;
    }

    private static double[][] deepCopy(
            double[][] source) {

        double[][] copy =
                new double[
                        source.length][];

        for (int i = 0;
             i < source.length;
             i++) {

            copy[i] =
                    source[i].clone();
        }

        return copy;
    }

    private static double[][][] deepCopy(
            double[][][] source) {

        double[][][] copy =
                new double[
                        source.length][][];

        for (int i = 0;
             i < source.length;
             i++) {

            copy[i] =
                    new double[
                            source[i].length][];

            for (int j = 0;
                 j < source[i].length;
                 j++) {

                copy[i][j] =
                        source[i][j]
                                .clone();
            }
        }

        return copy;
    }

    /*
     * =====================================================================
     * Public and diagnostic result records
     * =====================================================================
     */

    public record Evaluation(
            int sign,
            double logAbsoluteWavefunction,
            double[] logCoordinateGradient,
            double laplacianOverWavefunction,
            double[] parameterLogDerivatives) {

        public Evaluation {

            logCoordinateGradient =
                    logCoordinateGradient.clone();

            parameterLogDerivatives =
                    parameterLogDerivatives.clone();

            if (sign != 1
                    && sign != -1) {

                throw new IllegalArgumentException(
                        "invalid sign");
            }

            if (!Double.isFinite(
                    logAbsoluteWavefunction)
                    || !Double.isFinite(
                    laplacianOverWavefunction)) {

                throw new IllegalArgumentException(
                        "non-finite FermiNet evaluation");
            }
        }

        @Override
        public double[] logCoordinateGradient() {
            return logCoordinateGradient.clone();
        }

        @Override
        public double[] parameterLogDerivatives() {
            return parameterLogDerivatives.clone();
        }
    }

    public record PretrainingEvaluation(
            double loss,
            double[] gradient) {

        public PretrainingEvaluation {

            gradient =
                    gradient.clone();

            if (!Double.isFinite(loss)
                    || Arrays.stream(
                            gradient)
                    .anyMatch(
                            x -> !Double.isFinite(x))) {

                throw new IllegalArgumentException(
                        "non-finite pretraining evaluation");
            }
        }

        @Override
        public double[] gradient() {
            return gradient.clone();
        }
    }

    record SpatialEvaluation(
            int sign,
            double logAbsoluteWavefunction,
            double[] logCoordinateGradient,
            double laplacianOverWavefunction) {

        SpatialEvaluation {
            logCoordinateGradient =
                    logCoordinateGradient.clone();
        }

        @Override
        public double[] logCoordinateGradient() {
            return logCoordinateGradient.clone();
        }
    }

    record NuclearEvaluation(
            int sign,
            double logAbsoluteWavefunction,
            double[] logNuclearGradient) {

        NuclearEvaluation {
            logNuclearGradient = logNuclearGradient.clone();

            if ((sign != 1 && sign != -1)
                    || !Double.isFinite(logAbsoluteWavefunction)
                    || Arrays.stream(logNuclearGradient)
                    .anyMatch(value -> !Double.isFinite(value))) {

                throw new IllegalArgumentException(
                        "non-finite FermiNet nuclear evaluation");
            }
        }

        @Override
        public double[] logNuclearGradient() {
            return logNuclearGradient.clone();
        }
    }

    record DirectionalEvaluation(
            int sign,
            double logAbsoluteWavefunction,
            double directionalLogAbsoluteWavefunction,
            double laplacianOverWavefunction,
            double directionalLaplacianOverWavefunction) {

        DirectionalEvaluation {
            if ((sign != 1 && sign != -1)
                    || !Double.isFinite(logAbsoluteWavefunction)
                    || !Double.isFinite(directionalLogAbsoluteWavefunction)
                    || !Double.isFinite(laplacianOverWavefunction)
                    || !Double.isFinite(directionalLaplacianOverWavefunction)) {
                throw new IllegalArgumentException(
                        "non-finite FermiNet directional evaluation");
            }
        }
    }

    record BatchedDirectionalEvaluation(
            int sign,
            double logAbsoluteWavefunction,
            double[] logCoordinateGradient,
            double laplacianOverWavefunction,
            double[] directionalLogAbsoluteWavefunction,
            double[] directionalLaplacianOverWavefunction) {

        BatchedDirectionalEvaluation {
            logCoordinateGradient = logCoordinateGradient.clone();
            directionalLogAbsoluteWavefunction =
                    directionalLogAbsoluteWavefunction.clone();
            directionalLaplacianOverWavefunction =
                    directionalLaplacianOverWavefunction.clone();
        }

        @Override public double[] logCoordinateGradient() {
            return logCoordinateGradient.clone();
        }
        @Override public double[] directionalLogAbsoluteWavefunction() {
            return directionalLogAbsoluteWavefunction.clone();
        }
        @Override public double[] directionalLaplacianOverWavefunction() {
            return directionalLaplacianOverWavefunction.clone();
        }
    }

    record StructuredSrEvaluation(
            int sign,
            double logAbsoluteWavefunction,
            double[] logCoordinateGradient,
            double laplacianOverWavefunction,
            FermiNetStructuredSrStatistics statistics) {

        StructuredSrEvaluation {
            logCoordinateGradient = logCoordinateGradient.clone();
            Objects.requireNonNull(statistics, "statistics");
        }

        @Override
        public double[] logCoordinateGradient() {
            return logCoordinateGradient.clone();
        }
    }

    /**
     * Package-private immutable parity-test snapshot.
     */
    record ReferenceSnapshot(
            double[][] oneElectronFeatures,
            double[][][] twoElectronFeatures,
            List<LayerReferenceSnapshot> layers,
            List<DeterminantReferenceSnapshot> determinants,
            int sign,
            double logAbsoluteWavefunction) {

        ReferenceSnapshot {

            oneElectronFeatures =
                    deepCopy(
                            oneElectronFeatures);

            twoElectronFeatures =
                    deepCopy(
                            twoElectronFeatures);

            layers =
                    List.copyOf(
                            layers);

            determinants =
                    List.copyOf(
                            determinants);

            if (sign != 1
                    && sign != -1) {

                throw new IllegalArgumentException(
                        "invalid reference snapshot sign");
            }

            if (!Double.isFinite(
                    logAbsoluteWavefunction)) {

                throw new IllegalArgumentException(
                        "non-finite reference snapshot wavefunction");
            }
        }

        @Override
        public double[][] oneElectronFeatures() {
            return deepCopy(
                    oneElectronFeatures);
        }

        @Override
        public double[][][] twoElectronFeatures() {
            return deepCopy(
                    twoElectronFeatures);
        }
    }

    record LayerReferenceSnapshot(
            int layer,
            double[][] aggregateInput,
            double[][] oneElectronOutput,
            double[][][] twoElectronOutput,
            boolean transformedTwoElectronStream) {

        LayerReferenceSnapshot {

            aggregateInput =
                    deepCopy(
                            aggregateInput);

            oneElectronOutput =
                    deepCopy(
                            oneElectronOutput);

            twoElectronOutput =
                    deepCopy(
                            twoElectronOutput);
        }

        @Override
        public double[][] aggregateInput() {
            return deepCopy(
                    aggregateInput);
        }

        @Override
        public double[][] oneElectronOutput() {
            return deepCopy(
                    oneElectronOutput);
        }

        @Override
        public double[][][] twoElectronOutput() {
            return deepCopy(
                    twoElectronOutput);
        }
    }

    record DeterminantReferenceSnapshot(
            int determinant,
            double[][] orbitalMatrix,
            int sign,
            double logMagnitude) {

        DeterminantReferenceSnapshot {

            orbitalMatrix =
                    deepCopy(
                            orbitalMatrix);

            if (sign != 1
                    && sign != -1) {

                throw new IllegalArgumentException(
                        "invalid determinant sign");
            }

            if (!Double.isFinite(
                    logMagnitude)) {

                throw new IllegalArgumentException(
                        "non-finite determinant magnitude");
            }
        }

        @Override
        public double[][] orbitalMatrix() {
            return deepCopy(
                    orbitalMatrix);
        }
    }

    /*
     * =====================================================================
     * Internal records
     * =====================================================================
     */

    private static final class ParameterGradientAccumulator {

        private final int startInclusive;
        private final int endExclusive;
        private final double[] destination;

        private ParameterGradientAccumulator(
                int startInclusive,
                int length,
                double[] destination) {

            this.startInclusive =
                    startInclusive;

            this.endExclusive =
                    Math.addExact(
                            startInclusive,
                            length);

            this.destination =
                    destination;
        }

        private void add(
                int parameterIndex,
                double value) {

            if (parameterIndex >= startInclusive
                    && parameterIndex < endExclusive) {

                destination[
                        parameterIndex
                                - startInclusive] +=
                        value;
            }
        }
    }

    private record Inputs(
            FermiNetSpatialJet[][] one,
            FermiNetSpatialJet[][][] two) {}

    private record ValueInputs(
            double[][] one,
            double[][][] two) {}

    private record Interaction(
            FermiNetSpatialJet[][] one,
            List<LayerCache> caches) {}

    private record ValueInteraction(
            double[][] one,
            List<LayerCache> caches) {}

    private record LayerCache(
            double[][] one,
            double[][][] two,
            double[][] aggregate,
            double[][] oneTanh,
            double[][][] twoTanh,
            boolean transformTwo) {}

    private record DenseOrbitalMatrix(
            FermiNetSpatialJet[][] jets,
            double[][] values,
            double[][] raw,
            double[][] envelope,
            double[][] distances,
            double[][] features) {}

    private record DeterminantData(
            DenseOrbitalMatrix matrix,
            FermiNetSpatialJet det,
            int sign,
            double logMagnitude) {}

    private record Output(
            int sign,
            double logAbsolute,
            double[] logGradient,
            double laplacianOverWavefunction,
            DeterminantData[] data,
            double[] mixture,
            FermiNetSpatialJet wavefunction) {}

    private enum DerivativeDomain {
        ELECTRON,
        NUCLEAR,
        DIRECTIONAL,
        BATCHED_DIRECTIONAL
    }

    private record DirectionBatch(
            double[][] nuclearDirections,
            double[][] electronDirections,
            FermiNetBatchedJetWorkspace workspace) {
        int size() { return nuclearDirections.length; }
        double[] nuclearValues(int coordinate) {
            return coordinateValues(nuclearDirections, coordinate);
        }
        double[] electronValues(int coordinate) {
            return coordinateValues(electronDirections, coordinate);
        }
        private static double[] coordinateValues(
                double[][] directions, int coordinate) {
            double[] result = new double[directions.length];
            for (int direction = 0; direction < directions.length; direction++) {
                result[direction] = directions[direction][coordinate];
            }
            return result;
        }
    }

    // Add inside FermiNetV1State.java, alongside spatialEvaluation(...).

    /**
     * Fast value-only wavefunction evaluation for Metropolis sampling.
     *
     * <p>This path intentionally does not construct coordinate jets and therefore
     * does not calculate gradients or the Laplacian. It must be used for RWM/MH
     * accept-reject decisions, where only sign and log|Psi| are required.
     */
    SamplingEvaluation samplingEvaluation(QuantumCoordinates coordinates) {
        validate(coordinates);

        ValueInteraction interaction =
                valueInteraction(coordinates);

        int determinants =
                configuration.determinants();

        int[] signs =
                new int[determinants];

        double[] logMagnitudes =
                new double[determinants];

        double maxLog =
                Double.NEGATIVE_INFINITY;

        for (int determinant = 0;
             determinant < determinants;
             determinant++) {

            DenseOrbitalMatrix matrix =
                    denseOrbitalValueMatrix(
                            interaction.one(),
                            coordinates,
                            determinant);

            SignedLogDet signed =
                    signedLogDet(
                            matrix.values());

            signs[determinant] =
                    signed.sign();

            logMagnitudes[determinant] =
                    signed.logMagnitude();

            maxLog =
                    Math.max(
                            maxLog,
                            signed.logMagnitude());
        }

        double scaled =
                0.0;

        for (int determinant = 0;
             determinant < determinants;
             determinant++) {

            scaled +=
                    signs[determinant]
                            * Math.exp(
                            logMagnitudes[determinant]
                                    - maxLog);
        }

        if (!Double.isFinite(scaled)
                || Math.abs(scaled)
                < SINGULAR_THRESHOLD) {

            throw new IllegalStateException(
                    "FermiNet determinant sum is numerically singular");
        }

        return new SamplingEvaluation(
                scaled > 0.0 ? 1 : -1,
                maxLog
                        + Math.log(
                        Math.abs(scaled)));
    }

    private static SignedLogDet signedLogDet(
            double[][] input) {

        int n =
                input.length;

        if (n < 1) {
            throw new IllegalArgumentException(
                    "determinant matrix is empty");
        }

        double[][] matrix =
                new double[n][n];

        for (int row = 0;
             row < n;
             row++) {

            if (input[row].length != n) {
                throw new IllegalArgumentException(
                        "determinant matrix must be square");
            }

            matrix[row] =
                    input[row].clone();
        }

        int sign =
                1;

        double logMagnitude =
                0.0;

        for (int column = 0;
             column < n;
             column++) {

            int pivot =
                    column;

            for (int row = column + 1;
                 row < n;
                 row++) {

                if (Math.abs(
                        matrix[row][column])
                        > Math.abs(
                        matrix[pivot][column])) {

                    pivot =
                            row;
                }
            }

            double pivotValue =
                    matrix[pivot][column];

            if (!Double.isFinite(pivotValue)
                    || Math.abs(pivotValue)
                    < SINGULAR_THRESHOLD) {

                throw new IllegalStateException(
                        "singular FermiNet determinant");
            }

            if (pivot != column) {

                double[] swap =
                        matrix[column];

                matrix[column] =
                        matrix[pivot];

                matrix[pivot] =
                        swap;

                sign =
                        -sign;

                pivotValue =
                        matrix[column][column];
            }

            if (pivotValue < 0.0) {
                sign =
                        -sign;
            }

            logMagnitude +=
                    Math.log(
                            Math.abs(pivotValue));

            for (int row = column + 1;
                 row < n;
                 row++) {

                double factor =
                        matrix[row][column]
                                / pivotValue;

                for (int j = column + 1;
                     j < n;
                     j++) {

                    matrix[row][j] -=
                            factor
                                    * matrix[column][j];
                }
            }
        }

        return new SignedLogDet(
                sign,
                logMagnitude);
    }

    record SamplingEvaluation(
            int sign,
            double logAbsoluteWavefunction) {

        SamplingEvaluation {

            if (sign != 1
                    && sign != -1) {

                throw new IllegalArgumentException(
                        "invalid sampling sign");
            }

            if (!Double.isFinite(
                    logAbsoluteWavefunction)) {

                throw new IllegalArgumentException(
                        "non-finite sampling log amplitude");
            }
        }
    }

    private record SignedLogDet(
            int sign,
            double logMagnitude) {
    }
}
