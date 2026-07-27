package totah.lab.pocket;

import java.util.List;

public interface Residue {
    public String getName();
    public int getNumber();
    public String getChainId();
    public String getPosition();

    public List<Atom> getAtoms();
}
