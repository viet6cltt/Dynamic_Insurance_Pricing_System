import axiosClient from "./axiosClient";

export const customerService = {
  // ── Contracts ──────────────────────────────────────────────────────────────
  getContracts: async () => {
    return axiosClient.get("/contracts/me");
  },

  getContractById: async (contractId) => {
    return axiosClient.get(`/contracts/${contractId}`);
  },

  getClaimHistory: async () => {
    try {
      return await axiosClient.get("/contracts/claims/me");
    } catch (error) {
      console.error("Failed to fetch claim history", error);
      return [];
    }
  },

  getPolicyHistorySummaries: async () => {
    try {
      return await axiosClient.get("/contracts/policy-history/me");
    } catch (error) {
      console.error("Failed to fetch policy history summaries", error);
      return [];
    }
  },

  getPolicyHistorySummariesByInsuredPersonIds: async (insuredPersonIds = []) => {
    try {
      return await axiosClient.post("/contracts/policy-history/summaries", { insuredPersonIds });
    } catch (error) {
      console.error("Failed to fetch policy history summaries by insured persons", error);
      return [];
    }
  },

  createContract: async (payload) => {
    // payload: { quoteId, insuredPersonId, productId, coveragePlanId, simulatePaymentResult? }
    return axiosClient.post("/contracts", payload);
  },

  payContract: async (contractId, simulatePaymentResult = "SUCCESS") => {
    return axiosClient.post(`/contracts/${contractId}/pay`, { simulatePaymentResult });
  },

  // ── Insured Persons ────────────────────────────────────────────────────────
  getMyInsuredPersons: async ({ page = 0, size = 20, status } = {}) => {
    const params = new URLSearchParams({ page, size });
    if (status) params.append("status", status);
    return axiosClient.get(`/insured-persons/me?${params}`);
  },

  getInsuredPersonById: async (insuredPersonId) => {
    return axiosClient.get(`/insured-persons/${insuredPersonId}`);
  },

  createInsuredPerson: async (payload) => {
    // { fullName, dateOfBirth, gender, identityNumber, relationshipToOwner, linkedUserProfileId? }
    return axiosClient.post("/insured-persons", payload);
  },

  updateInsuredPerson: async (insuredPersonId, payload) => {
    return axiosClient.put(`/insured-persons/${insuredPersonId}`, payload);
  },

  deactivateInsuredPerson: async (insuredPersonId) => {
    return axiosClient.delete(`/insured-persons/${insuredPersonId}`);
  },

  // ── Pricing / Quotes ───────────────────────────────────────────────────────
  createQuote: async (payload) => {
    // { insuredPersonId, productId, coveragePlanId, riskProfile: JsonNode }
    return axiosClient.post("/pricing/quotes", payload);
  },

  getQuoteById: async (quoteId) => {
    return axiosClient.get(`/pricing/quotes/${quoteId}`);
  },

  getMyQuotes: async () => {
    return axiosClient.get("/pricing/quotes/me");
  },

  acceptQuote: async (quoteId) => {
    return axiosClient.post(`/pricing/quotes/${quoteId}/accept`);
  },

  // ── Products (GET /products) ───────────────────────────────────────────────
  getProducts: async ({ productType, status, page = 0, size = 20 } = {}) => {
    try {
      const params = new URLSearchParams({ page, size });
      if (productType) params.append("productType", productType);
      if (status) params.append("status", status);
      return await axiosClient.get(`/products?${params}`);
    } catch (error) {
      console.error("Failed to fetch products", error);
      return { items: [], totalElements: 0, totalPages: 0, page: 0, size };
    }
  },

  getProductById: async (productId) => {
    return axiosClient.get(`/products/${productId}`);
  },

  // ── Risk Input Schema ──────────────────────────────────────────────────────
  getRiskInputSchemaByProduct: async (productId) => {
    return axiosClient.get(`/products/${productId}/risk-input-schema`);
  },

  getRiskInputSchemaByProductType: async (productType) => {
    return axiosClient.get(`/risk-input-schemas/by-product-type/${productType}`);
  },

  getOccupationRiskMappings: async (productId, status = "ACTIVE") => {
    try {
      const params = status ? `?status=${status}` : "";
      const response = await axiosClient.get(`/products/${productId}/occupation-risk-mappings${params}`);
      return response.items || response.content || response || [];
    } catch (error) {
      console.error(`Failed to fetch occupation risk mappings for product ${productId}`, error);
      return [];
    }
  },

  // ── Coverage Plans ─────────────────────────────────────────────────────────
  getCoveragePlansByProduct: async (productId, status = null) => {
    try {
      const params = status ? `?status=${status}` : "";
      const response = await axiosClient.get(`/products/${productId}/coverage-plans${params}`);
      return response.items || response.content || response || [];
    } catch (error) {
      console.error(`Failed to fetch coverage plans for product ${productId}`, error);
      return [];
    }
  },

  getCoveragePlanById: async (coveragePlanId) => {
    return axiosClient.get(`/coverage-plans/${coveragePlanId}`);
  },

  // ── Notifications ──────────────────────────────────────────────────────────
  getNotifications: async ({ status, page = 0, size = 20 } = {}) => {
    try {
      const params = new URLSearchParams({ page, size });
      if (status) params.append("status", status);
      return await axiosClient.get(`/notifications?${params}`);
    } catch (error) {
      console.error("Failed to fetch notifications", error);
      return { content: [], page, size, totalElements: 0, totalPages: 0 };
    }
  },

  getUnreadNotificationCount: async () => {
    try {
      const response = await axiosClient.get("/notifications/unread-count");
      return response?.unreadCount ?? 0;
    } catch (error) {
      console.error("Failed to fetch unread notification count", error);
      return 0;
    }
  },

  markNotificationRead: async (notificationId) => {
    return axiosClient.patch(`/notifications/${notificationId}/read`);
  },

  markAllNotificationsRead: async () => {
    return axiosClient.patch("/notifications/read-all");
  },

  archiveNotification: async (notificationId) => {
    return axiosClient.patch(`/notifications/${notificationId}/archive`);
  },

  // ── Payments ───────────────────────────────────────────────────────────────
  mockPay: async (paymentData) => {
    return axiosClient.post("/payments/mock", paymentData);
  },

  getPaymentById: async (paymentId) => {
    return axiosClient.get(`/payments/${paymentId}`);
  },
};
