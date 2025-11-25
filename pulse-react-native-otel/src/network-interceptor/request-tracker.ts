import type {
  RequestStartContext,
  RequestEndContext,
  RequestStartCallback,
} from './network.interface';

export class RequestTracker {
  private callbacks: RequestStartCallback[] = [];

  onStart(startCallback: RequestStartCallback) {
    this.callbacks.push(startCallback);
  }

  start(context: RequestStartContext) {
    const results: Array<ReturnType<RequestStartCallback>> = [];
    for (const startCallback of this.callbacks) {
      const result = startCallback(context);
      if (result) results.push(result);
    }

    return {
      onRequestEnd: (endContext: RequestEndContext) => {
        for (const result of results) {
          if (result && result.onRequestEnd) {
            result.onRequestEnd(endContext);
          }
        }
      },
    };
  }
}
