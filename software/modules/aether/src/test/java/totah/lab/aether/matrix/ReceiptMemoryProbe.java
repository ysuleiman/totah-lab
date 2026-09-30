package totah.lab.aether.matrix;

import java.lang.ref.Reference;
import java.util.List;
import totah.lab.aether.HeapObservation;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.model.QuantumSystem;

/** Observational test tool; never changes canonical receipts or scientific computation. */
public final class ReceiptMemoryProbe {
    private ReceiptMemoryProbe() {}
    private static long used(){return HeapObservation.used();}
    private static void collect() throws InterruptedException {System.gc();Thread.sleep(100);}
    public static void measure(String name,QuantumSystem system,List<ContractedGaussian> basis,boolean withGrid) throws Exception {
        collect();long baseline=used();
        try(var heap=new HeapObservation(name+" mode=COMPONENT_FINAL_PHASE")) {
            long start=System.nanoTime();
            var eri=new ElectronRepulsionCalculator(system,basis).calculate();
            long integralPeak=heap.peakUsedHeap(),integralNanos=System.nanoTime()-start;
            collect();long hashBefore=used();heap.resetPeak();
            String hash=eri.canonicalResultHash();long hashPeak=heap.peakUsedHeap();
            if(!hash.equals(eri.receipt().resultHash()))throw new AssertionError("Receipt changed on replay");
            collect();long hashRetained=used()-hashBefore;
            long gridBytes=0,gridPeak=0,coordinateBytes=0,aoBytes=0;int gridPoints=0;AoGrid ao=null;
            if(withGrid) {
                heap.resetPeak();var grid=MolecularGrid.build(system,new GridDefinition(120,590));
                ao=new AoGrid(grid,basis);gridPoints=grid.size();coordinateBytes=32L*grid.size();aoBytes=8L*grid.size()*basis.size();gridBytes=coordinateBytes+aoBytes;
                gridPeak=heap.peakUsedHeap();
            }
            System.out.println("MEMORY_COMPONENT system="+name+" AO_COUNT="+basis.size()+" UNIQUE_ERI_SLOTS="+eri.uniqueQuartetCount()
                    +" ERI_PACKED_BYTES="+8L*eri.uniqueQuartetCount()+" BASELINE_HEAP="+baseline
                    +" INTEGRAL_PEAK_USED_HEAP="+integralPeak+" RECEIPT_HASH_PEAK_DELTA="+Math.max(0,hashPeak-hashBefore)
                    +" RECEIPT_HASH_RETAINED_DELTA="+hashRetained+" GRID_STORAGE_BYTES="+gridBytes+" GRID_POINT_COUNT="+gridPoints
                    +" GRID_COORDINATE_WEIGHT_BYTES="+coordinateBytes+" AO_GRID_BYTES="+aoBytes+" GRID_PEAK_USED_HEAP="+gridPeak
                    +" PEAK_USED_HEAP="+Math.max(integralPeak,Math.max(hashPeak,gridPeak))+" INTEGRAL_NANOS="+integralNanos+" ERI_HASH="+hash);
            Reference.reachabilityFence(eri);Reference.reachabilityFence(ao);
        }
    }
}
