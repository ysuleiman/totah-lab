# Lab Report

`lab-report` owns reusable scientific report analysis, evidence, narrative,
validation, and rendering.

The module is independent of HTTP and persistence. It may consume domain
objects and aggregated statistics through interfaces, but it must not query
PostgreSQL or read controller DTOs.

Initial scope:

- pocket identity and provenance;
- intrinsic pocket geometry;
- pocket residue composition;
- optional aggregated docking/contact statistics;
- transparent, configurable residue annotations;
- report quality and data-availability information;
- evidence-linked narrative generation through Spring AI;
- JSON, Markdown, and standalone HTML rendering.

Adapters remain outside the module:

- `web-api` adapts database results and exposes report endpoints;
- the React application renders canonical report JSON;
- PDF rendering may consume the same report model.

Java analyzers remain the source of all scientific measurements. Spring AI
receives the structured report and evidence identifiers, produces narrative
findings, and is validated before the narrative can enter a complete report.

The report must remain pocket-source agnostic. Missing source-specific metrics
remain absent and are never represented as zero.
