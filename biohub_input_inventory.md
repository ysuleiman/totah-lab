# BioHub Input Inventory

Inventory date: 2026-07-31  
Inventory scope: `/Users/yazan/artifacts/targets/METTL7/biohub/clusters`  
This document describes only files present in the supplied BioHub export. No
external accessions, organisms, sequences, annotations, memberships, or
similarity values have been added.

## Readiness decision

**Representative amino-acid sequences were recovered for all 10 clusters.**
The missing-representative-sequence stop condition is therefore not triggered.
No biological classification or residue mapping has been performed by this
inventory step.

Classification is not yet justified from these inputs alone. The export does
not include reviewed human METTL7A and METTL7B reference records, sequence
versions, reciprocal-search results, or an explicit per-representative domain
architecture analysis. Cluster names must not be used as classifications.

## File inventory

The export contains 538 files:

| Format | Count | Contents |
|---|---:|---|
| FASTA | 521 | One combined FASTA, 10 cluster FASTAs, 510 cached per-member or batch FASTAs |
| JSON | 16 | Manifest, cluster selection, 10 raw cluster records, and four batch metadata records |
| CSV | 1 | Member-to-cluster index and sequence metadata |

Top-level files:

- `all-clusters.fasta` — 809 complete amino-acid sequences. Each header records
  cluster ID, category, BioHub protein MD5 hash, accession, and source.
- `cluster-members.csv` — 809 data rows with cluster and sequence metadata.
- `cluster-selection.json` — the 10 requested cluster categories,
  representative hashes, and expected member counts.
- `manifest.json` — generation timestamp, category labels, representative
  hashes and names, member counts, and cluster-directory mappings.

Each `cluster-XX-<category>/` directory contains:

- `cluster.json` — raw BioHub cluster metadata.
- `sequences.fasta` — all complete member sequences assigned to that category.
- Clusters downloaded through individual lookups additionally contain
  `members/<protein_hash>.fasta` files.
- Clusters downloaded through the Atlas batch endpoint additionally contain
  `cluster_info.json` or `batches/batch-*-cluster-info.json` and matching batch
  FASTA files.

The complete literal member-file list is represented deterministically by the
directory tree and by `cluster-members.csv`; it is not duplicated as 809 lines
in this report.

## Cluster and representative inventory

There are 10 clusters and 10 recovered representative sequences. The
representative hash is present in its corresponding cluster FASTA.

| Cluster | Supplied category | Representative hash | Representative accession in FASTA | Source | Members |
|---:|---|---|---|---|---:|
| 1 | METTL7A | `60ee18447dcd7b69207b93fac7add250` | `UPI001336417D` | `uniparc` | 73 |
| 2 | Thiol methyltransferase 1A | `d3f8d432b47105d4d408d7a544c4c5d1` | `UPI00072EA7D3` | `uniparc` | 96 |
| 3 | TMT1B-like isoform X2 | `634f16f7e0f298727b395874854a8741` | `UPI002417A50C` | `uniparc` | 131 |
| 4 | Thiol methyltransferase 1B | `25d6052c0b963947285f3248b0b22cea` | `UPI001E250006` | `uniparc` | 132 |
| 5 | METTL7B-like protein | `ad419820f1badd42f0016b7bec28ddff` | `UPI001F953EA1` | `uniparc` | 65 |
| 6 | TMT1A-like isoform X1 | `d3acc3886c78e1b1530b8faec6521c9f` | `UPI001E670011` | `uniparc` | 60 |
| 7 | METTL7A isoform X2 | `fcbe733928010358c3534b015bfdc240` | `UPI0011E4F9CD` | `uniparc` | 56 |
| 8 | SAM-dependent methyltransferase superfamily protein | `2b38c3ca9b3dde5182055862f3ce5e7e` | `639590277` | `img_m` | 61 |
| 9 | ubiE/COQ5 methyltransferase family protein | `16d838091a6d42a721687bbd31cf29bb` | `MGYP001207546228` | `mgy` | 62 |
| 10 | METTL7B-like | `0f48266b04e5451f4167d5c5e98e4fc3` | `UPI00189A43A9` | `uniparc` | 73 |

These identifiers are reported exactly as present in the FASTA headers. They
have not been interpreted as reviewed UniProt accessions. In particular, the
`UPI...` values identify UniParc records, not reviewed Swiss-Prot records.

## Available metadata fields

### `cluster.json`

- `protein_hash`
- `protein_name`
- `source`
- `accession`
- `cluster_size`
- `cluster_pct_characterized`
- `cluster_mean_domain_coverage`
- `member_protein_hashes`
- `cluster_top_pfam_domains`
- `cluster_representative_features`
- `cluster_taxonomy_info`
- `top_phyla`
- `mean_plddt`
- `ptm`

Nested cluster fields include PFAM identifier, PFAM name, PFAM member count,
taxonomy rank/name, phylum counts, and representative SAE feature records.
These annotations are evidence fields; they are not substitutes for sequence
alignment or classification.

### `cluster-members.csv`

- `cluster_id`
- `category`
- `representative_hash`
- `member_hash`
- `accession`
- `source`
- `sequence_length`

### FASTA headers and sequences

- BioHub/Atlas sequence MD5 hash
- accession string
- source string
- complete amino-acid sequence
- combined FASTA only: supplied cluster ID and category

### Manifest and selection metadata

- schema version
- generation timestamp
- cluster ID and category
- representative hash and BioHub representative name
- expected and observed member counts
- cluster directory

## Sequence availability

- Full amino-acid sequences: **present** for 809/809 member records.
- Representative amino-acid sequences: **present** for 10/10 representatives.
- Representative accession strings: **present** for 10/10 representatives.
- Reviewed human reference sequences with sequence-version provenance:
  **not present as authoritative reference records in this export or located
  as local FASTA inputs in the repository inventory.**

The downloader's integrity validation confirmed that each sequence MD5 equals
the protein hash reported by BioHub.

## Similarity provenance

The supplied export contains **no pairwise similarity score column**, no
representative-to-query score, no reciprocal similarity result, and no field
that defines a score as sequence, structural, or embedding similarity.

Accordingly, for this export the similarity score type is recorded as:
**`UNSPECIFIED / NOT EXPORTED`**.

Fields such as `cluster_representative_features`, `ptm`, `mean_plddt`, and
`cluster_mean_domain_coverage` are not pairwise sequence-similarity scores and
must not be treated as such. The category names also do not encode a measured
similarity value.

## Reproducibility record

### Primary input checksums (SHA-256)

```text
5150343aa3c807909770335cd9e7736f67b50f763d08b804c8fe2ca9b427290e  all-clusters.fasta
d611b1af7c608c05ba574ec3067ad7e92963eedb32cdf453e48459a5f0314b13  cluster-members.csv
b064e0ac3358c85a6dd17bf2de574057159e2bcc9b8f96c7a1a603b26274ed6a  cluster-selection.json
6cd923fed354b321f141a717e9432419310975ce8b2f8084d51f684cbe993bd7  manifest.json
```

### Recorded software

```text
OpenJDK 21.0.11
Apache Maven 3.9.16
jq 1.7.1
BioHub downloader implementation commit: e1800a6
```

### Recorded download parameters

- Atlas base service: `https://biohub.ai`
- Sequence retrieval only; structures and feature tensors excluded.
- Ten representative hashes and expected counts are preserved in
  `cluster-selection.json`.
- The exact originating shell command line was not stored in the export and is
  therefore reported as unavailable rather than reconstructed from memory.
- No alignment, motif search, residue mapping, domain prediction, reciprocal
  search, or classification parameters exist yet because those analyses have
  not been run.

## Inputs still required before classification and residue analysis

The representative sequences themselves are available, so analysis need not
stop for missing representatives. Before assigning `TMT1A_LIKE`,
`TMT1B_LIKE`, `METHYLTRANSFERASE_OUTGROUP`, or `UNRESOLVED`, the analysis must
add or retrieve with provenance:

1. Reviewed human METTL7A and METTL7B reference sequences, including accession,
   sequence version, retrieval date, and source endpoint.
2. A documented multiple-sequence alignment and local-alignment quality checks.
3. Reciprocal similarity evidence where available.
4. Domain architecture evidence and explicit domain-boundary uncertainty.
5. Taxonomic context tied to each representative, not merely cluster-level
   aggregate taxonomy.
6. Diagnostic aligned residues and annotation evidence.

Until those inputs and analyses exist, all ten representatives remain
**`UNRESOLVED` for classification purposes**; this is a procedural status, not
a biological conclusion.
