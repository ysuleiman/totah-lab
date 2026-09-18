package totah.lab.athena.fragment.quantum;

import java.util.*;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.athena.energy.MolecularState;
import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.perception.FormalChargeAssignments;
import totah.lab.gaia.structure.*;

/** Immutable structural state plus explicit chemistry evidence; partial charges are not formal charges. */
public record QuantumEnvironment(MolecularState state, List<Interaction> annotations,
                                 List<ClassAnnotation> classAnnotations,
                                 Map<String,ChemistryEvidence> chemistry,
                                 Set<ResidueId> waterResidues, Set<ResidueId> cofactorResidues,
                                 boolean includeWater, boolean includeCofactor) {
    public enum ChargeStatus { VERIFIED, ASSIGNED_BY_FROZEN_RULE, AMBIGUOUS }
    public enum InteractionClass { hydrogen_bond, dispersion, pi_pi, cation_pi, ionic, sulfur_polar, sulfur_aromatic, halogen, mixed, unclassified }
    public record ClassAnnotation(ResidueId residue,InteractionClass interactionClass,String structuralEvidence) {
        public ClassAnnotation {Objects.requireNonNull(residue);Objects.requireNonNull(interactionClass);text(structuralEvidence);}
    }
    public record ChemistryEvidence(ChargeStatus status, String structureHash, String source,
                                    String protonationAssignment, FormalChargeAssignments formalCharges,
                                    boolean completeTopology, boolean completeExplicitHydrogens,
                                    int multiplicity) {
        public ChemistryEvidence {
            Objects.requireNonNull(status);Objects.requireNonNull(formalCharges);
            if(status==ChargeStatus.ASSIGNED_BY_FROZEN_RULE)throw new IllegalArgumentException("Assigned-by-rule is reserved for the validated cap construction output");
            text(structureHash);text(source);text(protonationAssignment);
            if(multiplicity<1)throw new IllegalArgumentException("Positive multiplicity required");
        }
    }
    public QuantumEnvironment {
        Objects.requireNonNull(state);annotations=List.copyOf(annotations);classAnnotations=List.copyOf(classAnnotations);chemistry=Map.copyOf(chemistry);
        waterResidues=Set.copyOf(waterResidues);cofactorResidues=Set.copyOf(cofactorResidues);
        if(waterResidues.stream().anyMatch(cofactorResidues::contains))throw new IllegalArgumentException("Water/cofactor classifications overlap");
        for(var set:List.of(waterResidues,cofactorResidues))for(var residue:set)
            if(state.receptor().findResidue(residue.chainId(),residue.residueNumber(),residue.insertionCode()).isEmpty())throw new IllegalArgumentException("Classification names a missing receptor residue");
        for(var entry:chemistry.entrySet()) {
            Structure structure=switch(entry.getKey()) {
                case "receptor"->state.receptor();case "ligand"->state.ligand();
                case "cofactor"->state.cofactor().orElseThrow();
                default->throw new IllegalArgumentException("Unknown structural component");
            };
            if(!structureHash(structure).equals(entry.getValue().structureHash()))throw new IllegalArgumentException("Chemical evidence does not bind this exact structure");
        }
        var receptorAtoms=Collections.newSetFromMap(new IdentityHashMap<Atom,Boolean>());
        receptorAtoms.addAll(atoms(state.receptor()).values());
        var ligandAtoms=Collections.newSetFromMap(new IdentityHashMap<Atom,Boolean>());
        ligandAtoms.addAll(atoms(state.ligand()).values());
        for(var annotation:annotations) {
            if(!receptorAtoms.containsAll(annotation.proteinAtoms())||!ligandAtoms.containsAll(annotation.ligandAtoms()))
                throw new IllegalArgumentException("Interaction annotation belongs to another structural state");
        }
        for(var annotation:classAnnotations)if(state.receptor().findResidue(annotation.residue().chainId(),annotation.residue().residueNumber(),annotation.residue().insertionCode()).isEmpty())
            throw new IllegalArgumentException("Class annotation names a missing receptor residue");
    }
    static String text(String value) {
        if(value==null||value.isBlank()||value.indexOf('\n')>=0||value.indexOf('\r')>=0)
            throw new IllegalArgumentException("Nonblank single-line provenance required");
        return value;
    }
    static LinkedHashMap<AtomReference,Atom> atoms(Structure structure) {
        var atoms=new LinkedHashMap<AtomReference,Atom>();
        for(var chain:structure.getChains())for(var residue:chain.residues())for(var atom:residue.getAtoms()) {
            var reference=new AtomReference(chain.id(),residue.getNumber(),residue.getInsertionCode()==null?' ':residue.getInsertionCode(),atom.getName());
            var p=atom.getPosition();
            if(atom.getElement()==null||atom.getElement().getAtomicNumber()==0||!Double.isFinite(p.x())||!Double.isFinite(p.y())||!Double.isFinite(p.z()))
                throw new IllegalArgumentException("Unknown element or nonfinite source coordinates");
            if(atoms.put(reference,atom)!=null)throw new IllegalArgumentException("Duplicate atom identity");
        }
        return atoms;
    }
    public static String structureHash(Structure structure) {
        var hash=ContentHash.accumulator().line("athena-quantum-structure-18-1;angstrom;source-order");
        for(var chain:structure.getChains())for(var residue:chain.residues()) {
            hash.line(ContentHash.sha256(chain.id())).line(Integer.toString(residue.getNumber()))
                    .line(String.valueOf(residue.getInsertionCode())).line(ContentHash.sha256(residue.getName()));
        }
        for(var entry:atoms(structure).entrySet()) {
            var a=entry.getValue();var p=a.getPosition();
            hash.line(ContentHash.sha256(entry.getKey().toString())).line(Integer.toString(a.getPdbSerial()))
                .line(a.getElement().name()).line(Double.toHexString(p.x())).line(Double.toHexString(p.y())).line(Double.toHexString(p.z()))
                .line(Double.toHexString(a.getCharge()));
        }
        structure.getBonds().stream().sorted(Comparator.comparing(Bond::toString)).forEach(b->hash.line(ContentHash.sha256(b.toString())));
        hash.line(structure.getConnectivityMetadata().toString());return hash.finish();
    }
    public String identity() {
        var hash=ContentHash.accumulator().line("athena-quantum-environment-18-1").line(ContentHash.sha256(state.stateId()))
                .line(structureHash(state.receptor())).line(structureHash(state.ligand()))
                .line(state.cofactor().map(QuantumEnvironment::structureHash).orElse("NO_COFACTOR"))
                .line(ContentHash.sha256(state.protonationProvenance()));
        for(String value:List.of(state.receptorId(),state.ligandId(),state.forceFieldIdentity(),state.parameterProvenance(),state.environmentModel(),state.restraintDefinition()))
            hash.line(ContentHash.sha256(value));
        hash.line(Boolean.toString(includeWater)).line(Boolean.toString(includeCofactor));
        waterResidues.stream().map(Object::toString).sorted().forEach(v->hash.line("WATER:"+v));
        cofactorResidues.stream().map(Object::toString).sorted().forEach(v->hash.line("COFACTOR:"+v));
        var receptorIndex=atoms(state.receptor());var ligandIndex=atoms(state.ligand());
        var receptorReferences=new IdentityHashMap<Atom,AtomReference>();receptorIndex.forEach((ref,atom)->receptorReferences.put(atom,ref));
        var ligandReferences=new IdentityHashMap<Atom,AtomReference>();ligandIndex.forEach((ref,atom)->ligandReferences.put(atom,ref));
        for(var annotation:annotations) {
            hash.line(annotation.type().name()).line(annotation.residue().toString()).line(annotation.thresholdsProvenance())
                .line(Double.toHexString(annotation.distanceAngstroms())).line(String.valueOf(annotation.primaryAngleDegrees()))
                .line(String.valueOf(annotation.secondaryAngleDegrees())).line(String.valueOf(annotation.proteinGroupId())).line(String.valueOf(annotation.ligandGroupId()));
            for(var atom:annotation.proteinAtoms())hash.line(receptorReferences.get(atom).toString());
            for(var atom:annotation.ligandAtoms())hash.line(ligandReferences.get(atom).toString());
        }
        for(var annotation:classAnnotations)hash.line(annotation.residue().toString()).line(annotation.interactionClass().name()).line(ContentHash.sha256(annotation.structuralEvidence()));
        new TreeMap<>(state.provenance()).forEach((k,v)->hash.line(ContentHash.sha256(k)).line(ContentHash.sha256(v)));
        new TreeMap<>(chemistry).forEach((key,value)->{
            hash.line(key).line(value.status().name()).line(value.structureHash()).line(ContentHash.sha256(value.source()))
                .line(ContentHash.sha256(value.protonationAssignment())).line(Boolean.toString(value.completeTopology()))
                .line(Boolean.toString(value.completeExplicitHydrogens())).line(Integer.toString(value.multiplicity()));
            new TreeMap<>(value.formalCharges().charges()).forEach((atom,charge)->hash.line(ContentHash.sha256(atom.toString())).line(charge.toString()));
        });
        return hash.finish();
    }
}
