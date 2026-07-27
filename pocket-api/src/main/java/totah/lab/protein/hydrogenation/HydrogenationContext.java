package totah.lab.protein.hydrogenation;

import totah.lab.protein.Atom;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.topology.SpatialClashChecker;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Per-run mutable state passed through the hydrogenation pipeline.
 */
public final class HydrogenationContext {
    private final ProtonationConfig config;
    private final List<Residue> allResidues;
    private final SpatialClashChecker checker;
    private final Set<Residue> disulfideCys;
    private final List<Atom> metalAtoms;
    private final double metalCutoff;
    private final Map<String, String> amberTemplates;

    public HydrogenationContext(ProtonationConfig config,
                                List<Residue> allResidues,
                                SpatialClashChecker checker,
                                Set<Residue> disulfideCys,
                                List<Atom> metalAtoms,
                                double metalCutoff,
                                Map<String, String> amberTemplates) {
        this.config = config;
        this.allResidues = List.copyOf(allResidues);
        this.checker = checker;
        this.disulfideCys = disulfideCys != null ? Set.copyOf(disulfideCys) : Collections.emptySet();
        this.metalAtoms = metalAtoms != null ? List.copyOf(metalAtoms) : Collections.emptyList();
        this.metalCutoff = metalCutoff;
        this.amberTemplates = amberTemplates != null ? Map.copyOf(amberTemplates) : Collections.emptyMap();
    }

    public ProtonationConfig config() { return config; }
    public List<Residue> allResidues() { return allResidues; }
    public SpatialClashChecker checker() { return checker; }
    public Set<Residue> disulfideCys() { return disulfideCys; }

    public boolean isDisulfideCys(Residue r) { return disulfideCys.contains(r); }

    public String baseTemplateName(Residue residue) {
        String template = amberTemplates.get(residueKey(residue));
        if (template == null || template.isBlank()) return null;
        String normalized = template.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() == 4 && (normalized.charAt(0) == 'N' || normalized.charAt(0) == 'C')) {
            return normalized.substring(1);
        }
        return normalized;
    }

    public boolean usesNTerminalTemplate(Residue residue) {
        return usesTerminalTemplate(residue, 'N');
    }

    public boolean usesCTerminalTemplate(Residue residue) {
        return usesTerminalTemplate(residue, 'C');
    }

    public boolean isNearMetal(Point3D pos) {
        for (Atom m : metalAtoms) {
            if (distance(pos, m.getPosition()) <= metalCutoff) return true;
        }
        return false;
    }

    public void tryAdd(List<Atom> atoms, Atom h) {
        HydrogenationGeometry.tryAdd(atoms, h, checker, config.clashCutoff());
    }

    public void tryAdd(List<Atom> atoms, Atom h, Atom parent) {
        HydrogenationGeometry.tryAdd(atoms, h, parent, checker, config.clashCutoff());
    }

    private static double distance(Point3D a, Point3D b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }

    private String residueKey(Residue residue) {
        return residue.getChain() + ":" + residue.getNumber();
    }

    private boolean usesTerminalTemplate(Residue residue, char prefix) {
        String template = amberTemplates.get(residueKey(residue));
        return template != null
                && template.length() == 4
                && Character.toUpperCase(template.charAt(0)) == prefix;
    }
}
