import React, { useEffect, useState, useCallback } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Shield, HeartPulse, Car, Search, Filter, ChevronLeft,
  Loader2, CheckCircle, AlertCircle, X, ArrowRight,
  Clock, Users, Layers, BadgeCheck, ChevronDown, ArrowUp, ArrowDown
} from "lucide-react";
import { customerService } from "../../services/customerService";
import { authService } from "../../services/authService";
import Pagination from "../../components/Pagination";

// ── helpers ──────────────────────────────────────────────────────────────────
const PRODUCT_TYPE_OPTIONS = [
  { value: "", label: "Tất cả loại" },
  { value: "HEALTH", label: "Sức khỏe" },
  { value: "LIFE", label: "Nhân thọ" },
  { value: "VEHICLE", label: "Phương tiện" },
];

function getTypeLabel(type) {
  const map = { HEALTH: "Sức khỏe", LIFE: "Nhân thọ", VEHICLE: "Phương tiện" };
  return map[type] || type;
}

function getTypeIcon(type) {
  switch (type) {
    case "HEALTH": return <HeartPulse className="w-5 h-5" />;
    case "VEHICLE": return <Car className="w-5 h-5" />;
    default: return <Shield className="w-5 h-5" />;
  }
}

function getTypeGradient(type) {
  switch (type) {
    case "HEALTH": return "from-blue-500 to-cyan-400";
    case "VEHICLE": return "from-emerald-500 to-teal-400";
    case "LIFE": return "from-violet-500 to-purple-400";
    default: return "from-slate-500 to-gray-400";
  }
}

function getTypeBg(type) {
  switch (type) {
    case "HEALTH": return "bg-blue-50 text-blue-600";
    case "VEHICLE": return "bg-emerald-50 text-emerald-600";
    case "LIFE": return "bg-violet-50 text-violet-600";
    default: return "bg-gray-50 text-gray-600";
  }
}

const FALLBACK_RISK_FIELDS = [
  { apiKey: "bmi", type: "NUMBER", label: "Chỉ số BMI", required: true, min: 10, max: 80, step: 0.1, baseline: 22 },
  { apiKey: "children", type: "INTEGER", label: "Số người phụ thuộc", required: false, min: 0, max: 20, step: 1, defaultValue: 0 },
  { apiKey: "smoker", type: "ENUM", label: "Hút thuốc lá", required: true, options: ["no", "yes"], optionLabels: { no: "Không", yes: "Có" }, baseline: "no" },
  { apiKey: "bloodPressure", type: "NUMBER", label: "Huyết áp tâm thu", required: true, min: 70, max: 220, step: 1, baseline: 120 },
  { apiKey: "exerciseFrequency", type: "ENUM", label: "Tần suất tập thể dục", required: true, options: ["daily", "weekly", "rarely", "none"], optionLabels: { daily: "Hàng ngày", weekly: "Hàng tuần", rarely: "Hiếm khi", none: "Không tập" }, baseline: "weekly" },
  { apiKey: "preExistingCondition", type: "BOOLEAN", label: "Bệnh nền", required: true, baseline: false, trueLabel: "Có", falseLabel: "Không" },
  { apiKey: "occupationCode", type: "TEXT", label: "Nghề nghiệp", required: false, defaultValue: "OFFICE_WORKER" },
  { apiKey: "occupationRisk", type: "TEXT", label: "Mức rủi ro nghề nghiệp", required: false, defaultValue: "low" },
];

const SELF_OPTION_ID = "__SELF__";

function formatMoney(value) {
  return `${Number(value || 0).toLocaleString("vi-VN", { maximumFractionDigits: 0 })}đ`;
}

function formatSignedMoney(value) {
  const amount = Number(value || 0);
  const prefix = amount > 0 ? "+" : amount < 0 ? "-" : "";
  return `${prefix}${formatMoney(Math.abs(amount))}`;
}

function extractItems(response) {
  return response?.items || response?.content || response || [];
}

function calculateAge(dateOfBirth) {
  if (!dateOfBirth) return "";
  const dob = new Date(dateOfBirth);
  if (Number.isNaN(dob.getTime())) return "";
  const today = new Date();
  let age = today.getFullYear() - dob.getFullYear();
  const monthDiff = today.getMonth() - dob.getMonth();
  if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < dob.getDate())) age -= 1;
  return age;
}

function normalizeGender(gender) {
  const lower = String(gender || "").toLowerCase();
  if (lower.startsWith("f")) return "female";
  return "male";
}

function getRiskFields(schema) {
  const schemaFields = Array.isArray(schema?.schemaDefinition?.fields)
    ? schema.schemaDefinition.fields
    : FALLBACK_RISK_FIELDS;
  const fields = schemaFields.map((field) => ({
    ...field,
    apiKey: field.apiKey || field.name,
  })).filter((field) => field.apiKey);

  if (!fields.some((field) => field.apiKey === "occupationCode")) {
    fields.push(FALLBACK_RISK_FIELDS.find((field) => field.apiKey === "occupationCode"));
  }
  if (!fields.some((field) => field.apiKey === "occupationRisk")) {
    fields.push(FALLBACK_RISK_FIELDS.find((field) => field.apiKey === "occupationRisk"));
  }
  return fields;
}

function defaultValueForField(field) {
  if (field.defaultValue !== undefined) return field.defaultValue;
  if (field.baseline !== undefined) return field.baseline;
  if (field.type === "BOOLEAN") return false;
  if (field.type === "ENUM") return field.options?.[0] || "";
  if (field.type === "NUMBER" || field.type === "INTEGER") return "";
  return "";
}

function buildRiskProfile(fields, insuredPerson) {
  return fields.reduce((acc, field) => {
    if (field.apiKey === "age") acc[field.apiKey] = calculateAge(insuredPerson?.dateOfBirth);
    else if (field.apiKey === "sex" || field.apiKey === "gender") acc[field.apiKey] = normalizeGender(insuredPerson?.gender);
    else acc[field.apiKey] = defaultValueForField(field);
    return acc;
  }, {});
}

function applyDefaultOccupation(riskProfile, occupationMappings) {
  if (!occupationMappings.length || riskProfile.occupationCode) return riskProfile;
  const defaultOccupation = occupationMappings[0];
  return {
    ...riskProfile,
    occupationCode: defaultOccupation.occupationCode,
    occupationRisk: normalizeOccupationRiskForProfile(defaultOccupation.riskLevel),
  };
}

function coerceRiskProfile(fields, form) {
  return fields.reduce((acc, field) => {
    const value = form[field.apiKey];
    if (field.type === "NUMBER") acc[field.apiKey] = value === "" ? null : Number(value);
    else if (field.type === "INTEGER") acc[field.apiKey] = value === "" ? null : parseInt(value, 10);
    else acc[field.apiKey] = value;
    return acc;
  }, {});
}

function relationLabel(value) {
  const map = {
    SELF: "Bản thân",
    SPOUSE: "Vợ/chồng",
    CHILD: "Con",
    PARENT: "Cha/mẹ",
    FAMILY: "Người thân",
    OTHER: "Khác",
  };
  return map[value] || value || "Người thân";
}

function hasContractForProduct(contracts, insuredPersonId, productId) {
  return contracts.some((contract) =>
    contract.insuredPersonId === insuredPersonId &&
    contract.productId === productId
  );
}

function formatOccupationRiskLevel(value) {
  const labels = {
    LOW: "Thấp",
    MODERATE: "Trung bình",
    HIGH: "Cao",
    low: "Thấp",
    moderate: "Trung bình",
    high: "Cao",
  };
  return labels[value] || value || "Chưa xác định";
}

function normalizeOccupationRiskForProfile(value) {
  return String(value || "").toLowerCase();
}

function formatReadonlyRiskValue(field, value) {
  if (field.apiKey === "occupationRisk") return formatOccupationRiskLevel(value);
  if (field.apiKey === "sex" || field.apiKey === "gender") {
    const lower = String(value || "").toLowerCase();
    if (lower === "female") return "Nữ";
    if (lower === "male") return "Nam";
  }
  return value || "Chưa có dữ liệu";
}

function toDisplayList(value) {
  if (!value) return [];
  if (Array.isArray(value)) return value;
  if (typeof value === "object") {
    if (Array.isArray(value.topRiskFactors)) return value.topRiskFactors;
    return Object.entries(value).map(([key, itemValue]) => ({ factor: key, value: itemValue }));
  }
  return [value];
}

function formatFeatureName(value) {
  const key = String(value || "").trim();
  const labels = {
    age: "Tuổi",
    sex: "Giới tính",
    gender: "Giới tính",
    bmi: "Chỉ số BMI",
    children: "Số người phụ thuộc",
    smoker: "Hút thuốc",
    bloodPressure: "Huyết áp",
    exerciseFrequency: "Tần suất vận động",
    preExistingCondition: "Bệnh nền",
    occupationRisk: "Rủi ro nghề nghiệp",
    occupationCode: "Nghề nghiệp",
    pastClaimCount: "Số lần bồi thường trước đây",
    past_claim_count: "Số lần bồi thường trước đây",
    totalPastClaimAmount: "Tổng tiền bồi thường trước đây",
    total_past_claim_amount: "Tổng tiền bồi thường trước đây",
    claimFreeYears: "Số năm không bồi thường",
    claim_free_years: "Số năm không bồi thường",
    recencyWeightedClaimScore: "Điểm bồi thường gần đây",
    recency_weighted_claim_score: "Điểm bồi thường gần đây",
    prevCostClaimsYear: "Chi phí bồi thường năm trước",
    prev_cost_claims_year: "Chi phí bồi thường năm trước",
    prevNMedicalServices: "Số lần dùng dịch vụ y tế năm trước",
    prev_n_medical_services: "Số lần dùng dịch vụ y tế năm trước",
    prevHadClaimOrService: "Có bồi thường/dịch vụ năm trước",
    prev_had_claim_or_service: "Có bồi thường/dịch vụ năm trước",
    claimFreePreviousYear: "Không bồi thường năm trước",
    claim_free_previous_year: "Không bồi thường năm trước",
  };
  if (labels[key]) return labels[key];
  return key
    .replace(/_/g, " ")
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

function formatImpact(impact) {
  const normalized = String(impact || "").toLowerCase();
  if (normalized === "increase") {
    return { label: "Làm tăng phí", className: "bg-red-50 text-red-700 border-red-100" };
  }
  if (normalized === "decrease") {
    return { label: "Giúp giảm phí", className: "bg-emerald-50 text-emerald-700 border-emerald-100" };
  }
  return { label: "Ít ảnh hưởng", className: "bg-gray-50 text-gray-600 border-gray-100" };
}

function formatRiskLevelLabel(value) {
  const normalized = String(value || "").toUpperCase();
  if (normalized === "LOW") return "Thấp";
  if (normalized === "HIGH") return "Cao";
  if (normalized === "MODERATE" || normalized === "MEDIUM") return "Trung bình";
  if (normalized === "STANDARD") return "Tiêu chuẩn";
  return value || "Chưa xác định";
}

function formatExplanationModel(model) {
  const normalized = String(model || "").toLowerCase();
  if (normalized === "portfolio") {
    return { label: "Lịch sử bảo hiểm", className: "bg-amber-50 text-amber-700 border-amber-100" };
  }
  if (normalized === "health") {
    return { label: "Hồ sơ sức khỏe", className: "bg-blue-50 text-blue-700 border-blue-100" };
  }
  return { label: "Mô hình", className: "bg-gray-50 text-gray-600 border-gray-100" };
}

function normalizeHealthFactorTitle(feature, rawContribution, impact) {
  const key = String(feature || "").trim();
  const decreases = rawContribution < 0 || String(impact || "").toLowerCase() === "decrease";
  const labels = {
    smoker: decreases ? "Không hút thuốc" : "Có hút thuốc",
    bmi: decreases ? "BMI ở mức phù hợp" : "BMI cần lưu ý",
    age: "Độ tuổi hiện tại",
    children: "Số người phụ thuộc",
    bloodPressure: decreases ? "Huyết áp ở mức phù hợp" : "Huyết áp cần lưu ý",
    exerciseFrequency: decreases ? "Tần suất vận động tốt" : "Tần suất vận động thấp",
    preExistingCondition: decreases ? "Không ghi nhận bệnh nền" : "Có bệnh nền",
    occupationRisk: decreases ? "Nghề nghiệp rủi ro thấp" : "Rủi ro nghề nghiệp",
  };
  return labels[key] || formatFeatureName(feature);
}

function normalizeExplanationItem(item) {
  if (item == null) {
    return {
      title: "Không có dữ liệu",
      impact: formatImpact("neutral"),
      detail: "",
      contribution: "",
      model: null,
    };
  }
  if (typeof item === "string" || typeof item === "number" || typeof item === "boolean") {
    return {
      title: String(item),
      impact: formatImpact("neutral"),
      detail: "",
      contribution: "",
      model: null,
    };
  }

  const feature = item.sourceFeature || item.feature || item.factor || item.name || item.key || "Yếu tố";
  const rawContribution = Number(item.contribution ?? item.value ?? item.score ?? 0);
  const modelKey = String(item.model || "").toLowerCase();
  return {
    title: modelKey === "health"
      ? normalizeHealthFactorTitle(feature, Number.isNaN(rawContribution) ? 0 : rawContribution, item.impact || item.direction || item.effect || item.trend)
      : formatFeatureName(feature),
    impact: formatImpact(item.impact || item.direction || item.effect || item.trend),
    detail: item.readableReason || item.reason || "",
    contribution: "",
    rawContribution: Number.isNaN(rawContribution) ? 0 : rawContribution,
    modelKey,
    model: formatExplanationModel(item.model),
  };
}

function selectVisibleHealthFactors(items, limit = 3) {
  return items
    .filter((item) => item.modelKey !== "portfolio")
    .sort((a, b) => Math.abs(b.rawContribution || 0) - Math.abs(a.rawContribution || 0))
    .slice(0, limit);
}

function getImpactTone(amount) {
  const absAmount = Math.abs(Number(amount || 0));
  if (absAmount < 50000) return "nhẹ";
  if (absAmount < 300000) return "vừa";
  return "nhiều";
}

function formatImpactBadge(amount) {
  const value = Number(amount || 0);
  if (Math.abs(value) < 1) {
    return { label: "Trung tính", className: "bg-gray-50 text-gray-600 border-gray-100" };
  }
  const tone = getImpactTone(value);
  if (value > 0) {
    return { label: `Tăng ${tone}`, className: "bg-red-50 text-red-700 border-red-100" };
  }
  return { label: `Giảm ${tone}`, className: "bg-emerald-50 text-emerald-700 border-emerald-100" };
}

function formatQualitativeFactorBadge(item) {
  const increases = item?.rawContribution > 0 || item?.impact?.label === "Làm tăng phí";
  if (increases) {
    return { label: "Bất lợi", className: "bg-red-50 text-red-700 border-red-100" };
  }
  return { label: "Tích cực", className: "bg-emerald-50 text-emerald-700 border-emerald-100" };
}

function getReadableFactorReason(item) {
  if (item?.modelKey === "portfolio") {
    return "Lịch sử bảo hiểm và sử dụng dịch vụ y tế ảnh hưởng đến điều chỉnh phí.";
  }
  const title = item?.title || "";
  const lower = String(title || "").toLowerCase();
  const decreases = item?.rawContribution < 0 || item?.impact?.label === "Giúp giảm phí";
  if (lower.includes("không hút thuốc")) return "Giúp giảm mức rủi ro sức khỏe.";
  if (lower.includes("có hút thuốc")) return "Làm tăng mức rủi ro sức khỏe.";
  if (lower.includes("nghề nghiệp")) return "Công việc có mức độ rủi ro cao hơn.";
  if (lower.includes("bmi")) return decreases ? "Chỉ số BMI của bạn nằm trong vùng phù hợp." : "Chỉ số BMI làm tăng rủi ro sức khỏe.";
  if (lower.includes("tuổi")) return decreases ? "Nhóm tuổi của bạn có rủi ro dự kiến thấp hơn." : "Độ tuổi làm thay đổi mức rủi ro.";
  if (lower.includes("bồi thường") || lower.includes("dịch vụ y tế")) return "Lịch sử bảo hiểm và sử dụng dịch vụ y tế ảnh hưởng đến điều chỉnh phí.";
  if (lower.includes("huyết áp")) return "Huyết áp làm thay đổi nguy cơ sức khỏe.";
  if (lower.includes("bệnh nền")) return "Bệnh nền làm thay đổi nguy cơ sức khỏe.";
  return decreases ? "Yếu tố này giúp giảm rủi ro trong mô hình." : "Yếu tố này làm tăng rủi ro trong mô hình.";
}

// ── ProductCard ───────────────────────────────────────────────────────────────
function ProductCard({ product, onSelect }) {
  return (
    <div
      onClick={() => onSelect(product)}
      className="bg-white rounded-2xl border border-gray-100 shadow-sm hover:shadow-lg hover:-translate-y-0.5 transition-all cursor-pointer group overflow-hidden"
    >
      {/* Top image / gradient banner */}
      <div className={`relative h-36 bg-gradient-to-br ${getTypeGradient(product.productType)} flex items-center justify-center`}>
        {product.imageUrl ? (
          <img src={product.imageUrl} alt={product.name} className="absolute inset-0 w-full h-full object-cover opacity-80" />
        ) : (
          <div className="text-white/80">
            {React.cloneElement(getTypeIcon(product.productType), { className: "w-14 h-14" })}
          </div>
        )}
        {/* Type badge */}
        <span className="absolute top-3 left-3 bg-white/90 backdrop-blur-sm text-xs font-bold px-2.5 py-1 rounded-lg text-gray-700">
          {getTypeLabel(product.productType)}
        </span>
        {product.status === "ACTIVE" && (
          <span className="absolute top-3 right-3 bg-emerald-500 text-white text-[10px] font-bold px-2 py-0.5 rounded-full">
            Đang bán
          </span>
        )}
      </div>

      {/* Body */}
      <div className="p-5 space-y-3">
        <h3 className="font-bold text-gray-900 text-base leading-snug group-hover:text-blue-600 transition-colors">
          {product.name}
        </h3>
        <p className="text-xs text-gray-500 line-clamp-2 leading-relaxed">
          {product.description || "Sản phẩm bảo hiểm chất lượng cao với quyền lợi toàn diện."}
        </p>

        <div className="flex items-center justify-between pt-1">
          <div className={`flex items-center space-x-1 text-xs font-semibold px-2.5 py-1 rounded-lg ${getTypeBg(product.productType)}`}>
            {getTypeIcon(product.productType)}
            <span>{getTypeLabel(product.productType)}</span>
          </div>
          <span className="text-xs font-bold text-blue-600 flex items-center space-x-0.5 group-hover:space-x-1.5 transition-all">
            <span>Xem chi tiết</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </span>
        </div>
      </div>
    </div>
  );
}

// ── CoveragePlanCard ──────────────────────────────────────────────────────────
function CoveragePlanCard({ plan, onRegister }) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm hover:shadow-md transition-all overflow-hidden">
      <div className="p-5 space-y-3">
        <div className="flex items-start justify-between">
          <div>
            <span className="text-[10px] font-bold text-blue-600 uppercase tracking-wider">Gói bảo hiểm</span>
            <h4 className="font-bold text-gray-900 mt-0.5">{plan.planName}</h4>
          </div>
          <span className={`text-[10px] font-bold px-2 py-1 rounded-lg border ${
            plan.status === "ACTIVE"
              ? "bg-emerald-50 text-emerald-700 border-emerald-100"
              : "bg-gray-50 text-gray-400 border-gray-100"
          }`}>
            {plan.status === "ACTIVE" ? "Đang áp dụng" : "Tạm dừng"}
          </span>
        </div>

        {/* Key numbers */}
        <div className="grid grid-cols-2 gap-3">
          <div className="bg-blue-50 rounded-xl p-3">
            <p className="text-[10px] font-semibold text-blue-400 uppercase tracking-wide">Tỷ lệ điều chỉnh</p>
            <p className="text-lg font-extrabold text-blue-700 mt-0.5">
              {(Number(plan.loadingRate || 0) * 100).toLocaleString("vi-VN")}
              <span className="text-xs font-normal text-blue-400">%</span>
            </p>
          </div>
          <div className="bg-gray-50 rounded-xl p-3">
            <p className="text-[10px] font-semibold text-gray-400 uppercase tracking-wide">Số tiền BH tối đa</p>
            <p className="text-base font-extrabold text-gray-800 mt-0.5">
              {Number(plan.sumInsured || 0).toLocaleString("vi-VN")}
              <span className="text-xs font-normal text-gray-400">đ</span>
            </p>
          </div>
        </div>

        {/* Description toggle */}
        {plan.description && (
          <button
            onClick={() => setExpanded(!expanded)}
            className="w-full text-left text-xs text-gray-500 flex items-center justify-between hover:text-gray-700 transition-colors"
          >
            <span className="font-semibold">Chi tiết quyền lợi</span>
            <ChevronDown className={`w-3.5 h-3.5 transition-transform ${expanded ? "rotate-180" : ""}`} />
          </button>
        )}
        {expanded && plan.description && (
          <p className="text-xs text-gray-500 leading-relaxed bg-gray-50 rounded-xl p-3">
            {plan.description}
          </p>
        )}

        {/* Register CTA */}
        {plan.status === "ACTIVE" && (
          <button
            onClick={() => onRegister(plan)}
            className="w-full py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-bold transition-all shadow-sm flex items-center justify-center space-x-2 mt-1"
          >
            <BadgeCheck className="w-4 h-4" />
            <span>Đăng ký gói này</span>
          </button>
        )}
      </div>
    </div>
  );
}

// ── ProductDetailPage ─────────────────────────────────────────────────────────
function ProductDetailPage({ productId, onBack, onRegisterSuccess }) {
  const [product, setProduct] = useState(null);
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [plansLoading, setPlansLoading] = useState(false);
  const [registerPlan, setRegisterPlan] = useState(null);
  const [toast, setToast] = useState(null);

  useEffect(() => {
    if (!productId) return undefined;
    let alive = true;
    async function loadProductDetail() {
      await Promise.resolve();
      if (!alive) return;
      setLoading(true);
      try {
        const [productData, planData] = await Promise.all([
          customerService.getProductById(productId),
          customerService.getCoveragePlansByProduct(productId, "ACTIVE"),
        ]);
        if (!alive) return;
        setProduct(productData);
        setPlans(planData);
      } catch {
        if (!alive) return;
        setProduct(null);
        setPlans([]);
      } finally {
        if (alive) {
          setLoading(false);
          setPlansLoading(false);
        }
      }
    }
    loadProductDetail();
    return () => { alive = false; };
  }, [productId]);

  const showToast = (msg, isError = false) => {
    setToast({ msg, isError });
    setTimeout(() => setToast(null), 4000);
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center py-24">
        <Loader2 className="w-10 h-10 text-blue-500 animate-spin" />
        <span className="text-sm text-gray-400 mt-3">Đang tải chi tiết sản phẩm...</span>
      </div>
    );
  }

  if (!product) {
    return (
      <div className="space-y-5">
        <button onClick={onBack} className="inline-flex items-center gap-2 text-sm font-bold text-blue-600 hover:text-blue-700">
          <ChevronLeft className="w-4 h-4" />
          <span>Quay lại danh sách</span>
        </button>
        <div className="rounded-2xl border border-red-100 bg-red-50 p-8 text-center">
          <AlertCircle className="w-10 h-10 text-red-400 mx-auto" />
          <p className="mt-3 text-sm font-semibold text-red-700">Không tìm thấy sản phẩm hoặc sản phẩm không còn khả dụng.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {toast && (
        <div className={`rounded-xl p-4 flex items-center space-x-3 text-sm shadow-sm ${
          toast.isError
            ? "bg-red-50 border border-red-100 text-red-800"
            : "bg-emerald-50 border border-emerald-100 text-emerald-800"
        }`}>
          {toast.isError
            ? <AlertCircle className="w-5 h-5 text-red-500 shrink-0" />
            : <CheckCircle className="w-5 h-5 text-emerald-500 shrink-0" />}
          <span className="font-medium">{toast.msg}</span>
        </div>
      )}

      <button onClick={onBack} className="inline-flex items-center gap-2 text-sm font-bold text-blue-600 hover:text-blue-700">
        <ChevronLeft className="w-4 h-4" />
        <span>Quay lại danh sách</span>
      </button>

      <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
        <div className={`relative min-h-64 bg-gradient-to-br ${getTypeGradient(product.productType)}`}>
          {product.imageUrl && (
            <img src={product.imageUrl} alt={product.name} className="absolute inset-0 w-full h-full object-cover opacity-75" />
          )}
          <div className="relative z-10 p-6 md:p-8 min-h-64 flex flex-col justify-end">
            <div className="mb-5">
              <span className="bg-white/90 text-xs font-bold px-2.5 py-1 rounded-lg text-gray-700">
                {getTypeLabel(product.productType)}
              </span>
            </div>
            <h2 className="text-3xl font-extrabold text-white tracking-tight">{product.name}</h2>
            <p className="text-white/85 text-sm mt-3 max-w-3xl leading-relaxed">{product.description}</p>
          </div>
        </div>

        <div className="px-6 py-5 grid grid-cols-1 sm:grid-cols-3 gap-3 border-b border-gray-100">
          {[
            { icon: <Shield className="w-4 h-4" />, label: "Bảo vệ toàn diện", color: "text-blue-600 bg-blue-50" },
            { icon: <Clock className="w-4 h-4" />, label: "Bồi thường nhanh", color: "text-emerald-600 bg-emerald-50" },
            { icon: <Users className="w-4 h-4" />, label: "Hỗ trợ 24/7", color: "text-violet-600 bg-violet-50" },
          ].map((f) => (
            <div key={f.label} className="flex items-center gap-3 rounded-xl bg-gray-50 p-3">
              <div className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 ${f.color}`}>{f.icon}</div>
              <span className="text-sm font-semibold text-gray-600 leading-tight">{f.label}</span>
            </div>
          ))}
        </div>

        <div className="px-6 py-6 space-y-4">
          <div className="flex items-center space-x-2">
            <Layers className="w-4 h-4 text-gray-400" />
            <h3 className="font-bold text-gray-800">Các gói bảo hiểm</h3>
          </div>

          {plansLoading ? (
            <div className="flex flex-col items-center py-12">
              <Loader2 className="w-8 h-8 text-blue-500 animate-spin" />
              <span className="text-xs text-gray-400 mt-2">Đang tải gói bảo hiểm...</span>
            </div>
          ) : plans.length === 0 ? (
            <div className="py-12 text-center text-gray-400 space-y-2">
              <Layers className="w-10 h-10 text-gray-200 mx-auto" />
              <p className="text-sm">Sản phẩm này chưa có gói nào đang áp dụng.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
              {plans.map(plan => (
                <CoveragePlanCard key={plan.coveragePlanId} plan={plan} onRegister={setRegisterPlan} />
              ))}
            </div>
          )}
        </div>
      </div>

      {registerPlan && (
        <QuoteFlowModal
          plan={registerPlan}
          product={product}
          onClose={() => setRegisterPlan(null)}
          onSuccess={(contract) => {
            showToast(`Đã tạo hợp đồng ${contract.contractId} từ báo giá.`);
            if (onRegisterSuccess) onRegisterSuccess();
          }}
        />
      )}
    </div>
  );
}

// ── QuoteFlowModal ───────────────────────────────────────────────────────────
function RiskFieldInput({ field, value, onChange, occupationMappings, riskProfile }) {
  const label = field.apiKey === "occupationCode"
    ? "Nghề nghiệp"
    : field.label || field.labelEn || field.apiKey;

  if (field.apiKey === "occupationCode" && occupationMappings.length > 0) {
    return (
      <label className="space-y-1.5">
        <span className="text-xs font-bold text-gray-500">{label}</span>
        <select
          value={value ?? ""}
          required={field.required}
          onChange={(e) => {
            const occupationCode = e.target.value;
            const selectedOccupation = occupationMappings.find((item) => item.occupationCode === occupationCode);
            onChange("occupationCode", occupationCode);
            if (selectedOccupation?.riskLevel) {
              onChange("occupationRisk", normalizeOccupationRiskForProfile(selectedOccupation.riskLevel));
            }
          }}
          className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none"
        >
          <option value="">Chọn nghề nghiệp</option>
          {occupationMappings.map((occupation) => (
            <option key={occupation.mappingId || occupation.occupationCode} value={occupation.occupationCode}>
              {occupation.occupationCode} - {occupation.occupationName} ({formatOccupationRiskLevel(occupation.riskLevel)})
            </option>
          ))}
        </select>
      </label>
    );
  }

  if (field.apiKey === "age" || field.apiKey === "sex" || field.apiKey === "gender" || field.apiKey === "occupationRisk") {
    const selectedOccupation = occupationMappings.find((item) => item.occupationCode === riskProfile.occupationCode);
    return (
      <div className="space-y-1.5">
        <span className="text-xs font-bold text-gray-500">{label}</span>
        <div className="w-full rounded-xl border border-gray-200 bg-gray-50 px-3 py-2.5 text-sm font-semibold text-gray-700">
          {formatReadonlyRiskValue(field, value)}
        </div>
        {field.apiKey === "occupationRisk" && selectedOccupation && (
          <p className="text-[11px] text-gray-400">
            Tự động theo nghề nghiệp: {selectedOccupation.occupationName}
          </p>
        )}
      </div>
    );
  }

  if (field.type === "BOOLEAN") {
    return (
      <label className="flex items-center justify-between rounded-xl border border-gray-200 px-3 py-2.5">
        <span className="text-sm font-semibold text-gray-700">{label}</span>
        <input
          type="checkbox"
          checked={Boolean(value)}
          onChange={(e) => onChange(field.apiKey, e.target.checked)}
          className="h-4 w-4 accent-blue-600"
        />
      </label>
    );
  }

  if (field.type === "ENUM" && Array.isArray(field.options)) {
    return (
      <label className="space-y-1.5">
        <span className="text-xs font-bold text-gray-500">{label}</span>
        <select
          value={value ?? ""}
          required={field.required}
          onChange={(e) => onChange(field.apiKey, e.target.value)}
          className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none"
        >
          {field.options.map((option) => (
            <option key={option} value={option}>
              {field.optionLabels?.[option] || option}
            </option>
          ))}
        </select>
      </label>
    );
  }

  const isNumeric = field.type === "NUMBER" || field.type === "INTEGER";
  return (
    <label className="space-y-1.5">
      <span className="text-xs font-bold text-gray-500">{label}</span>
      <input
        type={isNumeric ? "number" : "text"}
        value={value ?? ""}
        required={field.required}
        min={field.min}
        max={field.max}
        step={field.step || (field.type === "INTEGER" ? 1 : undefined)}
        onChange={(e) => onChange(field.apiKey, e.target.value)}
        className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none"
      />
    </label>
  );
}

function FactorRow({ factor, expanded, onToggle }) {
  const increases = factor.impact === "increase";
  const colorClass = increases ? "bg-red-650" : "bg-emerald-600";
  const pct = factor.contributionPct || 0;

  return (
    <div className="border border-gray-100 rounded-xl overflow-hidden shadow-sm transition-all hover:border-gray-250">
      <button
        type="button"
        onClick={onToggle}
        className="w-full p-3.5 text-left flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-white hover:bg-gray-50/50 transition-colors"
      >
        <div className="space-y-1.5 flex-1">
          <div className="flex items-center gap-2">
            <span className="font-semibold text-gray-905 text-sm">{factor.displayName}</span>
            <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded border ${
              increases ? "bg-red-50 text-red-700 border-red-100" : "bg-emerald-50 text-emerald-700 border-emerald-100"
            }`}>
              {increases ? "Tăng" : "Giảm"}
            </span>
          </div>
          
          <div className="flex items-center gap-3">
            <div className="w-full bg-gray-150 rounded-full h-2.5 max-w-[160px]">
              <div className={`${colorClass} h-2.5 rounded-full`} style={{ width: `${pct}%` }}></div>
            </div>
            <span className="text-xs font-bold text-gray-800 w-12 text-right">{pct}%</span>
          </div>
        </div>

        <div className="flex items-center gap-2 justify-end self-end sm:self-center">
          <span className="text-xs text-gray-400">Xem chi tiết</span>
          <ChevronDown className={`w-4 h-4 text-gray-450 transition-transform ${expanded ? "rotate-180" : ""}`} />
        </div>
      </button>

      {expanded && (
        <div className="bg-gray-50/50 border-t border-gray-100 p-4 space-y-3 text-xs sm:text-sm text-gray-700">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div className="bg-white border border-gray-100 rounded-xl p-3">
              <span className="text-[10px] font-bold text-gray-400 uppercase">Giá trị hiện tại</span>
              <p className="font-bold text-gray-800 mt-1">{factor.currentValue}</p>
            </div>
            <div className="bg-white border border-gray-100 rounded-xl p-3">
              <span className="text-[10px] font-bold text-gray-400 uppercase">Tỷ trọng ảnh hưởng</span>
              <p className="font-bold text-gray-800 mt-1">{pct}%</p>
            </div>
          </div>
          {factor.readableReason && (
            <p className="text-xs text-gray-600 bg-white border border-gray-100/50 rounded-xl p-3 leading-relaxed">
              {factor.readableReason}
            </p>
          )}
        </div>
      )}
    </div>
  );
}

function QuoteExplanationPanel({ quote, planName, onEdit, onContinue, submitting }) {
  const [activeTab, setActiveTab] = useState("frequency");
  const [expandedFactor, setExpandedFactor] = useState(null);
  const [techExpanded, setTechExpanded] = useState(false);

  const explanation = quote?.explanation;
  const topRiskFactors = toDisplayList(explanation?.topRiskFactors).slice(0, 5);

  const freqFactors = explanation?.frequencyExplanation?.topFactors || [];
  const sumFreqPct = freqFactors.reduce((sum, f) => sum + (f.contributionPct || 0), 0);
  const otherFreqPct = Math.max(0, 100 - sumFreqPct);

  const sevFactors = explanation?.severityExplanation?.topFactors || [];
  const sumSevPct = sevFactors.reduce((sum, f) => sum + (f.contributionPct || 0), 0);
  const otherSevPct = Math.max(0, 100 - sumSevPct);

  const finalPremium = Number(quote.finalPremium || 0);
  const purePremium = Number(quote.purePremium || 0);
  const loadingAmount = finalPremium - purePremium;
  const sumInsured = Number(quote.sumInsured || 0);

  const formatVnd = (val) => {
    return `${Number(val || 0).toLocaleString("vi-VN", { maximumFractionDigits: 0 }).replace(/,/g, ".")} VNĐ`;
  };

  const formatNumber = (val) => {
    const num = Number(val || 0);
    return num.toLocaleString("vi-VN", { minimumFractionDigits: 0, maximumFractionDigits: 2 });
  };

  function getCleanReason(item) {
    let reason = item.readableReason || "";
    const prefix = `${item.displayName} (${item.currentValue})`;
    if (reason.startsWith(prefix)) {
      reason = reason.substring(prefix.length).trim();
      reason = reason.charAt(0).toUpperCase() + reason.slice(1);
    }
    if (reason === "Ảnh hưởng đến đánh giá rủi ro.") {
      const freq = item.frequencyImpact;
      const sev = item.severityImpact;
      if (freq === "increase" && sev === "increase") return "Làm tăng cả khả năng phát sinh và chi phí bồi thường.";
      if (freq === "decrease" && sev === "decrease") return "Giúp giảm cả khả năng phát sinh và chi phí bồi thường.";
      if (freq === "increase") return "Chủ yếu làm tăng khả năng phát sinh bồi thường.";
      if (sev === "increase") return "Chủ yếu làm tăng chi phí trung bình mỗi lần bồi thường.";
      if (freq === "decrease") return "Giúp giảm khả năng phát sinh bồi thường.";
      if (sev === "decrease") return "Giúp giảm chi phí trung bình mỗi lần bồi thường.";
    }
    return reason;
  }

  let formattedExpiry = "";
  if (quote.expiredAt) {
    try {
      const expDate = new Date(quote.expiredAt);
      formattedExpiry = expDate.toLocaleDateString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
        year: "numeric"
      });
    } catch (e) {
      formattedExpiry = quote.expiredAt;
    }
  }

  return (
    <div className="space-y-6">
      {/* 1. Kết quả báo giá */}
      <div className="bg-slate-900 text-white rounded-2xl p-6 shadow-md space-y-6">
        <div>
          <span className="text-[10px] font-bold text-blue-400 uppercase tracking-widest">PHÍ BẢO HIỂM ĐỀ XUẤT</span>
          <h2 className="text-3xl font-extrabold mt-1.5 tracking-tight">
            {formatVnd(finalPremium)} <span className="text-sm font-normal text-slate-400">/ năm</span>
          </h2>
        </div>
        
        <div className="border-t border-slate-800 pt-4 space-y-3.5 text-sm">
          <div className="flex justify-between items-center text-slate-350">
            <span>Phí thuần</span>
            <span className="font-semibold text-slate-100">{formatVnd(purePremium)}</span>
          </div>
          <div className="flex justify-between items-center text-slate-350">
            <span>Phần điều chỉnh của gói</span>
            <span className="font-semibold text-slate-100">{formatVnd(loadingAmount)}</span>
          </div>
          <div className="flex justify-between items-center text-slate-350">
            <span>Hạn mức bảo hiểm</span>
            <span className="font-semibold text-slate-100">{formatVnd(sumInsured)}</span>
          </div>
          <div className="border-t border-slate-800/60 pt-3.5 flex justify-between items-center text-xs text-slate-400">
            <span>Báo giá có hiệu lực đến</span>
            <span className="font-medium text-slate-300">{formattedExpiry}</span>
          </div>
        </div>
      </div>

      {/* 2. Các yếu tố ảnh hưởng chính */}
      <div className="bg-white rounded-2xl border border-gray-150 p-6 space-y-4 shadow-sm">
        <h3 className="font-bold text-gray-900 text-base">Các yếu tố ảnh hưởng chính</h3>
        {topRiskFactors.length > 0 ? (
          <div className="space-y-4">
            {topRiskFactors.map((item, index) => {
              const increases = item.frequencyImpact === "increase" || item.severityImpact === "increase";
              const Icon = increases ? ArrowUp : ArrowDown;
              const cleanReason = getCleanReason(item);
              
              let affectedText = "";
              if (item.affectedModels && item.affectedModels.length > 0) {
                affectedText = "Ảnh hưởng đến: " + item.affectedModels.map(m => m === "frequency" ? "Tần suất" : "Chi phí").join(" · ");
              }

              return (
                <div key={index} className="flex gap-3.5 items-start">
                  <span className={`mt-0.5 flex h-7 w-7 items-center justify-center rounded-lg shrink-0 ${increases ? "bg-red-50 text-red-650" : "bg-emerald-50 text-emerald-650"}`}>
                    <Icon className="h-4 w-4 font-bold" />
                  </span>
                  <div className="space-y-1">
                    <h4 className="font-bold text-gray-900 text-sm">{item.displayName}</h4>
                    <p className="text-xs sm:text-sm text-gray-550 leading-relaxed">{cleanReason}</p>
                    {affectedText && (
                      <span className="inline-block text-[10px] font-bold text-slate-500 uppercase bg-slate-50 border border-slate-100 rounded px-1.5 py-0.5 mt-1">
                        {affectedText}
                      </span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className="rounded-xl bg-emerald-50/50 border border-emerald-100 p-4 text-xs sm:text-sm text-emerald-800 flex items-start gap-2.5">
            <CheckCircle className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
            <p className="leading-relaxed">
              <span className="font-bold">Hồ sơ ở mức tiêu chuẩn:</span> Không phát hiện yếu tố rủi ro bất thường nào ảnh hưởng đáng kể đến việc tăng hoặc giảm phí bảo hiểm dự kiến của bạn.
            </p>
          </div>
        )}
      </div>

      {/* 3. Phân tích chi tiết */}
      {explanation && (
        <div className="bg-white rounded-2xl border border-gray-150 p-6 space-y-5 shadow-sm">
          <h3 className="font-bold text-gray-900 text-base">Phân tích chi tiết</h3>
          
          <div className="flex bg-gray-50 p-1 rounded-xl border border-gray-100">
            <button
              type="button"
              onClick={() => setActiveTab("frequency")}
              className={`flex-1 py-2 text-xs sm:text-sm font-bold rounded-lg transition-all ${
                activeTab === "frequency"
                  ? "bg-white text-gray-950 shadow-sm border border-gray-100"
                  : "text-gray-500 hover:text-gray-950"
              }`}
            >
              Tần suất sử dụng/bồi thường
            </button>
            <button
              type="button"
              onClick={() => setActiveTab("severity")}
              className={`flex-1 py-2 text-xs sm:text-sm font-bold rounded-lg transition-all ${
                activeTab === "severity"
                  ? "bg-white text-gray-950 shadow-sm border border-gray-100"
                  : "text-gray-500 hover:text-gray-950"
              }`}
            >
              Chi phí mỗi lần bồi thường
            </button>
          </div>

          {activeTab === "frequency" ? (
            <div className="space-y-5">
              <div className="bg-slate-50 border border-slate-100 rounded-xl p-3.5">
                <span className="text-[10px] font-bold text-slate-450 uppercase">Tần suất sử dụng/bồi thường dự đoán</span>
                <p className="text-base sm:text-lg font-extrabold text-slate-800 mt-1">
                  {formatNumber(explanation.frequencyExplanation?.predictedValue)} <span className="text-xs font-normal text-slate-500">lần/năm</span>
                </p>
              </div>

              <p className="text-[11px] text-gray-500 bg-slate-50 border border-slate-100/60 rounded-xl p-3 leading-relaxed">
                Chú thích: Đây là số lần sử dụng dịch vụ hoặc phát sinh yêu cầu bồi thường kỳ vọng theo dữ liệu mô hình.
              </p>

              <div className="space-y-3">
                <p className="text-[10px] font-bold uppercase text-slate-450 tracking-wider">Các yếu tố ảnh hưởng</p>
                <div className="space-y-3">
                  {explanation.frequencyExplanation?.topFactors && explanation.frequencyExplanation.topFactors.length > 0 ? (
                    explanation.frequencyExplanation.topFactors.map((factor, index) => (
                      <FactorRow
                        key={index}
                        factor={factor}
                        expanded={expandedFactor === `freq-${factor.feature}`}
                        onToggle={() => setExpandedFactor(expandedFactor === `freq-${factor.feature}` ? null : `freq-${factor.feature}`)}
                      />
                    ))
                  ) : (
                    <p className="text-xs text-gray-500 italic bg-gray-50 rounded-xl p-3 border border-gray-100/50">
                      Không có yếu tố nào ảnh hưởng đáng kể đến tần suất sử dụng/bồi thường của bạn.
                    </p>
                  )}
                  
                  {explanation.frequencyExplanation?.topFactors && explanation.frequencyExplanation.topFactors.length > 0 && otherFreqPct > 0.05 && (
                    <div className="border border-dashed border-gray-250/70 rounded-xl p-3 bg-gray-50/50 flex items-center justify-between text-xs text-gray-550">
                      <span className="font-semibold text-gray-450">Các yếu tố khác (Chỉ hiển thị các yếu tố ảnh hưởng lớn nhất)</span>
                      <span className="font-bold text-gray-800">{otherFreqPct.toFixed(2)}%</span>
                    </div>
                  )}
                </div>
              </div>
            </div>
          ) : (
            <div className="space-y-5">
              <div className="bg-slate-50 border border-slate-100 rounded-xl p-3.5">
                <span className="text-[10px] font-bold text-slate-450 uppercase">Chi phí dự kiến mỗi lần</span>
                <p className="text-base sm:text-lg font-extrabold text-slate-800 mt-1">
                  {formatVnd(explanation.severityExplanation?.predictedValue)}
                </p>
              </div>

              <div className="space-y-3">
                <p className="text-[10px] font-bold uppercase text-slate-450 tracking-wider">Các yếu tố ảnh hưởng</p>
                <div className="space-y-3">
                  {explanation.severityExplanation?.topFactors && explanation.severityExplanation.topFactors.length > 0 ? (
                    explanation.severityExplanation.topFactors.map((factor, index) => (
                      <FactorRow
                        key={index}
                        factor={factor}
                        expanded={expandedFactor === `sev-${factor.feature}`}
                        onToggle={() => setExpandedFactor(expandedFactor === `sev-${factor.feature}` ? null : `sev-${factor.feature}`)}
                      />
                    ))
                  ) : (
                    <p className="text-xs text-gray-500 italic bg-gray-50 rounded-xl p-3 border border-gray-100/50">
                      Không có yếu tố nào ảnh hưởng đáng kể đến chi phí bồi thường trung bình của bạn.
                    </p>
                  )}

                  {explanation.severityExplanation?.topFactors && explanation.severityExplanation.topFactors.length > 0 && otherSevPct > 0.05 && (
                    <div className="border border-dashed border-gray-250/70 rounded-xl p-3 bg-gray-50/50 flex items-center justify-between text-xs text-gray-550">
                      <span className="font-semibold text-gray-450">Các yếu tố khác (Chỉ hiển thị các yếu tố ảnh hưởng lớn nhất)</span>
                      <span className="font-bold text-gray-800">{otherSevPct.toFixed(2)}%</span>
                    </div>
                  )}
                </div>
              </div>
            </div>
          )}

          <div className="border-t border-gray-100 pt-4">
            <p className="text-[11px] sm:text-xs text-gray-550 leading-relaxed bg-gray-50 rounded-xl p-3 border border-gray-100/50">
              Phần trọng số thể hiện tỷ lệ đóng góp của từng yếu tố vào giá trị dự đoán, không phải phần trăm tăng giảm phí trực tiếp.
            </p>
          </div>
        </div>
      )}

      {/* 4. Thông tin kỹ thuật mô hình */}
      <div className="border border-gray-150 rounded-2xl overflow-hidden bg-white shadow-sm">
        <button
          type="button"
          onClick={() => setTechExpanded(!techExpanded)}
          className="w-full px-5 py-4 flex items-center justify-between text-left hover:bg-gray-50 transition-colors"
        >
          <span className="font-bold text-gray-800 text-sm flex items-center gap-2">
            <Shield className="w-4 h-4 text-gray-450" />
            <span>Thông tin kỹ thuật mô hình</span>
          </span>
          <ChevronDown className={`w-4 h-4 text-gray-400 transition-transform ${techExpanded ? "rotate-180" : ""}`} />
        </button>
        {techExpanded && (
          <div className="px-5 pb-5 border-t border-gray-50 pt-4 divide-y divide-gray-100 text-xs sm:text-sm text-gray-600 space-y-2.5">
            <div className="flex justify-between items-center py-2">
              <span>Phiên bản Frequency Model</span>
              <span className="font-bold text-gray-850 bg-gray-50 border border-gray-100 rounded px-1.5 py-0.5">{quote.frequencyModelVersion || "11"}</span>
            </div>
            <div className="flex justify-between items-center py-2">
              <span>Phiên bản Severity Model</span>
              <span className="font-bold text-gray-850 bg-gray-50 border border-gray-100 rounded px-1.5 py-0.5">{quote.severityModelVersion || "1"}</span>
            </div>
            <div className="flex justify-between items-center py-2">
              <span>Phương pháp giải thích</span>
              <span className="font-bold text-gray-850 bg-gray-50 border border-gray-100 rounded px-1.5 py-0.5">
                {explanation?.frequencyExplanation?.method === "counterfactual" ? "Counterfactual Delta" : "SHAP"}
              </span>
            </div>
            <div className="flex justify-between items-center py-2">
              <span>Trạng thái giải thích</span>
              <span className="font-bold text-emerald-700 bg-emerald-50 border border-emerald-100 rounded px-1.5 py-0.5">Khả dụng</span>
            </div>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-2">
        <button onClick={onEdit} className="py-2.5 border border-gray-250 hover:bg-gray-50 rounded-xl text-sm font-bold text-gray-750">
          Chỉnh sửa hồ sơ
        </button>
        <button onClick={onContinue} disabled={submitting} className="py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white rounded-xl text-sm font-bold flex items-center justify-center gap-2 shadow-sm">
          {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
          <span>Tiếp tục mua</span>
        </button>
      </div>
    </div>
  );
}

function QuoteFlowModal({ plan, product, onClose, onSuccess }) {
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [schema, setSchema] = useState(null);
  const [occupationMappings, setOccupationMappings] = useState([]);
  const [insuredPersons, setInsuredPersons] = useState([]);
  const [selfOption, setSelfOption] = useState(null);
  const [selectedInsuredPersonId, setSelectedInsuredPersonId] = useState("");
  const [riskProfile, setRiskProfile] = useState({});
  const [quote, setQuote] = useState(null);
  const [contract, setContract] = useState(null);
  const [payment, setPayment] = useState(null);
  const [newInsured, setNewInsured] = useState({
    fullName: "",
    dateOfBirth: "",
    gender: "MALE",
    identityNumber: "",
    relationshipToOwner: "FAMILY",
  });

  const productId = product?.productId;
  const fields = getRiskFields(schema);

  useEffect(() => {
    if (!productId) return undefined;
    let alive = true;
    async function loadQuoteInputs() {
      setLoading(true);
      setError("");
      try {
        const [schemaResult, insuredResult, profileResult, contractsResult] = await Promise.allSettled([
          customerService.getRiskInputSchemaByProduct(productId),
          customerService.getMyInsuredPersons({ page: 0, size: 50, status: "ACTIVE" }),
          authService.getUserProfile(),
          customerService.getContracts(),
        ]);
        const occupationMappings = await customerService.getOccupationRiskMappings(productId, "ACTIVE");

        const nextSchema = schemaResult.status === "fulfilled" ? schemaResult.value : null;
        const peopleResult = insuredResult.status === "fulfilled" ? extractItems(insuredResult.value) : [];
        const profile = profileResult.status === "fulfilled" ? profileResult.value : null;
        const contracts = contractsResult.status === "fulfilled" ? contractsResult.value || [] : [];
        const eligiblePeople = peopleResult.filter((person) =>
          !hasContractForProduct(contracts, person.insuredPersonId, productId)
        );
        const selfPerson = peopleResult.find((person) => person.relationshipToOwner === "SELF");
        const canUseSelfRecord = selfPerson && !hasContractForProduct(contracts, selfPerson.insuredPersonId, productId);
        const canCreateSelf = !selfPerson && profile?.fullName;
        const nextSelfOption = canCreateSelf
          ? {
              insuredPersonId: SELF_OPTION_ID,
              fullName: profile.fullName,
              dateOfBirth: profile.dateOfBirth,
              gender: profile.gender,
              identityNumber: profile.identityNumber,
              relationshipToOwner: "SELF",
              isVirtualSelf: true,
            }
          : null;
        const selectablePeople = canUseSelfRecord
          ? [selfPerson, ...eligiblePeople.filter((person) => person.insuredPersonId !== selfPerson.insuredPersonId)]
          : eligiblePeople;
        const firstPerson = nextSelfOption || selectablePeople[0];
        if (!alive) return;

        setSchema(nextSchema);
        setOccupationMappings(occupationMappings);
        setSelfOption(nextSelfOption);
        setInsuredPersons(selectablePeople);
        setSelectedInsuredPersonId(firstPerson?.insuredPersonId || "");
        setRiskProfile(applyDefaultOccupation(buildRiskProfile(getRiskFields(nextSchema), firstPerson), occupationMappings));
      } catch {
        if (alive) setError("Không thể tải dữ liệu báo giá. Vui lòng thử lại.");
      } finally {
        if (alive) setLoading(false);
      }
    }

    loadQuoteInputs();
    return () => { alive = false; };
  }, [productId]);

  const handleInsuredPersonChange = (insuredPersonId) => {
    const person = insuredPersonId === SELF_OPTION_ID
      ? selfOption
      : insuredPersons.find((item) => item.insuredPersonId === insuredPersonId);
    setSelectedInsuredPersonId(insuredPersonId);
    if (person) setRiskProfile(applyDefaultOccupation(buildRiskProfile(fields, person), occupationMappings));
  };

  const updateRiskValue = (key, value) => {
    setRiskProfile((current) => ({ ...current, [key]: value }));
  };

  const handleCreateInsuredPerson = async (event) => {
    event.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      const created = await customerService.createInsuredPerson({
        ...newInsured,
        dateOfBirth: newInsured.dateOfBirth || null,
        linkedUserProfileId: null,
      });
      const nextPeople = [created, ...insuredPersons];
      setInsuredPersons(nextPeople);
      setSelectedInsuredPersonId(created.insuredPersonId);
      setRiskProfile(applyDefaultOccupation(buildRiskProfile(fields, created), occupationMappings));
      setNewInsured({ fullName: "", dateOfBirth: "", gender: "MALE", identityNumber: "", relationshipToOwner: "FAMILY" });
    } catch (err) {
      setError(err.response?.data?.message || err.message || "Không thể thêm người được bảo hiểm.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleCreateQuote = async (event) => {
    event.preventDefault();
    if (!selectedInsuredPersonId) {
      setError("Vui lòng chọn hoặc thêm người được bảo hiểm.");
      return;
    }
    setSubmitting(true);
    setError("");
    try {
      let insuredPersonId = selectedInsuredPersonId;
      if (selectedInsuredPersonId === SELF_OPTION_ID && selfOption) {
        const createdSelf = await customerService.createInsuredPerson({
          fullName: selfOption.fullName,
          dateOfBirth: selfOption.dateOfBirth || null,
          gender: selfOption.gender || "MALE",
          identityNumber: selfOption.identityNumber || "",
          relationshipToOwner: "SELF",
          linkedUserProfileId: null,
        });
        insuredPersonId = createdSelf.insuredPersonId;
        setSelfOption(null);
        setInsuredPersons((current) => [createdSelf, ...current]);
        setSelectedInsuredPersonId(createdSelf.insuredPersonId);
      }
      const createdQuote = await customerService.createQuote({
        insuredPersonId,
        productId: product.productId,
        coveragePlanId: plan.coveragePlanId,
        riskProfile: coerceRiskProfile(fields, riskProfile),
      });
      const quoteDetail = await customerService.getQuoteById(createdQuote.quoteId);
      setQuote(quoteDetail);
    } catch (err) {
      setError(err.response?.data?.message || err.message || "Không thể tạo báo giá.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleIssueContract = async () => {
    if (!quote) return;
    setSubmitting(true);
    setError("");
    try {
      const createdContract = await customerService.createContract({
        quoteId: quote.quoteId,
        insuredPersonId: quote.insuredPersonId,
        productId: quote.productId,
        coveragePlanId: quote.coveragePlanId,
        simulatePaymentResult: "SUCCESS",
      });
      setContract(createdContract);

      if (createdContract.paymentId) {
        const paymentDetail = await customerService.getPaymentById(createdContract.paymentId);
        setPayment(paymentDetail);
      }

      if (onSuccess) onSuccess(createdContract);
    } catch (err) {
      setError(err.response?.data?.message || err.message || "Không thể phát hành hợp đồng từ báo giá.");
    } finally {
      setSubmitting(false);
    }
  };

  if (!plan || !product) return null;

  return (
    <div className="fixed inset-0 z-[60] bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl shadow-xl w-full max-w-3xl max-h-[92vh] border border-gray-100 overflow-hidden flex flex-col">
        <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-gray-50">
          <div>
            <h3 className="font-bold text-gray-900">Báo giá và phát hành hợp đồng</h3>
            <p className="text-xs text-gray-500 mt-1">{product.name} - {plan.planName}</p>
          </div>
          <button onClick={onClose} className="p-1.5 hover:bg-gray-200 rounded-lg transition-colors">
            <X className="w-5 h-5 text-gray-400" />
          </button>
        </div>

        <div className="overflow-y-auto p-6 space-y-5">
          {error && (
            <div className="rounded-xl border border-red-100 bg-red-50 px-4 py-3 text-sm font-medium text-red-700 flex items-center gap-2">
              <AlertCircle className="w-4 h-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="rounded-2xl bg-blue-50/50 border border-blue-100/50 p-4 flex items-center justify-between shadow-sm">
            <div>
              <p className="text-[10px] font-bold uppercase text-blue-500 tracking-wider">Số tiền bảo hiểm tối đa</p>
              <p className="text-xl font-extrabold text-blue-750 mt-1">{formatMoney(plan.sumInsured)}</p>
            </div>
            <Shield className="w-9 h-9 text-blue-400/80" />
          </div>

          {loading ? (
            <div className="flex flex-col items-center py-12">
              <Loader2 className="w-8 h-8 text-blue-500 animate-spin" />
              <span className="text-xs text-gray-400 mt-2">Đang tải dữ liệu báo giá...</span>
            </div>
          ) : contract ? (
            <div className="rounded-2xl border border-emerald-100 bg-emerald-50 p-5 space-y-3">
              <div className="flex items-center gap-2 text-emerald-700">
                <CheckCircle className="w-5 h-5" />
                <h4 className="font-bold">Hợp đồng đã được tạo</h4>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm">
                <p><span className="text-gray-500">Mã hợp đồng:</span> <span className="font-bold text-gray-900">{contract.contractId}</span></p>
                <p><span className="text-gray-500">Trạng thái:</span> <span className="font-bold text-gray-900">{contract.status}</span></p>
                <p><span className="text-gray-500">Phí cuối:</span> <span className="font-bold text-gray-900">{formatMoney(contract.quotedPremium)}</span></p>
                <p><span className="text-gray-500">Thanh toán:</span> <span className="font-bold text-gray-900">{payment?.status || contract.paymentStatus || "Đã khởi tạo"}</span></p>
              </div>
              <button onClick={onClose} className="w-full py-2.5 rounded-xl bg-emerald-600 text-white text-sm font-bold hover:bg-emerald-700">
                Hoàn tất
              </button>
            </div>
          ) : quote ? (
            <div className="space-y-5">
              <QuoteExplanationPanel
                quote={quote}
                planName={plan.planName}
                onEdit={() => setQuote(null)}
                onContinue={handleIssueContract}
                submitting={submitting}
              />
            </div>
          ) : (
            <form onSubmit={handleCreateQuote} className="space-y-5">
              <div className="rounded-2xl border border-gray-100 p-4 space-y-4">
                <div>
                  <h4 className="font-bold text-gray-900">Người được bảo hiểm</h4>
                  <p className="text-xs text-gray-500 mt-0.5">Dữ liệu này được gửi vào PricingService để tạo báo giá.</p>
                </div>
                {(selfOption || insuredPersons.length > 0) && (
                  <select
                    value={selectedInsuredPersonId}
                    onChange={(e) => handleInsuredPersonChange(e.target.value)}
                    className="w-full rounded-xl border border-gray-200 px-3 py-2.5 text-sm focus:border-blue-500 focus:outline-none"
                  >
                    {selfOption && (
                      <option value={SELF_OPTION_ID}>
                        {selfOption.fullName} - Bản thân
                      </option>
                    )}
                    {insuredPersons.map((person) => (
                      <option key={person.insuredPersonId} value={person.insuredPersonId}>
                        {person.fullName} - {relationLabel(person.relationshipToOwner)}
                      </option>
                    ))}
                  </select>
                )}

                {!selfOption && insuredPersons.length === 0 && (
                  <div className="rounded-xl bg-amber-50 border border-amber-100 px-4 py-3 text-sm text-amber-800">
                    Không còn người được bảo hiểm đủ điều kiện cho sản phẩm này. Thêm nhanh một hồ sơ mới để tiếp tục.
                  </div>
                )}

                <div className="grid grid-cols-1 md:grid-cols-6 gap-3">
                  <input value={newInsured.fullName} onChange={(e) => setNewInsured({ ...newInsured, fullName: e.target.value })} placeholder="Họ tên" className="md:col-span-2 rounded-xl border border-gray-200 px-3 py-2.5 text-sm" />
                  <input type="date" value={newInsured.dateOfBirth} onChange={(e) => setNewInsured({ ...newInsured, dateOfBirth: e.target.value })} className="rounded-xl border border-gray-200 px-3 py-2.5 text-sm" />
                  <select value={newInsured.gender} onChange={(e) => setNewInsured({ ...newInsured, gender: e.target.value })} className="rounded-xl border border-gray-200 px-3 py-2.5 text-sm">
                    <option value="MALE">Nam</option>
                    <option value="FEMALE">Nữ</option>
                  </select>
                  <select value={newInsured.relationshipToOwner} onChange={(e) => setNewInsured({ ...newInsured, relationshipToOwner: e.target.value })} className="rounded-xl border border-gray-200 px-3 py-2.5 text-sm">
                    <option value="SPOUSE">Vợ/chồng</option>
                    <option value="CHILD">Con</option>
                    <option value="PARENT">Cha/mẹ</option>
                    <option value="FAMILY">Người thân</option>
                    <option value="OTHER">Khác</option>
                  </select>
                  <button type="button" onClick={handleCreateInsuredPerson} disabled={submitting || !newInsured.fullName || !newInsured.dateOfBirth} className="rounded-xl bg-gray-900 px-3 py-2.5 text-sm font-bold text-white disabled:bg-gray-300">
                    Thêm
                  </button>
                </div>
              </div>

              <div className="rounded-2xl border border-gray-100 p-4 space-y-4">
                <div>
                  <h4 className="font-bold text-gray-900">Thông tin rủi ro</h4>
                  <p className="text-xs text-gray-500 mt-0.5">Form được dựng từ risk input schema của sản phẩm.</p>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {fields.map((field) => (
                    <RiskFieldInput
                      key={field.apiKey}
                      field={field}
                      value={riskProfile[field.apiKey]}
                      onChange={updateRiskValue}
                      occupationMappings={occupationMappings}
                      riskProfile={riskProfile}
                    />
                  ))}
                </div>
              </div>

              <div className="flex gap-3">
                <button type="button" onClick={onClose} className="flex-1 py-2.5 border border-gray-200 hover:bg-gray-50 rounded-xl text-sm font-bold text-gray-600">
                  Hủy
                </button>
                <button type="submit" disabled={submitting || !selectedInsuredPersonId} className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white rounded-xl text-sm font-bold flex items-center justify-center gap-2">
                  {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
                  <span>Tạo báo giá</span>
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}

// ── Main ProductsTab ──────────────────────────────────────────────────────────
export default function ProductsTab({ onRegisterSuccess }) {
  const navigate = useNavigate();
  const { productId } = useParams();

  // List state
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [totalElements, setTotalElements] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  // Filters
  const [typeFilter, setTypeFilter] = useState("");
  const [search, setSearch] = useState("");
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(9);

  const fetchProducts = useCallback(async () => {
    if (productId) return;
    setLoading(true);
    try {
      const res = await customerService.getProducts({
        productType: typeFilter || undefined,
        status: "ACTIVE",
        page: currentPage - 1,
        size: pageSize,
      });
      const items = res?.items || res?.content || [];
      setProducts(items);
      setTotalElements(res?.totalElements ?? items.length);
      setTotalPages(res?.totalPages ?? Math.max(1, Math.ceil((res?.totalElements ?? items.length) / pageSize)));
    } catch {
      setProducts([]);
    } finally {
      setLoading(false);
    }
  }, [productId, typeFilter, currentPage, pageSize]);

  useEffect(() => {
    let alive = true;
    async function loadProducts() {
      await Promise.resolve();
      if (alive) fetchProducts();
    }
    loadProducts();
    return () => { alive = false; };
  }, [fetchProducts]);

  // Client-side search on top of paged results
  const displayed = products.filter(p =>
    !search || p.name.toLowerCase().includes(search.toLowerCase()) ||
    (p.description || "").toLowerCase().includes(search.toLowerCase())
  );

  if (productId) {
    return (
      <ProductDetailPage
        productId={productId}
        onBack={() => navigate("/products")}
        onRegisterSuccess={onRegisterSuccess}
      />
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h2 className="text-2xl font-bold text-gray-900">Sản phẩm bảo hiểm</h2>
        <p className="text-gray-500 text-sm mt-0.5">Khám phá và đăng ký các gói bảo hiểm phù hợp với bạn</p>
      </div>

      {/* Filters */}
      <div className="bg-white rounded-2xl border border-gray-100 p-4 shadow-sm flex flex-wrap gap-3 items-center">
        {/* Search */}
        <div className="flex items-center space-x-2 bg-gray-50 border border-gray-200 rounded-xl px-3 py-2 flex-1 min-w-[200px]">
          <Search className="w-4 h-4 text-gray-400 shrink-0" />
          <input
            type="text"
            placeholder="Tìm kiếm sản phẩm..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setCurrentPage(1);
            }}
            className="bg-transparent text-sm focus:outline-none w-full"
          />
          {search && (
            <button onClick={() => setSearch("")} className="text-gray-300 hover:text-gray-500">
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>

        {/* Type filter chips */}
        <div className="flex items-center space-x-2">
          <Filter className="w-4 h-4 text-gray-400" />
          <div className="flex space-x-1.5">
            {PRODUCT_TYPE_OPTIONS.map(opt => (
              <button
                key={opt.value}
                onClick={() => {
                  setTypeFilter(opt.value);
                  setCurrentPage(1);
                }}
                className={`px-3 py-1.5 rounded-xl text-xs font-bold border transition-all ${
                  typeFilter === opt.value
                    ? "bg-blue-600 text-white border-blue-600 shadow-sm"
                    : "bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100"
                }`}
              >
                {opt.label}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* Grid */}
      {loading ? (
        <div className="flex flex-col items-center py-24">
          <Loader2 className="w-10 h-10 text-blue-500 animate-spin" />
          <span className="text-sm text-gray-400 mt-3">Đang tải sản phẩm...</span>
        </div>
      ) : displayed.length === 0 ? (
        <div className="py-24 text-center space-y-3">
          <Shield className="w-12 h-12 text-gray-200 mx-auto" />
          <p className="text-gray-400 text-sm">Không tìm thấy sản phẩm phù hợp.</p>
          {(search || typeFilter) && (
            <button onClick={() => { setSearch(""); setTypeFilter(""); }}
              className="text-xs font-bold text-blue-600 hover:text-blue-700">
              Xóa bộ lọc
            </button>
          )}
        </div>
      ) : (
        <>
          {/* Result count */}
          <p className="text-xs text-gray-400 font-medium">
            Tìm thấy <span className="font-bold text-gray-600">{search ? displayed.length : totalElements}</span> sản phẩm
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            {displayed.map(p => (
              <ProductCard key={p.productId} product={p} onSelect={(product) => navigate(`/products/${product.productId}`)} />
            ))}
          </div>

          {/* Pagination (only when not searching client-side) */}
          {!search && totalPages > 1 && (
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm">
              <Pagination
                currentPage={currentPage}
                totalPages={totalPages}
                onPageChange={setCurrentPage}
                pageSize={pageSize}
                onPageSizeChange={(s) => { setPageSize(s); setCurrentPage(1); }}
                totalItems={totalElements}
                pageSizeOptions={[6, 9, 18]}
              />
            </div>
          )}
        </>
      )}

    </div>
  );
}
