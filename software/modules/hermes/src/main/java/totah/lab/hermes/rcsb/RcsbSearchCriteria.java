package totah.lab.hermes.rcsb;

/** Criteria supported by the RCSB Search API integration. */
public sealed interface RcsbSearchCriteria
        permits RcsbAttributeSearch, RcsbSequenceSearch, RcsbStructureMotifSearch {
}
