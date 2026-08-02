#!/usr/bin/env python3
"""Create the publication-oriented Markdown report from generated artifacts."""

from __future__ import annotations

import argparse
import csv
from collections import Counter
from pathlib import Path


def rows(path: Path):
    return list(csv.DictReader(path.open()))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("analysis_dir", type=Path)
    args = parser.parse_args()
    root = args.analysis_dir
    classifications = rows(root / "representative_classification.csv")
    metadata = {r["sequence_id"]: r for r in rows(root / "cluster_representatives_metadata.csv")}
    pocket = rows(root / "pocket_residue_conservation.csv")
    motifs = rows(root / "cysteine_motif_summary.csv")
    structures = {r["sequence_id"]: r for r in rows(root / "structural_availability.csv")}
    identity = {r["sequence_id"]: r for r in rows(root / "pairwise_identity.csv")}
    counts = Counter(r["classification"] for r in classifications)

    class_table = []
    for r in classifications:
        m = metadata[r["sequence_id"]]
        class_table.append(
            f"| {r['sequence_id']} | {m['organism'] or 'not resolved'} | {r['classification']} | {r['confidence']} | "
            f"{r['top_reviewed_uniprot_hit']} ({r['top_hit_percent_identity']}%, {r['top_hit_query_coverage_percent']}% coverage) | "
            f"{r['mettl7b_pocket_identical_positions_of_21']}/21 | {r['aligned_vicinal_cc']} |"
        )

    structure_table = []
    for sid, r in structures.items():
        structure_table.append(
            f"| {sid} | {r['pdb_identifiers'] or 'none in UniProt'} | "
            f"{r['alphafold_identifiers'] or 'none returned'} | {r['esmfold_model_available']} |"
        )

    report = f"""# BioHub representative analysis: relationships to human METTL7A and METTL7B

**Analysis date:** 2026-08-01  
**Scope:** human METTL7B (UniProt Q6UX53), human METTL7A (UniProt Q9H8H3), and exactly one representative from each of the ten supplied BioHub clusters. The remaining 809 cluster-member records were not aligned, searched, or classified.

## Executive summary

The representative panel resolves into **{counts['METTL7B-like']} provisional METTL7B-like representatives** (clusters 3, 4, 5, and 10), **{counts['METTL7A-like']} METTL7A-like representatives** (clusters 1, 2, 6, and 7), and **{counts['Other SAM-dependent methyltransferase']} other SAM-dependent methyltransferases** (clusters 8 and 9). No representative is classified as an unrelated protein. Cluster 4 is a near-identical primate TMT1B and is the only unambiguous METTL7B-like representative at very high confidence. Clusters 1 and 2 are the strongest METTL7A-like representatives. Cluster 9 is a distant ubiE/COQ5-family methyltransferase lineage and serves as a functional outgroup, although the maximum-likelihood tree itself is unrooted.

The full METTL7B pocket signature is retained only by cluster 4 (21/21 supplied positions). METTL7A and clusters 1–2 retain 16–17/21 positions, illustrating that most of this pocket is family-conserved rather than METTL7B-specific. The Q6UX53 vicinal **Cys202-Cys203** motif is present at the aligned site in cluster 4 and, importantly, in METTL7A-like cluster 6. Several annotated METTL7B-like representatives lack an aligned CC. Therefore the sequence panel does **not** support aligned CC as either a necessary METTL7B marker or a sufficient METTL7B-selectivity determinant.

## Data provenance and metadata

The BioHub export contains 809 complete cluster-member sequences, but the present analysis extracts only the ten representative hashes recorded in `manifest.json`. MD5 identity and declared sequence length are checked during extraction. Human reference sequences and annotations were retrieved from [UniProt Q6UX53](https://www.uniprot.org/uniprotkb/Q6UX53/entry) and [UniProt Q9H8H3](https://www.uniprot.org/uniprotkb/Q9H8H3/entry). Representative UPI identifiers were resolved through [UniParc](https://www.uniprot.org/uniparc/), and active UniProtKB records were queried where available. Domain annotations use UniProt cross-references to Pfam, InterPro, and CDD; cluster-level domain counts are retained only when no representative-level UniProt record was available.

Eight representatives have UniParc identifiers; five currently resolve to active UniProtKB records. The two non-UniParc representatives retain their supplied IMG/M or MGnify identifiers. Empty organism or taxonomy fields mean the supplied identifier could not be tied to an authoritative per-protein record; cluster-level aggregate taxonomy was not substituted.

## Alignment and conservation

MAFFT L-INS-i produced a 342-column alignment of the 12 sequences. There are 15 fully conserved, gap-free columns. Using occupancy >=0.75 and Shannon entropy >=2.0 bits, 99 columns are highly variable. No Q6UX53 residue occurs in a METTL7B-only insertion supported by gaps in at least 75% of the other sequences; accordingly, the panel offers no strong METTL7B-specific insertion segment. The most conspicuous indel and variability burden lies in N-terminal targeting/membrane regions and in divergent loops, whereas the central methyltransferase core aligns across the panel.

Pairwise gap-excluded identities to METTL7A/METTL7B range from 25.00/26.89% (cluster 9) to 57.38/97.13% (cluster 4). Clusters 1 and 2 are more identical to METTL7A (54.20% and 56.20%) than to METTL7B (50.00% and 47.52%). The exact alignment, per-column entropy table, and identity matrix are supplied separately.

## Maximum-likelihood phylogeny

IQ-TREE selected LG+G4 by BIC and estimated a maximum-likelihood tree with 1,000 ultrafast bootstrap and 1,000 SH-aLRT replicates (seed 20260801). Cluster 4 is essentially coincident with human METTL7B. Clusters 1 and 2 form a supported pair (87.1/87) adjacent to the METTL7A side of the family. Clusters 3 and 10 form a supported pair (93.6/95), and clusters 7 and 8 form a strongly supported pair (100/100). Deeper relationships among these divergent branches have weak support (for example 24.9/46 and 35.9/51) and should not be used for confident subtype assignment.

Because no external homolog was specified to root the tree, `phylogenetic_tree.nwk` is interpreted as unrooted. Cluster 9 is called a functional/distant comparison, not a proven ancestral outgroup.

## Pocket-residue conservation

All supplied residue labels were audited against Q6UX53 and match the authoritative sequence exactly: Lys33, Phe36, Thr44, Lys55, Leu77, Gly78, Cys79, Gly127, Thr144, Leu145, Cys148, Ser149, Gln151, His175, Trp195, Lys196, Gly199, Asp200, Gly201, Cys202, and Cys203. Mapping therefore uses one-based Q6UX53 numbering without an offset.

Substitutions are called conservative only within these deliberately coarse groups: {{A,V,L,I,M}}, {{F,W,Y}}, {{S,T,N,Q}}, {{K,R,H}}, and {{D,E}}; Cys, Gly, and Pro substitutions are not automatically treated as conservative. `pocket_residue_conservation.csv` gives every residue call for every sequence. Pocket identity counts and aligned CC status are summarized below.

| Representative | Organism | Assignment | Confidence | Best reviewed-UniProt hit | Identical pocket sites | aligned CC |
|---|---|---|---|---|---:|---|
{chr(10).join(class_table)}

Cluster 4 preserves all 21 sites. Clusters 1 and 2 preserve 17 and 16, respectively, while clusters 3, 7, 8, and 10 preserve only 10–11. This pattern argues that a single family-wide pocket model will be inadequate for distant representatives; structure-aware pocket comparison is needed before extrapolating ligand selectivity.

## Cysteine motifs and the Cys202/Cys203 hypothesis

The exact motif scanner reports overlapping CC, CCC, CXC, CXXC, and CXXXC occurrences and their sequence contexts. At the Q6UX53 Cys202/Cys203 alignment region:

- human METTL7B and cluster 4 contain **GDGCC**;
- METTL7A contains **DGCN** and lacks vicinal CC;
- METTL7A-like cluster 6 contains **DGCC**;
- cluster 3 contains an overlapping **CXC** motif (CGC);
- clusters 1, 2, 5, 7, 8, 9, and 10 lack aligned vicinal CC.

Thus the vicinal pair is neither universally retained by provisional METTL7B-like proteins nor restricted to them. Sequence conservation alone cannot establish disulfide formation, redox state, geometry, or functional coupling. The proposed vicinal-disulfide mechanism remains testable for human METTL7B and cluster 4, but requires structural geometry, cysteine accessibility, redox-sensitive mass spectrometry, and C202/C203 mutagenesis. Cluster 6 is a particularly informative counterexample/control.

## SAM binding and catalytic features

The human references and representatives with active UniProtKB records carry the METTL7/TMT-associated PF08241, IPR013216/IPR029063/IPR052356, and CDD cd02440 annotations. The Q6UX53 segment 75–84 is **LELGCGTGAN**; a related glycine-rich methyltransferase-core segment is retained in the close TMT proteins but becomes GAAFGPN, GAGSGAN, GVGEGPN, GIGTGPN, or GPGPGTT in distant representatives. This supports a shared SAM-dependent methyltransferase fold while showing substantial motif divergence.

Neither reviewed human UniProt record provides an experimentally curated active-site residue feature. Consequently, pocket residues are reported as structurally nominated ligand-contact candidates, not relabeled as proven catalytic residues. The present data support conservation of the methyltransferase core and SAM-binding architecture, but do not justify assigning a universally conserved catalytic side chain beyond published/curated evidence.

## Reciprocal similarity searches and classification

Forward BLASTP searches used all 12 panel sequences against a frozen reviewed-UniProtKB/Swiss-Prot FASTA (575,503 sequences; downloaded 2026-08-01). The second leg searched each unique top reviewed hit back against the ten representatives. All four unique reviewed top hits (human, mouse, or rat TMT1A/TMT1B) return cluster 4 as their best representative because it is a near-identical primate TMT1B; therefore strict reciprocal-best-hit logic identifies only the close cluster-4 relationship and cannot alone subtype the remaining paralogous/divergent representatives. Final assignments integrate hit identity/coverage, supplied and UniProt annotation, pocket conservation, and supported local tree relationships.

Assignments are provisional where deep tree support is weak. Cluster 8 remains “other SAM-dependent methyltransferase” despite its TMT1A-like best hit and sister relationship to cluster 7 because it lacks stable representative identity/taxonomy and carries only a generic annotation. Cluster 9 is confidently “other” because its ubiE/COQ5 annotation and long, weakly similar branch agree. No representative is called unrelated.

## Structural availability

Experimental structures are reported only when a PDB cross-reference occurs in the current UniProt record. AlphaFold identifiers come from the [AlphaFold Protein Structure Database API](https://alphafold.ebi.ac.uk/). The ESM Metagenomic Atlas does not provide a stable accession in the supplied BioHub or UniProt records for these entries; those cells are reported as **not established**, not as proof of absence.

| Sequence | Experimental PDB IDs | AlphaFold DB ID | ESMFold status |
|---|---|---|---|
{chr(10).join(structure_table)}

## Implications for METTL7B selectivity

The high pocket identity shared by METTL7A-like clusters 1–2 implies that selectivity is unlikely to follow from the conserved core alone. The most informative discriminants in this panel are combinations of the 195–203 region (including Lys196 versus the METTL7A His196 and Cys203 versus Asn203), neighboring loop geometry, and N-terminal membrane context. Cluster 4 is the best surrogate for conserved METTL7B pocket chemistry. Distant clusters should not be used as ligand-binding surrogates without structural alignment or docking-site validation.

## Limitations

- This is a representative-panel analysis, not an analysis of all 809 proteins and not a statement about within-cluster heterogeneity.
- The tree is unrooted and deep nodes are weakly supported.
- Cluster-derived Pfam counts describe cluster prevalence, not necessarily the representative's exact domain architecture.
- BLAST against reviewed UniProt is conservative and may omit closer unreviewed homologs.
- Pocket mapping is sequence-alignment based; it does not prove three-dimensional equivalence.
- ESMFold availability could not be established from stable identifiers and is not reported as absent.
- Disulfide formation cannot be inferred from a cysteine motif.

## Reproducibility

Software and parameters:

```text
MAFFT v7.526: --localpair --maxiterate 1000 --reorder
IQ-TREE 3.1.2: -m MFP -B 1000 --alrt 1000 -seed 20260801
Selected model: LG+G4 (BIC)
NCBI BLAST+ 2.17.0: blastp -evalue 1e-5 -max_target_seqs 20 -seg yes
Reviewed UniProtKB snapshot: query=(reviewed:true), FASTA, downloaded 2026-08-01
Swiss-Prot snapshot sequences: 575,503
```

Primary checksums:

```text
0b2432e9c4d497bdf2e3a1b68d60205102339c4d62ed32313b1035247e969729  uniprot_sprot_2026-08-01.fasta.gz
829f377ea224a7da30124413309492b3b1644ee9558ec1de1b31e1bbbc78f3b3  cluster_representatives.fasta
a379c178cbfb33639493eb6aa4122a28385c225f442f2682cb8ae115ddca84fb  cluster_alignment.fasta
```

The analysis scripts are under `analysis/biohub-representatives/`. Raw UniProt/UniParc/AlphaFold responses and the frozen reviewed-UniProt FASTA are retained under `raw/`; IQ-TREE model and support outputs are under `tmp/`. The exact generated tables should be used as supplementary data rather than transcribing values from this narrative.
"""
    (root / "biohub_representative_analysis.md").write_text(report)


if __name__ == "__main__":
    main()
