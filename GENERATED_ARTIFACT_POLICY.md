# Generated Artifact Policy

Git is the source of truth for code and compact, reviewable scientific provenance. It is not the storage layer for recoverable runtime bulk.

## Keep in Git

- Java and project-owned orchestration source;
- tests and deterministic regression fixtures;
- immutable protocols, manifests, checksums, frozen policies, and provenance receipts;
- compact reports and tabular evidence required to reproduce a conclusion;
- prepared scientific inputs when they are authoritative and reasonably sized.

## Keep out of Git

- local virtual environments, dependency checkouts, caches, and bytecode;
- scratch directories and recoverable backup copies;
- trajectories, checkpoints, logs, and other large runtime intermediates;
- duplicated archives whose contents have an authoritative home elsewhere.

The names `analysis`, `research`, and `tools` do not determine retention. Those trees contain both durable source/evidence and generated data, so they must not be ignored or untracked wholesale.

Large authoritative binary inputs or irreplaceable results should be placed in checksum-addressed external storage or Git LFS, with their manifest and retrieval provenance committed here. A new broad ignore rule must be reviewed against tracked and untracked inventories before adoption.
