# Pocket Module Boundaries

Pocket code is divided by responsibility rather than by a shared package name.

## Domain model

`domain-model` owns the reusable pocket concepts:

```text
totah.lab.pocket
  Pocket
  PocketBox
  Dimensions
  PocketSource
  ResidueRef
  Sphere

totah.lab.pocket.geometry
  PocketGeometry
```

`PocketGeometry` contains pure deterministic calculations. It performs no file,
database, or pipeline operations.

## Readers

`pocket-reader` owns external-format adapters:

```text
totah.lab.pocket.FPocketParser
totah.lab.pocket.P2RankJsonParser
totah.lab.io.FPocketAdapter
totah.lab.io.P2RankAdapter
```

These classes translate external pocket formats into the domain model.

## Application services and exporters

`pipeline` owns behavior that depends on infrastructure:

```text
totah.lab.pocket.analysis.PosePocketContactAnalyzer
totah.lab.pocket.export.PyMolExporter
```

Database analysis and file export must not be moved into `domain-model`.

## Removed legacy API module

The former `api` Maven module defined a second, incompatible pocket object
model. No active module depended on it, so it was removed from the reactor and
dependency management.

A locally modified legacy `PocketAnalyzer` was preserved verbatim at
`docs/archive/legacy-pocket-api/PocketAnalyzer.java`. It is not compiled
because it depends on the removed legacy `Pocket`, `Residue`, `Atom`, and
alpha-sphere APIs. Porting it requires a separate behavioral review against the
active domain model.
