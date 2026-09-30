package totah.lab.aether.matrix;

import java.util.ArrayList;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AetherExactWorkersTest {
    @Test void independentResultsAreConsumedInSubmissionOrder() {
        try(var workers=new ExactWorkers(8)) {
            var inputs=IntStream.range(0,100).boxed().toList();
            var actual=new ArrayList<Integer>();
            workers.forEachOrdered(inputs,i->i*i,actual::add);
            assertEquals(inputs.stream().map(i->i*i).toList(),actual);
            assertEquals(16,workers.capacity());
        }
    }
    @Test void workerFailurePropagatesWithoutFabricatedResults() {
        try(var workers=new ExactWorkers(8)) {
            var actual=new ArrayList<Integer>();
            assertThrows(ArithmeticException.class,()->workers.forEachOrdered(
                    IntStream.range(0,100).boxed().toList(),i->{
                        if(i==3)throw new ArithmeticException("deliberate worker failure");return i;
                    },actual::add));
            assertEquals(java.util.List.of(0,1,2),actual);
        }
    }
}
