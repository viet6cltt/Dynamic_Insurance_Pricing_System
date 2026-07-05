import { useEffect, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { authService } from "../../services/authService";
import { customerService } from "../../services/customerService";
import ProductsTab from "./ProductsTab";
import InsuredPersonsTab from "./InsuredPersonsTab";
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
  RefreshCw
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
  
  // Notifications state
  const [notifications, setNotifications] = useState([]);
  const [unreadNotificationsCount, setUnreadNotificationsCount] = useState(0);

  // Interaction / Modal state
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [paymentSuccessMsg, setPaymentSuccessMsg] = useState("");

  // Fetch all initial data
  const fetchData = async () => {
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

      // 2. Get contracts
      const contractsData = await customerService.getContracts();
      setContracts(contractsData || []);
      setContractsLoading(false);

      // 3. Get notifications
      const notifs = await customerService.getNotifications();
      setNotifications(notifs || []);
      
      // Calculate unread count
      const unreads = notifs.filter(n => n.status === "UNREAD" || !n.readAt).length;
      setUnreadNotificationsCount(unreads);
    } catch (error) {
      console.error("Failed to load dashboard data", error);
      setProfileError("Không thể đồng bộ dữ liệu từ máy chủ.");
      setContractsError("Không thể tải danh sách hợp đồng.");
      setProfileLoading(false);
      setContractsLoading(false);
    }
  };

  useEffect(() => {
    let alive = true;
    async function loadDashboard() {
      await Promise.resolve();
      if (alive) fetchData();
    }
    loadDashboard();
    return () => { alive = false; };
  }, []);


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

  const handlePayContract = async (contract) => {
    try {
      setPaymentLoading(true);
      setPaymentSuccessMsg("");

      await customerService.payContract(contract.contractId);
      setPaymentSuccessMsg(`Thanh toán hợp đồng ${getProductDisplayName(contract.productType)} thành công!`);
      
      // Reload contracts to reflect paid status
      const updatedContracts = await customerService.getContracts();
      setContracts(updatedContracts || []);
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
      const updatedContracts = await customerService.getContracts();
      setContracts(updatedContracts || []);
    } catch (err) {
      console.error("Failed to reload contracts", err);
      setContractsError("Không thể tải danh sách hợp đồng.");
    } finally {
      setContractsLoading(false);
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

  const formatDate = (value) => value || "Chưa hiệu lực";

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

  // Helpers to calculate stats
  const activeContracts = contracts.filter(c => c.status === "ACTIVE" || c.status === "ISSUED");
  
  // Pending payment contract (usually status PENDING_PAYMENT or unpaid)
  const pendingPaymentContracts = contracts.filter(c => !isPaymentCompleted(c.paymentStatus) && c.status !== "ACTIVE");
  const totalUnpaidAmount = pendingPaymentContracts.reduce((sum, c) => sum + (c.quotedPremium || 0), 0);

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
                          onClick={() => handlePayContract(pendingPaymentContracts[0])}
                          disabled={paymentLoading}
                          className="w-full py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white rounded-xl text-sm font-semibold transition-all shadow-sm flex items-center justify-center space-x-2"
                        >
                          {paymentLoading && <Loader2 className="w-4 h-4 animate-spin" />}
                          <span>Thanh toán ngay</span>
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
                      <div className="space-y-3 py-2">
                        {/* Static/mock notifications fallback */}
                        <div className="flex items-start space-x-3 text-xs">
                          <div className="w-8 h-8 rounded-lg bg-purple-50 text-purple-600 flex items-center justify-center shrink-0">
                            <HeartPulse className="w-4 h-4" />
                          </div>
                          <div>
                            <p className="font-semibold text-gray-900">Yêu cầu bồi thường #CLM2024/001 đang được xử lý</p>
                            <p className="text-gray-400 text-[10px] mt-0.5">Cập nhật mới nhất 2 giờ trước</p>
                          </div>
                        </div>

                        <div className="flex items-start space-x-3 text-xs">
                          <div className="w-8 h-8 rounded-lg bg-orange-50 text-orange-600 flex items-center justify-center shrink-0">
                            <Bell className="w-4 h-4" />
                          </div>
                          <div>
                            <p className="font-semibold text-gray-900">Hợp đồng An Tâm Sức Khỏe sắp đến hạn thanh toán</p>
                            <p className="text-gray-400 text-[10px] mt-0.5">Hạn thanh toán: 01/06/2024</p>
                          </div>
                        </div>
                      </div>
                    ) : (
                      <div className="space-y-3">
                        {notifications.slice(0, 2).map((n) => (
                          <div key={n.notificationId} className="flex items-start space-x-3 text-xs">
                            <div className="w-8 h-8 rounded-lg bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                                <Bell className="w-4 h-4" />
                            </div>
                            <div className="min-w-0 flex-1">
                              <p className="font-semibold text-gray-900 truncate">{n.title || "Thông báo hệ thống"}</p>
                              <p className="text-gray-500 text-[10px] truncate mt-0.5">{n.message}</p>
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

                  <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
                    {contracts.map((c) => (
                      <div key={c.contractId} className="bg-white rounded-2xl border border-gray-100 shadow-sm p-5 space-y-5">
                        <div className="flex items-start justify-between gap-4">
                          <div className="flex items-start gap-3 min-w-0">
                            <div className={`w-11 h-11 rounded-xl flex items-center justify-center shrink-0 ${getProductIconBg(c.productType)}`}>
                              {getProductIcon(c.productType)}
                            </div>
                            <div className="min-w-0">
                              <h4 className="font-bold text-gray-900 truncate">{getProductDisplayName(c.productType)}</h4>
                              <p className="text-xs font-mono text-gray-400 mt-1 break-all">HĐ {c.contractId}</p>
                            </div>
                          </div>
                          <span className={`shrink-0 inline-flex items-center px-2.5 py-1 rounded-full border text-xs font-semibold ${getContractStatusClass(c.status)}`}>
                            {getContractStatusLabel(c.status)}
                          </span>
                        </div>

                        <div className="grid grid-cols-2 gap-3 text-sm">
                          <div className="rounded-xl bg-gray-50 p-3">
                            <p className="text-xs text-gray-500">Phí thường niên</p>
                            <p className="font-extrabold text-gray-900 mt-1">{formatMoney(c.quotedPremium)}</p>
                          </div>
                          <div className="rounded-xl bg-gray-50 p-3">
                            <p className="text-xs text-gray-500">Số tiền bảo hiểm</p>
                            <p className="font-extrabold text-gray-900 mt-1">{formatMoney(c.sumInsured)}</p>
                          </div>
                          <div className="rounded-xl bg-gray-50 p-3">
                            <p className="text-xs text-gray-500">Ngày hiệu lực</p>
                            <p className="font-bold text-gray-900 mt-1">{formatDate(c.effectiveDate)}</p>
                          </div>
                          <div className="rounded-xl bg-gray-50 p-3">
                            <p className="text-xs text-gray-500">Ngày kết thúc</p>
                            <p className="font-bold text-gray-900 mt-1">{formatDate(c.expiryDate)}</p>
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
                              Năm hợp đồng {c.policyYear || 1}
                            </span>
                          </div>

                          {!isPaymentCompleted(c.paymentStatus) && c.status !== "ACTIVE" && (
                            <button
                              onClick={() => handlePayContract(c)}
                              disabled={paymentLoading}
                              className="inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white rounded-xl text-sm font-semibold transition-colors"
                            >
                              {paymentLoading && <Loader2 className="w-4 h-4 animate-spin" />}
                              <span>Thanh toán phí</span>
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
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
                customerService.getContracts().then(data => setContracts(data || []));
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
          {/* TAB 6: NOTIFICATIONS */}
          {/* ================================================================ */}
          {activeTab === "notifications" && (
            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 space-y-6">
              <div className="flex justify-between items-center">
                <div>
                  <h3 className="text-xl font-bold text-gray-900">Hộp thư thông báo</h3>
                  <p className="text-xs text-gray-500">Nhận các cập nhật mới nhất về hợp đồng, sự kiện bồi thường và nhắc phí</p>
                </div>
              </div>

              {notifications.length === 0 ? (
                <div className="space-y-4">
                  {/* Local mock notifications */}
                  <div className="p-4 bg-gray-50 rounded-xl flex items-start space-x-3 text-sm">
                    <Bell className="w-5 h-5 text-blue-500 shrink-0 mt-0.5" />
                    <div>
                      <h4 className="font-bold text-gray-900">Chào mừng bạn gia nhập InsuCare</h4>
                      <p className="text-gray-600 mt-1">Cảm ơn bạn đã lựa chọn sử dụng dịch vụ của chúng tôi. Hãy hoàn thiện hồ sơ để có trải nghiệm tính toán giá bảo hiểm nhanh nhất!</p>
                      <span className="text-[10px] text-gray-400 block mt-2">01/06/2024</span>
                    </div>
                  </div>

                  <div className="p-4 bg-gray-50 rounded-xl flex items-start space-x-3 text-sm">
                    <Bell className="w-5 h-5 text-blue-500 shrink-0 mt-0.5" />
                    <div>
                      <h4 className="font-bold text-gray-900">Báo cáo kiểm định hồ sơ bồi thường #CLM2024/001</h4>
                      <p className="text-gray-600 mt-1">Chúng tôi đã tiếp nhận hồ sơ yêu cầu nằm viện An Tâm Sức Khỏe của bạn. Quá trình kiểm định sẽ hoàn tất trong tối đa 3 ngày làm việc.</p>
                      <span className="text-[10px] text-gray-400 block mt-2">04/06/2024</span>
                    </div>
                  </div>
                </div>
              ) : (
                <div className="space-y-4">
                  {notifications.map((n) => (
                    <div 
                      key={n.notificationId} 
                      className={`p-4 rounded-xl flex items-start space-x-3 text-sm transition-all border ${
                        n.readAt || n.status === "READ"
                          ? "bg-white border-gray-100" 
                          : "bg-blue-50/50 border-blue-100"
                      }`}
                    >
                      <Bell className={`w-5 h-5 shrink-0 mt-0.5 ${n.readAt || n.status === "READ" ? "text-gray-400" : "text-blue-500"}`} />
                      <div className="flex-1 min-w-0">
                        <div className="flex justify-between items-start gap-4">
                          <h4 className="font-bold text-gray-900 truncate">{n.title || "Thông báo từ InsuCare"}</h4>
                          {!(n.readAt || n.status === "READ") && (
                            <button
                              onClick={async () => {
                                await customerService.markNotificationRead(n.notificationId);
                                fetchData(); // reload count
                              }}
                              className="text-xs text-blue-600 hover:text-blue-700 font-bold hover:underline shrink-0"
                            >
                              Đánh dấu đã đọc
                            </button>
                          )}
                        </div>
                        <p className="text-gray-600 mt-1">{n.message}</p>
                        <span className="text-[10px] text-gray-400 block mt-2">
                          {n.createdAt ? new Date(n.createdAt).toLocaleString("vi-VN") : "Gần đây"}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
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
