package totah.lab.daedalus.cli;

import java.io.PrintWriter;

public interface CliCommand {
    String name();

    String description();

    String help();

    int execute(String[] arguments, PrintWriter out, PrintWriter err);
}
