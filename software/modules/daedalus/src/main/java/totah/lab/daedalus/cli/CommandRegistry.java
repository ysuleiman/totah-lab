package totah.lab.daedalus.cli;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CommandRegistry {
    private final Map<String, CliCommand> commands;

    public CommandRegistry(Collection<? extends CliCommand> commands) {
        Objects.requireNonNull(commands, "commands");
        Map<String, CliCommand> registered = new LinkedHashMap<>();
        for (CliCommand command : commands) {
            Objects.requireNonNull(command, "command");
            if (registered.putIfAbsent(command.name(), command) != null) {
                throw new IllegalArgumentException(
                        "Duplicate command: " + command.name());
            }
        }
        this.commands = Collections.unmodifiableMap(registered);
    }

    public Optional<CliCommand> find(String name) {
        return Optional.ofNullable(commands.get(name));
    }

    public Collection<CliCommand> commands() {
        return commands.values();
    }

    public Set<String> names() {
        return commands.keySet();
    }
}
