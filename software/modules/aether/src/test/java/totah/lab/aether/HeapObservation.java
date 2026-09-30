package totah.lab.aether;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicReference;

/** Samples simultaneous used heap, unlike a sum of independently timed memory-pool peaks. */
public final class HeapObservation implements AutoCloseable {
    private final String label;
    private long peak=used();
    private final AtomicReference<Throwable> failure=new AtomicReference<>();
    private final Thread sampler;
    public HeapObservation(String label) {
        this.label=label;
        sampler=Thread.ofPlatform().daemon().start(()->{
            try{while(!Thread.currentThread().isInterrupted()){sample();Thread.sleep(5);}}
            catch(InterruptedException ignored){Thread.currentThread().interrupt();}
            catch(Throwable error){failure.set(error);}
        });
    }
    public static long used(){return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();}
    private synchronized void sample(){peak=Math.max(peak,used());}
    public synchronized void resetPeak(){peak=used();}
    public synchronized long peakUsedHeap(){sample();return peak;}
    @Override public void close() throws InterruptedException {
        sample();sampler.interrupt();sampler.join();
        if(failure.get()!=null)throw new AssertionError("Heap sampler failed",failure.get());
        System.out.println("MEMORY_HEAP "+label+" sampled_peak_used_heap="+peakUsedHeap()+" sampling_ms=5 max_heap="+Runtime.getRuntime().maxMemory());
    }
}
