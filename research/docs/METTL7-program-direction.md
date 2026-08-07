# METTL7B Program Direction

I’d prioritize the whole METTL7B program like this:

1. **Fix the pocket-ranking/comparison methodology first.** We still have unresolved questions about why METTL7A and other METTLs rank where they do, whether our whole-pocket similarity score is biologically meaningful, and how much residue chemistry versus geometry should contribute. Until that is stable, proteome-wide conclusions remain shaky.

2. **Build the structural reference set.** Add 2PXX, 5F8C/E/F, 8K76, 7NOY and any better experimental homologs we find. Align them to 7B/7A and determine what AlphaFold features are experimentally plausible. The mouth is part of this, but so are the core, neck, loops, SAM region, and ligand-facing surface.

3. **Understand the 7B vs 7A pocket comprehensively.** We need a residue-by-residue map of conserved versus discriminatory positions, shape differences, electrostatics, hydration, cavity accessibility, and dynamics. The “7B wider mouth” hypothesis belongs here, alongside Cys203 accessibility and other local differences.

4. **Interrogate the binder / known ligand evidence.** This is very high priority because real biochemical data outranks docking. We need to pin down exactly what Dr. Totah’s published compound was: binder, inhibitor, substrate, what assay was used, potency, and whether 7A selectivity was actually measured. If there is one published selective ligand, it may tell us more than another 10,000 dockings.

5. **Reanalyze our existing ligand hits rather than screen more molecules.** Take the strongest 7B/7A differential compounds and determine why they score differently: core interaction, entrance extension, steric exclusion in 7A, unique residue contact, conformational effect, etc. We already have enough compounds to learn from.

6. **Run perturbation tests.** Reciprocal 7B↔7A mutations and targeted MD should test proposed selectivity determinants. This is where we distinguish correlation from mechanism.

7. **Then derive the fragment grammar.** Not before. The grammar should encode the interaction features we have actually established: anchor fragments, tolerated linker vectors, selective peripheral fragments, excluded 7A geometries, and possibly a separate Cys-directed grammar.

8. **Then de novo ligand design.** Once the grammar exists, generate molecules deliberately rather than doing another broad virtual screen.

9. **Then proteome-wide selectivity assessment.** Your human pocket database becomes much more powerful once we can search for the specific discriminating feature rather than asking only “what pockets globally resemble 7B?”

## The four major scientific questions, in order

1. Is our pocket comparison trustworthy?
2. What does the real 7B pocket look like?
3. What structural feature actually distinguishes 7B?
4. Can chemistry exploit that feature?

The mouth is only one candidate answer to question three.

## Immediate top task

Recover and characterize that reported ligand from the Totah publication. If there really is experimental 7B selectivity there, that becomes an anchor for almost everything else we’re doing.
