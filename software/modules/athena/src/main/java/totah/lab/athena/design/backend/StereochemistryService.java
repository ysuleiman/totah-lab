package totah.lab.athena.design.backend;

public interface StereochemistryService {
    Result validate(MolecularGraph graph) throws MolecularBackendException;
    record Result(boolean valid, int definedStereoElements, BackendEvidence evidence) { }
}
