interface Props {
  title: string
  message?: string
  loading?: boolean
  compact?: boolean
  onRetry?: () => void
}

export function AsyncState({
  title,
  message,
  loading = false,
  compact = false,
  onRetry,
}: Props) {
  return (
    <section className={`async-state${compact ? ' compact' : ''}`}>
      {loading && <span className="spinner" aria-hidden="true" />}
      <h2>{title}</h2>
      {message && <p>{message}</p>}
      {onRetry && <button onClick={onRetry}>Try again</button>}
    </section>
  )
}
