package totah.lab.math.charges;

import java.util.List;

/**
 * Lightweight DTO: geometry + topology + element info for charge models.
 * Decoupled from protein.Residue so models work on any molecule.
 */
public interface ChargeSystem {
    int size();
    double getX(int i);
    double getY(int i);
    double getZ(int i);
    String getElement(int i);
    List<Integer> getNeighbors(int i);
}