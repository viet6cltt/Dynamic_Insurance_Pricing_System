import axios from "axios";
import axiosClient, { AUTH_URL } from "./axiosClient";

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GG_CLIENT_ID || "485996629423-td2c31e9vppq688o0ucdjtfgb89i6lt1.apps.googleusercontent.com";
const GOOGLE_REDIRECT_URI = import.meta.env.VITE_GOOGLE_REDIRECT_URI || `${window.location.origin}/login/oauth2/code/google`;

export const authService = {
  login: async (emailOrPhone, password) => {
    try {
      const response = await axios.post(`${AUTH_URL}/api/v1/users/login`, {
        username: emailOrPhone, // Matches back-end expectations
        password: password,
      });
      return response.data;
    } catch (error) {
      const errorMsg = error.response?.data || "Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.";
      throw new Error(errorMsg);
    }
  },

  register: async (userData) => {
    try {
      const response = await axios.post(`${AUTH_URL}/api/v1/users/register`, {
        email: userData.email,
        phoneNumber: userData.phoneNumber,
        password: userData.password,
        fullName: userData.fullName,
        identityNumber: userData.identityNumber,
        dateOfBirth: userData.dateOfBirth,
        gender: userData.gender,
        role: "USER" // Default role
      });
      return response.data;
    } catch (error) {
      const errorMsg = error.response?.data || "Đăng ký thất bại. Vui lòng kiểm tra lại thông tin.";
      throw new Error(errorMsg);
    }
  },

  getUserProfile: async () => {
    return axiosClient.get("/users/me");
  },

  exchangeGoogleCode: async (code) => {
    try {
      const response = await axios.post(`${AUTH_URL}/api/v1/oauth/google/exchange`, {
        code,
        redirectUri: GOOGLE_REDIRECT_URI,
      });
      return response.data;
    } catch (error) {
      const errorMsg = error.response?.data?.message
        || error.response?.data
        || "Đăng nhập Google thất bại. Vui lòng thử lại.";
      throw new Error(errorMsg);
    }
  },

  updateUserProfile: async (profileData) => {
    return axiosClient.put("/users/me", profileData);
  },

  getGoogleAuthUrl: () => {
    return `https://accounts.google.com/o/oauth2/v2/auth?client_id=${GOOGLE_CLIENT_ID}&redirect_uri=${encodeURIComponent(GOOGLE_REDIRECT_URI)}&response_type=code&scope=${encodeURIComponent("openid email profile")}`;
  }
};
