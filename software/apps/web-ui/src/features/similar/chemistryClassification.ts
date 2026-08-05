export function classificationLabel(classification: string): string {
  switch (classification) {
    case 'STRONG_SIMILARITY':
      return 'Strong similarity'
    case 'MODERATE_SIMILARITY':
      return 'Moderate similarity'
    case 'SHAPE_ONLY_NEIGHBOR':
      return 'Shape-only neighbor'
    case 'REJECTED':
      return 'Rejected'
    default:
      return classification
  }
}

export function classificationClass(classification: string): string {
  switch (classification) {
    case 'STRONG_SIMILARITY':
      return 'cls-strong'
    case 'MODERATE_SIMILARITY':
      return 'cls-moderate'
    case 'SHAPE_ONLY_NEIGHBOR':
      return 'cls-shape-only'
    case 'REJECTED':
      return 'cls-rejected'
    default:
      return ''
  }
}
