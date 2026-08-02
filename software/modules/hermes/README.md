# Hephaestus

Hephaestus is a molecular preparation library for docking workflows.

It prepares proteins and ligands for downstream docking engines while remaining independent of any specific file format. File reading and writing are delegated to Hermes.

Current capabilities include:

* receptor preparation
* ligand preparation (in progress)
* hydrogenation
* residue state assignment
* topology construction
* Amber charge assignment
* AutoDock4 atom typing
* receptor flexibility preparation
* rigid receptor PDBQT export
* comprehensive preparation validation
* PDBQT validation

## Architecture

```
Gaia
    Molecular model
        │
        ▼
Hermes
    Readers / Writers
        │
        ▼
Hephaestus
    Preparation
    Validation
```

Preparation algorithms remain inside Hephaestus.

Serialization remains inside Hermes.

The molecular domain remains inside Gaia.

## Features

### Preparation

* immutable preparation pipeline
* chain-aware processing
* Amber residue templates
* topology generation
* charge assignment
* AD4 atom typing
* flexible receptor model generation

### Validation

* prepared protein validation
* flexibility model validation
* PDBQT export validation
* serializer validation
* standalone PDBQT validation

Validation reports aggregate all detectable issues rather than stopping on the first error.

## Library Usage

Typical workflow:

```
read structure
        ↓
prepare receptor
        ↓
validate preparation
        ↓
export PDBQT
        ↓
validate generated PDBQT
```

## Public API

```
HephaestusClient

prepareReceptor(...)
prepareAndWriteReceptor(...)

validatePreparedProtein(...)
validatePdbqt(...)
validateFlexiblePdbqt(...)
```

## Design Goals

* immutable molecular objects
* chain-aware preparation
* deterministic output
* aggregated validation
* no module cycles
* reusable preparation algorithms
* reusable validators
* standalone library
* standalone CLI support

## Modules

### Gaia

Core molecular model.

### Hermes

Readers, writers, and file validation.

### Hephaestus

Preparation algorithms and molecular validation.

## Current Status

Implemented:

* receptor preparation
* topology generation
* hydrogenation
* hydrogen optimization
* residue-state assignment
* Amber charges
* AD4 atom typing
* receptor flexibility model
* rigid receptor export
* preparation validation
* PDBQT validation

Planned:

* ligand preparation
* ligand flexibility
* additional docking formats
* CLI application
