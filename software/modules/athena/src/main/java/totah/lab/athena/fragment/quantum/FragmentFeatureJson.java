package totah.lab.athena.fragment.quantum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Explicit JSON boundary: unavailable values are null; no Optional or backend matrix serialization. */
public final class FragmentFeatureJson {
    private static final ObjectMapper JSON=new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private FragmentFeatureJson() {}
    public static String feature(FragmentPhysicalFeatures row) throws IOException {
        var data=new TreeMap<String,Object>();data.put("calculation_id",row.plan().id());data.put("channel",row.plan().channel().name());
        data.put("ATHENA_INTERACTION_CLASS",row.interactionClass().name());data.put("AETHER_VALIDITY_CLASS",row.validity().name());
        data.put("RHF_CP_INTERACTION_ENERGY",number(row.rhfCp()));data.put("PBE_CP_INTERACTION_ENERGY",number(row.pbeCp()));
        data.put("D3_DELTA",number(row.d3Delta()));data.put("PBE_D3_CP_INTERACTION_ENERGY",number(row.pbeD3Cp()));
        data.put("RHF_AVAILABLE",row.rhfCp().isPresent());data.put("PBE_AVAILABLE",row.pbeCp().isPresent());
        data.put("D3_AVAILABLE",row.d3Delta().isPresent());data.put("PBE_D3_AVAILABLE",row.pbeD3Cp().isPresent());
        var statuses=new TreeMap<String,String>();var provenance=new TreeMap<String,Object>();
        provenance.put("feature_receipt",row.receiptHash());provenance.put("plan_receipt",row.plan().receiptHash());
        provenance.put("selection_receipt",row.plan().selectionHash());provenance.put("units","hartree");
        provenance.put("aether_protocol",totah.lab.aether.matrix.FragmentInteractionCalculator.PROTOCOL);
        provenance.put("D3_protocol",totah.lab.aether.matrix.D3Dispersion.PROTOCOL);
        provenance.put("constituent_classes",row.plan().constituentClasses());
        provenance.put("left",fragment(row.plan().left()));provenance.put("right",fragment(row.plan().right()));
        var components=new ArrayList<Object>();
        if(row.calculation().isPresent()) {
            var result=row.calculation().orElseThrow();provenance.put("aether_receipt",result.receiptHash());provenance.put("D3_physical_atom_receipts",result.dispersionReceipts());
            for(var component:result.components()) {
                statuses.put(component.method()+"/"+component.role(),component.scfStatus());
                var values=new TreeMap<String,Object>();values.put("method",component.method());values.put("role",component.role());
                values.put("status",component.scfStatus());values.put("energy_hartree",number(component.energyHartree()));
                values.put("receipt_hash",component.receiptHash());values.put("system_hash",component.systemHash());
                values.put("cache_identity",component.requestedCacheIdentity());values.put("canonical_cache_identity",component.canonicalCacheIdentity());
                values.put("permutation_reuse",component.permutationReuse());values.put("cache_hit",component.cacheHit());
                values.put("permutation_provenance",component.permutationProvenance());values.put("failure",component.failure());
                // Each iteration remains the original frozen typed receipt representation.
                values.put("iterations",component.iterations().stream().map(Object::toString).toList());components.add(values);
            }
        } else statuses.put("ALL","UNAVAILABLE_INPUT_OR_EXECUTION");
        provenance.put("components",components);data.put("provenance",provenance);data.put("SCF_STATUS",statuses);
        data.put("unavailable_reasons",row.unavailableReasons());data.put("status","SCREENING_ONLY");
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data)+"\n";
    }
    public static void write(Path path,String content)throws IOException {
        if(path.getParent()!=null)Files.createDirectories(path.getParent());Files.writeString(path,content);
    }
    public static String fragmentProvenance(PreparedQuantumFragment fragment)throws IOException {
        var values=new TreeMap<>(fragment(fragment));values.put("status","SCREENING_ONLY");
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(values)+"\n";
    }
    public static String selection(FragmentSelection selection)throws IOException {
        var values=new TreeMap<String,Object>();values.put("DIRECT_CONTACT_RESIDUES",selection.directContactResidues());
        values.put("SECOND_SHELL_RESIDUES",selection.secondShellResidues());values.put("WATER_INCLUDED",!selection.waterIncluded().isEmpty());
        values.put("COFACTOR_INCLUDED",!selection.cofactorIncluded().isEmpty()||selection.standaloneCofactorIncluded());
        values.put("water_residues",selection.waterIncluded());values.put("cofactor_residues",selection.cofactorIncluded());
        values.put("standalone_cofactor_included",selection.standaloneCofactorIncluded());values.put("environment_hash",selection.environmentHash());
        values.put("selection_receipt",selection.receiptHash());values.put("protocol",FragmentSelection.PROTOCOL);values.put("status","SCREENING_ONLY");
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(values)+"\n";
    }
    public static String aggregates(List<FragmentFeatureService.Aggregate> input)throws IOException {
        var rows=new ArrayList<Object>();
        for(var row:input) {
            var values=new TreeMap<String,Object>();values.put("channel",row.channel());values.put("interaction_class",row.interactionClass());
            values.put("component_ids",row.componentIds());values.put("component_receipts",row.componentReceipts());
            values.put("contact_count",row.contactCount());values.put("unavailable_contact_count",row.unavailableContactCount());
            values.put("sum_RHF_CP_available_contacts",number(row.sumRhfCp()));values.put("sum_PBE_CP_available_contacts",number(row.sumPbeCp()));
            values.put("sum_D3_available_contacts",number(row.sumD3()));values.put("sum_PBE_D3_CP_available_contacts",number(row.sumPbeD3Cp()));
            values.put("minimum_PBE_D3_contact",number(row.minimumPbeD3Cp()));values.put("RHF_available_count",row.rhfAvailableCount());
            values.put("PBE_available_count",row.pbeAvailableCount());values.put("D3_available_count",row.d3AvailableCount());values.put("PBE_D3_available_count",row.pbeD3AvailableCount());
            values.put("interpretation","FRAGMENT_DESCRIPTORS_NOT_BINDING_ENERGY");values.put("status","SCREENING_ONLY");rows.add(values);
        }
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(rows)+"\n";
    }
    private static Double number(OptionalDouble value){return value.isPresent()?value.getAsDouble():null;}
    private static Map<String,Object> fragment(PreparedQuantumFragment f) {
        var result=new TreeMap<String,Object>();result.put("id",f.id());result.put("source_component",f.sourceComponent());result.put("environment_hash",f.environmentHash());
        result.put("source_state_id",f.sourceStateId());result.put("source_structure_hash",f.sourceStructureHash());result.put("source_provenance",f.sourceProvenance());
        result.put("retained_atoms",f.retained().stream().map(FragmentFeatureJson::atom).toList());
        result.put("deleted_atoms",f.deleted().stream().map(FragmentFeatureJson::atom).toList());result.put("cap_atoms",f.caps());
        result.put("CHARGE_STATUS",f.chargeStatus().name());result.put("formal_charge",f.formalCharge().isPresent()?f.formalCharge().getAsInt():null);
        result.put("multiplicity",f.multiplicity());result.put("protonation_assignment",f.protonationAssignment());result.put("charge_source",f.chargeSource());
        result.put("receipt_hash",f.receiptHash());result.put("unavailable_reasons",f.unavailableReasons());
        result.put("aether_input",f.quantum().isPresent()?Map.of("fragment_id",f.quantum().orElseThrow().id(),"units","bohr",
                "nuclei",f.quantum().orElseThrow().system().nuclei(),"charge",f.quantum().orElseThrow().system().molecularCharge(),"multiplicity",f.multiplicity()):null);
        return result;
    }
    private static Map<String,Object> atom(PreparedQuantumFragment.OriginalAtom atom) {
        var result=new TreeMap<String,Object>();result.put("original_atom",atom.reference());result.put("original_residue_name",atom.residueName());
        result.put("source_atom_order",atom.sourceOrder());result.put("atomic_number",atom.atomicNumber());result.put("original_coordinates_angstrom",atom.originalAngstrom());
        result.put("source_atom_serial",atom.sourceSerial());
        result.put("formal_charge",atom.formalCharge().isPresent()?atom.formalCharge().getAsInt():null);return result;
    }
}
