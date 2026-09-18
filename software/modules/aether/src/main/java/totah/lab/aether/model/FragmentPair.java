package totah.lab.aether.model;

import java.util.ArrayList;
import java.util.Objects;

/** Disjoint ordered fragments; the complex order is A nuclei followed by B nuclei. */
public record FragmentPair(MolecularFragment a, MolecularFragment b) {
    public FragmentPair {
        Objects.requireNonNull(a);Objects.requireNonNull(b);
        if(a.id().equals(b.id()))throw new IllegalArgumentException("Fragment identities must be distinct");
        for(var x:a.system().nuclei())for(var y:b.system().nuclei()) {
            if(x.centerBohr().x()==y.centerBohr().x()&&x.centerBohr().y()==y.centerBohr().y()&&x.centerBohr().z()==y.centerBohr().z())
                throw new IllegalArgumentException("Fragments have coincident real nuclei");
        }
    }
    public QuantumSystem complex() {
        var nuclei=new ArrayList<>(a.system().nuclei());nuclei.addAll(b.system().nuclei());
        return new QuantumSystem(nuclei,Math.addExact(a.system().molecularCharge(),b.system().molecularCharge()),1);
    }
}
