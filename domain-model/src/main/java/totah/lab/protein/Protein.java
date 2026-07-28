package totah.lab.protein;

import lombok.Getter;
import lombok.ToString;
import totah.lab.pocket.Pocket;

import java.util.*;

@Getter
@ToString
public class Protein {
    private TargetId targetId;
    @ToString.Include
    private String uniProtId;
    @ToString.Include
    private String name;
    @ToString.Include
    private String gene;
    @ToString.Include
    private String organism;
    @ToString.Include
    private String function;

    private final Structure structure;

    // Tells Lombok NOT to generate a standard public getter for this field
    //@Getter(lombok.AccessLevel.NONE)
    @ToString.Exclude
    private final List<Pocket> pockets = new ArrayList<>();

    public Protein(TargetId targetId, Structure structure) {
        this.targetId = targetId;
        this.structure = Objects.requireNonNull(structure);
    }

    public void addPocket(Pocket pocket) {
        Objects.requireNonNull(pocket);
        // MAGIC HAPPENS HERE:
        // Give the pocket a direct link to this specific structure's instant lookup method
        pocket.bindResolver(ref -> this.structure.getResidue(ref.chain(), ref.number()));
        this.pockets.add(pocket);
    }

    public void addPockets(List<Pocket> pockets) {
        for (Pocket p : Objects.requireNonNull(pockets)) {
            addPocket(p);
        }
    }

    @ToString.Include(name = "pocketCount")
    public int getPocketCount() {
        return pockets.size();
    }

    public Pocket getBestPocket() {
        return pockets.stream()
                .max(Comparator.comparingDouble(p -> p.getScore() != null ? p.getScore() : 0.0))
                .orElse(null);
    }


}
