package totah.lab.athena.relaxation;

import totah.lab.gaia.structure.AtomReference;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Deterministic Gaia atom to OpenMM particle and component-role mapping. */
public record SystemAtomMapping(List<Entry> entries, String sha256, String provenance) {
    public SystemAtomMapping {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (entries.isEmpty()) throw new IllegalArgumentException("atom mapping must not be empty");
        provenance = require(provenance, "provenance");
        Set<Integer> particles = new HashSet<>();
        Set<MappedAtom> atoms = new HashSet<>();
        for (Entry entry : entries) {
            Objects.requireNonNull(entry, "mapping entry");
            if (!particles.add(entry.openMmParticleIndex())) {
                throw new IllegalArgumentException("duplicate OpenMM particle index "
                        + entry.openMmParticleIndex());
            }
            if (!atoms.add(new MappedAtom(component(entry.componentRole()), entry.gaiaAtom()))) {
                throw new IllegalArgumentException("duplicate Gaia atom mapping " + entry.gaiaAtom());
            }
        }
        int maximum = particles.stream().mapToInt(Integer::intValue).max().orElseThrow();
        if (maximum + 1 != entries.size() || !particles.contains(0)) {
            throw new IllegalArgumentException("particle mapping must completely cover 0..N-1");
        }
        String calculated = calculateHash(entries, provenance);
        if (!calculated.equals(sha256)) {
            throw new IllegalArgumentException("mapping SHA-256 mismatch: calculated " + calculated);
        }
    }

    public static SystemAtomMapping create(List<Entry> entries, String provenance) {
        return new SystemAtomMapping(entries, calculateHash(entries, provenance), provenance);
    }

    public static String calculateHash(List<Entry> entries, String provenance) {
        Objects.requireNonNull(entries, "entries");
        String normalizedProvenance = require(provenance, "provenance");
        StringBuilder canonical = new StringBuilder("athena-system-atom-map-v1\n")
                .append(normalizedProvenance).append('\n');
        entries.stream().sorted(Comparator.comparingInt(Entry::openMmParticleIndex))
                .forEach(entry -> canonical.append(entry.openMmParticleIndex()).append('|')
                        .append(entry.componentRole()).append('|')
                        .append(entry.gaiaAtom().chainId()).append('|')
                        .append(entry.gaiaAtom().residueNumber()).append('|')
                        .append((int) entry.gaiaAtom().insertionCode()).append('|')
                        .append(entry.gaiaAtom().atomName()).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private static String require(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }

    public record Entry(AtomReference gaiaAtom, int openMmParticleIndex,
            ComponentRole componentRole) {
        public Entry {
            Objects.requireNonNull(gaiaAtom, "gaiaAtom");
            if (openMmParticleIndex < 0) throw new IllegalArgumentException("negative particle index");
            Objects.requireNonNull(componentRole, "componentRole");
        }
    }

    public enum ComponentRole {
        PROTEIN_BACKBONE, PROTEIN_SIDE_CHAIN, LIGAND, SAM, SOLVENT, ION
    }

    private static String component(ComponentRole role) {
        return switch (role) {
            case LIGAND -> "LIGAND";
            case SAM -> "SAM";
            case PROTEIN_BACKBONE, PROTEIN_SIDE_CHAIN, SOLVENT, ION -> "RECEPTOR";
        };
    }

    private record MappedAtom(String component, AtomReference atom) {}
}
