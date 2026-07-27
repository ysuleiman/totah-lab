package totah.lab.io;


import java.io.IOException;

public interface Adapter<I, O> {
    boolean supports(I input);
    O parse(I input) throws IOException;
}
