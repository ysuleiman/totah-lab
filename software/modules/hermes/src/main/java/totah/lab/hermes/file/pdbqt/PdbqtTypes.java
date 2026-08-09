package totah.lab.hermes.file.pdbqt;

import java.util.Set;

/** Canonical AutoDock4 atom types accepted by Hermes PDBQT output. */
public final class PdbqtTypes {
    private static final Set<String> SUPPORTED = Set.of(
            "C", "A", "N", "NA", "O", "OA", "S", "SA", "P",
            "HD", "H", "F", "Cl", "Br", "I", "Mg", "Mn", "Fe",
            "Zn", "Ca");

    private PdbqtTypes() {
    }

    public static boolean isSupported(String type) {
        return type != null && SUPPORTED.contains(type);
    }

    public static Set<String> supported() {
        return SUPPORTED;
    }
}
