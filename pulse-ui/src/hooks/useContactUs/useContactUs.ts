import { useMutation } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";

export const useContactUs = () => {
  const route = API_ROUTES.CONTACT_US;

  return useMutation<ApiResponse<string>, Error, { message?: string | null }>({
    mutationFn: async (data) => {
      const response = await makeRequest<string>({
        url: `${API_BASE_URL}${route.apiPath}?type=sales`,
        init: {
          method: route.method,
          body: JSON.stringify({
            message: data?.message ?? null,
          }),
        },
      });

      if (response.error) {
        throw new Error(
          response.error.message ?? "Failed to submit contact request",
        );
      }

      return response;
    },
  });
};
