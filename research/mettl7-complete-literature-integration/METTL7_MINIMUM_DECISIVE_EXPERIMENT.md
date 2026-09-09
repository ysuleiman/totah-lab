# METTL7 minimum decisive experiment

Run one matched, blinded isotope-transfer matrix using separately purified,
identity-confirmed METTL7A and METTL7B preparations from the same expression and
purification platform.

## Minimal matrix

- Enzymes: METTL7A WT, METTL7B WT, and one folded/SAM-binding-competent
  catalytic-loss control for each paralog.
- Substrates: TSL as the established thiol control; one defined KLF4 or NFKBIA
  RNA fragment containing the experimentally constrained region; one defined
  candidate-m6A RNA oligonucleotide from the strongest existing m6A study.
- Cofactor: isotope-labelled SAM.
- Readout: product-resolved LC-MS/MS for methyl-TSL, d3-m7G and d3-m6A, with
  nucleotide mapping for positive RNA products.
- Controls: enzyme-minus, SAM-minus, substrate-minus, heat-inactivated enzyme,
  mock purification, time course and enzyme-dose series.

This single factorial experiment distinguishes direct m7G from copurification,
tests whether m6A is genuine, compares A and B without sequence-based inference,
and asks whether loss of one catalytic function tracks loss of all acceptor
classes. A selected recognition-shell mutant should be added only after WT
product formation is established; otherwise a negative mutant result is not
interpretable.

## Decision table

| Outcome | Interpretation |
|---|---|
| WT A makes thiol product and m7G; catalytic control loses both | Strong support for shared A catalytic center |
| WT A makes thiol product but no RNA product | Published RNA activity requires another condition or component |
| m7G survives catalytic-loss control or follows mock fraction | Copurifying-factor explanation favored |
| Chemically resolved d3-m6A forms only with WT A | Genuine dual RNA-base promiscuity supported |
| A and B differ under identical conditions | Direct paralog-specific RNA behavior established |

