import { useMutation, UseMutationResult } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ApiResponse } from "../../helpers/makeRequest";
import {
  GetPulseAiResponseRequestBody,
  GetPulseAiResponseType,
  OnSettled,
  PulseAiResponseData,
} from "./useGetPulseAiResponse.interface";
import { getCookies } from "../../helpers/cookies";
import { COOKIES_KEY } from "../../constants";

// Custom streaming request handler for SSE
const makeStreamingRequest = async (
  requestBody: GetPulseAiResponseRequestBody,
  url: string,
): Promise<ApiResponse<GetPulseAiResponseType>> => {
  return new Promise(async (resolve) => {
    try {
      // Check if mock server is enabled
      if (
        process.env.REACT_APP_USE_MOCK_SERVER === "true" ||
        (process.env.NODE_ENV === "development" &&
          process.env.REACT_APP_USE_MOCK_SERVER !== "false")
      ) {
        try {
          const MockServerClass = await import("../../mocks/MockServer");
          if (MockServerClass) {
            const mockServer = MockServerClass.MockServer.getInstance();
            if (mockServer.isEnabled()) {
              const mockRequestConfig = {
                url: url,
                init: {
                  method: "POST",
                  body: JSON.stringify(requestBody),
                  headers: {
                    "content-type": "application/json",
                  },
                },
              };

              const mockResponse =
                await mockServer.handleRequest(mockRequestConfig);
              const mockData = await mockResponse.json();

              resolve({
                data: mockData.data,
                error: mockData.error,
                status: mockData.status,
              });
              return;
            }
          }
        } catch (error) {
          console.warn(
            "[useGetPulseAiResponse] Failed to load mock server, falling back to real API:",
            error,
          );
        }
      }

      // Get auth token
      const accessToken = getCookies(COOKIES_KEY.ACCESS_TOKEN);
      const userEmail = getCookies(COOKIES_KEY.USER_EMAIL);

      if (!accessToken) {
        resolve({
          data: null,
          error: {
            code: "401",
            message: "No access token available",
            cause: "Authentication failed",
          },
          status: 401,
        });
        return;
      }

      // Create fetch request for SSE
      const controller = new AbortController();

      fetch(url, {
        method: "POST",
        headers: {
          authorization: `Bearer ${accessToken}`,
          "content-type": "application/json",
          origin: window.location.origin,
          referer: window.location.href,
          "user-agent": navigator.userAgent,
          "user-email": userEmail || "",
        },
        body: JSON.stringify(requestBody),
        signal: controller.signal,
      })
        .then(async (response) => {
          if (!response.ok) {
            resolve({
              data: null,
              error: {
                code: `${response.status}`,
                message: `HTTP ${response.status}`,
                cause: "Request failed",
              },
              status: response.status,
            });
            return;
          }

          if (!response.body) {
            resolve({
              data: null,
              error: {
                code: "500",
                message: "No response body",
                cause: "Stream error",
              },
              status: 500,
            });
            return;
          }

          const reader = response.body.getReader();
          const decoder = new TextDecoder();
          let accumulatedData = "";
          let isComplete = false;
          let buffer = "";
          let finalJsonResponse: PulseAiResponseData | null = null;

          const readStream = async () => {
            try {
              while (!isComplete) {
                const { done, value } = await reader.read();

                if (done) {
                  isComplete = true;
                  break;
                }

                const chunk = decoder.decode(value, { stream: true });
                buffer += chunk;

                // Process complete lines
                const lines = buffer.split("\n");
                buffer = lines.pop() || ""; // Keep incomplete line in buffer

                for (const line of lines) {
                  const trimmedLine = line.trim();
                  if (trimmedLine === "") continue;

                  // Parse SSE format
                  if (trimmedLine.startsWith("data: ")) {
                    const data = trimmedLine.substring(6); // Remove 'data: ' prefix

                    // Skip heartbeat messages
                    if (data === "heartbeat") continue;

                    // Check for completion markers
                    if (
                      data === "[DONE]" ||
                      data === "[COMPLETE]" ||
                      data.includes("Stream completed")
                    ) {
                      isComplete = true;
                      break;
                    }

                    // Try to parse as JSON (final response)
                    try {
                      const jsonData = JSON.parse(data);
                      if (jsonData.text !== undefined) {
                        finalJsonResponse = jsonData as PulseAiResponseData;
                        isComplete = true;
                        break;
                      }
                    } catch (e) {
                      // Not JSON, treat as regular text data
                      accumulatedData = data;
                    }
                  }

                  // Handle event lines
                  if (trimmedLine.startsWith("event: ")) {
                    const eventType = trimmedLine.substring(7); // Remove 'event: ' prefix
                    if (eventType === "done" || eventType === "complete") {
                      isComplete = true;
                      break;
                    }
                  }
                }
              }

              // Resolve with the appropriate data
              if (finalJsonResponse) {
                // Return the parsed JSON response
                resolve({
                  data: {
                    event: "complete",
                    data: {
                      text: finalJsonResponse.text,
                      status: finalJsonResponse.status,
                      function_call: finalJsonResponse.function_call,
                      function_response: finalJsonResponse.function_response,
                    },
                  },
                  error: null,
                  status: 200,
                });
              } else {
                // Return accumulated text data
                resolve({
                  data: {
                    event: isComplete ? "complete" : undefined,
                    data: {
                      text: accumulatedData.trim(),
                      status: "success",
                      function_call: null,
                      function_response: null,
                    },
                  },
                  error: null,
                  status: 200,
                });
              }
            } catch (error) {
              resolve({
                data: null,
                error: {
                  code: "500",
                  message:
                    error instanceof Error
                      ? error.message
                      : "Stream reading error",
                  cause: "Stream error",
                },
                status: 500,
              });
            }
          };

          await readStream();
        })
        .catch((error) => {
          resolve({
            data: null,
            error: {
              code: "500",
              message: error instanceof Error ? error.message : "Network error",
              cause: "Request failed",
            },
            status: 500,
          });
        });
    } catch (error) {
      resolve({
        data: null,
        error: {
          code: "500",
          message: error instanceof Error ? error.message : "Unknown error",
          cause: "Request setup failed",
        },
        status: 500,
      });
    }
  });
};

export const useGetPulseAiResponse = (
  onSettled: OnSettled,
  sessionId: string | null,
): UseMutationResult<
  ApiResponse<GetPulseAiResponseType>,
  unknown,
  GetPulseAiResponseRequestBody,
  unknown
> => {
  const getUserQueryPulseAiInsights =
    API_ROUTES.GET_USER_QUERY_PULSE_AI_INSIGHTS;

  return useMutation<
    ApiResponse<GetPulseAiResponseType>,
    unknown,
    GetPulseAiResponseRequestBody
  >({
    mutationKey: [API_ROUTES.GET_USER_QUERY_PULSE_AI_INSIGHTS.key, sessionId],
    mutationFn: (requestBody) => {
      if (
        !requestBody["session-id"] ||
        !requestBody["user-id"] ||
        !requestBody.query
      ) {
        return Promise.resolve({
          data: null,
          error: {
            code: "400",
            message: "Missing required fields",
            cause: "Validation error",
          },
          status: 400,
        });
      }

      // Use custom streaming request for this specific endpoint
      return makeStreamingRequest(
        requestBody,
        `${API_BASE_URL}${getUserQueryPulseAiInsights.apiPath}`,
      );
    },
    onSettled: onSettled,
  });
};
