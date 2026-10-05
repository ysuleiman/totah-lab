# Direct chemical perception: Mobley et al. (2018)

Foundational architectural support, not an Athena scientific qualification certificate.

Mobley DL, Bannan CC, Rizzi A, Bayly CI, Chodera JD, Lim VT, Lim NM,
Beauchamp KA, Slochower DR, Shirts MR, Gilson MK, Eastman PK.
*Escaping Atom Types in Force Fields Using Direct Chemical Perception.*
J Chem Theory Comput 14(11), 6076–6092 (2018).
DOI: [10.1021/acs.jctc.8b00640](https://doi.org/10.1021/acs.jctc.8b00640).
PMID 30351006; PMCID PMC6245550. Published online October 30, 2018.

## Verified source and technical analysis

[PubMed abstract and Figures 1–2](https://pubmed.ncbi.nlm.nih.gov/30351006/)
were inspected on 2026-10-05. The PMC full-text endpoint returned a browser
challenge. No publication bytes were copied; no full-text digest is claimed.
Exact full-text sections supporting hierarchy/versioning remain unverified here.

The abstract and Figure 1 explain why an intermediate atom-type label must encode
many unrelated parameter questions. Direct perception instead queries the chemical
graph, retaining bond order and chemical environment. SMARTS with SMIRKS atom labels
addresses each force-field term separately. Figure 2 illustrates type proliferation
needed to distinguish bonding environments. This supports decoupling chemical
questions, not treating a single type as the complete chemistry of an atom.

## Athena decisions (user-approved architectural interpretation)

- **ADOPT:** direct graph perception; declarative environment patterns; separate
  questions; inspectable, versioned definitions. Generic-to-specific definitions
  are reusable constraints, not evidence priority. Detailed paper hierarchy and
  serialization claims require full-text verification; Athena's versioning is its
  own explicit provenance requirement.
- **MODIFY:** retain general, specialized and overlapping identities together;
  retain every applicable correspondence. Specificity does not erase evidence.
- **REJECT / NOT APPLICABLE:** SMIRNOFF parameter assignment and last-match-wins
  as scientific identity semantics; force constants, energies, charge generation
  or other force-field functionality imported merely because SMIRNOFF has them.

## Current implementation comparison

Repository paths below are relative to its root; the generated reference pins bytes.

| Layer | Comparison and boundary |
|---|---|
| B00 | `athena-openchemlib/.../OclOccurrenceMatcher.java` executes OCL queries with distinct occurrences and mapping provenance. This is bounded OCL semantics, not SMIRKS/RDKit parity. |
| B01 /1 and /2 | `athena/.../system/rules/FunctionalGroupRules.java` validates declarative definitions and required source state; sorted occurrence, role-alternative and context collections preserve mappings. /2 adds local occurrence exclusions. |
| 44 groups | `groups-b01`, `groups-foundation-v1`, `groups-foundation-v2` resources supply independent definitions, not a universal atom label. |
| 17 roles | `perception-foundation-v1` uses the same interpreter with role-specific H/charge/domain requirements. Identity does not establish an interaction. |
| 8 features | `perception-state-v1` keeps explicit charge/nonpolar questions separate; inferred protonation is not source truth. |
| Geometry | `ContinuousGeometryRules` preserves attributed measurements independently of classification; no type replaces coordinates or surface gaps. |

No universal-type replacement or destructive precedence was found in these inspected
foundation paths. This is a bounded code comparison, not certification of every legacy
consumer. Legacy detector chemistry stays separately versioned. The new role coverage
witnesses exercise coexistence and incomplete-state behavior; they do not qualify
force-field behavior or general donor/acceptor chemistry.

No production code change is justified by this comparison. No empirical dataset or
calibration was analyzed for this architectural note. The paper's force-field validation
is not evidence for Athena interaction thresholds, biological favorability or potency.
