import { makeRequest } from '../makeRequest';
import { API_BASE_URL } from '../../constants';
import { ApiKeysResponse, ApiKeyResult } from './getProjectApiKey.interface';

/**
 * Fetches the active API key for the current project.
 * Falls back to a dummy development key if the API is not implemented yet (404).
 * 
 * @param projectId - The project ID to generate dummy key if needed
 * @returns Object with key, isDummy flag, and optional error message
 */
export const getProjectApiKey = async (projectId: string): Promise<ApiKeyResult> => {
  try {
    // X-Project-ID header is automatically added by makeRequest
    const response = await makeRequest<ApiKeysResponse>({
      url: `${API_BASE_URL}/v1/projects/${projectId}/api-keys`,
      init: { method: 'GET' },
    });
    
    // Check if API returned 404 (not implemented yet)
    if (response.status === 404) {
      return {
        key: `pulse_${projectId}_sk_dummy_development_key`,
        isDummy: true,
      };
    }
    
    // Check for successful response with data
    if (response.data?.apiKeys && response.data.apiKeys.length > 0) {
      // Get the active key
      const activeKey = response.data.apiKeys.find(k => k.isActive);
      if (activeKey?.apiKey) {
        return {
          key: activeKey.apiKey,
          isDummy: false,
        };
      }
    }

    // No active keys found in response
    return {
      key: null,
      isDummy: false,
    };
    
  } catch (error) {
    console.error('[getProjectApiKey] API call failed:', error);
    // On network/unexpected errors, return dummy key
    return {
      key: `pulse_${projectId}_sk_dummy_development_key`,
      isDummy: true,
      error: error instanceof Error ? error.message : 'Unknown error',
    };
  }
};
