import axiosClient from "./axiosClient";

export const customerService = {
  getContracts: async () => {
    return axiosClient.get("/contracts/me");
  },

  getNotifications: async () => {
    try {
      const response = await axiosClient.get("/notifications");
      // Handle Spring Page/PagedResponse structure
      return response.items || response.content || response || [];
    } catch (error) {
      console.error("Failed to fetch notifications", error);
      return [];
    }
  },

  getProducts: async () => {
    try {
      const response = await axiosClient.get("/products");
      // GET /products returns paged list, we extract content
      return response.items || response.content || response || [];
    } catch (error) {
      console.error("Failed to fetch products", error);
      return [];
    }
  },

  markNotificationRead: async (notificationId) => {
    return axiosClient.patch(`/notifications/${notificationId}/read`);
  },

  mockPay: async (paymentData) => {
    return axiosClient.post("/payments/mock", paymentData);
  },

  createContract: async (contractData) => {
    return axiosClient.post("/contracts", contractData);
  }
};
