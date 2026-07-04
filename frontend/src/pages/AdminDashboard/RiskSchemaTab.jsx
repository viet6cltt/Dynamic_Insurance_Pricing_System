import React, { useEffect, useState } from "react";
import {
  FileCode2, Plus, Edit2, CheckCircle, AlertCircle, Loader2,
  X, Save, ChevronDown, ChevronUp, Tag
} from "lucide-react";
import { adminService } from "../../services/adminService";

const DEFAULT_SCHEMA = JSON.stringify({
  productType: "HEALTH",
  schemaVersion: "1.0.0",
  description: "Risk input schema for HEALTH insurance – maps to HealthRiskProfile consumed by the HealthRiskModifierModel (ai-model-service).",
  modelInputClass: "HealthRiskProfile",
  apiField: "riskProfile",
  baseline: {
    bmi: 22.0,
    smoker: "no",
    bloodPressure: 120.0,
    exerciseFrequency: "daily",
    preExistingCondition: false,
    occupationRisk: "low"
  },
  fields: [
    {
      name: "age",
      apiKey: "age",
      type: "NUMBER",
      label: "Tuổi",
      labelEn: "Age",
      required: true,
      min: 0,
      max: 120,
      step: 1,
      unit: "tuổi",
      description: "Tuổi của người được bảo hiểm (năm)"
    },
    {
      name: "sex",
      apiKey: "sex",
      type: "ENUM",
      label: "Giới tính",
      labelEn: "Sex",
      required: true,
      options: ["male", "female"],
      optionLabels: { "male": "Nam", "female": "Nữ" },
      description: "Giới tính sinh học của người được bảo hiểm"
    },
    {
      name: "bmi",
      apiKey: "bmi",
      type: "NUMBER",
      label: "Chỉ số BMI",
      labelEn: "Body Mass Index",
      required: true,
      min: 10,
      max: 80,
      step: 0.1,
      unit: "kg/m²",
      baseline: 22.0,
      description: "Chỉ số khối cơ thể (cân nặng / chiều cao²)"
    },
    {
      name: "children",
      apiKey: "children",
      type: "INTEGER",
      label: "Số người phụ thuộc",
      labelEn: "Number of dependents",
      required: false,
      min: 0,
      max: 20,
      step: 1,
      unit: "người",
      defaultValue: 0,
      description: "Số người phụ thuộc (con cái) được bảo hiểm theo hợp đồng"
    },
    {
      name: "smoker",
      apiKey: "smoker",
      type: "ENUM",
      label: "Hút thuốc lá",
      labelEn: "Smoker",
      required: true,
      options: ["yes", "no"],
      optionLabels: { "yes": "Có hút thuốc", "no": "Không hút thuốc" },
      baseline: "no",
      description: "Người được bảo hiểm có thói quen hút thuốc lá hay không"
    },
    {
      name: "bloodPressure",
      apiKey: "bloodPressure",
      type: "NUMBER",
      label: "Huyết áp tâm thu",
      labelEn: "Systolic Blood Pressure",
      required: true,
      min: 70,
      max: 220,
      step: 1,
      unit: "mmHg",
      baseline: 120.0,
      description: "Huyết áp tâm thu đo tại thời điểm đăng ký (đơn vị: mmHg)"
    },
    {
      name: "exerciseFrequency",
      apiKey: "exerciseFrequency",
      type: "ENUM",
      label: "Tần suất tập thể dục",
      labelEn: "Exercise Frequency",
      required: true,
      options: ["daily", "weekly", "rarely", "none"],
      optionLabels: {
        "daily": "Hàng ngày",
        "weekly": "Hàng tuần",
        "rarely": "Hiếm khi",
        "none": "Không tập"
      },
      baseline: "daily",
      description: "Tần suất hoạt động thể chất thường xuyên của người được bảo hiểm"
    },
    {
      name: "preExistingCondition",
      apiKey: "preExistingCondition",
      type: "BOOLEAN",
      label: "Bệnh nền / Bệnh có sẵn",
      labelEn: "Pre-existing Condition",
      required: true,
      baseline: false,
      trueLabel: "Có bệnh nền",
      falseLabel: "Không có bệnh nền",
      description: "Người được bảo hiểm có bệnh lý nền được chẩn đoán trước thời điểm đăng ký hay không"
    },
    {
      name: "occupationRisk",
      apiKey: "occupationRisk",
      type: "ENUM",
      label: "Mức rủi ro nghề nghiệp",
      labelEn: "Occupation Risk",
      required: true,
      options: ["low", "moderate", "high"],
      optionLabels: {
        "low": "Thấp (văn phòng, ít nguy hiểm)",
        "moderate": "Trung bình (kỹ thuật, dịch vụ)",
        "high": "Cao (xây dựng, khai thác, lái xe)"
      },
      baseline: "low",
      description: "Mức độ rủi ro của nghề nghiệp đang theo đuổi, được ánh xạ từ OccupationRiskMapping"
    }
  ],
  optionalGroups: {
    portfolioProfile: {
      description: "Thông tin hồ sơ danh mục (tùy chọn – dùng cho Portfolio Model)",
      fields: ["gender", "typeProduct", "typePolicy", "reimbursement", "exposureTime",
               "seniorityInsured", "newBusiness", "distributionChannel",
               "prevCostClaimsYear", "prevNMedicalServices", "prevHadClaimOrService", "claimFreePreviousYear"]
    },
    historicalExperience: {
      description: "Lịch sử bồi thường (tùy chọn – cải thiện độ chính xác định giá)",
      fields: ["pastClaimCount", "totalPastClaimAmount", "claimFreeYears", "recencyWeightedClaimScore"]
    }
  }
}, null, 2);

export default function RiskSchemaTab({ products, triggerToast }) {
  const [selectedProductId, setSelectedProductId] = useState(products[0]?.productId || "");
  const [schema, setSchema] = useState(null);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [schemaExpanded, setSchemaExpanded] = useState(false);

  const [form, setForm] = useState({
    schemaVersion: "1.0.0",
    schemaDefinition: DEFAULT_SCHEMA,
    status: "ACTIVE",
  });
  const [jsonError, setJsonError] = useState("");

  const fetchSchema = async (productId) => {
    if (!productId) return;
    setLoading(true);
    setSchema(null);
    try {
      const data = await adminService.getRiskInputSchema(productId);
      setSchema(data);
    } catch {
      setSchema(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (selectedProductId) fetchSchema(selectedProductId);
  }, [selectedProductId]);

  useEffect(() => {
    if (products.length > 0 && !selectedProductId) {
      setSelectedProductId(products[0].productId);
    }
  }, [products]);

  const openCreate = () => {
    setEditing(false);
    setForm({ schemaVersion: "1.0.0", schemaDefinition: DEFAULT_SCHEMA, status: "ACTIVE" });
    setJsonError("");
    setModalOpen(true);
  };

  const openEdit = () => {
    if (!schema) return;
    setEditing(true);
    setForm({
      schemaVersion: schema.schemaVersion,
      schemaDefinition: JSON.stringify(schema.schemaDefinition, null, 2),
      status: schema.status,
    });
    setJsonError("");
    setModalOpen(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    let parsedDef;
    try {
      parsedDef = JSON.parse(form.schemaDefinition);
    } catch {
      setJsonError("Schema Definition không phải JSON hợp lệ.");
      return;
    }
    setJsonError("");
    setSubmitting(true);
    try {
      const payload = { schemaVersion: form.schemaVersion, schemaDefinition: parsedDef, status: form.status };
      if (editing && schema) {
        await adminService.updateRiskInputSchema(schema.schemaId, payload);
        triggerToast("Cập nhật Risk Input Schema thành công!");
      } else {
        await adminService.createRiskInputSchema(selectedProductId, payload);
        triggerToast("Tạo Risk Input Schema thành công!");
      }
      setModalOpen(false);
      fetchSchema(selectedProductId);
    } catch {
      triggerToast("Thao tác thất bại. Vui lòng kiểm tra lại dữ liệu.", true);
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleStatus = async () => {
    if (!schema) return;
    const newStatus = schema.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    try {
      await adminService.updateRiskInputSchemaStatus(schema.schemaId, newStatus);
      triggerToast(`Đã chuyển trạng thái schema sang ${newStatus}!`);
      fetchSchema(selectedProductId);
    } catch {
      triggerToast("Cập nhật trạng thái thất bại.", true);
    }
  };

  const getProductTypeLabel = (type) => {
    const map = { HEALTH: "Sức khỏe", LIFE: "Nhân thọ", VEHICLE: "Phương tiện" };
    return map[type] || type;
  };

  const selectedProduct = products.find(p => p.productId === selectedProductId);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center flex-wrap gap-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Quản lý Risk Input Schema</h2>
          <p className="text-gray-500 text-sm mt-0.5">
            Định nghĩa JSON schema cho các trường đầu vào tính toán rủi ro của từng sản phẩm
          </p>
        </div>
        {schema ? (
          <button
            onClick={openEdit}
            className="flex items-center space-x-2 px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-sm font-semibold transition-all shadow-sm"
          >
            <Edit2 className="w-4 h-4" />
            <span>Cập nhật Schema</span>
          </button>
        ) : selectedProductId && !loading ? (
          <button
            onClick={openCreate}
            className="flex items-center space-x-2 px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-sm font-semibold transition-all shadow-sm"
          >
            <Plus className="w-4 h-4" />
            <span>Tạo Schema</span>
          </button>
        ) : null}
      </div>

      {/* Product Selector */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="space-y-1">
          <label className="text-xs font-bold text-gray-400 uppercase tracking-wider">Chọn sản phẩm bảo hiểm</label>
          <p className="text-xs text-gray-500">Mỗi sản phẩm chỉ có một Risk Input Schema tương ứng</p>
        </div>
        <select
          value={selectedProductId}
          onChange={(e) => setSelectedProductId(e.target.value)}
          className="bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-indigo-500 min-w-[280px]"
        >
          {products.length === 0 ? (
            <option value="">(Không có sản phẩm)</option>
          ) : (
            products.map((p) => (
              <option key={p.productId} value={p.productId}>
                [{getProductTypeLabel(p.productType)}] {p.name}
              </option>
            ))
          )}
        </select>
      </div>

      {/* Schema Display */}
      {loading ? (
        <div className="flex flex-col items-center py-20 bg-white rounded-2xl border border-gray-100">
          <Loader2 className="w-10 h-10 text-indigo-600 animate-spin" />
          <span className="text-sm text-gray-400 mt-3">Đang tải schema...</span>
        </div>
      ) : !schema ? (
        <div className="py-20 text-center bg-white rounded-2xl border border-gray-100 space-y-4">
          <FileCode2 className="w-12 h-12 text-gray-200 mx-auto" />
          <p className="text-gray-400 text-sm">Sản phẩm này chưa có Risk Input Schema.</p>
          {selectedProductId && (
            <button
              onClick={openCreate}
              className="px-4 py-2 border border-indigo-100 text-indigo-600 bg-indigo-50/50 hover:bg-indigo-50 rounded-xl text-xs font-bold transition-all"
            >
              Tạo Schema đầu tiên
            </button>
          )}
        </div>
      ) : (
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
          {/* Schema Info Header */}
          <div className="p-6 border-b border-gray-100 flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center space-x-4">
              <div className="w-12 h-12 rounded-xl bg-indigo-50 flex items-center justify-center">
                <FileCode2 className="w-6 h-6 text-indigo-600" />
              </div>
              <div>
                <div className="flex items-center space-x-2">
                  <h3 className="font-bold text-gray-900">Schema v{schema.schemaVersion}</h3>
                  <span className="text-[10px] font-mono bg-gray-100 text-gray-500 px-2 py-0.5 rounded">
                    {schema.schemaId?.slice(0, 8)}...
                  </span>
                </div>
                <p className="text-xs text-gray-400 mt-0.5">
                  Sản phẩm: <strong className="text-gray-600">{selectedProduct?.name}</strong>
                  {" · "}Cập nhật: {new Date(schema.updatedAt).toLocaleDateString("vi-VN")}
                </p>
              </div>
            </div>
            <div className="flex items-center space-x-3">
              <button
                onClick={handleToggleStatus}
                className={`px-3 py-1.5 text-xs font-bold rounded-lg border transition-all ${
                  schema.status === "ACTIVE"
                    ? "bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100"
                    : "bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100"
                }`}
              >
                {schema.status === "ACTIVE" ? "✓ ĐANG HOẠT ĐỘNG" : "TẠM DỪNG"}
              </button>
            </div>
          </div>

          {/* Schema Definition Preview */}
          <div className="p-6">
            <button
              onClick={() => setSchemaExpanded(!schemaExpanded)}
              className="w-full flex items-center justify-between text-sm font-bold text-gray-700 mb-3 hover:text-indigo-600 transition-colors"
            >
              <span className="flex items-center space-x-2">
                <Tag className="w-4 h-4" />
                <span>Schema Definition (JSON)</span>
              </span>
              {schemaExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
            </button>
            {schemaExpanded && (
              <pre className="bg-gray-950 text-emerald-400 rounded-xl p-5 text-xs overflow-x-auto leading-relaxed font-mono max-h-96">
                {JSON.stringify(schema.schemaDefinition, null, 2)}
              </pre>
            )}
            {!schemaExpanded && (
              <div
                onClick={() => setSchemaExpanded(true)}
                className="bg-gray-50 border border-dashed border-gray-200 rounded-xl p-4 text-xs text-gray-400 cursor-pointer hover:bg-gray-100 transition-colors text-center"
              >
                Click để xem nội dung JSON Schema...
              </div>
            )}
          </div>
        </div>
      )}

      {/* Modal */}
      {modalOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-2xl border border-gray-100 overflow-hidden">
            <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-gray-50">
              <h3 className="text-lg font-bold text-gray-900">
                {editing ? "Cập nhật Risk Input Schema" : "Tạo Risk Input Schema mới"}
              </h3>
              <button onClick={() => setModalOpen(false)} className="p-1.5 hover:bg-gray-200 rounded-lg transition-colors">
                <X className="w-5 h-5 text-gray-400" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Phiên bản Schema</label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: 1.0.0"
                  value={form.schemaVersion}
                  onChange={(e) => setForm({ ...form, schemaVersion: e.target.value })}
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">
                  Schema Definition (JSON)
                </label>
                <textarea
                  rows={12}
                  required
                  value={form.schemaDefinition}
                  onChange={(e) => { setForm({ ...form, schemaDefinition: e.target.value }); setJsonError(""); }}
                  className={`w-full bg-gray-950 text-emerald-400 border rounded-xl px-4 py-3 text-xs font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500 leading-relaxed ${jsonError ? "border-red-400" : "border-gray-700"}`}
                  spellCheck={false}
                />
                {jsonError && (
                  <p className="text-xs text-red-500 mt-1 flex items-center space-x-1">
                    <AlertCircle className="w-3.5 h-3.5" />
                    <span>{jsonError}</span>
                  </p>
                )}
              </div>

              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Trạng thái</label>
                <select
                  value={form.status}
                  onChange={(e) => setForm({ ...form, status: e.target.value })}
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-indigo-500"
                >
                  <option value="ACTIVE">ACTIVE (Đang sử dụng)</option>
                  <option value="INACTIVE">INACTIVE (Tạm dừng)</option>
                </select>
              </div>

              <div className="flex space-x-3 pt-2">
                <button type="button" onClick={() => setModalOpen(false)}
                  className="flex-1 py-2.5 border border-gray-200 hover:bg-gray-50 rounded-xl text-sm font-bold text-gray-600 transition-all">
                  Đóng
                </button>
                <button type="submit" disabled={submitting}
                  className="flex-1 py-2.5 bg-indigo-600 hover:bg-indigo-700 disabled:bg-indigo-300 text-white rounded-xl text-sm font-bold transition-all flex items-center justify-center space-x-2 shadow-sm">
                  {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                  <span>{editing ? "Lưu thay đổi" : "Tạo Schema"}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
