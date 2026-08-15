# Evidence identity and persistence

Two records are exact duplicates only when their complete `EvidenceIdentity` hashes match. The hash distinguishes electronic state, protocol, constraints, and outputs even at identical coordinates. Same geometry under another method is evidence, but not interchangeable evidence.

Source artifacts remain immutable. A one-time importer extracts numeric results and provenance into Prometheus-owned JSON files. The canonical store writes one file per quantum or classical evidence record, a manifest containing every record checksum, and a current-generation pointer. Loading verifies checksums before constructing the memory index. Tampered canonical records fail closed.

A source fingerprint, importer version change, or schema version change creates a new generation. An unchanged generation is loaded without invoking the importer. Import timestamps never participate in scientific identity.
