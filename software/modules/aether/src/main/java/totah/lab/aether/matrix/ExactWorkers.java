package totah.lab.aether.matrix;

import java.util.ArrayDeque;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/** Bounded independent work, consumed strictly in submission order. Never reduces floating-point values. */
final class ExactWorkers implements AutoCloseable {
    private final int count;
    private final ExecutorService executor;

    ExactWorkers(int count) {
        if(count<1||count>32)throw new IllegalArgumentException("Worker count must be 1..32");
        this.count=count;
        executor=count==1?null:Executors.newFixedThreadPool(count,Thread.ofPlatform().daemon().name("aether-exact-",0).factory());
    }
    int capacity(){return count==1?1:2*count;}
    <T,R> void forEachOrdered(Iterable<T> inputs,Function<T,R> evaluate,Consumer<R> consume) {
        if(executor==null){for(T input:inputs)consume.accept(evaluate.apply(input));return;}
        var pending=new ArrayDeque<Future<R>>();
        try {
            for(T input:inputs) {
                pending.addLast(executor.submit(()->evaluate.apply(input)));
                if(pending.size()==capacity())consume.accept(await(pending.removeFirst()));
            }
            while(!pending.isEmpty())consume.accept(await(pending.removeFirst()));
        }finally{for(var future:pending)future.cancel(true);}
    }
    private static <T> T await(Future<T> future) {
        try{return future.get();}
        catch(InterruptedException e){future.cancel(true);Thread.currentThread().interrupt();throw new CancellationException("Exact evaluation interrupted");}
        catch(ExecutionException e){
            if(e.getCause() instanceof RuntimeException r)throw r;
            if(e.getCause() instanceof Error error)throw error;
            throw new IllegalStateException("Exact worker failed",e.getCause());
        }
    }
    @Override public void close(){if(executor!=null)executor.close();}
}
