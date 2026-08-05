export function alignmentInitializationLabel(initialization: string): string {
  switch (initialization) {
    case 'PCA_ICP':
      return 'PCA + ICP'
    case 'SEQUENCE_SEEDED_KABSCH':
      return 'sequence-seeded Kabsch'
    case 'SEQUENCE_SEEDED_KABSCH_ICP':
      return 'sequence-seeded Kabsch + ICP'
    default:
      return initialization
  }
}
