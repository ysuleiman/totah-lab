package totah.lab.hephaestus.ligand.charge;

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

    default int getFormalCharge(int i) {
        return 0;
    }

    default double getBondOrder(int atomIndexA, int atomIndexB) {
        return 1.0;
    }

    default boolean isAromatic(int i) {
        return false;
    }
}
