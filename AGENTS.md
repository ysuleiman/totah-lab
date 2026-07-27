# Project Instructions

## General
- Java 21
- Maven project
- Never change public APIs without asking.
- Prefer immutable objects.
- Avoid unnecessary dependencies.

## Coding Style
- Use builder pattern where appropriate.
- Prefer Path over File.
- Use try-with-resources.
- Throw checked exceptions for I/O.

## Testing
- Add JUnit 5 tests for new functionality.
- Test resources are under src/test/resources.

## Domain
- This project implements a molecular docking pipeline.
- Protein preparation should match Meeko/Open Babel behavior when possible.
- Preserve atom ordering.
- Amber charges are the source of truth unless explicitly overridden.

## Don't
- Don't rewrite unrelated code.
- Don't reformat the whole project.
- Don't remove comments unless incorrect.