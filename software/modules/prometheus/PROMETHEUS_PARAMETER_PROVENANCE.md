# Parameter provenance

Every parameter retains molecule, atom identities, parameter type, value and units, derivation method/version, source evidence hashes, literature/source reference, candidate lineage, and validation status. Generic force-field atom types do not prevent molecule-specific parameter identities.

Published/source parameters and derived candidates are different artifacts. Source values are immutable; serialization normalization or format conversion creates a named derived artifact with its own checksum. Rejected candidates remain preserved negative evidence and cannot be promoted.

Provenance establishes where a value came from. It does not establish chemical transferability or validation.
