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
};
