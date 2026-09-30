package totah.lab.aether.matrix;

import java.util.List;
import java.util.function.ToDoubleBiFunction;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.GaussianTerm;
import totah.lab.aether.model.NuclearCenter;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** Private matrix storage and canonical identities shared by typed integral wrappers. */
final class IntegralMatrixData {
    private final List<ContractedGaussian> functions;
    private final double[][] values;
    private final Identity identity;

    IntegralMatrixData(List<ContractedGaussian> functions,
                       ToDoubleBiFunction<ContractedGaussian, ContractedGaussian> integral,
                       String implementation, String protocol, String resultDomain, String reason) {
        this(functions, integral, implementation, protocol, resultDomain, reason, "");
    }

    IntegralMatrixData(List<ContractedGaussian> functions,
                       ToDoubleBiFunction<ContractedGaussian, ContractedGaussian> integral,
                       String implementation, String protocol, String resultDomain, String reason,
                       String additionalIdentity) {
        this(functions, integral, implementation, protocol, resultDomain, reason, additionalIdentity, null);
    }

    static IntegralMatrixData compose(List<ContractedGaussian> functions, Entry entries,
                                      String implementation, String protocol, String resultDomain,
                                      String reason, String additionalIdentity) {
        return new IntegralMatrixData(functions, null, implementation, protocol, resultDomain,
                reason, additionalIdentity, java.util.Objects.requireNonNull(entries));
    }

    private IntegralMatrixData(List<ContractedGaussian> functions,
                               ToDoubleBiFunction<ContractedGaussian, ContractedGaussian> integral,
                               String implementation, String protocol, String resultDomain, String reason,
                               String additionalIdentity, Entry entries) {
        this(functions, integral, implementation, protocol, resultDomain, reason, additionalIdentity, entries, true);
    }

    /** Full square storage for AO-to-orbital transformations, which need not be symmetric. */
    static IntegralMatrixData capture(List<ContractedGaussian> functions, Entry entries,
                                      String implementation, String protocol, String resultDomain,
                                      String reason, String additionalIdentity) {
        return new IntegralMatrixData(functions, null, implementation, protocol, resultDomain,
                reason, additionalIdentity, java.util.Objects.requireNonNull(entries), false);
    }

    private IntegralMatrixData(List<ContractedGaussian> functions,
                               ToDoubleBiFunction<ContractedGaussian, ContractedGaussian> integral,
                               String implementation, String protocol, String resultDomain, String reason,
                               String additionalIdentity, Entry entries, boolean symmetric) {
        this.functions = List.copyOf(functions);
        if (this.functions.isEmpty()) throw new IllegalArgumentException("Basis must not be empty");
        int size = this.functions.size();
        values = new double[size][size];
        String basisHash = basisGeometryHash(this.functions);
        for (int i = 0; i < size; i++) {
            for (int j = symmetric ? i : 0; j < size; j++) {
                values[i][j] = entries == null
                        ? integral.applyAsDouble(this.functions.get(i), this.functions.get(j))
                        : entries.get(i, j);
                if (symmetric) values[j][i] = values[i][j];
            }
        }
        var result = ContentHash.accumulator().line(resultDomain).line(Integer.toString(size));
        for (double[] row : values) {
            for (double value : row) result.line(ContentHash.number(value));
        }
        identity = identityFromResultHash(basisHash, implementation, protocol, reason, additionalIdentity, result.finish());
    }

    static Identity identity(String basisHash, String implementation, String protocol, String reason,
                             String additionalIdentity, String canonicalResult) {
        return identityFromResultHash(basisHash,implementation,protocol,reason,additionalIdentity,ContentHash.sha256(canonicalResult));
    }

    static Identity identityFromResultHash(String basisHash,String implementation,String protocol,String reason,
                                           String additionalIdentity,String resultHash) {
        String calculationHash = ContentHash.sha256("aether-calculation-v1\n" + implementation
                + "\n" + protocol + "\n" + basisHash + additionalIdentity);
        ScientificStatus status = ScientificStatus.SCREENING_ONLY;
        String receiptHash = ContentHash.sha256("aether-receipt-v1\n" + calculationHash + "\n"
                + resultHash + "\n" + status + "\n" + reason);
        return new Identity(basisHash, calculationHash, resultHash, receiptHash);
    }

    /** Preserve every legacy s-only protocol byte; explicitly version angular recurrence use. */
    static String protocol(String legacy, List<ContractedGaussian> functions) {
        boolean angular = functions.stream().anyMatch(f -> f.angularMomentum() != totah.lab.aether.basis.CartesianAngularMomentum.S);
        String result = angular ? legacy.replace("s-only", "s/p-Cartesian").replace("STO-3G-H;", "STO-3G-H-C-N-O;")
                + ";angular-integrals=McMurchie-Davidson-v1;Boys0-4=positive-series-below16,upward-from-F0-at16;no-d-f-AOs" : legacy;
        if(functions.stream().anyMatch(f->f.angularMomentum().x()+f.angularMomentum().y()+f.angularMomentum().z()==2)) result=result.replace("s/p-Cartesian","s/p/d-Cartesian").replace("Boys0-4=","Boys0-8=").replace("no-d-f-AOs","no-f-AOs");
        return BasisScope.protocol(result,functions);
    }

    static String systemHash(QuantumSystem system) {
        return ContentHash.sha256("aether-quantum-system-v1\nbohr\n" + nuclearCentersHash(system.nuclei())
                + "\n" + system.molecularCharge() + "\n" + system.multiplicity() + "\n");
    }

    static String basisGeometryHash(List<ContractedGaussian> functions) {
        int size = functions.size();
        if (size == 0) throw new IllegalArgumentException("Basis must not be empty");
        StringBuilder basis = new StringBuilder("aether-basis-v1\n").append(size).append('\n');
        for (ContractedGaussian function : functions) {
            if (function.angularMomentum() != totah.lab.aether.basis.CartesianAngularMomentum.S) {
                basis.append("angular-v1|").append(function.angularMomentum()).append("|normalized-Cartesian\n");
            }
            basis.append(function.terms().size()).append('\n');
            for (GaussianTerm term : function.terms()) {
                var p = term.primitive();
                basis.append(ContentHash.number(p.centerBohr().x())).append('|')
                        .append(ContentHash.number(p.centerBohr().y())).append('|')
                        .append(ContentHash.number(p.centerBohr().z())).append('|')
                        .append(ContentHash.number(p.exponent())).append('|')
                        .append(ContentHash.number(term.coefficient())).append('\n');
            }
        }
        return ContentHash.sha256(basis.toString());
    }

    static String nuclearCentersHash(List<NuclearCenter> nuclei) {
        StringBuilder centers = new StringBuilder("aether-point-nuclei-v1\n")
                .append(nuclei.size()).append('\n');
        for (NuclearCenter nucleus : nuclei) {
            centers.append(ContentHash.number(nucleus.charge())).append('|')
                    .append(ContentHash.number(nucleus.centerBohr().x())).append('|')
                    .append(ContentHash.number(nucleus.centerBohr().y())).append('|')
                    .append(ContentHash.number(nucleus.centerBohr().z())).append('\n');
        }
        return ContentHash.sha256(centers.toString());
    }

    @FunctionalInterface
    interface Entry { double get(int row, int column); }

    int size() { return values.length; }
    double get(int row, int column) { return values[row][column]; }
    List<ContractedGaussian> functions() { return functions; }
    Identity identity() { return identity; }

    record Identity(String basisGeometryHash, String calculationHash, String resultHash, String receiptHash) {}
}
