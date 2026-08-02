package totah.lab.hephaestus.receptor.cleanup;


import totah.lab.gaia.chemistry.ElementResolver;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MetalIonPolicy {

    private static final Map<String, IonParameters> FIXED_IONS = Map.of(
            "ZN", new IonParameters("Zn", 2.0, "Zn"),
            "MG", new IonParameters("Mg", 2.0, "Mg"),
            "CA", new IonParameters("Ca", 2.0, "Ca"),
            "NA", new IonParameters("Na", 1.0, null),
            "K", new IonParameters("K", 1.0, null),
            "CL", new IonParameters("Cl", -1.0, "Cl")
    );

    private static final Map<String, String> AMBIGUOUS_IONS = Map.of(
            "FE", "iron oxidation state is ambiguous",
            "MN", "manganese oxidation state is ambiguous",
            "CU", "copper oxidation state is ambiguous",
            "CO", "cobalt oxidation state is ambiguous",
            "NI", "nickel oxidation state is ambiguous"
    );

    public Optional<IonParameters> fixedIon(Residue residue) {
        if (!isMonoatomicIon(residue)) {
            return Optional.empty();
        }
        return Optional.ofNullable(FIXED_IONS.get(elementKey(residue)));
    }

    public boolean isKnownIonResidue(Residue residue) {
        if (!isMonoatomicIon(residue)) {
            return false;
        }
        String key = elementKey(residue);
        return FIXED_IONS.containsKey(key) || AMBIGUOUS_IONS.containsKey(key);
    }

    public String requireFixedChargeFailureMessage(Residue residue) {
        String key = elementKey(residue);
        String reason = AMBIGUOUS_IONS.get(key);
        if (reason != null) {
            return "No fixed charge for " + residueLabel(residue) + ": " + reason
                    + "; provide an explicit metal policy before docking prep";
        }
        return "Unsupported ion '" + key + "' in " + residueLabel(residue);
    }

    public String requireAd4Type(IonParameters ion, Residue residue) {
        if (ion.ad4Type() == null || ion.ad4Type().isBlank()) {
            throw new IllegalArgumentException("No supported AutoDock4 atom type for "
                    + residueLabel(residue) + " (" + ion.elementSymbol()
                    + "); remove it or add an explicit docking policy before PDBQT export");
        }
        return ion.ad4Type();
    }

    private boolean isMonoatomicIon(Residue residue) {
        return residue != null && residue.getAtomCount() == 1;
    }

    private String elementKey(Residue residue) {
        Atom atom = residue.getAtoms().getFirst();
        return ElementResolver.resolve(atom, residue).toUpperCase(Locale.ROOT);
    }

    private String residueLabel(Residue residue) {
        String insertion = residue.getInsertionCode() == null || residue.getInsertionCode() == ' '
                ? ""
                : residue.getInsertionCode().toString();
        return residue.getName() + " " + residue.getNumber() + insertion;
    }

    public record IonParameters(String elementSymbol, double formalCharge, String ad4Type) {
    }
}
