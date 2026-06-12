/**
 * Error handling utilities for API responses
 */
import { ApiResponse } from "../helpers/makeRequest/makeRequest.interface";

export enum ErrorType {
  NETWORK = "NETWORK",
  CLIENT = "CLIENT",
  SERVER = "SERVER",
  UNKNOWN = "UNKNOWN",
}

export interface ErrorInfo {
  type: ErrorType;
  message: string;
  code?: string;
  cause?: string;
  status?: number;
}

/**
 * Classifies error type based on status code or error structure
 */
export function classifyError(error: unknown, status?: number): ErrorInfo {
  // Network errors (no response from server)
  if (error instanceof TypeError && error.message.includes("fetch")) {
    return {
      type: ErrorType.NETWORK,
      message: "Network error. Please check your connection.",
      code: "NETWORK_ERROR",
    };
  }

  // Check if it's an ApiResponse with error
  if (error && typeof error === "object" && "error" in error) {
    const apiError = error as ApiResponse<unknown>;
    if (apiError.error) {
      const statusCode = apiError.status || status;
      return {
        type: getErrorTypeFromStatus(statusCode),
        message: apiError.error.message || "An error occurred",
        code: apiError.error.code,
        cause: apiError.error.cause,
        status: statusCode,
      };
    }
  }

  // React Query error structure
  if (error && typeof error === "object" && "message" in error) {
    const err = error as { message?: string; code?: string };
    return {
      type: ErrorType.UNKNOWN,
      message: err.message || "An unexpected error occurred",
      code: err.code,
    };
  }

  // Fallback
  return {
    type: ErrorType.UNKNOWN,
    message: "An unexpected error occurred",
  };
}

/**
 * Determines error type from HTTP status code
 */
function getErrorTypeFromStatus(status?: number): ErrorType {
  if (!status) return ErrorType.UNKNOWN;

  if (status >= 400 && status < 500) {
    return ErrorType.CLIENT;
  }

  if (status >= 500) {
    return ErrorType.SERVER;
  }

  return ErrorType.UNKNOWN;
}

/**
 * Gets user-friendly error message based on error type
 */
export function getErrorMessage(errorInfo: ErrorInfo): string {
  switch (errorInfo.type) {
    case ErrorType.NETWORK:
      return "Unable to connect to the server. Please check your internet connection.";
    case ErrorType.CLIENT:
      return errorInfo.message || "Invalid request. Please check your input.";
    case ErrorType.SERVER:
      return "Server error. Please try again later or contact support.";
    default:
      return errorInfo.message || "An unexpected error occurred.";
  }
}
