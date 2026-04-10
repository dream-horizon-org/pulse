import { useMutation } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { LoginResponse } from "./useLogin.interface";

export const useLogin = () => {
  const route = API_ROUTES.LOGIN;

  return useMutation<ApiResponse<LoginResponse>, unknown, string>({
    mutationFn: async (firebaseIdToken: string) => {
      const response = await makeRequest<LoginResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({ firebaseIdToken }),
        },
      });

      if (response.error || !response.data) {
        throw new Error(response.error?.message ?? 'Login failed. Please try again.');
      }

      return response;
    },
  });
};
