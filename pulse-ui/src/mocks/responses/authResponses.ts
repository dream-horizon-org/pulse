/**
 * Authentication Mock Responses
 */

export const mockAuthResponses = {
  authenticate: {
    data: {
      accessToken: "mock_access_token",
      refreshToken: "mock_refresh_token",
      tokenType: "Bearer",
      expiresIn: 3600,
      user: {
        email: "mock@example.com",
        name: "Mock User",
        picture: "https://via.placeholder.com/150",
      },
    },
    status: 200,
  },
  refreshToken: {
    data: {
      accessToken: "mock_refreshed_token",
      refreshToken: "mock_refresh_token",
      tokenType: "Bearer",
      expiresIn: 3600,
    },
    status: 200,
  },
  verifyToken: {
    data: {
      isAuthTokenValid: true,
    },
    status: 200,
  },
};
