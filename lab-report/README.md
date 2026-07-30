# Lab Report

`lab-report` owns reusable scientific report analysis and report models.

The module is independent of HTTP, persistence, and presentation concerns.
It may consume domain objects and aggregated statistics through interfaces,
but it must not query PostgreSQL, read controller DTOs, or render UI-specific
output directly.

Initial scope:

- pocket identity and provenance;
- intrinsic pocket geometry;
- pocket residue composition;
- optional aggregated docking/contact statistics;
- transparent, configurable residue annotations;
- report quality and data-availability information.

Renderers and adapters belong outside the core analysis:

- `web-api` adapts database results and exposes report endpoints;
- the React application renders canonical report JSON;
- PDF rendering consumes the same report model.

The report must remain pocket-source agnostic. Missing source-specific metrics
remain absent and are never represented as zero.
