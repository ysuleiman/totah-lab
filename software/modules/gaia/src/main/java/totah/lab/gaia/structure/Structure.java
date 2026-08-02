package totah.lab.gaia.structure;

import lombok.Getter;
import lombok.ToString;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class Structure {

    @ToString.Exclude
    private final List<Chain> chains;

    @ToString.Exclude
    private final List<Bond> bonds;

    @ToString.Exclude
    private final Map<String, Chain> chainIndex;

    public Structure(List<Chain> chains) {
        this(chains, List.of());
    }

    public Structure(
            List<Chain> chains,
            List<Bond> bonds) {

        Objects.requireNonNull(chains, "chains");
        Objects.requireNonNull(bonds, "bonds");

        this.chains = List.copyOf(chains);
        this.bonds = validateBonds(
                List.copyOf(bonds),
                atomCount(this.chains));
        this.chainIndex = buildChainIndex(this.chains);
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

    private static int atomCount(List<Chain> chains) {
        return chains.stream()
                .mapToInt(chain -> chain.residues().stream()
                        .mapToInt(Residue::getAtomCount)
                        .sum())
                .sum();
    }

    private static List<Bond> validateBonds(
            List<Bond> bonds,
            int atomCount) {

        for (Bond bond : bonds) {
            Objects.requireNonNull(
                    bond,
                    "bonds must not contain null elements");
            if (bond.atomIndexA() >= atomCount
                    || bond.atomIndexB() >= atomCount) {
                throw new IllegalArgumentException(
                        "Bond endpoint is outside structure atom order: "
                                + bond);
            }
        }
        return bonds;
    }
}
