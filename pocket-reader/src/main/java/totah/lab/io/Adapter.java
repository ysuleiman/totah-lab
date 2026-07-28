package totah.lab.io;


import java.io.IOException;
import java.util.List;

public interface Adapter<I, O> {
    default String getName() {
        return getClass().getSimpleName();
    }
    boolean supports(I input);
    List<O> parse(I input) throws IOException;
}
