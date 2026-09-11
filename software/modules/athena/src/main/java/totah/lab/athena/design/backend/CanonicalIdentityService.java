package totah.lab.athena.design.backend;

public interface CanonicalIdentityService {
    Result identify(MolecularGraph graph) throws MolecularBackendException;
    record Result(String canonicalKey, BackendEvidence evidence) { }
}
