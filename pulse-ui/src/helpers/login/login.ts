import { API_BASE_URL } from "../../constants";
import { makeRequest } from "../makeRequest";
import { LoginResponse, LoginResult } from "./login.interface";

/**
 * Call POST /v1/auth/login with Firebase ID token
 * Backend determines if user needs onboarding or has projects
 */
export const login = async (
  firebaseIdToken: string
): Promise<LoginResult> => {
  try {
    const response = await makeRequest<LoginResponse>({
      url: `${API_BASE_URL}/v1/auth/login`,
      init: {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ firebaseIdToken }),
      },
    });
    
    if (response.data) {
      return { data: response.data };
    } else {
      return { 
        error: { 
          message: response.error?.message || 'Login failed. Please try again.' 
        } 
      };
    }
  } catch (error: any) {
    return { 
      error: { 
        message: error.message || 'Login failed. Please try again.' 
      } 
    };
  }
};
