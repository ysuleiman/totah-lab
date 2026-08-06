package totah.lab.hephaestus.client;

import java.util.Set;

public final class HephaestusCapabilities {
    private HephaestusCapabilities() {
    }

    public static Set<HephaestusCapability> supported() {
        return Set.of(
                HephaestusCapability.PREPARE_RIGID_RECEPTOR,
                HephaestusCapability.VALIDATE_PREPARED_PROTEIN,
                HephaestusCapability.VALIDATE_PDBQT,
                HephaestusCapability.VALIDATE_FLEXIBLE_PDBQT,
                HephaestusCapability.PREPARE_LIGAND);
    }
}
