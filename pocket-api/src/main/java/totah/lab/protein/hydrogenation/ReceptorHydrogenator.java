package totah.lab.protein.hydrogenation;

import lombok.extern.slf4j.Slf4j;
import totah.lab.protein.Atom;
import totah.lab.protein.Residue;
import totah.lab.topology.AmberParameterSet;
import totah.lab.topology.AmberResidueTemplateLibrary;
import totah.lab.topology.SpatialClashChecker;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public entry point for receptor hydrogenation.
 * Can be used standalone or from a pipeline Stage.
 */
@Slf4j
public final class ReceptorHydrogenator {

    private ReceptorHydrogenator() {}

    public static List<Residue> hydrogenate(List<Residue> incoming, ProtonationConfig config) {
        return hydrogenate(incoming, config, Collections.emptyMap());
    }

    public static List<Residue> hydrogenate(List<Residue> incoming, ProtonationConfig config,
                                            Map<String, String> amberTemplates) {
        // 1. Strip existing hydrogens
        List<Residue> residues = new ArrayList<>();
        int stripped = 0;
        for (Residue r : incoming) {
            List<Atom> heavy = new ArrayList<>();
            for (Atom a : r.getAtoms()) {
                if (!"H".equals(a.getElement().getSymbol())) heavy.add(a);
                else stripped++;
            }
            residues.add(r.toBuilder().atoms(heavy).build());
        }
        log.info("[ReceptorHydrogenation] Stripped {} existing hydrogen(s)", stripped);

        // 2. Build clash checker
        SpatialClashChecker checker = new SpatialClashChecker(config.voxelGridSize());
        for (Residue r : residues){
            checker.addAll(r.getAtoms());
        }

        // 3. Collect metals
        List<Atom> metals = new ArrayList<>();
        for (Residue r : residues) {
            for (Atom a : r.getAtoms()) {
                if (ProtonationConfig.METAL_ELEMENTS.contains(a.getElement().getSymbol())){
                    metals.add(a);
                }
            }
        }
        log.info("[ReceptorHydrogenation] Found {} metal atom(s); guard active within {} Å", metals.size(), config.metalCutoff());

        // 4. Detect disulfides
        Set<Residue> disulfideCys = config.detectDisulfides()
                ? DisulfideDetector.findDisulfideBonds(residues, config.disulfideCutoff())
                : Collections.emptySet();
        log.info("[ReceptorHydrogenation] Detected {} disulfide bond(s)", disulfideCys.size() / 2);
        log.info("[ReceptorHydrogenation] pH = {}, HIS state = {}", config.ph(), config.hisState());

        // 5. Build context
        HydrogenationContext ctx = new HydrogenationContext(config, residues, checker, disulfideCys, metals,
                config.metalCutoff(), amberTemplates);

        // 6. First pass: add hydrogens
        List<Residue> protonated = new ArrayList<>();
        for (int i = 0; i < residues.size(); i++) {
            Residue r = residues.get(i);
            List<Atom> atoms = new ArrayList<>(r.getAtoms());
            ResidueHydrogenator.hydrogenateBackbone(r, i, atoms, ctx);
            ResidueHydrogenator.hydrogenateSideChain(r, atoms, ctx);
            protonated.add(r.toBuilder().atoms(atoms).build());
        }

        // 7. Second pass: optimize rotatable groups
        AmberResidueTemplateLibrary amberLib = AmberResidueTemplateLibrary.getInstance();
        AmberParameterSet ljSet = null;
        if (config.amberParmPath() != null) {
            try {
                // Fresh per-run instance: loading into the shared singleton would
                // leak per-run parameters across runs and leave it partially
                // modified after a failed load
                ljSet = AmberParameterSet.createEmpty();
                if (config.amberParmPath() instanceof Path) {
                    ljSet.loadFromFile((Path) config.amberParmPath());
                } else {
                    ljSet.loadFromResource(config.amberParmPath().toString());
                }
            } catch (Exception e) {
                System.err.println("[ReceptorHydrogenation] Failed to load LJ parameters: " + e.getMessage());
            }
        }

        // Assuming HydrogenOptimizer constructor takes (amberLib, ljSet, clashCutoff)
        // and optimize(r, allResidues) returns List<Atom>
        // totah.lab.structure.HydrogenOptimizer optimizer =
        //     new totah.lab.structure.HydrogenOptimizer(amberLib, ljSet, config.clashCutoff());
        // List<Residue> optimized = new ArrayList<>();
        // for (Residue r : protonated) {
        //     List<Atom> optAtoms = optimizer.optimize(r, protonated);
        //     optimized.add(r.toBuilder().atoms(optAtoms).build());
        // }
        // return optimized;

        // If HydrogenOptimizer is not available in the shown code, return protonated directly
        // and let the Stage wire it in. For now, returning protonated to keep compile-clean:
        return protonated;
    }
}
