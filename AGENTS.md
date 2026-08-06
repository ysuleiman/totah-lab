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

## Pocket evidence pipeline
- Never merge evidence dimensions into a master score; identity,
  substitution, chemistry, geometry, sequence consistency and contact
  conservation stay separate end to end. The verdict comes only from
  PocketAssessmentRules (uncalibrated) and always carries its reason.
- Retrieval ranks/scores are never invented: a channel that did not
  evaluate a candidate reports evaluated=false with empty optionals.
  Chosen-reference pockets get guaranteed evaluation, never a bonus.
- Evidence records live in athena (totah.lab.athena.pocket.evidence);
  web-api maps them to *View DTOs — do not serialize athena records
  into API JSON.
- Batch jobs are gated CommandLineRunners
  (totah.<name>.enabled=true, dry-run default true), run with
  --spring.main.web-application-type=none; one transaction per
  structure; find-or-create idempotency.

## Don't
- Don't rewrite unrelated code.
- Don't reformat the whole project.
- Don't remove comments unless incorrect.