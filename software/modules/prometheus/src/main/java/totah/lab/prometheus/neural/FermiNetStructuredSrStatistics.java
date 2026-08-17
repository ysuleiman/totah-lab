package totah.lab.prometheus.neural;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generic compact sufficient statistics for exact FermiNet-v1 SR contractions.
 */
final class FermiNetStructuredSrStatistics {

    enum Kind {
        DENSE_WEIGHT,
        EXPLICIT
    }

    record Family(
            String blockName,
            Kind kind,
            int occurrences,
            int outputs,
            int inputs,
            int statisticOffset,
            int statisticLength) {

        int inputLength() {
            return occurrences * inputs;
        }

        int adjointLength() {
            return occurrences * outputs;
        }
    }

    static final class Schema {

        private final FermiNetParameterLayout layout;
        private final List<Family> families;
        private final Map<String, Family> byBlock;
        private final int statisticCount;

        Schema(FermiNetParameterLayout layout) {
            this.layout = Objects.requireNonNull(layout, "layout");

            int electrons = layout.molecule().electrons().value();
            int alpha = layout.molecule().spin().alphaElectrons();
            int beta = layout.molecule().spin().betaElectrons();
            int offset = 0;
            List<Family> assembled = new ArrayList<>();
            Set<String> covered = new LinkedHashSet<>();

            for (FermiNetParameterLayout.Block block : layout.blocks()) {
                if (covered.contains(block.name())) {
                    continue;
                }

                String name = block.name();
                int[] shape = block.shape();
                Family family;

                if (name.startsWith("interaction.")
                        && name.endsWith(".weight")) {
                    int occurrences = name.contains(".one.")
                            ? electrons
                            : Math.multiplyExact(electrons, electrons);
                    int outputs = shape[0];
                    int inputs = shape[1];
                    int length = Math.addExact(
                            Math.multiplyExact(occurrences, inputs),
                            Math.multiplyExact(occurrences, outputs));
                    family = new Family(
                            name,
                            Kind.DENSE_WEIGHT,
                            occurrences,
                            outputs,
                            inputs,
                            offset,
                            length);
                } else if (name.startsWith("orbital.")
                        && name.endsWith(".weight")) {
                    int occurrences = name.contains(".alpha.")
                            ? alpha
                            : beta;
                    int outputs = Math.multiplyExact(shape[0], shape[1]);
                    int inputs = shape[2];
                    int length = Math.addExact(
                            Math.multiplyExact(occurrences, inputs),
                            Math.multiplyExact(occurrences, outputs));
                    family = new Family(
                            name,
                            Kind.DENSE_WEIGHT,
                            occurrences,
                            outputs,
                            inputs,
                            offset,
                            length);
                } else if (isExplicitFamily(name)) {
                    family = new Family(
                            name,
                            Kind.EXPLICIT,
                            0,
                            0,
                            0,
                            offset,
                            block.size());
                } else {
                    throw new IllegalArgumentException(
                            "unsupported FermiNet SR parameter block: " + name);
                }

                assembled.add(family);
                covered.add(name);
                offset = Math.addExact(offset, family.statisticLength());
            }

            for (FermiNetParameterLayout.Block block : layout.blocks()) {
                if (!covered.contains(block.name())) {
                    throw new IllegalArgumentException(
                            "uncovered FermiNet SR parameter block: " + block.name());
                }
            }

            this.families = List.copyOf(assembled);
            Map<String, Family> indexed = new LinkedHashMap<>();
            for (Family family : families) {
                indexed.put(family.blockName(), family);
            }
            this.byBlock = Map.copyOf(indexed);
            this.statisticCount = offset;
        }

        private static boolean isExplicitFamily(String name) {
            return name.startsWith("interaction.")
                    && (name.endsWith(".one.bias")
                    || name.endsWith(".two.bias"))
                    || name.startsWith("envelope.")
                    && (name.endsWith(".pi")
                    || name.endsWith(".sigma"));
        }

        FermiNetParameterLayout layout() {
            return layout;
        }

        List<Family> families() {
            return families;
        }

        Family family(String blockName) {
            Family family = byBlock.get(blockName);
            if (family == null) {
                throw new IllegalArgumentException(
                        "unknown structured SR family: " + blockName);
            }
            return family;
        }

        int statisticCount() {
            return statisticCount;
        }
    }

    static final class Builder {

        private final Schema schema;
        private final double[] values;
        private boolean built;

        Builder(Schema schema) {
            this.schema = Objects.requireNonNull(schema, "schema");
            this.values = new double[schema.statisticCount()];
        }

        void dense(
                String blockName,
                double[][] inputs,
                double[][] adjoints) {
            ensureMutable();
            Family family = schema.family(blockName);
            if (family.kind() != Kind.DENSE_WEIGHT
                    || inputs.length != family.occurrences()
                    || adjoints.length != family.occurrences()) {
                throw new IllegalArgumentException(
                        "invalid dense SR statistics for " + blockName);
            }

            int at = family.statisticOffset();
            for (double[] row : inputs) {
                if (row.length != family.inputs()) {
                    throw new IllegalArgumentException(
                            "invalid dense input width for " + blockName);
                }
                System.arraycopy(row, 0, values, at, row.length);
                at += row.length;
            }
            for (double[] row : adjoints) {
                if (row.length != family.outputs()) {
                    throw new IllegalArgumentException(
                            "invalid dense adjoint width for " + blockName);
                }
                System.arraycopy(row, 0, values, at, row.length);
                at += row.length;
            }
        }

        void denseInputs(
                String blockName,
                double[][] inputs) {
            ensureMutable();
            Family family = schema.family(blockName);
            if (family.kind() != Kind.DENSE_WEIGHT
                    || inputs.length != family.occurrences()) {
                throw new IllegalArgumentException(
                        "invalid dense SR inputs for " + blockName);
            }
            int at = family.statisticOffset();
            for (double[] row : inputs) {
                if (row.length != family.inputs()) {
                    throw new IllegalArgumentException(
                            "invalid dense input width for " + blockName);
                }
                System.arraycopy(row, 0, values, at, row.length);
                at += row.length;
            }
        }

        void denseAdjoint(
                String blockName,
                int occurrence,
                int output,
                double value) {
            ensureMutable();
            Family family = schema.family(blockName);
            if (family.kind() != Kind.DENSE_WEIGHT
                    || occurrence < 0
                    || occurrence >= family.occurrences()
                    || output < 0
                    || output >= family.outputs()) {
                throw new IllegalArgumentException(
                        "invalid dense SR adjoint for " + blockName);
            }
            int adjointOffset = family.statisticOffset()
                    + family.inputLength();
            values[adjointOffset
                    + occurrence * family.outputs()
                    + output] = value;
        }

        void addExplicit(
                String blockName,
                int localIndex,
                double value) {
            ensureMutable();
            Family family = schema.family(blockName);
            if (family.kind() != Kind.EXPLICIT
                    || localIndex < 0
                    || localIndex >= family.statisticLength()) {
                throw new IllegalArgumentException(
                        "invalid explicit SR statistic for " + blockName);
            }
            values[family.statisticOffset() + localIndex] += value;
        }

        FermiNetStructuredSrStatistics build() {
            ensureMutable();
            for (double value : values) {
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException(
                            "non-finite structured SR statistic");
                }
            }
            built = true;
            return new FermiNetStructuredSrStatistics(schema, values);
        }

        private void ensureMutable() {
            if (built) {
                throw new IllegalStateException(
                        "structured SR statistics builder already consumed");
            }
        }
    }

    private final Schema schema;
    private final double[] values;

    private FermiNetStructuredSrStatistics(
            Schema schema,
            double[] values) {
        this.schema = schema;
        this.values = values;
    }

    Schema schema() {
        return schema;
    }

    double[] values() {
        return values.clone();
    }

    /**
     * Package-private ownership view for the immediate spool write only.
     * Callers must neither modify nor retain the returned array.
     */
    double[] internalValuesForSpoolWrite() {
        return values;
    }

    @Override
    public String toString() {
        return "FermiNetStructuredSrStatistics[count="
                + values.length + ", finite="
                + Arrays.stream(values).allMatch(Double::isFinite) + "]";
    }
}
