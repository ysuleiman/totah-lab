# Hephaestus client and CLI

The executable client belongs to Hephaestus. Hermes supplies the readers,
PDBQT writers, and PDBQT validators used by it.

## Implemented commands

```text
prepare-receptor
validate-pdbqt
validate-flex-pdbqt
version
help
```

The generated `--help` output in Hephaestus is the command-line contract and
is derived from its registered commands. Flexible receptor preparation,
ligand preparation, prepared-state JSON loading, inspect, and generic convert
commands are planned and are intentionally not advertised as active commands.
