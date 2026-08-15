package totah.lab.prometheus.variational;

/** Deterministic two-center mixture importance samples for fixed-R H2. */
public final class HydrogenMoleculeImportancePointSet {
    private HydrogenMoleculeImportancePointSet() { }
    public static CollocationPointSet create(int count,double bondLengthBohr,double exponent,int skip) {
        return new HydrogenMoleculeImportanceBatches(count,bondLengthBohr,exponent,skip,
                HydrogenMoleculeImportanceBatches.MAXIMUM_BATCH_SIZE).materialize();
    }
}
