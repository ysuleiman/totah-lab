package totah.lab.gaia.structure;

import lombok.Getter;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class Structure {

    @ToString.Exclude
    private final List<Chain> chains;

    @ToString.Exclude
    private final List<Bond> bonds;

    @ToString.Exclude
    private final ConnectivityMetadata connectivityMetadata;

    @ToString.Exclude
    private final Map<String, Chain> chainIndex;

    public Structure(List<Chain> chains) {
        this(chains, List.of(), ConnectivityMetadata.ABSENT);
    }

    /**
     * Convenience constructor that asserts the given bonds represent
     * fully-mapped explicit connectivity
     * ({@link ConnectivityProvenance#EXPLICIT}). Callers whose bonds were
     * inferred or only partially mapped from the source must use
     * {@link #Structure(List, List, ConnectivityProvenance)} instead, so
     * downstream provenance checks are not misled.
     */
    public Structure(
            List<Chain> chains,
            List<Bond> bonds) {

        this(chains, bonds, bonds.isEmpty()
                ? ConnectivityMetadata.ABSENT
                : new ConnectivityMetadata(ConnectivityProvenance.EXPLICIT, List.of()));
    }

    public Structure(
            List<Chain> chains,
            List<Bond> bonds,
            ConnectivityProvenance provenance) {

        this(chains, bonds, new ConnectivityMetadata(
                Objects.requireNonNull(provenance, "provenance"),
                List.of()));
    }

    public Structure(
            List<Chain> chains,
            List<Bond> bonds,
            ConnectivityMetadata connectivityMetadata) {

        Objects.requireNonNull(chains, "chains");
        Objects.requireNonNull(bonds, "bonds");
        Objects.requireNonNull(connectivityMetadata, "connectivityMetadata");

        this.chains = List.copyOf(chains);
        this.chainIndex = buildChainIndex(this.chains);
        validateUniqueAtomReferences();
        this.bonds = validateBonds(List.copyOf(bonds));
        this.connectivityMetadata = connectivityMetadata;
    }

    public Optional<Chain> findChain(String chainId) {
        if (chainId == null || chainId.isBlank()) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                chainIndex.get(chainId.trim()));
    }

    public Optional<Residue> findResidue(
            String chainId,
            int residueNumber) {

        return findChain(chainId)
                .flatMap(chain ->
                        chain.findResidue(residueNumber));
    }

    public Optional<Residue> findResidue(
            String chainId,
            int residueNumber,
            Character insertionCode) {

        return findChain(chainId)
                .flatMap(chain ->
                        chain.findResidue(
                                residueNumber,
                                insertionCode));
    }

    public Optional<Residue> findResidue(ResidueId residueId) {
        Objects.requireNonNull(residueId, "residueId");
        return findChain(residueId.chainId())
                .flatMap(chain -> chain.findResidue(residueId));
    }

    public Residue residue(ResidueId residueId) {
        return findResidue(residueId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Residue not found: " + residueId));
    }

    public boolean contains(ResidueId residueId) {
        return findResidue(residueId).isPresent();
    }

    @ToString.Include(name = "chainCount")
    public int getChainCount() {
        return chains.size();
    }

    @ToString.Include(name = "residueCount")
    public int getResidueCount() {
        return chains.stream()
                .mapToInt(Chain::residueCount)
                .sum();
    }

    @ToString.Include(name = "atomCount")
    public int getAtomCount() {
        return chains.stream()
                .flatMap(chain -> chain.residues().stream())
                .mapToInt(Residue::getAtomCount)
                .sum();
    }

    public boolean isEmpty() {
        return chains.isEmpty();
    }

    public List<Bond> bonds() {
        return bonds;
    }

    public List<Bond> bondsFor(AtomReference atom) {
        Objects.requireNonNull(atom, "atom");
        return bonds.stream()
                .filter(bond -> bond.atom1().equals(atom) || bond.atom2().equals(atom))
                .toList();
    }

    public boolean hasBond(AtomReference atom1, AtomReference atom2) {
        return bondBetween(atom1, atom2).isPresent();
    }

    public Optional<Bond> bondBetween(AtomReference atom1, AtomReference atom2) {
        Objects.requireNonNull(atom1, "atom1");
        Objects.requireNonNull(atom2, "atom2");
        return bonds.stream()
                .filter(bond -> (bond.atom1().equals(atom1) && bond.atom2().equals(atom2))
                        || (bond.atom1().equals(atom2) && bond.atom2().equals(atom1)))
                .findFirst();
    }

    public Optional<Atom> findAtom(AtomReference reference) {
        Objects.requireNonNull(reference, "reference");
        Character insertionCode = reference.insertionCode() == ' '
                ? null
                : reference.insertionCode();
        return findResidue(reference.chainId(), reference.residueNumber(), insertionCode)
                .flatMap(residue -> residue.findAtom(reference.atomName()));
    }

    private static Map<String, Chain> buildChainIndex(
            List<Chain> chains) {
        Map<String, Chain> index = new LinkedHashMap<>();
        for (Chain chain : chains) {
            Objects.requireNonNull(
                    chain,
                    "chains must not contain null elements");
            Chain existing = index.putIfAbsent(
                    chain.id(),
                    chain);
            if (existing != null) {
                throw new IllegalArgumentException(
                        "Duplicate chain id: " + chain.id());
            }
        }
        return Map.copyOf(index);
    }

    private List<Bond> validateBonds(List<Bond> bonds) {
        Set<EndpointPair> unique = new HashSet<>();
        for (Bond bond : bonds) {
            Objects.requireNonNull(bond, "bonds must not contain null elements");
            if (findAtom(bond.atom1()).isEmpty() || findAtom(bond.atom2()).isEmpty()) {
                throw new IllegalArgumentException(
                        "Bond endpoint does not exist in structure: " + bond);
            }
            if (!unique.add(new EndpointPair(bond.atom1(), bond.atom2()))) {
                throw new IllegalArgumentException("Duplicate bond: " + bond);
            }
        }
        return bonds;
    }

    private void validateUniqueAtomReferences() {
        Set<AtomReference> references = new HashSet<>();
        for (Chain chain : chains) {
            for (Residue residue : chain.residues()) {
                char insertionCode = residue.getInsertionCode() == null
                        ? ' '
                        : residue.getInsertionCode();
                for (Atom atom : residue.getAtoms()) {
                    AtomReference reference = new AtomReference(
                            chain.id(), residue.getNumber(), insertionCode, atom.getName());
                    if (!references.add(reference)) {
                        throw new IllegalArgumentException(
                                "Duplicate canonical atom reference: " + reference);
                    }
                }
            }
        }
    }

    private record EndpointPair(AtomReference atom1, AtomReference atom2) {
    }
}
