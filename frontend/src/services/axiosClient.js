import axios from "axios";

// API Gateway base URL
const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";
// Authorization Server base URL
const AUTH_URL = import.meta.env.VITE_AUTH_URL || "http://localhost:9000";

const axiosClient = axios.create({
  baseURL: API_URL,
  headers: {
    "Content-Type": "application/json",
  },
});

let refreshTokenPromise = null;

async function refreshAccessToken() {
  const refreshToken = localStorage.getItem("refresh_token");
  if (!refreshToken) {
    throw new Error("No refresh token available");
  }

  if (!refreshTokenPromise) {
    refreshTokenPromise = axios
      .post(`${AUTH_URL}/api/v1/users/refresh`, {
        refreshToken: refreshToken,
      })
      .then((response) => {
        const tokenData = response.data;
        if (!tokenData?.accessToken) {
          throw new Error("Invalid response from refresh token endpoint");
        }

        // Store tokens
        localStorage.setItem("token", tokenData.accessToken);
        if (tokenData.refreshToken) {
          localStorage.setItem("refresh_token", tokenData.refreshToken);
        }
        return tokenData.accessToken;
      })
      .catch((err) => {
        // Clear tokens on failure
        localStorage.removeItem("token");
        localStorage.removeItem("refresh_token");
        localStorage.removeItem("user");
        throw err;
      })
      .finally(() => {
        refreshTokenPromise = null;
      });
  }

  return refreshTokenPromise;
}

// Request Interceptor: Attach Authorization Bearer token
axiosClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem("token");
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response Interceptor: Handle data formatting and token expiration (401)
axiosClient.interceptors.response.use(
  (response) => {
    return response.data;
  },
  async (error) => {
    const originalRequest = error.config;

    // If 401 Unauthorized and not retried yet
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true;
      const refreshToken = localStorage.getItem("refresh_token");

      if (refreshToken) {
        try {
          console.log("Access token expired. Attempting refresh...");
          const newAccessToken = await refreshAccessToken();
          originalRequest.headers = originalRequest.headers || {};
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
          return axiosClient(originalRequest);
        } catch (refreshError) {
          console.warn("Failed to refresh token. Logging out...", refreshError);
        }
      }

      // If refresh failed or no refresh token, logout and redirect to login
      localStorage.removeItem("token");
      localStorage.removeItem("refresh_token");
      localStorage.removeItem("user");
      window.location.href = "/login";
    }

    return Promise.reject(error);
  }
);

export default axiosClient;
export { AUTH_URL, API_URL };
