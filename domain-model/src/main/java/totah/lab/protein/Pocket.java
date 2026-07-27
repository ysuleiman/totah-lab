package totah.lab.protein;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import totah.lab.pocket.PocketSource;
import totah.lab.pocket.ResidueRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Getter
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class Pocket {

    @ToString.Include
    private long id;

    @ToString.Include
    private String name;

    @ToString.Include
    private Point3D center;

    @ToString.Include
    private Double score;

    @Singular
    private List<ResidueRef> residueRefs;

    @ToString.Include
    private PocketSource source;

    @ToString.Include
    @Builder.Default
    private Map<String, Object> attributes = new HashMap<>();

    // Automatically excluded from ToString because onlyExplicitlyIncluded = true is set at the class level
    private Function<ResidueRef, Residue> residueResolver;

    // Custom constructor matching exact class properties to fix the Lombok compilation error
    public Pocket(long id, String name, Point3D center, Double score,
                  List<ResidueRef> residueRefs, PocketSource source,
                  Map<String, Object> attributes, Function<ResidueRef, Residue> residueResolver) {
        this.id = id;
        this.name = name;
        this.center = center;
        this.score = score;
        // Forces built list collections to be standard mutable ArrayLists so .add() methods work safely
        this.residueRefs = residueRefs != null ? new ArrayList<>(residueRefs) : new ArrayList<>();
        this.source = source;
        this.attributes = attributes != null ? new HashMap<>(attributes) : new HashMap<>();
        this.residueResolver = residueResolver;
    }

    /**
     * Internal framework setter used by the Protein class to bind the lookup engine.
     */
    protected void bindResolver(Function<ResidueRef, Residue> resolver) {
        this.residueResolver = resolver;
    }

    public List<Residue> getResidues() {
        if (this.residueResolver == null) {
            throw new IllegalStateException("Pocket has not been bound to a protein structure context.");
        }

        List<Residue> actualResidues = new ArrayList<>();
        for (ResidueRef ref : residueRefs) {
            Residue realResidue = this.residueResolver.apply(ref);
            if (realResidue != null) {
                actualResidues.add(realResidue);
            }
        }
        return actualResidues;
    }

    @ToString.Include(name = "residueCount")
    public int getResidueCount() {
        return residueRefs == null ? 0 : residueRefs.size();
    }

    public void add(String key, Object value) {
        this.attributes.put(key, value);
    }

    public void addResidueRef(ResidueRef residueRef) {
        if (this.residueRefs == null) {
            this.residueRefs = new ArrayList<>();
        }
        this.residueRefs.add(residueRef);
    }

    public void addResidueRefs(List<ResidueRef> residueRefs) {
        if (residueRefs == null) return;
        if (this.residueRefs == null) {
            this.residueRefs = new ArrayList<>();
        }
        this.residueRefs.addAll(residueRefs);
    }
}
