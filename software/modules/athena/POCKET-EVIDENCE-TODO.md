# Pocket Evidence Roadmap

## Next: experimental methyltransferase site grammar

- [ ] Restrict the initial cohort to single-human-target canonical SAM, SAH,
      and SFG experimental binding sites from canonical-site method version
      `f77da7fc9`.
- [x] Persist provenance-preserving PDB polymer residue to UniProt position
      mappings. Keep assembly, entity, chain, author/label residue identifiers,
      insertion codes, mutations, missing residues, and mapping failures.
      Initial SAM/SAH/SFG pass: 4,488/4,505 site residues mapped across 210
      assembly-target evaluations; 17 construct/outside-alignment residues
      remain explicitly unmapped; rerun checksum is stable.
- [ ] Build sequence-supported residue correspondence among represented human
      methyltransferases without forcing low-confidence alignments.
- [ ] Derive separate site-grammar dimensions for residue identity,
      substitution, chemistry class, direct-contact frequency, near-shell
      frequency, ligand-region contact, and structural variability. Do not
      combine these into a master score.
- [ ] Build CCD-topology-based atom correspondence for the chemically shared
      SAM/SAH/SFG scaffold and retain analog-specific substituent regions.
- [ ] Structurally compare sites using sequence-supported site residues, with
      method/version metadata and explicit evaluated, no-evidence,
      not-applicable, and failed states.
- [ ] Persist the grammar and correspondence results additively and verify
      deterministic, idempotent reruns before interpreting METTL7A/METTL7B.

## Then: AlphaFold projection and METTL7A/METTL7B placement

- [ ] Recover global and per-residue pLDDT from the authoritative AlphaFold v6
      mmCIF artifacts; confidence is not currently persisted in the database.
- [ ] Reconcile the 833 persisted AlphaFold structures without persisted
      pockets and the seven extra historical fpocket run directories.
- [ ] Preserve exact AlphaFold model/fragment identity. Do not collapse models
      to UniProt accession alone.
- [ ] Add an experimental-site to AlphaFold-pocket correspondence layer that
      keeps sequence mapping, residue-contact conservation, geometry, chemistry,
      pLDDT, and fpocket retrieval evidence separate.
- [ ] Project the validated experimental grammar onto METTL7A and METTL7B and
      report conserved core, variable substrate-facing positions, unusual
      substitutions, structural displacement, and cavity extension/mouth
      differences.
- [ ] Treat AlphaFold pockets as predicted comparison evidence only. Do not
      treat absence of a bound ligand as a negative and do not copy experimental
      ligand coordinates into predicted structures as observations.

## Deferred

- [ ] Web/API view mapping for the grammar and AlphaFold correspondence layers.
- [ ] Generic ML, embeddings, docking, ligand generation, model training, and
      negative-pocket labeling remain out of scope until the interpretable
      correspondence diagnostics are reviewed.
