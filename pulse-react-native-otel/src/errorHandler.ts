import PulseReactNativeOtel from './NativePulseReactNativeOtel';
import { mergeWithGlobalAttributes } from './globalAttributes';
import { isSupportedPlatform } from './initialization';
import { extractErrorDetails } from './utility';
import type { PulseAttributes } from './pulse.interface';

let previousErrorHandler: ((error: Error, isFatal?: boolean) => void) | null =
  null;

let isInitialized = false;
let handlingFatal = false;

function reportToOpenTelemetry(
  errorMessage: string,
  stackTrace: string,
  isFatal: boolean,
  errorType: string,
  attributes?: PulseAttributes
): void {
  const observedTimeMs = Date.now();
  const mergedAttributes = mergeWithGlobalAttributes(attributes || {});
  PulseReactNativeOtel.reportException(
    errorMessage,
    observedTimeMs,
    stackTrace,
    isFatal,
    errorType,
    mergedAttributes
  );
}

function handleGlobalError(error: Error, isFatal?: boolean): void {
  const shouldHandleFatal = !!isFatal;
  if (shouldHandleFatal) {
    if (handlingFatal) {
      PulseReactNativeOtel.trackEvent(
        'Encountered multiple fatal errors. The latest:',
        Date.now(),
        {
          message: error.message,
        }
      );
      return;
    }
    handlingFatal = true;
  }

  const { message, stackTrace, errorType } = extractErrorDetails(error);
  reportToOpenTelemetry(message, stackTrace, isFatal || false, errorType);
  console.error('[Pulse RN Crash]', 'Fatal:', isFatal, 'Error:', error);
  if (previousErrorHandler && typeof previousErrorHandler === 'function') {
    try {
      if (isFatal) {
        setTimeout(() => {
          previousErrorHandler!(error, isFatal);
        }, 150);
      } else {
        previousErrorHandler(error, isFatal);
      }
    } catch (handlerError) {
      console.error('[Pulse RN] Previous error handler threw:', handlerError);
    }
  }
}

export function reportException(
  error: Error | string,
  isFatal: boolean = false,
  attributes?: PulseAttributes
): void {
  if (!isSupportedPlatform()) {
    return;
  }

  let errorMessage: string;
  let stackTrace: string;
  let errorType: string;

  if (typeof error === 'string') {
    errorMessage = error;
    stackTrace = '';
    errorType = '';
  } else {
    const details = extractErrorDetails(error);
    errorMessage = details.message;
    stackTrace = details.stackTrace;
    errorType = details.errorType;
  }

  reportToOpenTelemetry(
    errorMessage,
    stackTrace,
    isFatal,
    errorType,
    attributes
  );
}

function initializeErrorHandler(): void {
  if (isInitialized) {
    console.warn('[Pulse RN] Error handler already initialized. Skipping.');
    return;
  }
  handlingFatal = false;

  if (!ErrorUtils) {
    console.warn(
      '[Pulse RN] ErrorUtils not available; cannot install global error handler.'
    );
    PulseReactNativeOtel.trackEvent(
      'ErrorUtils not found. Cannot install global error handler.',
      Date.now()
    );
    return;
  }

  const currentHandler = ErrorUtils.getGlobalHandler?.();

  if (currentHandler) {
    previousErrorHandler = currentHandler;
    console.log(
      '[Pulse RN] Previous error handler detected (likely React Native default) - will be preserved'
    );
  } else {
    console.log(
      '[Pulse RN] No previous error handler detected (unusual in React Native)'
    );
  }

  ErrorUtils.setGlobalHandler(handleGlobalError);

  isInitialized = true;
  handlingFatal = false;

  console.log('[Pulse RN] Error handler initialized successfully');
}

function disableErrorHandler(): void {
  if (!isInitialized) {
    console.warn(
      '[Pulse RN] Error handler not initialized. Nothing to disable.'
    );
    return;
  }

  if (previousErrorHandler) {
    ErrorUtils.setGlobalHandler(previousErrorHandler);
    console.log(
      '[Pulse RN] Error handler disabled. Previous handler restored.'
    );
  } else {
    ErrorUtils.setGlobalHandler(null as any);
    console.log(
      '[Pulse RN] Error handler disabled. Restored to React Native default.'
    );
  }

  isInitialized = false;
  previousErrorHandler = null;
  handlingFatal = false;
}

export function setupErrorHandler(enableErrorHandler: boolean): void {
  if (enableErrorHandler) {
    initializeErrorHandler();
  } else {
    disableErrorHandler();
  }
}
