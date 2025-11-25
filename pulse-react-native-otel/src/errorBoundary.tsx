import * as React from 'react';
import { Pulse } from './index';

export const UNKNOWN_COMPONENT = 'unknown';
const COMPONENT_STACK_UNAVAILABLE = '<component stack unavailable>';

export type FallbackRender = (errorData: {
  error: unknown;
  componentStack: string;
}) => React.ReactElement;

export type ErrorBoundaryProps = {
  children?: React.ReactNode | (() => React.ReactNode);
  fallback?: React.ReactElement | FallbackRender | undefined;
  onError?: ((error: unknown, componentStack: string) => void) | undefined;
};

type ErrorBoundaryState =
  | {
      componentStack: null;
      error: null;
    }
  | {
      componentStack: string;
      error: unknown;
    };

const INITIAL_STATE: ErrorBoundaryState = {
  componentStack: null,
  error: null,
};

export class ErrorBoundary extends React.Component<ErrorBoundaryProps, ErrorBoundaryState> {
  public state: ErrorBoundaryState = INITIAL_STATE;

  public componentDidCatch(error: unknown, errorInfo: React.ErrorInfo): void {
    const componentStack = errorInfo.componentStack || COMPONENT_STACK_UNAVAILABLE;
    const { onError } = this.props;

    // Error is handled if a fallback is provided, otherwise it's unhandled (fatal)
    const handled = !!this.props.fallback;

    const errorToReport = error instanceof Error ? error : new Error(String(error));
    Pulse.reportException(errorToReport, !handled);

    if (onError) {
      onError(error, componentStack);
    }

    this.setState({ error, componentStack });
  }

  public render(): React.ReactNode {
    const { fallback, children } = this.props;
    const state = this.state;

    if (state.componentStack === null) {
      return typeof children === 'function' ? children() : children;
    }

    const element =
      typeof fallback === 'function'
        ? React.createElement(fallback, {
            error: state.error,
            componentStack: state.componentStack,
          })
        : fallback;

    if (React.isValidElement(element)) {
      return element;
    }

    return null;
  }
}

export function withErrorBoundary<P extends Record<string, any>>(
  WrappedComponent: React.ComponentType<P>,
  errorBoundaryOptions: ErrorBoundaryProps,
): React.FC<P> {
  const componentDisplayName = WrappedComponent.displayName || WrappedComponent.name || UNKNOWN_COMPONENT;

  const Wrapped = React.memo((props: P) => (
    <ErrorBoundary {...errorBoundaryOptions}>
      <WrappedComponent {...props} />
    </ErrorBoundary>
  )) as unknown as React.FC<P>;

  Wrapped.displayName = `errorBoundary(${componentDisplayName})`;

  return Wrapped;
}
