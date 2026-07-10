import { useCallback, useEffect, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { authService } from "../../services/authService";
import { customerService } from "../../services/customerService";
import ProductsTab from "./ProductsTab";
import InsuredPersonsTab from "./InsuredPersonsTab";
import Pagination from "../../components/Pagination";
import {
  LayoutDashboard,
  FileText,
  Shield,
  HeartPulse,
  CreditCard,
  Bell,
  User,
  LifeBuoy,
  LogOut,
  Menu,
  CheckCircle,
  ArrowRight,
  ChevronRight,
  Car,
  ShieldCheck,
  Send,
  Loader2,
  AlertCircle,
  CalendarDays,
  ReceiptText,
  RefreshCw,
  Archive,
  CheckCheck,
  Inbox,
  ClipboardList,
  Activity
} from "lucide-react";

export default function CustomerDashboard() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();

  // Map pathname to activeTab
  const getActiveTabFromPathname = (pathname) => {
    if (pathname.startsWith("/products")) return "products";
    switch (pathname) {
      case "/contracts": return "contracts";
      case "/insured-persons": return "insured-persons";
      case "/payments": return "payments";
      case "/claims": return "claims";
      case "/notifications": return "notifications";
      case "/profile": return "profile";
      case "/support": return "support";
      default: return "overview";
    }
  };

  const activeTab = getActiveTabFromPathname(location.pathname);

  const handleTabChange = (tabName) => {
    if (tabName === "overview") {
      navigate("/");
    } else {
      navigate(`/${tabName}`);
    }
  };

  // Profile data state
  const [profile, setProfile] = useState(null);
  const [profileLoading, setProfileLoading] = useState(true);
  const [profileError, setProfileError] = useState("");

  // Edit Profile Form State
  const [fullName, setFullName] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [identityNumber, setIdentityNumber] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [gender, setGender] = useState("MALE");
  const [isEditing, setIsEditing] = useState(false);
  const [profileUpdateSuccess, setProfileUpdateSuccess] = useState("");
  const [profileUpdateLoading, setProfileUpdateLoading] = useState(false);

  // Contracts & stats state
  const [contracts, setContracts] = useState([]);
  const [contractsLoading, setContractsLoading] = useState(true);
  const [contractsError, setContractsError] = useState("");
  const [expandedContractIds, setExpandedContractIds] = useState(() => new Set());

  const [claims, setClaims] = useState([]);
  const [claimsLoading, setClaimsLoading] = useState(false);
  const [claimsError, setClaimsError] = useState("");
  const [claimInsuredPersons, setClaimInsuredPersons] = useState([]);
  const [selectedClaimInsuredPersonId, setSelectedClaimInsuredPersonId] = useState("ALL");
  
  // Notifications state
  const [notifications, setNotifications] = useState([]);
  const [unreadNotificationsCount, setUnreadNotificationsCount] = useState(0);
  const [notificationsLoading, setNotificationsLoading] = useState(false);
  const [notificationsError, setNotificationsError] = useState("");
  const [notificationStatusFilter, setNotificationStatusFilter] = useState("");
  const [notificationPage, setNotificationPage] = useState(1);
  const [notificationPageSize, setNotificationPageSize] = useState(10);
  const [notificationTotalPages, setNotificationTotalPages] = useState(1);
  const [notificationTotalItems, setNotificationTotalItems] = useState(0);
  const [notificationActionId, setNotificationActionId] = useState("");

  // Interaction / Modal state
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [paymentSuccessMsg, setPaymentSuccessMsg] = useState("");

  const loadContractsWithQuotes = useCallback(async () => {
    const contractsData = await customerService.getContracts();
    const baseContracts = contractsData || [];

    const enrichedContracts = await Promise.all(baseContracts.map(async (contract) => {
      const [quoteResult, insuredResult, productResult, planResult] = await Promise.allSettled([
        contract.quoteId ? customerService.getQuoteById(contract.quoteId) : Promise.resolve(null),
        contract.insuredPersonId ? customerService.getInsuredPersonById(contract.insuredPersonId) : Promise.resolve(null),
        contract.productId ? customerService.getProductById(contract.productId) : Promise.resolve(null),
        contract.coveragePlanId ? customerService.getCoveragePlanById(contract.coveragePlanId) : Promise.resolve(null),
      ]);

      const enriched = {
        ...contract,
        quote: quoteResult.status === "fulfilled" ? quoteResult.value : null,
        insuredPerson: insuredResult.status === "fulfilled" ? insuredResult.value : null,
        product: productResult.status === "fulfilled" ? productResult.value : null,
        coveragePlan: planResult.status === "fulfilled" ? planResult.value : null,
      };

      if (quoteResult.status === "rejected") {
        console.error("Failed to load quote detail for contract", contract.contractId, quoteResult.reason);
        enriched.quoteLoadError = true;
      }
      return enriched;
    }));

    return enrichedContracts;
  }, []);

  const toggleContractExpanded = (contractId) => {
    setExpandedContractIds((current) => {
      const next = new Set(current);
      if (next.has(contractId)) next.delete(contractId);
      else next.add(contractId);
      return next;
    });
  };

  const getContractProductName = (contract) => (
    contract.product?.name || contract.quote?.productName || getProductDisplayName(contract.productType)
  );

  const getContractPlanName = (contract) => (
    contract.coveragePlan?.planName || contract.quote?.planName || "Chưa có gói quyền lợi"
  );

  const getContractInsuredName = (contract) => (
    contract.insuredPerson?.fullName || "Chưa tải được người được bảo hiểm"
  );

  const getRiskFactorLabel = (factor) => (
    factor?.displayName || factor?.feature || factor?.factor || factor?.name || "Yếu tố rủi ro"
  );

  const getRiskFactorDetail = (factor) => (
    factor?.readableReason || factor?.reason || factor?.impact || factor?.direction || ""
  );

  const loadNotifications = useCallback(async ({ status = "", page = 1, size = 10 } = {}) => {
    setNotificationsLoading(true);
    setNotificationsError("");
    try {
      const [notificationsData, unreadCount] = await Promise.all([
        customerService.getNotifications({
          status: status || undefined,
          page: Math.max(0, page - 1),
          size
        }),
        customerService.getUnreadNotificationCount()
      ]);

      const items = notificationsData?.content || notificationsData?.items || [];
      setNotifications(items);
      setNotificationTotalItems(notificationsData?.totalElements ?? items.length);
      setNotificationTotalPages(notificationsData?.totalPages ?? Math.max(1, Math.ceil(items.length / size)));
      setUnreadNotificationsCount(unreadCount);
    } catch (error) {
      console.error("Failed to load notifications", error);
      setNotificationsError("Không thể tải thông báo từ máy chủ.");
      setNotifications([]);
      setUnreadNotificationsCount(0);
    } finally {
      setNotificationsLoading(false);
    }
  }, []);

  const loadClaimInsuredPersons = useCallback(async () => {
    try {
      const response = await customerService.getMyInsuredPersons({ page: 0, size: 100 });
      return response?.items || response?.content || response || [];
    } catch (error) {
      console.error("Failed to load insured persons for claim history", error);
      return [];
    }
  }, []);

  // Fetch all initial data
  const fetchData = useCallback(async () => {
    try {
      setProfileLoading(true);
      setContractsLoading(true);
      setContractsError("");

      // 1. Get profile
      const profileData = await authService.getUserProfile();
      setProfile(profileData);
      setFullName(profileData.fullName || "");
      setPhoneNumber(profileData.phoneNumber || "");
      setIdentityNumber(profileData.identityNumber || "");
      setDateOfBirth(profileData.dateOfBirth || "");
      setGender(profileData.gender || "MALE");
      setProfileLoading(false);

      // 2. Get contracts and related pricing quotes
      const contractsData = await loadContractsWithQuotes();
      setContracts(contractsData);
      setContractsLoading(false);

      const insuredPeople = await loadClaimInsuredPersons();
      setClaimInsuredPersons(insuredPeople);
      const insuredPersonIds = insuredPeople.map((person) => person.insuredPersonId).filter(Boolean);
      const claimHistory = insuredPersonIds.length > 0
        ? await customerService.getPolicyHistorySummariesByInsuredPersonIds(insuredPersonIds)
        : [];
      setClaims(claimHistory || []);

      // 3. Get notifications
      await loadNotifications({ status: "", page: 1, size: 10 });
    } catch (error) {
      console.error("Failed to load dashboard data", error);
      setProfileError("Không thể đồng bộ dữ liệu từ máy chủ.");
      setContractsError("Không thể tải danh sách hợp đồng.");
      setProfileLoading(false);
      setContractsLoading(false);
    }
  }, [loadNotifications, loadContractsWithQuotes, loadClaimInsuredPersons]);

  useEffect(() => {
    let alive = true;
    async function loadDashboard() {
      await Promise.resolve();
      if (alive) fetchData();
    }
    loadDashboard();
    return () => { alive = false; };
  }, [fetchData]);

  useEffect(() => {
    let alive = true;
    async function loadActiveNotifications() {
      if (activeTab !== "notifications") return;
      await Promise.resolve();
      if (alive) {
        loadNotifications({
          status: notificationStatusFilter,
          page: notificationPage,
          size: notificationPageSize
        });
      }
    }
    loadActiveNotifications();
    return () => { alive = false; };
  }, [activeTab, notificationStatusFilter, notificationPage, notificationPageSize, loadNotifications]);


  const handleUpdateProfile = async (e) => {
    e.preventDefault();
    try {
      setProfileUpdateLoading(true);
      setProfileUpdateSuccess("");
      setProfileError("");
      const updated = await authService.updateUserProfile({
        fullName,
        phoneNumber,
        identityNumber,
        dateOfBirth: dateOfBirth || null,
        gender
      });
      setProfile(updated);
      setProfileUpdateSuccess("Cập nhật thông tin hồ sơ thành công!");
      setIsEditing(false);
    } catch (err) {
      setProfileError(err.message || "Cập nhật hồ sơ thất bại.");
    } finally {
      setProfileUpdateLoading(false);
    }
  };

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  const handlePayContract = async (contract, simulatePaymentResult = "SUCCESS") => {
    try {
      setPaymentLoading(true);
      setPaymentSuccessMsg("");

      await customerService.payContract(contract.contractId, simulatePaymentResult);
      setPaymentSuccessMsg(
        simulatePaymentResult === "FAILED"
          ? `Đã giả lập thanh toán thất bại cho hợp đồng ${getProductDisplayName(contract.productType)}.`
          : `Thanh toán hợp đồng ${getProductDisplayName(contract.productType)} thành công!`
      );
      
      // Reload contracts to reflect paid status
      const updatedContracts = await loadContractsWithQuotes();
      setContracts(updatedContracts);
    } catch (err) {
      console.error("Payment failed", err);
      alert("Thanh toán thất bại. Vui lòng thử lại.");
    } finally {
      setPaymentLoading(false);
    }
  };

  const reloadContracts = async () => {
    try {
      setContractsLoading(true);
      setContractsError("");
      const updatedContracts = await loadContractsWithQuotes();
      setContracts(updatedContracts);
    } catch (err) {
      console.error("Failed to reload contracts", err);
      setContractsError("Không thể tải danh sách hợp đồng.");
    } finally {
      setContractsLoading(false);
    }
  };

  const reloadClaims = async () => {
    try {
      setClaimsLoading(true);
      setClaimsError("");
      const insuredPeople = await loadClaimInsuredPersons();
      setClaimInsuredPersons(insuredPeople);
      const insuredPersonIds = insuredPeople.map((person) => person.insuredPersonId).filter(Boolean);
      const claimHistory = insuredPersonIds.length > 0
        ? await customerService.getPolicyHistorySummariesByInsuredPersonIds(insuredPersonIds)
        : [];
      setClaims(claimHistory || []);
    } catch (error) {
      console.error("Failed to reload claim history", error);
      setClaimsError("Không thể tải lịch sử bồi thường.");
    } finally {
      setClaimsLoading(false);
    }
  };

  const handleNotificationStatusChange = (status) => {
    setNotificationStatusFilter(status);
    setNotificationPage(1);
  };

  const handleMarkNotificationRead = async (notificationId) => {
    try {
      setNotificationActionId(notificationId);
      await customerService.markNotificationRead(notificationId);
      await loadNotifications({
        status: notificationStatusFilter,
        page: notificationPage,
        size: notificationPageSize
      });
    } catch (error) {
      console.error("Failed to mark notification as read", error);
      setNotificationsError("Không thể đánh dấu thông báo đã đọc.");
    } finally {
      setNotificationActionId("");
    }
  };

  const handleMarkAllNotificationsRead = async () => {
    try {
      setNotificationActionId("read-all");
      await customerService.markAllNotificationsRead();
      await loadNotifications({
        status: notificationStatusFilter,
        page: notificationPage,
        size: notificationPageSize
      });
    } catch (error) {
      console.error("Failed to mark all notifications as read", error);
      setNotificationsError("Không thể đánh dấu tất cả thông báo đã đọc.");
    } finally {
      setNotificationActionId("");
    }
  };

  const handleArchiveNotification = async (notificationId) => {
    try {
      setNotificationActionId(`archive-${notificationId}`);
      await customerService.archiveNotification(notificationId);
      await loadNotifications({
        status: notificationStatusFilter,
        page: notificationPage,
        size: notificationPageSize
      });
    } catch (error) {
      console.error("Failed to archive notification", error);
      setNotificationsError("Không thể lưu trữ thông báo.");
    } finally {
      setNotificationActionId("");
    }
  };


  // Helper mapping productType values
  const getProductDisplayName = (type) => {
    if (!type) return "Sản phẩm bảo hiểm";
    switch (type.toUpperCase()) {
      case "HEALTH": return "An Tâm Sức Khỏe";
      case "CAR":
      case "AUTO": return "An Toàn Xe Hơi";
      case "LIFE":
      case "FAMILY": return "Bảo Vệ Gia Đình";
      default: return type;
    }
  };

  const getProductIcon = (type) => {
    if (!type) return <Shield className="w-5 h-5 text-indigo-600" />;
    switch (type.toUpperCase()) {
      case "HEALTH":
        return <HeartPulse className="w-5 h-5 text-blue-600" />;
      case "CAR":
      case "AUTO":
        return <Car className="w-5 h-5 text-green-600" />;
      default:
        return <Shield className="w-5 h-5 text-amber-600" />;
    }
  };

  const getProductIconBg = (type) => {
    if (!type) return "bg-indigo-50";
    switch (type.toUpperCase()) {
      case "HEALTH": return "bg-blue-50";
      case "CAR":
      case "AUTO": return "bg-emerald-50";
      default: return "bg-amber-50";
    }
  };

  const formatMoney = (value) => {
    if (value === null || value === undefined) return "Chưa có";
    return `${Number(value).toLocaleString("vi-VN")}đ`;
  };

  const formatNumber = (value, digits = 2) => {
    if (value === null || value === undefined || Number.isNaN(Number(value))) return "Chưa có";
    return Number(value).toLocaleString("vi-VN", {
      minimumFractionDigits: 0,
      maximumFractionDigits: digits
    });
  };

  const formatPercent = (value) => {
    if (value === null || value === undefined || Number.isNaN(Number(value))) return "Chưa có";
    return `${(Number(value) * 100).toLocaleString("vi-VN", {
      minimumFractionDigits: 0,
      maximumFractionDigits: 2
    })}%`;
  };

  const formatDate = (value) => value || "Chưa hiệu lực";

  const formatDateTime = (value) => {
    if (!value) return "Gần đây";
    return new Date(value).toLocaleString("vi-VN", {
      hour: "2-digit",
      minute: "2-digit",
      day: "2-digit",
      month: "2-digit",
      year: "numeric"
    });
  };

  const isNotificationUnread = (notification) => (
    notification?.status === "UNREAD" || (!notification?.readAt && notification?.status !== "ARCHIVED")
  );

  const getNotificationStatusLabel = (status) => {
    switch ((status || "").toUpperCase()) {
      case "UNREAD": return "Chưa đọc";
      case "READ": return "Đã đọc";
      case "ARCHIVED": return "Đã lưu trữ";
      default: return status || "Không rõ";
    }
  };

  const getContractStatusLabel = (status) => {
    switch ((status || "").toUpperCase()) {
      case "ACTIVE":
      case "ISSUED":
        return "Đang hiệu lực";
      case "PENDING_PAYMENT":
        return "Chờ thanh toán";
      case "PENDING":
      case "SUBMITTED":
        return "Đang xử lý";
      case "EXPIRED":
        return "Hết hiệu lực";
      case "CANCELLED":
      case "CANCELED":
        return "Đã hủy";
      default:
        return status || "Chưa xác định";
    }
  };

  const getContractStatusClass = (status) => {
    switch ((status || "").toUpperCase()) {
      case "ACTIVE":
      case "ISSUED":
        return "bg-emerald-50 text-emerald-700 border-emerald-100";
      case "PENDING_PAYMENT":
      case "PENDING":
      case "SUBMITTED":
        return "bg-amber-50 text-amber-700 border-amber-100";
      case "EXPIRED":
      case "CANCELLED":
      case "CANCELED":
        return "bg-gray-100 text-gray-600 border-gray-200";
      default:
        return "bg-blue-50 text-blue-700 border-blue-100";
    }
  };

  const getPaymentStatusLabel = (status) => {
    switch ((status || "").toUpperCase()) {
      case "PAID":
      case "SUCCESS":
        return "Đã thanh toán";
      case "UNPAID":
      case "PENDING":
        return "Chưa thanh toán";
      case "FAILED":
        return "Thanh toán lỗi";
      default:
        return status || "Chưa thanh toán";
    }
  };

  const isPaymentCompleted = (status) => {
    const normalized = (status || "").toUpperCase();
    return normalized === "PAID" || normalized === "SUCCESS";
  };

  const getPaymentStatusClass = (status) => (
    isPaymentCompleted(status)
      ? "bg-emerald-50 text-emerald-700 border-emerald-100"
      : "bg-rose-50 text-rose-700 border-rose-100"
  );

  const getRiskLevelLabel = (riskLevel) => {
    switch ((riskLevel || "").toUpperCase()) {
      case "LOW": return "Rủi ro thấp";
      case "MEDIUM": return "Rủi ro trung bình";
      case "HIGH": return "Rủi ro cao";
      default: return riskLevel || "Chưa phân loại";
    }
  };

  const getRiskLevelClass = (riskLevel) => {
    switch ((riskLevel || "").toUpperCase()) {
      case "LOW":
        return "bg-emerald-50 text-emerald-700 border-emerald-100";
      case "MEDIUM":
        return "bg-amber-50 text-amber-700 border-amber-100";
      case "HIGH":
        return "bg-rose-50 text-rose-700 border-rose-100";
      default:
        return "bg-gray-100 text-gray-600 border-gray-200";
    }
  };

  const getTopRiskFactors = (quote) => {
    const factors = quote?.explanation?.topRiskFactors;
    if (Array.isArray(factors)) return factors.slice(0, 3);
    return [];
  };

  const getSeverityLabel = (severity) => {
    switch ((severity || "").toUpperCase()) {
      case "LOW": return "Nhẹ";
      case "MEDIUM": return "Trung bình";
      case "HIGH": return "Nghiêm trọng";
      default: return severity || "Chưa phân loại";
    }
  };

  const getSeverityClass = (severity) => {
    switch ((severity || "").toUpperCase()) {
      case "LOW":
        return "bg-emerald-50 text-emerald-700 border-emerald-100";
      case "MEDIUM":
        return "bg-amber-50 text-amber-700 border-amber-100";
      case "HIGH":
        return "bg-rose-50 text-rose-700 border-rose-100";
      default:
        return "bg-gray-100 text-gray-600 border-gray-200";
    }
  };

  const getClaimInsuredName = (insuredPersonId) => {
    const person = claimInsuredPersons.find((item) => item.insuredPersonId === insuredPersonId);
    return person?.fullName || "Người được bảo hiểm";
  };

  const claimInsuredOptions = [
    ...claimInsuredPersons.map((person) => ({
      insuredPersonId: person.insuredPersonId,
      fullName: person.fullName,
      identityNumber: person.identityNumber,
    })),
    ...claims
      .filter((summary) => summary.insuredPersonId)
      .filter((summary) => !claimInsuredPersons.some((person) => person.insuredPersonId === summary.insuredPersonId))
      .map((summary) => ({
        insuredPersonId: summary.insuredPersonId,
        fullName: `Người được bảo hiểm ${summary.insuredPersonId.slice(0, 8)}`,
        identityNumber: "",
      }))
  ].filter((item, index, arr) => (
    item.insuredPersonId && arr.findIndex((candidate) => candidate.insuredPersonId === item.insuredPersonId) === index
  ));

  const selectedClaims = selectedClaimInsuredPersonId === "ALL"
    ? claims
    : claims.filter((summary) => summary.insuredPersonId === selectedClaimInsuredPersonId);

  // Helpers to calculate stats
  const activeContracts = contracts.filter(c => c.status === "ACTIVE" || c.status === "ISSUED");
  
  // Pending payment contract (usually status PENDING_PAYMENT or unpaid)
  const pendingPaymentContracts = contracts.filter(c => !isPaymentCompleted(c.paymentStatus) && c.status !== "ACTIVE");
  const totalUnpaidAmount = pendingPaymentContracts.reduce((sum, c) => sum + (c.quotedPremium || 0), 0);
  const previousYearClaimCost = selectedClaims.reduce((sum, summary) => sum + Number(summary.prevCostClaimsYear || 0), 0);
  const medicalServiceCount = selectedClaims.reduce((sum, summary) => sum + Number(summary.prevNMedicalServices || 0), 0);
  const getPreviousAverageSeverity = (summary) => {
    const services = Number(summary.prevNMedicalServices || 0);
    if (services <= 0) return 0;
    return Number(summary.prevCostClaimsYear || 0) / services;
  };

  return (
    <div className="min-h-screen bg-[#f8f9fa] flex text-gray-800 font-sans">
      
      {/* 1. LEFT SIDEBAR */}
      <aside className="w-64 bg-white border-r border-gray-200 hidden md:flex flex-col justify-between shrink-0">
        <div>
          {/* Logo Brand */}
          <div className="p-6 border-b border-gray-100 flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-md shadow-blue-200">
              <ShieldCheck className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-xl font-bold tracking-tight text-gray-900">InsuCare</h2>
              <p className="text-[10px] text-gray-400 font-medium tracking-wide uppercase">Bảo hiểm thông minh</p>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="p-4 space-y-1.5">
            <button
              onClick={() => handleTabChange("overview")}
              className={`w-full flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all ${
                activeTab === "overview"
                  ? "bg-blue-50 text-blue-600 shadow-sm"
                  : "text-gray-500 hover:bg-gray-50 hover:text-gray-900"
              }`}
            >
              <LayoutDashboard className="w-5 h-5" />
              <span>Tổng quan</span>
            </button>

            <button
              onClick={() => handleTabChange("contracts")}
              className={`w-full flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all ${
                activeTab === "contracts"
                  ? "bg-blue-50 text-blue-600 shadow-sm"
                  : "text-gray-500 hover:bg-gray-50 hover:text-gray-900"
              }`}
            >
              <FileText className="w-5 h-5" />
              <span>Hợp đồng của tôi</span>
            </button>

            <button
              onClick={() => handleTabChange("products")}
              className={`w-full flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all ${
                activeTab === "products"
                  ? "bg-blue-50 text-blue-600 shadow-sm"
                  : "text-gray-500 hover:bg-gray-50 hover:text-gray-900"
              }`}
            >
              <Shield className="w-5 h-5" />
              <span>Sản phẩm bảo hiểm</span>
            </button>

            <button
              onClick={() => handleTabChange("insured-persons")}
              className={`w-full flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all ${
                activeTab === "insured-persons"
                  ? "bg-blue-50 text-blue-600 shadow-sm"
                  : "text-gray-500 hover:bg-gray-50 hover:text-gray-900"
              }`}
            >
              <User className="w-5 h-5" />
              <span>Người được bảo hiểm</span>
            </button>


            <button
              onClick={() => handleTabChange("payments")}
              className={`w-full flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all ${
                activeTab === "payments"
                  ? "bg-blue-50 text-blue-600 shadow-sm"
                  : "text-gray-500 hover:bg-gray-50 hover:text-gray-900"
              }`}
            >
              <CreditCard className="w-5 h-5" />
              <span>Thanh toán</span>
            </button>

            <button
              onClick={() => handleTabChange("claims")}
              className={`w-full flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all ${
                activeTab === "claims"
                  ? "bg-blue-50 text-blue-600 shadow-sm"
                  : "text-gray-500 hover:bg-gray-50 hover:text-gray-900"
              }`}
            >
              <ClipboardList className="w-5 h-5" />
              <span>Lịch sử bồi thường</span>
            </button>

            <button
              onClick={() => handleTabChange("notifications")}
              className={`w-full flex items-center justify-between px-4 py-3 rounded-xl text-sm font-medium transition-all ${
                activeTab === "notifications"
                  ? "bg-blue-50 text-blue-600 shadow-sm"
                  : "text-gray-500 hover:bg-gray-50 hover:text-gray-900"
              }`}
            >
              <div className="flex items-center space-x-3">
                <Bell className="w-5 h-5" />
                <span>Thông báo</span>
              </div>
              {unreadNotificationsCount > 0 && (
                <span className="bg-red-500 text-white text-xs font-semibold px-2 py-0.5 rounded-full">
                  {unreadNotificationsCount}
                </span>
              )}
            </button>

            <button
              onClick={() => handleTabChange("profile")}
              className={`w-full flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all ${
                activeTab === "profile"
                  ? "bg-blue-50 text-blue-600 shadow-sm"
                  : "text-gray-500 hover:bg-gray-50 hover:text-gray-900"
              }`}
            >
              <User className="w-5 h-5" />
              <span>Hồ sơ của tôi</span>
            </button>

            <button
              onClick={() => handleTabChange("support")}
              className={`w-full flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium transition-all ${
                activeTab === "support"
                  ? "bg-blue-50 text-blue-600 shadow-sm"
                  : "text-gray-500 hover:bg-gray-50 hover:text-gray-900"
              }`}
            >
              <LifeBuoy className="w-5 h-5" />
              <span>Hỗ trợ</span>
            </button>
          </nav>
        </div>

        {/* User profile segment & Log out */}
        <div className="p-4 border-t border-gray-100 space-y-3">
          <button
            onClick={handleLogout}
            className="w-full flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium text-red-500 hover:bg-red-50 transition-all"
          >
            <LogOut className="w-5 h-5" />
            <span>Đăng xuất</span>
          </button>
        </div>
      </aside>

      {/* 2. MAIN WORKSPACE CONTAINER */}
      <div className="flex-1 flex flex-col min-w-0 overflow-y-auto">
        
        {/* Top Header Bar */}
        <header className="bg-white border-b border-gray-200 h-16 px-6 flex items-center justify-between sticky top-0 z-30 shrink-0">
          <div className="flex items-center space-x-3">
            <button className="md:hidden p-2 text-gray-500 hover:bg-gray-50 rounded-lg">
              <Menu className="w-6 h-6" />
            </button>
            <h1 className="text-lg font-bold text-gray-900 md:hidden">InsuCare</h1>
          </div>

          <div className="flex items-center space-x-5">
            {/* Notification Bell */}
            <button 
              onClick={() => handleTabChange("notifications")}
              className="p-2 text-gray-400 hover:text-gray-600 rounded-full hover:bg-gray-50 relative transition-colors"
            >
              <Bell className="w-5 h-5" />
              {unreadNotificationsCount > 0 && (
                <span className="absolute top-1 right-1 w-2.5 h-2.5 bg-red-500 border border-white rounded-full"></span>
              )}
            </button>

            {/* Profile Brief Info */}
            <div className="flex items-center space-x-3 border-l border-gray-100 pl-4">
              <div className="text-right">
                <p className="text-sm font-bold text-gray-900">{profile?.fullName || user?.name || "Khách hàng"}</p>
                <p className="text-[11px] text-gray-400 font-medium">{profile?.email || user?.email}</p>
              </div>
            </div>
          </div>
        </header>

        {/* Content Area */}
        <main className="flex-grow p-6 md:p-8 max-w-6xl w-full mx-auto space-y-8">
          
          {/* Toast / Banner Messages */}
          {paymentSuccessMsg && (
            <div className="bg-emerald-50 border border-emerald-100 rounded-xl p-4 flex items-center space-x-3 text-emerald-800 text-sm shadow-sm animate-fade-in">
              <CheckCircle className="w-5 h-5 text-emerald-600 shrink-0" />
              <span className="font-medium">{paymentSuccessMsg}</span>
            </div>
          )}

          {/* ================================================================ */}
          {/* TAB 1: OVERVIEW */}
          {/* ================================================================ */}
          {activeTab === "overview" && (
            <div className="space-y-6">
              
              {/* Header greeting */}
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                  <h2 className="text-2xl font-bold text-gray-900">Xin chào, {profile?.fullName || user?.name || "Khách hàng"}! 👋</h2>
                  <p className="text-gray-500 text-sm mt-0.5">Chào mừng bạn quay trở lại với InsuCare</p>
                </div>

                <button
                  onClick={() => handleTabChange("products")}
                  className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold transition-all shadow-sm"
                >
                  <Shield className="w-4 h-4" />
                  <span>Đăng ký bảo hiểm</span>
                </button>
              </div>

              {/* 2 Summary Stats Cards */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                
                {/* Stat 1: Active Contracts */}
                <div className="bg-white rounded-2xl border border-gray-100 p-6 flex items-center space-x-4 shadow-sm hover:shadow-md transition-shadow">
                  <div className="w-12 h-12 rounded-xl bg-blue-50 flex items-center justify-center text-blue-600 shrink-0">
                    <FileText className="w-6 h-6" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Hợp đồng đang hiệu lực</p>
                    <h3 className="text-3xl font-extrabold text-gray-900 mt-1">{contractsLoading ? "..." : activeContracts.length}</h3>
                    <p className="text-xs text-gray-500 font-medium">Hợp đồng</p>
                  </div>
                </div>

                {/* Stat 2: Unpaid Premiums */}
                <div className="bg-white rounded-2xl border border-gray-100 p-6 flex items-center space-x-4 shadow-sm hover:shadow-md transition-shadow">
                  <div className="w-12 h-12 rounded-xl bg-emerald-50 flex items-center justify-center text-emerald-600 shrink-0">
                    <CreditCard className="w-6 h-6" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Số tiền chưa thanh toán</p>
                    <h3 className="text-2xl font-extrabold text-gray-900 mt-1">
                      {contractsLoading ? "..." : totalUnpaidAmount.toLocaleString("vi-VN")}đ
                    </h3>
                    <p className="text-xs text-gray-500 font-medium">VND</p>
                  </div>
                </div>

              </div>

              {/* Primary Content Grid */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
                
                {/* Left Side: Contracts Widget */}
                <div className="lg:col-span-2 bg-white rounded-2xl border border-gray-100 shadow-sm p-6 space-y-6">
                  <div className="flex justify-between items-center">
                    <h4 className="text-lg font-bold text-gray-900">Hợp đồng của tôi</h4>
                    <button 
                      onClick={() => handleTabChange("contracts")} 
                      className="text-xs font-bold text-blue-600 hover:text-blue-700 flex items-center space-x-1"
                    >
                      <span>Xem tất cả</span>
                      <ArrowRight className="w-3.5 h-3.5" />
                    </button>
                  </div>

                  {contractsLoading ? (
                    <div className="flex flex-col justify-center items-center py-12">
                      <Loader2 className="w-8 h-8 text-blue-600 animate-spin" />
                      <span className="text-xs text-gray-400 mt-2">Đang tải hợp đồng...</span>
                    </div>
                  ) : contracts.length === 0 ? (
                    <div className="py-12 text-center text-gray-400 space-y-3">
                      <p className="text-sm">Bạn chưa tham gia hợp đồng bảo hiểm nào.</p>
                      <button
                        onClick={() => handleTabChange("products")}
                        className="px-4 py-2 border border-blue-100 text-blue-600 bg-blue-50/50 hover:bg-blue-50 rounded-xl text-xs font-bold transition-all"
                      >
                        Khám phá sản phẩm
                      </button>
                    </div>
                  ) : (
                    <div className="divide-y divide-gray-100">
                      {contracts.slice(0, 3).map((contract) => (
                        <div key={contract.contractId} className="py-4 first:pt-0 last:pb-0 flex justify-between items-center hover:bg-gray-50/50 px-2 rounded-xl transition-all cursor-pointer">
                          <div className="flex items-center space-x-3.5">
                            <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${getProductIconBg(contract.productType)}`}>
                              {getProductIcon(contract.productType)}
                            </div>
                            <div>
                              <h5 className="text-sm font-bold text-gray-900">{getProductDisplayName(contract.productType)}</h5>
                              <p className="text-xs text-gray-400 mt-0.5">Số HĐ: {contract.contractId?.substring(0, 13).toUpperCase()}</p>
                            </div>
                          </div>

                          <div className="flex items-center space-x-6 text-right">
                            <div className="hidden sm:block">
                              <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold ${
                                contract.status === "ACTIVE" || contract.status === "ISSUED"
                                  ? "bg-green-50 text-green-700"
                                  : "bg-amber-50 text-amber-700"
                              }`}>
                                {contract.status === "ACTIVE" || contract.status === "ISSUED" ? "Hiệu lực" : "Chờ thanh toán"}
                              </span>
                              <p className="text-[10px] text-gray-400 mt-1">
                                {contract.effectiveDate} - {contract.expiryDate}
                              </p>
                            </div>

                            <div>
                              <p className="text-sm font-bold text-gray-900">{(contract.quotedPremium || 0).toLocaleString("vi-VN")}đ</p>
                              <p className="text-[10px] text-gray-400 mt-0.5">Phí năm</p>
                            </div>

                            <ChevronRight className="w-4 h-4 text-gray-300" />
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Right Side Widgets */}
                <div className="space-y-6">
                  
                  {/* Widget 1: Upcoming Payment */}
                  <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 space-y-4">
                    <h4 className="text-sm font-bold text-gray-400 uppercase tracking-wide">Thanh toán sắp tới</h4>
                    
                    {pendingPaymentContracts.length === 0 ? (
                      <div className="py-4 text-center text-gray-400">
                        <CheckCircle className="w-8 h-8 text-emerald-500 mx-auto mb-2" />
                        <p className="text-xs">Tất cả hóa đơn đã thanh toán đầy đủ!</p>
                      </div>
                    ) : (
                      <div className="space-y-4">
                        <div className="flex items-start space-x-3">
                          <div className="w-10 h-10 rounded-xl bg-blue-50 flex items-center justify-center shrink-0">
                            {getProductIcon(pendingPaymentContracts[0].productType)}
                          </div>
                          <div>
                            <h5 className="text-sm font-bold text-gray-900">{getProductDisplayName(pendingPaymentContracts[0].productType)}</h5>
                            <p className="text-xs text-gray-400">Số HĐ: {pendingPaymentContracts[0].contractId?.substring(0, 13).toUpperCase()}</p>
                            <p className="text-[11px] text-red-500 font-semibold mt-1">Hạn thanh toán: {pendingPaymentContracts[0].effectiveDate || "01/06/2024"}</p>
                          </div>
                        </div>

                        <div className="flex justify-between items-baseline border-t border-dashed border-gray-100 pt-3">
                          <span className="text-xs text-gray-400">Tổng phí thanh toán:</span>
                          <span className="text-lg font-extrabold text-gray-900">{(pendingPaymentContracts[0].quotedPremium || 0).toLocaleString("vi-VN")}đ</span>
                        </div>

                        <button
                          onClick={() => handleTabChange("payments")}
                          className="w-full py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold transition-all shadow-sm flex items-center justify-center space-x-2"
                        >
                          <span>Đi tới thanh toán</span>
                        </button>
                      </div>
                    )}
                  </div>

                  {/* Widget 2: Recent Notifications */}
                  <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center">
                      <h4 className="text-sm font-bold text-gray-400 uppercase tracking-wide">Thông báo mới</h4>
                      <button 
                        onClick={() => handleTabChange("notifications")}
                        className="text-xs font-semibold text-blue-600 hover:text-blue-700"
                      >
                        Xem tất cả
                      </button>
                    </div>

                    {notifications.length === 0 ? (
                      <div className="py-8 text-center text-gray-400">
                        <Inbox className="w-8 h-8 mx-auto mb-2 text-gray-300" />
                        <p className="text-xs">Chưa có thông báo mới.</p>
                      </div>
                    ) : (
                      <div className="space-y-3">
                        {notifications.slice(0, 2).map((n) => (
                          <div key={n.notificationId} className="flex items-start space-x-3 text-xs">
                            <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${
                              isNotificationUnread(n) ? "bg-blue-50 text-blue-600" : "bg-gray-50 text-gray-400"
                            }`}>
                                <Bell className="w-4 h-4" />
                            </div>
                            <div className="min-w-0 flex-1">
                              <p className="font-semibold text-gray-900 truncate">{n.title || "Thông báo hệ thống"}</p>
                              <p className="text-gray-500 text-[10px] truncate mt-0.5">{n.message}</p>
                              <p className="text-gray-400 text-[10px] mt-0.5">{formatDateTime(n.createdAt)}</p>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                </div>

              </div>

            </div>
          )}

          {/* ================================================================ */}
          {/* TAB 2: MY CONTRACTS */}
          {/* ================================================================ */}
          {activeTab === "contracts" && (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <h3 className="text-2xl font-bold text-gray-900">Hợp đồng của tôi</h3>
                  <p className="text-sm text-gray-500 mt-1">Theo dõi trạng thái hợp đồng, thời hạn hiệu lực và phí cần thanh toán.</p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={reloadContracts}
                    disabled={contractsLoading}
                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 border border-gray-200 bg-white hover:bg-gray-50 text-gray-700 rounded-xl text-sm font-semibold transition-colors disabled:opacity-60"
                  >
                    <RefreshCw className={`w-4 h-4 ${contractsLoading ? "animate-spin" : ""}`} />
                    <span>Làm mới</span>
                  </button>
                  <button
                    onClick={() => handleTabChange("products")}
                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold transition-colors shadow-sm"
                  >
                    <Shield className="w-4 h-4" />
                    <span>Đăng ký gói mới</span>
                  </button>
                </div>
              </div>

              {contractsLoading ? (
                <div className="bg-white rounded-2xl border border-gray-100 shadow-sm flex flex-col justify-center items-center py-20">
                  <Loader2 className="w-10 h-10 text-blue-600 animate-spin" />
                  <span className="text-sm text-gray-400 mt-3">Đang tải danh sách hợp đồng...</span>
                </div>
              ) : contractsError ? (
                <div className="bg-white rounded-2xl border border-rose-100 shadow-sm p-8 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                  <div className="flex items-start gap-3">
                    <div className="w-10 h-10 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center shrink-0">
                      <AlertCircle className="w-5 h-5" />
                    </div>
                    <div>
                      <h4 className="font-bold text-gray-900">Không tải được hợp đồng</h4>
                      <p className="text-sm text-gray-500 mt-1">{contractsError}</p>
                    </div>
                  </div>
                  <button
                    onClick={reloadContracts}
                    className="px-4 py-2.5 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-sm font-semibold transition-colors"
                  >
                    Thử lại
                  </button>
                </div>
              ) : contracts.length === 0 ? (
                <div className="bg-white rounded-2xl border border-gray-100 shadow-sm py-20 px-6 text-center space-y-4">
                  <div className="w-14 h-14 rounded-2xl bg-blue-50 text-blue-600 flex items-center justify-center mx-auto">
                    <FileText className="w-7 h-7" />
                  </div>
                  <div>
                    <h4 className="text-lg font-bold text-gray-900">Chưa có hợp đồng nào</h4>
                    <p className="text-sm text-gray-500 mt-1">Sau khi chấp nhận báo giá và tạo hợp đồng, hợp đồng sẽ xuất hiện ở đây.</p>
                  </div>
                  <button
                    onClick={() => handleTabChange("products")}
                    className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold transition-all shadow-sm"
                  >
                    Xem sản phẩm bảo hiểm
                  </button>
                </div>
              ) : (
                <div className="space-y-4">
                  <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
                      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Tổng hợp đồng</p>
                      <p className="text-3xl font-extrabold text-gray-900 mt-2">{contracts.length}</p>
                    </div>
                    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
                      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Đang hiệu lực</p>
                      <p className="text-3xl font-extrabold text-emerald-600 mt-2">{activeContracts.length}</p>
                    </div>
                    <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
                      <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Cần thanh toán</p>
                      <p className="text-2xl font-extrabold text-rose-600 mt-2">{formatMoney(totalUnpaidAmount)}</p>
                    </div>
                  </div>

                  <div className="grid grid-cols-1 gap-4">
                    {contracts.map((c) => {
                      const isExpanded = expandedContractIds.has(c.contractId);
                      const topRiskFactors = getTopRiskFactors(c.quote);
                      return (
                        <div key={c.contractId} className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
                          <button
                            type="button"
                            onClick={() => toggleContractExpanded(c.contractId)}
                            className="w-full p-5 text-left hover:bg-gray-50/60 transition-colors"
                          >
                            <div className="flex flex-col lg:flex-row lg:items-start justify-between gap-4">
                              <div className="flex items-start gap-3 min-w-0">
                                <div className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 ${getProductIconBg(c.productType)}`}>
                                  {getProductIcon(c.productType)}
                                </div>
                                <div className="min-w-0">
                                  <div className="flex flex-wrap items-center gap-2">
                                    <h4 className="font-bold text-gray-900">{getContractProductName(c)}</h4>
                                    <span className={`inline-flex items-center px-2.5 py-1 rounded-full border text-xs font-semibold ${getContractStatusClass(c.status)}`}>
                                      {getContractStatusLabel(c.status)}
                                    </span>
                                  </div>
                                  <p className="text-sm text-gray-600 mt-1">
                                    {getContractInsuredName(c)} · {getContractPlanName(c)}
                                  </p>
                                  <div className="flex flex-wrap items-center gap-2 mt-3">
                                    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-xs font-semibold ${getPaymentStatusClass(c.paymentStatus)}`}>
                                      <ReceiptText className="w-3.5 h-3.5" />
                                      {getPaymentStatusLabel(c.paymentStatus)}
                                    </span>
                                    {c.quote?.riskLevel && (
                                      <span className={`inline-flex items-center px-2.5 py-1 rounded-full border text-xs font-semibold ${getRiskLevelClass(c.quote.riskLevel)}`}>
                                        {getRiskLevelLabel(c.quote.riskLevel)}
                                      </span>
                                    )}
                                  </div>
                                </div>
                              </div>

                              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 lg:min-w-[420px]">
                                <div className="rounded-xl bg-gray-50 p-3">
                                  <p className="text-xs text-gray-500">Phí cuối</p>
                                  <p className="font-extrabold text-gray-900 mt-1">{formatMoney(c.quotedPremium)}</p>
                                </div>
                                <div className="rounded-xl bg-gray-50 p-3">
                                  <p className="text-xs text-gray-500">Số tiền BH</p>
                                  <p className="font-extrabold text-gray-900 mt-1">{formatMoney(c.sumInsured)}</p>
                                </div>
                                <div className="rounded-xl bg-gray-50 p-3 col-span-2 sm:col-span-1">
                                  <p className="text-xs text-gray-500">Thời hạn</p>
                                  <p className="font-bold text-gray-900 mt-1">{formatDate(c.effectiveDate)} - {formatDate(c.expiryDate)}</p>
                                </div>
                              </div>

                              <ChevronRight className={`w-5 h-5 text-gray-400 shrink-0 transition-transform ${isExpanded ? "rotate-90" : ""}`} />
                            </div>
                          </button>

                          {isExpanded && (
                            <div className="border-t border-gray-100 p-5 space-y-4">
                              <div className="rounded-xl border border-blue-100 bg-blue-50/50 p-4 space-y-3">
                                <div className="flex items-center justify-between gap-3">
                                  <div className="flex items-center gap-2 min-w-0">
                                    <Activity className="w-4 h-4 text-blue-600 shrink-0" />
                                    <p className="text-sm font-bold text-gray-900">Yếu tố rủi ro nổi bật</p>
                                  </div>
                                  {c.quote?.riskLevel && (
                                    <span className={`shrink-0 inline-flex items-center px-2.5 py-1 rounded-full border text-xs font-semibold ${getRiskLevelClass(c.quote.riskLevel)}`}>
                                      {getRiskLevelLabel(c.quote.riskLevel)}
                                    </span>
                                  )}
                                </div>

                                {c.quoteLoadError ? (
                                  <p className="text-xs text-amber-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
                                    Không tải được chi tiết rủi ro cho hợp đồng này.
                                  </p>
                                ) : topRiskFactors.length > 0 ? (
                                  <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                                    {topRiskFactors.map((factor, index) => (
                                      <div key={`${getRiskFactorLabel(factor)}-${index}`} className="rounded-xl bg-white border border-blue-100 p-3">
                                        <p className="text-sm font-bold text-gray-900">{getRiskFactorLabel(factor)}</p>
                                        {getRiskFactorDetail(factor) && (
                                          <p className="text-xs text-gray-500 mt-1 leading-relaxed">{getRiskFactorDetail(factor)}</p>
                                        )}
                                      </div>
                                    ))}
                                  </div>
                                ) : (
                                  <p className="text-xs text-gray-500">Chưa có dữ liệu giải thích rủi ro nổi bật cho báo giá này.</p>
                                )}
                              </div>

                              <div className="rounded-xl border border-gray-100 p-4">
                                <p className="text-sm font-bold text-gray-900 mb-3">Thông tin liên quan</p>
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-sm">
                                  <div>
                                    <p className="text-xs text-gray-500">Người được bảo hiểm</p>
                                    <p className="font-semibold text-gray-900 mt-1">{getContractInsuredName(c)}</p>
                                  </div>
                                  <div>
                                    <p className="text-xs text-gray-500">Sản phẩm</p>
                                    <p className="font-semibold text-gray-900 mt-1">{getContractProductName(c)}</p>
                                  </div>
                                  <div>
                                    <p className="text-xs text-gray-500">Gói quyền lợi</p>
                                    <p className="font-semibold text-gray-900 mt-1">{getContractPlanName(c)}</p>
                                  </div>
                                  <div>
                                    <p className="text-xs text-gray-500">Quyền lợi hoàn trả</p>
                                    <p className="font-semibold text-gray-900 mt-1">{c.reimbursement || "Chưa có"}</p>
                                  </div>
                                  <div>
                                    <p className="text-xs text-gray-500">Kênh phân phối</p>
                                    <p className="font-semibold text-gray-900 mt-1">{c.distributionChannel || "Chưa có"}</p>
                                  </div>
                                  <div>
                                    <p className="text-xs text-gray-500">Năm hợp đồng</p>
                                    <p className="font-semibold text-gray-900 mt-1">{c.policyYear || 1}</p>
                                  </div>
                                </div>
                              </div>

                              <div className="rounded-xl border border-gray-100 p-4">
                                <p className="text-sm font-bold text-gray-900 mb-3">Chi tiết định phí</p>
                                <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
                                  <div>
                                    <p className="text-xs text-gray-500">Pure premium</p>
                                    <p className="font-semibold text-gray-900 mt-1">{formatMoney(c.quote?.purePremium ?? c.purePremium)}</p>
                                  </div>
                                  <div>
                                    <p className="text-xs text-gray-500">Loading rate</p>
                                    <p className="font-semibold text-gray-900 mt-1">{formatPercent(c.quote?.loadingRate ?? c.loadingRate)}</p>
                                  </div>
                                  <div>
                                    <p className="text-xs text-gray-500">Tần suất dự đoán/năm</p>
                                    <p className="font-semibold text-gray-900 mt-1">{formatNumber(c.quote?.predictedAnnualFrequency, 4)}</p>
                                  </div>
                                  <div>
                                    <p className="text-xs text-gray-500">Severity dự đoán</p>
                                    <p className="font-semibold text-gray-900 mt-1">{formatMoney(c.quote?.predictedAverageSeverity)}</p>
                                  </div>
                                </div>
                              </div>

                              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pt-1">
                                <div className="flex flex-wrap items-center gap-2">
                                  <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-xs font-semibold ${getPaymentStatusClass(c.paymentStatus)}`}>
                                    <ReceiptText className="w-3.5 h-3.5" />
                                    {getPaymentStatusLabel(c.paymentStatus)}
                                  </span>
                                  <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border border-gray-200 bg-white text-gray-600 text-xs font-semibold">
                                    <CalendarDays className="w-3.5 h-3.5" />
                                    {formatDate(c.effectiveDate)} - {formatDate(c.expiryDate)}
                                  </span>
                                </div>

                                {!isPaymentCompleted(c.paymentStatus) && c.status !== "ACTIVE" && (
                                  <button
                                    onClick={() => handleTabChange("payments")}
                                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white rounded-xl text-sm font-semibold transition-colors"
                                  >
                                    <span>Đi tới thanh toán</span>
                                  </button>
                                )}
                              </div>
                            </div>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}
            </div>
          )}

          {/* ================================================================ */}
          {/* TAB 3: INSURANCE PRODUCTS */}
          {/* ================================================================ */}
          {activeTab === "products" && (
            <ProductsTab
              onRegisterSuccess={() => {
                // Reload contracts after successful registration
                loadContractsWithQuotes().then(data => setContracts(data || []));
              }}
            />
          )}

          {/* ================================================================ */}
          {/* TAB 4: INSURED PERSONS */}
          {/* ================================================================ */}
          {activeTab === "insured-persons" && (
            <InsuredPersonsTab />
          )}


          {/* ================================================================ */}
          {/* TAB 5: PAYMENTS */}
          {/* ================================================================ */}
          {activeTab === "payments" && (
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 space-y-6">
              <div>
                <h3 className="text-xl font-bold text-gray-900">Lịch sử giao dịch thanh toán</h3>
                <p className="text-xs text-gray-500">Danh sách các hóa đơn đóng phí bảo hiểm thường niên của quý khách</p>
              </div>

              <div className="space-y-3">
                <h4 className="text-sm font-bold text-gray-800">Hợp đồng chờ thanh toán</h4>
                {pendingPaymentContracts.length === 0 ? (
                  <div className="rounded-2xl border border-gray-100 bg-gray-50 px-4 py-6 text-center text-sm text-gray-400">
                    Không có hợp đồng nào đang chờ thanh toán.
                  </div>
                ) : (
                  <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
                    {pendingPaymentContracts.map((c) => (
                      <div key={c.contractId} className="rounded-2xl border border-gray-100 p-4 space-y-4">
                        <div className="flex items-start justify-between gap-3">
                          <div>
                            <p className="text-sm font-bold text-gray-900">{getProductDisplayName(c.productType)}</p>
                            <p className="text-xs text-gray-400 mt-1">Số HĐ: {c.contractId?.substring(0, 13).toUpperCase()}</p>
                          </div>
                          <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-xs font-semibold ${getPaymentStatusClass(c.paymentStatus)}`}>
                            <ReceiptText className="w-3.5 h-3.5" />
                            {getPaymentStatusLabel(c.paymentStatus)}
                          </span>
                        </div>
                        <div className="flex items-baseline justify-between border-t border-dashed border-gray-100 pt-3">
                          <span className="text-xs text-gray-400">Số tiền</span>
                          <span className="text-lg font-extrabold text-gray-900">{(c.quotedPremium || 0).toLocaleString("vi-VN")}đ</span>
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                          <button
                            onClick={() => handlePayContract(c, "SUCCESS")}
                            disabled={paymentLoading}
                            className="inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-2.5 text-sm font-bold text-white hover:bg-emerald-700 disabled:bg-emerald-300"
                          >
                            {paymentLoading && <Loader2 className="w-4 h-4 animate-spin" />}
                            <span>Giả lập thành công</span>
                          </button>
                          <button
                            onClick={() => handlePayContract(c, "FAILED")}
                            disabled={paymentLoading}
                            className="inline-flex items-center justify-center gap-2 rounded-xl bg-red-600 px-4 py-2.5 text-sm font-bold text-white hover:bg-red-700 disabled:bg-red-300"
                          >
                            {paymentLoading && <Loader2 className="w-4 h-4 animate-spin" />}
                            <span>Giả lập thất bại</span>
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {contracts.filter(c => isPaymentCompleted(c.paymentStatus)).length === 0 ? (
                <div className="py-12 text-center text-gray-400">
                  Chưa ghi nhận bất kỳ giao dịch thanh toán nào từ tài khoản này.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full border-collapse text-left text-sm">
                    <thead>
                      <tr className="border-b border-gray-100 text-gray-400 font-semibold text-xs uppercase bg-gray-50/50">
                        <th className="py-3.5 px-4 rounded-l-xl">Hợp đồng liên kết</th>
                        <th className="py-3.5 px-4">Mã giao dịch</th>
                        <th className="py-3.5 px-4">Phương thức</th>
                        <th className="py-3.5 px-4">Số tiền</th>
                        <th className="py-3.5 px-4 rounded-r-xl">Trạng thái</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                      {contracts.filter(c => isPaymentCompleted(c.paymentStatus)).map((c) => (
                        <tr key={c.contractId} className="hover:bg-gray-50/50 transition-colors">
                          <td className="py-4 px-4 font-bold text-gray-900">
                            {getProductDisplayName(c.productType)}
                          </td>
                          <td className="py-4 px-4 font-mono text-xs text-gray-500">
                            {c.paymentId ? c.paymentId.substring(0, 20).toUpperCase() : `TX-${c.contractId?.substring(0,8).toUpperCase()}`}
                          </td>
                          <td className="py-4 px-4 text-gray-600">Thẻ Quốc Tế (Credit Card)</td>
                          <td className="py-4 px-4 font-extrabold text-emerald-600">
                            +{(c.quotedPremium || 0).toLocaleString("vi-VN")}đ
                          </td>
                          <td className="py-4 px-4">
                            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-green-50 text-green-700">
                              Thành công
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          )}

          {/* ================================================================ */}
          {/* TAB 6: CLAIM HISTORY */}
          {/* ================================================================ */}
          {activeTab === "claims" && (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div>
                  <h3 className="text-2xl font-bold text-gray-900">Lịch sử bồi thường</h3>
                  <p className="text-sm text-gray-500 mt-1">Theo dõi tổng hợp lịch sử bồi thường theo từng người được bảo hiểm.</p>
                </div>
                <div className="flex flex-col sm:flex-row gap-2">
                  <select
                    value={selectedClaimInsuredPersonId}
                    onChange={(event) => setSelectedClaimInsuredPersonId(event.target.value)}
                    className="bg-white border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-semibold text-gray-700 focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="ALL">Tất cả người được bảo hiểm</option>
                    {claimInsuredOptions.map((person) => (
                      <option key={person.insuredPersonId} value={person.insuredPersonId}>
                        {person.fullName}{person.identityNumber ? ` - ${person.identityNumber}` : ""}
                      </option>
                    ))}
                  </select>
                  <button
                    onClick={reloadClaims}
                    disabled={claimsLoading}
                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 border border-gray-200 bg-white hover:bg-gray-50 text-gray-700 rounded-xl text-sm font-semibold transition-colors disabled:opacity-60"
                  >
                    <RefreshCw className={`w-4 h-4 ${claimsLoading ? "animate-spin" : ""}`} />
                    <span>Làm mới</span>
                  </button>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
                  <div className="w-11 h-11 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
                    <ClipboardList className="w-5 h-5" />
                  </div>
                  <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mt-4">Dòng input AI</p>
                  <p className="text-3xl font-extrabold text-gray-900 mt-2">{selectedClaims.length}</p>
                </div>
                <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
                  <div className="w-11 h-11 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center">
                    <ReceiptText className="w-5 h-5" />
                  </div>
                  <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mt-4">Chi phí BT năm trước</p>
                  <p className="text-2xl font-extrabold text-gray-900 mt-2">{formatMoney(previousYearClaimCost)}</p>
                </div>
                <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
                  <div className="w-11 h-11 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
                    <Activity className="w-5 h-5" />
                  </div>
                  <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide mt-4">Dịch vụ y tế năm trước</p>
                  <p className="text-3xl font-extrabold text-gray-900 mt-2">{medicalServiceCount}</p>
                </div>
              </div>

              {claimsError && (
                <div className="bg-red-50 border border-red-100 rounded-xl p-4 flex items-center space-x-3 text-red-800 text-sm shadow-sm">
                  <AlertCircle className="w-5 h-5 text-red-600 shrink-0" />
                  <span className="font-medium">{claimsError}</span>
                </div>
              )}

              <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
                {claimsLoading ? (
                  <div className="flex flex-col justify-center items-center py-20">
                    <Loader2 className="w-10 h-10 text-blue-600 animate-spin" />
                      <span className="text-sm text-gray-400 mt-3">Đang tải tổng hợp lịch sử bồi thường...</span>
                  </div>
                ) : claims.length === 0 ? (
                  <div className="py-20 text-center text-gray-400 space-y-3">
                    <ClipboardList className="w-12 h-12 mx-auto text-gray-300" />
                    <div>
                      <h4 className="text-base font-bold text-gray-700">Chưa có dữ liệu lịch sử</h4>
                      <p className="text-sm mt-1">Khi có dữ liệu tổng hợp theo người được bảo hiểm, thông tin sẽ xuất hiện tại đây.</p>
                    </div>
                  </div>
                ) : selectedClaims.length === 0 ? (
                  <div className="py-20 text-center text-gray-400 space-y-3">
                    <User className="w-12 h-12 mx-auto text-gray-300" />
                    <div>
                      <h4 className="text-base font-bold text-gray-700">Người này chưa có lịch sử bồi thường</h4>
                      <p className="text-sm mt-1">Chọn người được bảo hiểm khác hoặc chọn tất cả để xem toàn bộ lịch sử.</p>
                    </div>
                  </div>
                ) : (
                  <div className="overflow-x-auto">
                    <table className="w-full border-collapse text-left text-sm">
                      <thead>
                        <tr className="border-b border-gray-100 text-gray-400 font-semibold text-xs uppercase bg-gray-50/50">
                          <th className="py-4 px-6">Người được bảo hiểm</th>
                          <th className="py-4 px-6">Sản phẩm</th>
                          <th className="py-4 px-6">Thâm niên</th>
                          <th className="py-4 px-6">Năm không BT</th>
                          <th className="py-4 px-6">BT năm trước</th>
                          <th className="py-4 px-6">Dịch vụ năm trước</th>
                          <th className="py-4 px-6">Severity TB năm trước</th>
                          <th className="py-4 px-6">Có BT/dịch vụ</th>
                          <th className="py-4 px-6">Không BT năm trước</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100">
                        {selectedClaims.map((summary) => (
                          <tr key={summary.summaryId || `${summary.insuredPersonId}-${summary.productType}`} className="hover:bg-gray-50/40 transition-colors">
                            <td className="py-4 px-6">
                              <p className="font-bold text-gray-900">{getClaimInsuredName(summary.insuredPersonId)}</p>
                              <p className="font-mono text-[10px] text-gray-400 mt-1">{summary.insuredPersonId}</p>
                            </td>
                            <td className="py-4 px-6">
                              <span className="inline-flex items-center px-2.5 py-1 rounded-lg bg-blue-50 text-blue-700 text-xs font-bold border border-blue-100">
                                {getProductDisplayName(summary.productType)}
                              </span>
                            </td>
                            <td className="py-4 px-6 text-gray-700 font-semibold">{formatNumber(summary.seniorityInsured, 2)}</td>
                            <td className="py-4 px-6 text-gray-700 font-semibold">{summary.claimFreeYears ?? 0}</td>
                            <td className="py-4 px-6 font-semibold text-gray-800">{formatMoney(summary.prevCostClaimsYear)}</td>
                            <td className="py-4 px-6 text-gray-700 font-semibold">{summary.prevNMedicalServices || 0}</td>
                            <td className="py-4 px-6 font-semibold text-gray-800">{formatMoney(getPreviousAverageSeverity(summary))}</td>
                            <td className="py-4 px-6">
                              <span className={`inline-flex items-center px-2.5 py-1 rounded-full border text-xs font-bold ${
                                summary.prevHadClaimOrService
                                  ? "bg-rose-50 text-rose-700 border-rose-100"
                                  : "bg-emerald-50 text-emerald-700 border-emerald-100"
                              }`}>
                                {summary.prevHadClaimOrService ? "Có" : "Không"}
                              </span>
                            </td>
                            <td className="py-4 px-6">
                              <span className={`inline-flex items-center px-2.5 py-1 rounded-full border text-xs font-bold ${
                                summary.claimFreePreviousYear
                                  ? "bg-emerald-50 text-emerald-700 border-emerald-100"
                                  : "bg-rose-50 text-rose-700 border-rose-100"
                              }`}>
                                {summary.claimFreePreviousYear ? "Có" : "Không"}
                              </span>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </div>
            </div>
          )}

          {/* ================================================================ */}
          {/* TAB 7: NOTIFICATIONS */}
          {/* ================================================================ */}
          {activeTab === "notifications" && (
            <div className="space-y-6">
              <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                <div>
                  <h3 className="text-2xl font-bold text-gray-900">Hộp thư thông báo</h3>
                  <p className="text-sm text-gray-500 mt-1">Theo dõi cập nhật về hợp đồng, thanh toán và hồ sơ bảo hiểm.</p>
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <button
                    onClick={() => loadNotifications({
                      status: notificationStatusFilter,
                      page: notificationPage,
                      size: notificationPageSize
                    })}
                    disabled={notificationsLoading}
                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 border border-gray-200 bg-white hover:bg-gray-50 text-gray-700 rounded-xl text-sm font-semibold transition-colors disabled:opacity-60"
                  >
                    <RefreshCw className={`w-4 h-4 ${notificationsLoading ? "animate-spin" : ""}`} />
                    <span>Làm mới</span>
                  </button>
                  <button
                    onClick={handleMarkAllNotificationsRead}
                    disabled={unreadNotificationsCount === 0 || notificationActionId === "read-all"}
                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white rounded-xl text-sm font-semibold transition-colors"
                  >
                    {notificationActionId === "read-all" ? <Loader2 className="w-4 h-4 animate-spin" /> : <CheckCheck className="w-4 h-4" />}
                    <span>Đọc tất cả</span>
                  </button>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
                  <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Chưa đọc</p>
                  <p className="text-3xl font-extrabold text-blue-600 mt-2">{unreadNotificationsCount}</p>
                </div>
                <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
                  <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Tổng theo bộ lọc</p>
                  <p className="text-3xl font-extrabold text-gray-900 mt-2">{notificationTotalItems}</p>
                </div>
                <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5">
                  <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Trang hiện tại</p>
                  <p className="text-3xl font-extrabold text-gray-900 mt-2">{notificationPage}/{notificationTotalPages}</p>
                </div>
              </div>

              <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
                <div className="p-4 border-b border-gray-100 flex flex-col md:flex-row md:items-center justify-between gap-3">
                  <div className="inline-flex w-fit rounded-xl bg-gray-100 p-1">
                    {[
                      { value: "", label: "Tất cả" },
                      { value: "UNREAD", label: "Chưa đọc" },
                      { value: "READ", label: "Đã đọc" },
                      { value: "ARCHIVED", label: "Lưu trữ" }
                    ].map((item) => (
                      <button
                        key={item.value}
                        onClick={() => handleNotificationStatusChange(item.value)}
                        className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-colors ${
                          notificationStatusFilter === item.value
                            ? "bg-white text-blue-600 shadow-sm"
                            : "text-gray-500 hover:text-gray-900"
                        }`}
                      >
                        {item.label}
                      </button>
                    ))}
                  </div>
                  {notificationsError && (
                    <div className="flex items-center gap-2 text-sm text-rose-600">
                      <AlertCircle className="w-4 h-4" />
                      <span>{notificationsError}</span>
                    </div>
                  )}
                </div>

                {notificationsLoading ? (
                  <div className="flex flex-col justify-center items-center py-20">
                    <Loader2 className="w-10 h-10 text-blue-600 animate-spin" />
                    <span className="text-sm text-gray-400 mt-3">Đang tải thông báo...</span>
                  </div>
                ) : notifications.length === 0 ? (
                  <div className="py-20 text-center text-gray-400 space-y-3">
                    <Inbox className="w-12 h-12 mx-auto text-gray-300" />
                    <div>
                      <h4 className="text-base font-bold text-gray-700">Không có thông báo</h4>
                      <p className="text-sm mt-1">Các cập nhật mới sẽ xuất hiện tại đây.</p>
                    </div>
                  </div>
                ) : (
                  <div className="divide-y divide-gray-100">
                    {notifications.map((n) => (
                      <div
                        key={n.notificationId}
                        className={`p-5 flex items-start gap-4 transition-colors ${
                          isNotificationUnread(n) ? "bg-blue-50/40" : "bg-white hover:bg-gray-50/60"
                        }`}
                      >
                        <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${
                          isNotificationUnread(n) ? "bg-blue-100 text-blue-600" : "bg-gray-100 text-gray-400"
                        }`}>
                          <Bell className="w-5 h-5" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <div className="flex flex-col md:flex-row md:items-start justify-between gap-3">
                            <div className="min-w-0">
                              <div className="flex flex-wrap items-center gap-2">
                                <h4 className="font-bold text-gray-900 truncate">{n.title || "Thông báo từ InsuCare"}</h4>
                                <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold border ${
                                  isNotificationUnread(n)
                                    ? "bg-blue-50 text-blue-700 border-blue-100"
                                    : n.status === "ARCHIVED"
                                      ? "bg-gray-100 text-gray-600 border-gray-200"
                                      : "bg-emerald-50 text-emerald-700 border-emerald-100"
                                }`}>
                                  {getNotificationStatusLabel(n.status)}
                                </span>
                              </div>
                              <p className="text-sm text-gray-600 mt-1 leading-6">{n.message}</p>
                              <div className="flex flex-wrap items-center gap-3 mt-3 text-xs text-gray-400">
                                <span>{formatDateTime(n.createdAt)}</span>
                                {n.eventType && <span className="font-mono">{n.eventType}</span>}
                                {n.channelPolicy && <span>{n.channelPolicy}</span>}
                              </div>
                            </div>
                            <div className="flex items-center gap-2 shrink-0">
                              {isNotificationUnread(n) && (
                                <button
                                  onClick={() => handleMarkNotificationRead(n.notificationId)}
                                  disabled={notificationActionId === n.notificationId}
                                  className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-blue-100 bg-blue-50 text-blue-700 hover:bg-blue-100 text-xs font-bold transition-colors disabled:opacity-60"
                                  title="Đánh dấu đã đọc"
                                >
                                  {notificationActionId === n.notificationId ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <CheckCheck className="w-3.5 h-3.5" />}
                                  <span>Đã đọc</span>
                                </button>
                              )}
                              {n.status !== "ARCHIVED" && (
                                <button
                                  onClick={() => handleArchiveNotification(n.notificationId)}
                                  disabled={notificationActionId === `archive-${n.notificationId}`}
                                  className="inline-flex items-center gap-1.5 px-3 py-2 rounded-lg border border-gray-200 bg-white text-gray-600 hover:bg-gray-50 text-xs font-bold transition-colors disabled:opacity-60"
                                  title="Lưu trữ thông báo"
                                >
                                  {notificationActionId === `archive-${n.notificationId}` ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Archive className="w-3.5 h-3.5" />}
                                  <span>Lưu trữ</span>
                                </button>
                              )}
                            </div>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                )}

                <Pagination
                  currentPage={notificationPage}
                  totalPages={notificationTotalPages}
                  onPageChange={setNotificationPage}
                  pageSize={notificationPageSize}
                  onPageSizeChange={(size) => {
                    setNotificationPageSize(size);
                    setNotificationPage(1);
                  }}
                  totalItems={notificationTotalItems}
                />
              </div>
            </div>
          )}

          {/* ================================================================ */}
          {/* TAB 7: PROFILE */}
          {/* ================================================================ */}
          {activeTab === "profile" && (
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 md:p-8 space-y-8">
              
              {/* Cover Header */}
              <div className="bg-gradient-to-r from-blue-600 to-indigo-700 h-28 rounded-2xl p-6 flex items-end shadow-sm">
                <h3 className="text-xl font-bold text-white tracking-tight">Hồ sơ khách hàng</h3>
              </div>

              {profileLoading ? (
                <div className="flex flex-col justify-center items-center py-12">
                  <Loader2 className="w-10 h-10 text-blue-600 animate-spin" />
                  <span className="text-sm text-gray-400 mt-2">Đang tải thông tin hồ sơ...</span>
                </div>
              ) : (
                <div className="space-y-6">
                  {profileError && (
                    <div className="bg-red-50 border-l-4 border-red-500 p-4 rounded-xl text-sm text-red-700">
                      {profileError}
                    </div>
                  )}
                  {profileUpdateSuccess && (
                    <div className="bg-green-50 border-l-4 border-green-500 p-4 rounded-xl text-sm text-green-700 font-medium animate-fade-in">
                      {profileUpdateSuccess}
                    </div>
                  )}

                  {!isEditing ? (
                    // Display Read-only Profile
                    <div className="space-y-6">
                      <div className="flex justify-between items-center pb-4 border-b border-gray-100">
                        <div>
                          <h4 className="text-xl font-extrabold text-gray-900">{profile?.fullName || "Khách hàng"}</h4>
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold bg-green-100 text-green-800 mt-1">
                            Trạng thái: {profile?.customerStatus || "ACTIVE"}
                          </span>
                        </div>
                        <button
                          onClick={() => setIsEditing(true)}
                          className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-semibold transition-all shadow-sm"
                        >
                          Chỉnh sửa
                        </button>
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <div className="flex items-center space-x-3 text-gray-700">
                          <Bell className="w-5 h-5 text-gray-400" />
                          <div>
                            <p className="text-xs text-gray-400 font-semibold uppercase">Email</p>
                            <p className="text-sm font-semibold text-gray-900">{profile?.email || "N/A"}</p>
                          </div>
                        </div>

                        <div className="flex items-center space-x-3 text-gray-700">
                          <Bell className="w-5 h-5 text-gray-400" />
                          <div>
                            <p className="text-xs text-gray-400 font-semibold uppercase">Số điện thoại</p>
                            <p className="text-sm font-semibold text-gray-900">{profile?.phoneNumber || "N/A"}</p>
                          </div>
                        </div>

                        <div className="flex items-center space-x-3 text-gray-700">
                          <Bell className="w-5 h-5 text-gray-400" />
                          <div>
                            <p className="text-xs text-gray-400 font-semibold uppercase">Số CCCD / Hộ chiếu</p>
                            <p className="text-sm font-semibold text-gray-900">{profile?.identityNumber || "N/A"}</p>
                          </div>
                        </div>

                        <div className="flex items-center space-x-3 text-gray-700">
                          <Bell className="w-5 h-5 text-gray-400" />
                          <div>
                            <p className="text-xs text-gray-400 font-semibold uppercase">Ngày sinh</p>
                            <p className="text-sm font-semibold text-gray-900">{profile?.dateOfBirth || "N/A"}</p>
                          </div>
                        </div>

                        <div className="flex items-center space-x-3 text-gray-700">
                          <Bell className="w-5 h-5 text-gray-400" />
                          <div>
                            <p className="text-xs text-gray-400 font-semibold uppercase">Giới tính</p>
                            <p className="text-sm font-semibold text-gray-900">
                              {profile?.gender === "MALE" ? "Nam" : profile?.gender === "FEMALE" ? "Nữ" : "Chưa xác định"}
                            </p>
                          </div>
                        </div>
                      </div>
                    </div>
                  ) : (
                    // Edit Profile Form
                    <form onSubmit={handleUpdateProfile} className="space-y-6">
                      <div className="flex justify-between items-center pb-4 border-b border-gray-100">
                        <h4 className="text-lg font-bold text-gray-900">Cập nhật hồ sơ</h4>
                        <div className="flex space-x-2">
                          <button
                            type="button"
                            onClick={() => setIsEditing(false)}
                            className="px-4 py-2 border border-gray-200 text-gray-700 bg-white hover:bg-gray-50 rounded-xl text-xs font-semibold transition-all"
                          >
                            Hủy
                          </button>
                          <button
                            type="submit"
                            disabled={profileUpdateLoading}
                            className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-semibold transition-all flex items-center space-x-1.5"
                          >
                            {profileUpdateLoading && <Loader2 className="w-3.5 h-3.5 animate-spin" />}
                            <span>Lưu thay đổi</span>
                          </button>
                        </div>
                      </div>

                      <div className="space-y-4 max-w-lg">
                        <div>
                          <label className="block text-xs font-semibold text-gray-400 uppercase mb-1">Họ và tên</label>
                          <input
                            type="text"
                            required
                            value={fullName}
                            onChange={(e) => setFullName(e.target.value)}
                            className="block w-full border border-gray-200 rounded-xl px-3.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-semibold text-gray-400 uppercase mb-1">Số điện thoại</label>
                          <input
                            type="tel"
                            required
                            value={phoneNumber}
                            onChange={(e) => setPhoneNumber(e.target.value)}
                            className="block w-full border border-gray-200 rounded-xl px-3.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-semibold text-gray-400 uppercase mb-1">Số CCCD / Hộ chiếu</label>
                          <input
                            type="text"
                            required
                            value={identityNumber}
                            onChange={(e) => setIdentityNumber(e.target.value)}
                            className="block w-full border border-gray-200 rounded-xl px-3.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                          />
                        </div>

                        <div>
                          <label className="block text-xs font-semibold text-gray-400 uppercase mb-1">Ngày sinh</label>
                          <input
                            type="date"
                            value={dateOfBirth}
                            onChange={(e) => setDateOfBirth(e.target.value)}
                            className="block w-full border border-gray-200 rounded-xl px-3.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                          />
                        </div>

                        <div className="flex items-center space-x-6 text-sm text-gray-700 py-1">
                          <span className="font-semibold text-xs text-gray-400 uppercase">Giới tính:</span>
                          <label className="flex items-center space-x-1.5 cursor-pointer">
                            <input
                              type="radio"
                              name="gender"
                              value="MALE"
                              checked={gender === "MALE"}
                              onChange={() => setGender("MALE")}
                              className="text-blue-600 focus:ring-blue-500"
                            />
                            <span className="text-sm font-semibold">Nam</span>
                          </label>
                          <label className="flex items-center space-x-1.5 cursor-pointer">
                            <input
                              type="radio"
                              name="gender"
                              value="FEMALE"
                              checked={gender === "FEMALE"}
                              onChange={() => setGender("FEMALE")}
                              className="text-blue-600 focus:ring-blue-500"
                            />
                            <span className="text-sm font-semibold">Nữ</span>
                          </label>
                        </div>
                      </div>
                    </form>
                  )}
                </div>
              )}
            </div>
          )}

          {/* ================================================================ */}
          {/* TAB 8: SUPPORT */}
          {/* ================================================================ */}
          {activeTab === "support" && (
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 space-y-6">
              <div>
                <h3 className="text-xl font-bold text-gray-900">Liên hệ trung tâm hỗ trợ</h3>
                <p className="text-xs text-gray-500">Chúng tôi luôn sẵn sàng lắng nghe và trả lời mọi thắc mắc của quý khách</p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-8 pt-2">
                <div className="space-y-4">
                  <h4 className="text-sm font-bold text-gray-900">Thông tin liên hệ</h4>
                  
                  <div className="p-4 bg-gray-50 rounded-xl space-y-3 text-xs">
                    <p className="text-gray-600">
                      <strong>Tổng đài hỗ trợ 24/7:</strong> 1800 6000 (Miễn phí cuộc gọi)
                    </p>
                    <p className="text-gray-600">
                      <strong>Địa chỉ email:</strong> support@insucare.com.vn
                    </p>
                    <p className="text-gray-600">
                      <strong>Địa chỉ văn phòng chính:</strong> Số 1 Trần Hưng Đạo, Hoàn Kiếm, Hà Nội
                    </p>
                  </div>
                </div>

                <form 
                  onSubmit={(e) => {
                    e.preventDefault();
                    alert("Cảm ơn góp ý của bạn! Trung tâm CSKH sẽ liên hệ lại với bạn sớm nhất.");
                    e.target.reset();
                  }} 
                  className="space-y-4"
                >
                  <h4 className="text-sm font-bold text-gray-900">Gửi lời nhắn nhanh</h4>

                  <div>
                    <input
                      type="text"
                      required
                      placeholder="Tiêu đề lời nhắn"
                      className="block w-full border border-gray-200 rounded-xl px-3.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>

                  <div>
                    <textarea
                      required
                      rows="4"
                      placeholder="Nội dung chi tiết câu hỏi hoặc góp ý của bạn..."
                      className="block w-full border border-gray-200 rounded-xl px-3.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    />
                  </div>

                  <button
                    type="submit"
                    className="px-6 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-xs font-bold transition-all shadow-sm flex items-center space-x-1.5"
                  >
                    <Send className="w-3.5 h-3.5" />
                    <span>Gửi lời nhắn</span>
                  </button>
                </form>
              </div>
            </div>
          )}

        </main>

        {/* Footer */}
        <footer className="bg-white border-t border-gray-200 py-4 px-6 md:px-8 text-center sm:flex sm:justify-between items-center text-xs text-gray-400 shrink-0">
          <p>© 2024 InsuCare. Tất cả quyền được bảo lưu.</p>
          <div className="flex justify-center space-x-4 mt-2 sm:mt-0 font-medium">
            <a href="#" onClick={(e) => e.preventDefault()} className="hover:text-gray-600 transition-colors">Điều khoản sử dụng</a>
            <span>|</span>
            <a href="#" onClick={(e) => e.preventDefault()} className="hover:text-gray-600 transition-colors">Chính sách bảo mật</a>
          </div>
        </footer>

      </div>

    </div>
  );
}
