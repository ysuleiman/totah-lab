export function assessmentLabel(assessment: string): string {
  switch (assessment) {
    case 'STRONG_FUNCTIONAL_MATCH':
      return 'Strong functional match'
    case 'PROBABLE_FUNCTIONAL_MATCH':
      return 'Probable functional match'
    case 'GEOMETRIC_MATCH_ONLY':
      return 'Geometric match only'
    case 'CONFLICTING_EVIDENCE':
      return 'Conflicting evidence'
    case 'INSUFFICIENT_EVIDENCE':
      return 'Insufficient evidence'
    case 'REJECTED':
      return 'Rejected'
    default:
      return assessment
  }
}

export function assessmentClass(assessment: string): string {
  switch (assessment) {
    case 'STRONG_FUNCTIONAL_MATCH':
      return 'assessment-strong'
    case 'PROBABLE_FUNCTIONAL_MATCH':
      return 'assessment-probable'
    case 'GEOMETRIC_MATCH_ONLY':
      return 'assessment-geometric-only'
    case 'CONFLICTING_EVIDENCE':
      return 'assessment-conflicting'
    case 'INSUFFICIENT_EVIDENCE':
      return 'assessment-insufficient'
    case 'REJECTED':
      return 'assessment-rejected'
    default:
      return ''
  }
}
