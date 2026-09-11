package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.Canonizer;
import com.actelion.research.chem.Molecule;
import com.actelion.research.chem.SSSearcher;
import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import com.actelion.research.chem.forcefield.mmff.ForceFieldMMFF94;
import org.openmolecules.chem.conf.gen.ConformerGenerator;
import totah.lab.athena.design.backend.BackendEvidence;
import totah.lab.athena.design.backend.CanonicalIdentityService;
import totah.lab.athena.design.backend.ConformerGenerator3d;
import totah.lab.athena.design.backend.ConformerMinimizer;
import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.backend.MolecularSanitizer;
import totah.lab.athena.design.backend.StereochemistryService;
import totah.lab.athena.design.backend.SubstructureMatcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal replaceable OpenChemLib kernel. It performs no edit selection or target logic. */
public final class OclMolecularBackend implements MolecularSanitizer, CanonicalIdentityService,
        StereochemistryService, ConformerGenerator3d, ConformerMinimizer, SubstructureMatcher {
    public static final String BACKEND = "OPEN_CHEM_LIB";
    public static final String VERSION = "2026.7.2";
    private final OclGraphMapper mapper = new OclGraphMapper();

    @Override
    public MolecularSanitizer.Result sanitize(MolecularGraph graph, SanitizationPolicy policy)
            throws MolecularBackendException {
        try {
            var mapping = mapper.toOcl(graph);
            if (graph.atoms().stream().allMatch(atom -> atom.stereochemistry().equals("UNSPECIFIED")
                    || atom.stereochemistry().equals("NONE"))) {
                mapping.molecule().stripStereoInformation();
            }
            mapping.molecule().ensureHelperArrays(Molecule.cHelperCIP);
            mapping.molecule().validate();
            var after = mapper.fromOcl(mapping, mapping.molecule());
            var changes = meaningfulChanges(graph, after);
            boolean unauthorized = changes.stream().anyMatch(change ->
                    change.disposition() == BackendEvidence.Disposition.UNAUTHORIZED_MEANINGFUL_CHANGE
                            || (change.disposition() == BackendEvidence.Disposition.BENIGN_NORMALIZATION
                            && !policy.allowedBenignNormalizations().contains(normalizationId(change))));
            if (unauthorized && policy.failOnMeaningfulChange()) {
                throw new MolecularBackendException("OCL sanitization changed chemically meaningful graph dimensions");
            }
            return new MolecularSanitizer.Result(unauthorized ? after : graph, !unauthorized,
                    evidence("sanitize", mapping, changes, List.of("OCL_VALIDATE_PASS")));
        } catch (MolecularBackendException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MolecularBackendException("OCL sanitization failed", exception);
        }
    }

    @Override
    public CanonicalIdentityService.Result identify(MolecularGraph graph) throws MolecularBackendException {
        var mapping = mapper.toOcl(graph);
        try {
            mapping.molecule().ensureHelperArrays(Molecule.cHelperCIP);
            String idCode = new Canonizer(mapping.molecule()).getIDCode();
            return new CanonicalIdentityService.Result("OCL_IDCODE:" + idCode,
                    evidence("canonical-identity", mapping, List.of(), List.of("STEREO_AWARE_IDCODE")));
        } catch (RuntimeException exception) {
            throw new MolecularBackendException("OCL canonicalization failed", exception);
        }
    }

    @Override
    public StereochemistryService.Result validate(MolecularGraph graph) throws MolecularBackendException {
        var mapping = mapper.toOcl(graph);
        try {
            mapping.molecule().ensureHelperArrays(Molecule.cHelperCIP);
            mapping.molecule().validate();
            return new StereochemistryService.Result(true, mapping.molecule().getStereoCenterCount(),
                    evidence("stereo-validation", mapping, List.of(), List.of("OCL_STEREO_VALIDATE_PASS")));
        } catch (Exception exception) {
            throw new MolecularBackendException("OCL stereo validation failed", exception);
        }
    }

    @Override
    public ConformerGenerator3d.Result generate(MolecularGraph graph,
                                                ConformerGenerator3d.Configuration configuration)
            throws MolecularBackendException {
        var mapping = mapper.toOcl(graph);
        try {
            var generator = new ConformerGenerator(configuration.seed(), configuration.optimizeRigidFragments());
            generator.setTimeOut(configuration.timeoutMillis());
            if (!generator.initializeConformers(mapping.molecule())) {
                throw new MolecularBackendException("OCL conformer initialization failed");
            }
            var conformers = new ArrayList<ConformerGenerator3d.Conformer>();
            for (int index = 0; index < configuration.maximumConformers(); index++) {
                var conformer = generator.getNextConformer();
                if (conformer == null) break;
                conformers.add(new ConformerGenerator3d.Conformer("ocl-conf-" + (index + 1),
                        mapper.fromOcl(mapping, conformer.toMolecule())));
            }
            if (conformers.isEmpty()) throw new MolecularBackendException("OCL generated no conformers");
            return new ConformerGenerator3d.Result(conformers,
                    evidence("conformer-generation", mapping, List.of(),
                            List.of("seed=" + configuration.seed(), "count=" + conformers.size())));
        } catch (MolecularBackendException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MolecularBackendException("OCL conformer generation failed", exception);
        }
    }

    @Override
    public ConformerMinimizer.Result minimize(MolecularGraph graph,
                                              ConformerMinimizer.Configuration configuration)
            throws MolecularBackendException {
        var mapping = mapper.toOcl(graph);
        try {
            String forceField = OclMmff94Support.resolve(configuration.forceField());
            var ff = OclMmff94Support.create(mapping, forceField);
            int status = ff.minimise(configuration.maximumIterations(), configuration.gradientTolerance(),
                    configuration.functionTolerance());
            var minimized = mapper.fromOcl(mapping, ff.getMMFFMolecule());
            return new ConformerMinimizer.Result(minimized, status == 0, ff.getTotalEnergy(),
                    evidence("conformer-minimization", mapping, List.of(),
                            List.of("forceField=" + forceField, "status=" + status)));
        } catch (MolecularBackendException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new MolecularBackendException("OCL minimization failed", exception);
        }
    }

    @Override
    public SubstructureMatcher.Result match(String query, MolecularGraph graph)
            throws MolecularBackendException {
        var mapping = mapper.toOcl(graph);
        try {
            var parser = new SmilesParser(SmilesParser.SMARTS_MODE_IS_SMARTS);
            var fragment = parser.parseMolecule(query);
            var searcher = new SSSearcher(); searcher.setMol(fragment, mapping.molecule());
            searcher.findFragmentInMolecule(SSSearcher.cCountModeUnique, SSSearcher.cDefaultMatchMode);
            var results = new ArrayList<Map<String, String>>();
            for (int[] match : searcher.getMatchList()) {
                var result = new LinkedHashMap<String, String>();
                for (int queryIndex = 0; queryIndex < match.length; queryIndex++) {
                    int targetMap = mapping.molecule().getAtomMapNo(match[queryIndex]);
                    result.put("query:" + queryIndex, mapping.idByMapNumber().get(targetMap));
                }
                results.add(result);
            }
            return new SubstructureMatcher.Result(results,
                    evidence("substructure-match", mapping, List.of(), List.of("query=" + query)));
        } catch (Exception exception) {
            throw new MolecularBackendException("OCL SMARTS/substructure match failed", exception);
        }
    }

    private static BackendEvidence evidence(String operation, OclGraphMapper.Mapping mapping,
                                            List<BackendEvidence.GraphChange> changes, List<String> messages) {
        var lineage = new LinkedHashMap<String, String>();
        mapping.source().atoms().forEach(atom -> lineage.put(atom.id(), atom.id()));
        return new BackendEvidence(BACKEND, VERSION, operation, lineage, changes, messages);
    }

    private static List<BackendEvidence.GraphChange> meaningfulChanges(MolecularGraph before, MolecularGraph after) {
        var changes = new ArrayList<BackendEvidence.GraphChange>();
        compare(changes, "atomCount", before.atoms().size(), after.atoms().size());
        compare(changes, "bondCount", before.bonds().size(), after.bonds().size());
        for (var atom : before.atoms()) after.atom(atom.id()).ifPresentOrElse(other -> {
            compare(changes, "element:" + atom.id(), atom.element(), other.element());
            compare(changes, "formalCharge:" + atom.id(), atom.formalCharge(), other.formalCharge());
            compare(changes, "isotope:" + atom.id(), atom.isotope(), other.isotope());
            if (!java.util.Objects.equals(atom.stereochemistry(), other.stereochemistry())) {
                var disposition = atom.stereochemistry().equals("UNSPECIFIED")
                        && other.stereochemistry().equals("UNKNOWN")
                        ? BackendEvidence.Disposition.BENIGN_NORMALIZATION
                        : BackendEvidence.Disposition.UNAUTHORIZED_MEANINGFUL_CHANGE;
                changes.add(new BackendEvidence.GraphChange("stereo:" + atom.id(), atom.stereochemistry(),
                        other.stereochemistry(), disposition));
            }
        }, () -> changes.add(change("atom:" + atom.id(), "present", "missing")));
        for (var bond : before.bonds()) after.bond(bond.id()).ifPresentOrElse(other -> {
                if (!java.util.Objects.equals(bond.order(), other.order())) {
                    boolean aromaticNormalization = other.order() == MolecularGraph.BondOrder.AROMATIC
                            && (bond.order() == MolecularGraph.BondOrder.SINGLE
                            || bond.order() == MolecularGraph.BondOrder.DOUBLE)
                            && before.atom(bond.firstAtomId()).map(MolecularGraph.Atom::aromatic).orElse(false)
                            && before.atom(bond.secondAtomId()).map(MolecularGraph.Atom::aromatic).orElse(false);
                    changes.add(new BackendEvidence.GraphChange("bondOrder:" + bond.id(),
                            bond.order().toString(), other.order().toString(), aromaticNormalization
                            ? BackendEvidence.Disposition.BENIGN_NORMALIZATION
                            : BackendEvidence.Disposition.UNAUTHORIZED_MEANINGFUL_CHANGE));
                }
            },
                () -> changes.add(change("bond:" + bond.id(), "present", "missing")));
        return changes;
    }
    private static String normalizationId(BackendEvidence.GraphChange change) {
        if (change.dimension().startsWith("bondOrder:") && change.after().equals("AROMATIC")) {
            return "KEKULE_TO_AROMATIC";
        }
        if (change.dimension().startsWith("stereo:") && change.before().equals("UNSPECIFIED")
                && change.after().equals("UNKNOWN")) return "UNSPECIFIED_TO_UNKNOWN_STEREO";
        return change.dimension();
    }
    private static void compare(List<BackendEvidence.GraphChange> changes, String dimension, Object before, Object after) {
        if (!java.util.Objects.equals(before, after)) changes.add(change(dimension, String.valueOf(before), String.valueOf(after)));
    }
    private static BackendEvidence.GraphChange change(String dimension, String before, String after) {
        return new BackendEvidence.GraphChange(dimension, before, after,
                BackendEvidence.Disposition.UNAUTHORIZED_MEANINGFUL_CHANGE);
    }
}
