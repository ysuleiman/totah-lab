package totah.lab.prometheus.numerics;

/** Cost/memory decision for CACHE_IF_BENEFICIAL nodes. */
@FunctionalInterface
public interface IntermediateRetentionPolicy {
    boolean retain(NumericalNode<?> node, RetentionContext context);

    record RetentionContext(long memoryBudgetBytes, long retainedBytes, int expectedReuseCount) {
        public RetentionContext {
            if (memoryBudgetBytes < 0 || retainedBytes < 0 || expectedReuseCount < 0) {
                throw new IllegalArgumentException("retention values must be non-negative");
            }
        }
    }

    static IntermediateRetentionPolicy costAware() {
        return (node, context) -> context.expectedReuseCount() > 0
                && context.retainedBytes() + node.estimatedRetainedBytes() <= context.memoryBudgetBytes()
                && node.estimatedComputeCost() * context.expectedReuseCount() > 0.0;
    }
}
