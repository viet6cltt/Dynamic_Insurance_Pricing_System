import { create } from "zustand";
import { authService } from "../services/authService";

export const useAuthStore = create((set) => ({
  user: JSON.parse(localStorage.getItem("user")) || null,
  isAuthenticated: !!localStorage.getItem("token"),
  loading: false,
  error: null,

  login: async (emailOrPhone, password) => {
    set({ loading: true, error: null });
    try {
      const data = await authService.login(emailOrPhone, password);
      
      // Save to localStorage
      localStorage.setItem("token", data.accessToken);
      if (data.refreshToken) {
        localStorage.setItem("refresh_token", data.refreshToken);
      }
      
      const user = {
        id: data.userId,
        name: data.name,
        email: data.email,
        role: data.role,
      };
      localStorage.setItem("user", JSON.stringify(user));
      
      set({ user, isAuthenticated: true, loading: false });
      return user;
    } catch (error) {
      set({ error: error.message, loading: false });
      throw error;
    }
  },

  loginWithTokenResponse: (data) => {
    localStorage.setItem("token", data.accessToken);
    if (data.refreshToken) {
      localStorage.setItem("refresh_token", data.refreshToken);
    }

    const user = {
      id: data.userId,
      name: data.name,
      email: data.email,
      role: data.role,
    };
    localStorage.setItem("user", JSON.stringify(user));

    set({ user, isAuthenticated: true, error: null, loading: false });
    return user;
  },

  logout: () => {
    localStorage.removeItem("token");
    localStorage.removeItem("refresh_token");
    localStorage.removeItem("user");
    set({ user: null, isAuthenticated: false, error: null });
  },

  clearError: () => set({ error: null })
}));
