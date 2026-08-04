import { Component, type ReactNode } from 'react'
import { AsyncState } from './AsyncState'

interface Props {
  children: ReactNode
}

interface State {
  error: Error | null
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  render() {
    if (this.state.error) {
      return (
        <AsyncState
          title="Something went wrong rendering this page"
          message={this.state.error.message}
        />
      )
    }
    return this.props.children
  }
}
