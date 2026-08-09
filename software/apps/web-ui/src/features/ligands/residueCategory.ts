import type { ResidueCategoryKey } from '../../api/types'

export type { ResidueCategoryKey } from '../../api/types'

// Kept clear of the page's green/red (pocket membership) semantics.
export const CATEGORY_COLORS: Record<ResidueCategoryKey, string> = {
  HYDROPHOBIC: '#8a8f84',
  AROMATIC: '#c07a2b',
  POLAR: '#2b8a8a',
  POSITIVELY_CHARGED: '#3568c9',
  NEGATIVELY_CHARGED: '#8a4fbf',
  CYSTEINE: '#c9a227',
  GLYCINE: '#b5baae',
  PROLINE: '#b5baae',
}
