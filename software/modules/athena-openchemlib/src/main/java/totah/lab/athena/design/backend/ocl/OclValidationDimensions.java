package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.Molecule;
import totah.lab.athena.design.backend.*;
import java.util.*;
import static totah.lab.athena.design.backend.MolecularValidationService.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Adapter over pinned OCL 2026.7.2 predicates; never edits the supplied chemical state. */
final class OclValidationDimensions {
    static final String VERSION="2026.7.2/athena-validation-dimensions/1";
    private OclValidationDimensions() { }
    static Assessment assessment(totah.lab.mnemosyne.EvidenceInterpretation.Status s,String reason) {return new Assessment(s,List.of(reason));}
    static Result assess(MolecularGraph graph,NeutralityPolicy policy)throws MolecularBackendException {
        Objects.requireNonNull(graph);Objects.requireNonNull(policy);
        var values=new EnumMap<Dimension,Assessment>(Dimension.class);
        for(var d:Dimension.values())values.put(d,assessment(UNKNOWN_INCONCLUSIVE,"not established independently"));
        int charge=0;
        try {for(var atom:graph.atoms())charge=Math.addExact(charge,atom.formalCharge());}
        catch(ArithmeticException e){throw new MolecularBackendException("net formal charge overflow",e);}
        values.put(Dimension.NET_NEUTRALITY,assessment(policy==NeutralityPolicy.OBSERVE_ONLY?NOT_EVALUATED:charge==0?SUPPORTED_PRESENT:ABSENT_FALSE,
                "COMPONENT_NET_NEUTRAL; policy="+policy+"; netFormalCharge="+charge));
        var diagnostics=new ArrayList<String>();var lineage=new TreeMap<String,String>();graph.atoms().forEach(a->lineage.put(a.id(),a.id()));
        OclGraphMapper.Mapping mapping=null;
        boolean topology=true;
        try {graph.validateTopology(true);}
        catch(Exception e){topology=false;diagnostics.add("topology: "+e);values.put(Dimension.TOPOLOGY_VALENCE,assessment(FAILED,e.toString()));}
        if(topology)try {mapping=new OclGraphMapper().toOcl(graph);}
        catch(Exception e){
            diagnostics.add("mapping: "+e);
            values.put(Dimension.TOPOLOGY_VALENCE,assessment(UNKNOWN_INCONCLUSIVE,"mapping unavailable; valence not established: "+e));
            values.put(Dimension.STEREOCHEMISTRY,assessment(UNSUPPORTED,"source representation not supported by mapper: "+e));
        }
        if(mapping!=null) {
            var m=mapping.molecule();
            try {m.validate();diagnostics.add("legacyCombined=PASS");}
            catch(Exception e){for(Throwable cause=e;cause!=null;cause=cause.getCause())diagnostics.add("legacyCombined: "+cause);}
            try {
                m.ensureHelperArrays(Molecule.cHelperNeighbours);
                boolean valid=true;for(int i=0;i<m.getAtoms();i++)if(m.getOccupiedValence(i)>m.getMaxValence(i))valid=false;
                values.put(Dimension.TOPOLOGY_VALENCE,assessment(valid?SUPPORTED_PRESENT:FAILED,"OCL occupied valence <= max valence; nonempty supplied topology; no neutrality requirement"));
            }catch(Exception e){values.put(Dimension.TOPOLOGY_VALENCE,assessment(UNKNOWN_INCONCLUSIVE,e.toString()));}
            try {new OclGraphMapper().validateHydrogenCounts(mapping);values.put(Dimension.SUPPLIED_H_STATE,assessment(SUPPORTED_PRESENT,"supplied positive H constraints consistent; zero annotation unspecified, not a complete protonation certificate"));}
            catch(Exception e){values.put(Dimension.SUPPLIED_H_STATE,assessment(FAILED,e.toString()));}
            boolean finite=graph.atoms().stream().allMatch(a->a.coordinates()!=null&&Double.isFinite(a.coordinates().x())&&Double.isFinite(a.coordinates().y())&&Double.isFinite(a.coordinates().z()));
            if(finite) {
                double limit=m.getAverageBondLength()*m.getAverageBondLength()/16.0;boolean close=false;
                for(int i=1;i<m.getAllAtoms();i++)for(int j=0;j<i;j++) {
                    double x=m.getAtomX(i)-m.getAtomX(j),y=m.getAtomY(i)-m.getAtomY(j),z=m.getAtomZ(i)-m.getAtomZ(j);
                    if(x*x+y*y+z*z<limit)close=true;
                }
                values.put(Dimension.OCL_COORDINATE_COMPATIBILITY,assessment(close?FAILED:SUPPORTED_PRESENT,"OCL combined-validator distance guard (average bond length squared /16); not a scientific clash threshold"));
            }else values.put(Dimension.OCL_COORDINATE_COMPATIBILITY,assessment(UNKNOWN_INCONCLUSIVE,"missing or nonfinite supplied coordinates"));
            try {
                m.ensureHelperArrays(Molecule.cHelperCIP);boolean invalid=false,unresolved=false;
                for(int i=0;i<m.getAtoms();i++) {
                    int esr=m.getAtomESRType(i),parity=m.getAtomParity(i);
                    if((esr==Molecule.cESRTypeAnd||esr==Molecule.cESRTypeOr)&&(!m.isAtomStereoCenter(i)||parity==Molecule.cAtomParityUnknown))invalid=true;
                    if(m.getStereoProblem(i))invalid=true;
                    // Remaining OCL stereo branch calls protected bondsAreParallel. Do not approximate it.
                    if((parity==Molecule.cAtomParity1||parity==Molecule.cAtomParity2)&&m.getAtomPi(i)==0)unresolved=true;
                }
                if(!invalid&&unresolved) {
                    // A successful original validator actually executes its stereo tail; charge failure does not.
                    try {m.validate();unresolved=false;}catch(Exception e){diagnostics.add("stereoTailNotEstablished: "+e);}
                }
                values.put(Dimension.STEREOCHEMISTRY,assessment(invalid?FAILED:unresolved?UNKNOWN_INCONCLUSIVE:SUPPORTED_PRESENT,
                    invalid?"OCL ESR/parity/stereo-problem failure":unresolved?"OCL tetrahedral drawing check not independently established; combined failure is not stereo success":"OCL ESR/parity/stereo-problem predicates; no unchecked tetrahedral drawing branch"));
            }catch(Exception e){values.put(Dimension.STEREOCHEMISTRY,assessment(UNKNOWN_INCONCLUSIVE,e.toString()));}
        }
        return new Result(values,charge,new BackendEvidence("OPEN_CHEM_LIB",VERSION,"validation-dimensions",lineage,List.of(),diagnostics));
    }
}
