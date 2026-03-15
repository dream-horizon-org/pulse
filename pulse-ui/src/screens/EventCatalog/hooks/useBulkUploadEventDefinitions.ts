import { useMutation } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../../constants";
import { BulkUploadResponse } from "../EventCatalog.types";
import { makeRequest } from "../../../helpers/makeRequest";

export const useBulkUploadEventDefinitions = () => {
  const route = API_ROUTES.BULK_UPLOAD_EVENT_DEFINITIONS;

  return useMutation({
    mutationKey: [route.key],
    mutationFn: async (file: File) => {
      const formData = new FormData();
      formData.append("file", file);

      return makeRequest<BulkUploadResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
          body: formData,
        },
      });
    },
  });
};
