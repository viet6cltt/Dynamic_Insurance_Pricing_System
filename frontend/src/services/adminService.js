import axiosClient from "./axiosClient";

export const adminService = {
  // --- Product Management ---
  getProducts: async () => {
    try {
      const response = await axiosClient.get("/products?size=100");
      return response.items || response.content || response || [];
    } catch (error) {
      console.error("Failed to fetch products", error);
      throw error;
    }
  },

  createProduct: async (productData) => {
    return axiosClient.post("/admin/products", productData);
  },

  updateProduct: async (productId, productData) => {
    return axiosClient.put(`/admin/products/${productId}`, productData);
  },

  updateProductStatus: async (productId, status) => {
    return axiosClient.patch(`/admin/products/${productId}/status`, { status });
  },

  uploadProductImage: async (productId, file) => {
    const formData = new FormData();
    formData.append("file", file);
    return axiosClient.post(`/admin/products/${productId}/image`, formData, {
      headers: {
        "Content-Type": "multipart/form-data",
      },
    });
  },

  // --- Coverage Plan Management ---
  getCoveragePlans: async (productId, status = null) => {
    try {
      const params = status ? `?status=${status}` : "";
      const response = await axiosClient.get(`/products/${productId}/coverage-plans${params}`);
      return response.items || response.content || response || [];
    } catch (error) {
      console.error(`Failed to fetch coverage plans for product ${productId}`, error);
      throw error;
    }
  },

  getCoveragePlanById: async (planId) => {
    return axiosClient.get(`/coverage-plans/${planId}`);
  },

  createCoveragePlan: async (productId, planData) => {
    return axiosClient.post(`/admin/products/${productId}/coverage-plans`, planData);
  },

  updateCoveragePlan: async (planId, planData) => {
    return axiosClient.put(`/admin/coverage-plans/${planId}`, planData);
  },

  updateCoveragePlanStatus: async (planId, status) => {
    return axiosClient.patch(`/admin/coverage-plans/${planId}/status`, { status });
  },

  updateCoveragePlanLoadingRate: async (planId, loadingRate) => {
    return axiosClient.patch(`/admin/coverage-plans/${planId}/loading-rate`, { loadingRate });
  },

  // --- Risk Input Schema Management ---
  getRiskInputSchema: async (productId) => {
    try {
      return await axiosClient.get(`/products/${productId}/risk-input-schema`);
    } catch (error) {
      if (error.response?.status === 404) return null;
      console.error(`Failed to fetch risk input schema for product ${productId}`, error);
      throw error;
    }
  },

  createRiskInputSchema: async (productId, schemaData) => {
    return axiosClient.post(`/admin/products/${productId}/risk-input-schemas`, schemaData);
  },

  updateRiskInputSchema: async (schemaId, schemaData) => {
    return axiosClient.put(`/admin/risk-input-schemas/${schemaId}`, schemaData);
  },

  updateRiskInputSchemaStatus: async (schemaId, status) => {
    return axiosClient.patch(`/admin/risk-input-schemas/${schemaId}/status`, { status });
  },

  // --- Occupation Risk Mapping Management ---
  getOccupationRiskMappings: async (productId, status = null) => {
    try {
      const params = status ? `?status=${status}` : "";
      const response = await axiosClient.get(`/products/${productId}/occupation-risk-mappings${params}`);
      return response.items || response.content || response || [];
    } catch (error) {
      console.error(`Failed to fetch occupation risk mappings for product ${productId}`, error);
      throw error;
    }
  },

  createOccupationRiskMapping: async (productId, mappingData) => {
    return axiosClient.post(`/admin/products/${productId}/occupation-risk-mappings`, mappingData);
  },

  updateOccupationRiskMapping: async (mappingId, mappingData) => {
    return axiosClient.put(`/admin/occupation-risk-mappings/${mappingId}`, mappingData);
  },

  updateOccupationRiskMappingStatus: async (mappingId, status) => {
    return axiosClient.patch(`/admin/occupation-risk-mappings/${mappingId}/status`, { status });
  },

  // --- Notification Email Delivery Management ---
  getEmailDeliveries: async ({ status = "FAILED", size = 50 } = {}) => {
    try {
      const params = new URLSearchParams({ size });
      if (status) params.append("status", status);
      return await axiosClient.get(`/notifications/admin/email-deliveries?${params}`);
    } catch (error) {
      console.error("Failed to fetch notification email deliveries", error);
      throw error;
    }
  },

  retryEmailDelivery: async (deliveryId) => {
    return axiosClient.post(`/notifications/admin/email-deliveries/${deliveryId}/retry`);
  },

  // --- AI Model Lifecycle Management ---
  createTrainingJob: async (modelType) => {
    return axiosClient.post("/api/admin/training-jobs", { modelType });
  },

  getTrainingJob: async (jobId) => {
    return axiosClient.get(`/api/admin/training-jobs/${jobId}`);
  },

  getAiModel: async (modelType) => {
    return axiosClient.get(`/api/admin/models/${modelType}`);
  },

  getAiModelComparison: async (modelType) => {
    return axiosClient.get(`/api/admin/models/${modelType}/comparison`);
  },

  promoteAiModel: async (modelType, candidateVersion, reason) => {
    return axiosClient.post(`/api/admin/models/${modelType}/promote`, {
      candidateVersion,
      reason,
    });
  },

  rejectAiModel: async (modelType, candidateVersion, reason) => {
    return axiosClient.post(`/api/admin/models/${modelType}/reject`, {
      candidateVersion,
      reason,
    });
  },
};
