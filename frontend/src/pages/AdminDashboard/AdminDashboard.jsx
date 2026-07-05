import { useCallback, useEffect, useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { adminService } from "../../services/adminService";
import RiskSchemaTab from "./RiskSchemaTab";
import OccupationMappingTab from "./OccupationMappingTab";
import Pagination from "../../components/Pagination";
import {
  LayoutDashboard,
  Shield,
  Layers,
  LogOut,
  Plus,
  Edit2,
  Upload,
  X,
  CheckCircle,
  AlertCircle,
  Loader2,
  ChevronRight,
  FileCode2,
  Briefcase
} from "lucide-react";

export default function AdminDashboard() {
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();

  // Tab routing mapping
  const getActiveTabFromPathname = (pathname) => {
    switch (pathname) {
      case "/admin/products": return "products";
      case "/admin/coverage-plans": return "plans";
      case "/admin/risk-schemas": return "risk-schemas";
      case "/admin/occupation-mappings": return "occupation-mappings";
      default: return "overview";
    }
  };

  const activeTab = getActiveTabFromPathname(location.pathname);

  const handleTabChange = (tabName) => {
    if (tabName === "overview") {
      navigate("/admin");
    } else if (tabName === "plans") {
      setProductPage(1);
      setPlanPage(1);
      navigate("/admin/coverage-plans");
    } else {
      setProductPage(1);
      navigate(`/admin/${tabName}`);
    }
  };

  const NAV_ITEMS = [
    { id: "overview",             label: "Tổng quan",                icon: LayoutDashboard },
    { id: "products",             label: "Sản phẩm bảo hiểm",        icon: Shield },
    { id: "plans",                label: "Các gói bảo hiểm",         icon: Layers },
    { id: "risk-schemas",         label: "Risk Input Schema",         icon: FileCode2 },
    { id: "occupation-mappings",  label: "Occupation Risk Mapping",   icon: Briefcase },
  ];

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  // --- API State Data ---
  const [products, setProducts] = useState([]);
  const [productsLoading, setProductsLoading] = useState(true);
  const [productsError, setProductsError] = useState("");

  const [plans, setPlans] = useState([]);
  const [plansLoading, setPlansLoading] = useState(false);
  const [plansError, setPlansError] = useState("");
  const [selectedProductId, setSelectedProductId] = useState("");
  const [planStatusFilter, setPlanStatusFilter] = useState("");

  // --- Toast/Banners ---
  const [successMsg, setSuccessMsg] = useState("");
  const [errorMsg, setErrorMsg] = useState("");

  // --- Pagination: Products ---
  const [productPage, setProductPage] = useState(1);
  const [productPageSize, setProductPageSize] = useState(10);

  // --- Pagination: Coverage Plans ---
  const [planPage, setPlanPage] = useState(1);
  const [planPageSize, setPlanPageSize] = useState(10);

  // --- Product Modals & Forms ---
  const [productModalOpen, setProductModalOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState(null); // null means "Create New"
  const [productForm, setProductForm] = useState({
    productType: "HEALTH",
    name: "",
    description: "",
    status: "ACTIVE",
    imageUrl: ""
  });
  const [productSubmitting, setProductSubmitting] = useState(false);
  const [modalImageFile, setModalImageFile] = useState(null);

  // --- Coverage Plan Modals & Forms ---
  const [planModalOpen, setPlanModalOpen] = useState(false);
  const [editingPlan, setEditingPlan] = useState(null); // null means "Create New"
  const [planForm, setPlanForm] = useState({
    planName: "",
    description: "",
    basePremium: "",
    sumInsured: "",
    status: "ACTIVE"
  });
  const [planSubmitting, setPlanSubmitting] = useState(false);

  // --- Fetch Products ---
  const fetchProducts = useCallback(async () => {
    setProductsLoading(true);
    setProductsError("");
    try {
      const data = await adminService.getProducts();
      setProducts(data);
      if (data.length > 0 && !selectedProductId) {
        setSelectedProductId(data[0].productId);
      }
    } catch {
      setProductsError("Không thể tải danh sách sản phẩm.");
    } finally {
      setProductsLoading(false);
    }
  }, [selectedProductId, setSelectedProductId]);

  // --- Fetch Coverage Plans for selected product ---
  const fetchCoveragePlans = useCallback(async (prodId, status = planStatusFilter) => {
    if (!prodId) return;
    setPlansLoading(true);
    setPlansError("");
    try {
      const data = await adminService.getCoveragePlans(prodId, status || null);
      setPlans(data);
    } catch {
      setPlansError("Không thể tải danh sách gói bảo hiểm.");
    } finally {
      setPlansLoading(false);
    }
  }, [planStatusFilter]);

  useEffect(() => {
    let alive = true;
    async function loadProducts() {
      await Promise.resolve();
      if (alive) fetchProducts();
    }
    loadProducts();
    return () => { alive = false; };
  }, [fetchProducts]);

  useEffect(() => {
    let alive = true;
    async function loadPlans() {
      await Promise.resolve();
      if (alive && activeTab === "plans" && selectedProductId) {
        fetchCoveragePlans(selectedProductId, planStatusFilter);
      }
    }
    loadPlans();
    return () => { alive = false; };
  }, [activeTab, selectedProductId, planStatusFilter, fetchCoveragePlans]);

  const triggerToast = (msg, isError = false) => {
    if (isError) {
      setErrorMsg(msg);
      setTimeout(() => setErrorMsg(""), 4000);
    } else {
      setSuccessMsg(msg);
      setTimeout(() => setSuccessMsg(""), 4000);
    }
  };

  // --- Product Actions ---
  const handleOpenProductCreate = () => {
    setEditingProduct(null);
    setProductForm({
      productType: "HEALTH",
      name: "",
      description: "",
      status: "ACTIVE",
      imageUrl: ""
    });
    setModalImageFile(null);
    setProductModalOpen(true);
  };

  const handleOpenProductEdit = (product) => {
    setEditingProduct(product);
    setProductForm({
      productType: product.productType || "HEALTH",
      name: product.name || "",
      description: product.description || "",
      status: product.status || "ACTIVE",
      imageUrl: product.imageUrl || ""
    });
    setModalImageFile(null);
    setProductModalOpen(true);
  };

  const handleProductSubmit = async (e) => {
    e.preventDefault();
    setProductSubmitting(true);
    try {
      let productId = null;
      if (editingProduct) {
        // Update
        productId = editingProduct.productId;
        await adminService.updateProduct(productId, {
          name: productForm.name,
          description: productForm.description,
          status: productForm.status,
          imageUrl: productForm.imageUrl
        });
      } else {
        // Create
        const created = await adminService.createProduct(productForm);
        productId = created.productId;
      }

      // If a file was selected in the modal, upload it to MinIO immediately
      if (modalImageFile && productId) {
        await adminService.uploadProductImage(productId, modalImageFile);
      }

      triggerToast(editingProduct ? "Cập nhật sản phẩm thành công!" : "Tạo sản phẩm bảo hiểm mới thành công!");
      setProductModalOpen(false);
      setModalImageFile(null);
      fetchProducts();
    } catch (err) {
      console.error(err);
      triggerToast("Thao tác thất bại. Vui lòng kiểm tra lại dữ liệu hoặc file ảnh tải lên.", true);
    } finally {
      setProductSubmitting(false);
    }
  };

  const handleToggleProductStatus = async (product) => {
    const newStatus = product.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    try {
      await adminService.updateProductStatus(product.productId, newStatus);
      triggerToast(`Đã chuyển trạng thái sản phẩm sang ${newStatus}!`);
      fetchProducts();
    } catch {
      triggerToast("Cập nhật trạng thái thất bại.", true);
    }
  };



  // --- Coverage Plan Actions ---
  const handleOpenPlanCreate = () => {
    setEditingPlan(null);
    setPlanForm({
      planName: "",
      description: "",
      basePremium: "",
      sumInsured: "",
      status: "ACTIVE"
    });
    setPlanModalOpen(true);
  };

  const handleOpenPlanEdit = async (plan) => {
    setPlansError("");
    try {
      const detail = await adminService.getCoveragePlanById(plan.coveragePlanId);
      setEditingPlan(detail);
      setPlanForm({
        planName: detail.planName || "",
        description: detail.description || "",
        basePremium: detail.basePremium?.toString() || "",
        sumInsured: detail.sumInsured?.toString() || "",
        status: detail.status || "ACTIVE"
      });
      setPlanModalOpen(true);
    } catch {
      triggerToast("Không thể tải chi tiết gói bảo hiểm.", true);
    }
  };

  const handlePlanSubmit = async (e) => {
    e.preventDefault();
    if (!selectedProductId) {
      triggerToast("Vui lòng chọn một sản phẩm trước khi tạo gói bảo hiểm.", true);
      return;
    }
    setPlanSubmitting(true);
    try {
      const payload = {
        planName: planForm.planName,
        description: planForm.description,
        basePremium: Number(planForm.basePremium),
        sumInsured: Number(planForm.sumInsured),
        status: planForm.status
      };

      if (editingPlan) {
        await adminService.updateCoveragePlan(editingPlan.coveragePlanId, payload);
        triggerToast("Cập nhật gói bảo hiểm thành công!");
      } else {
        await adminService.createCoveragePlan(selectedProductId, payload);
        triggerToast("Thêm gói bảo hiểm mới thành công!");
      }
      setPlanModalOpen(false);
      fetchCoveragePlans(selectedProductId, planStatusFilter);
    } catch {
      triggerToast("Thao tác thất bại. Vui lòng kiểm tra lại thông tin nhập.", true);
    } finally {
      setPlanSubmitting(false);
    }
  };

  const handleTogglePlanStatus = async (plan) => {
    const newStatus = plan.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    try {
      await adminService.updateCoveragePlanStatus(plan.coveragePlanId, newStatus);
      triggerToast(`Đã chuyển trạng thái gói bảo hiểm sang ${newStatus}!`);
      fetchCoveragePlans(selectedProductId, planStatusFilter);
    } catch {
      triggerToast("Cập nhật trạng thái thất bại.", true);
    }
  };

  // Helper displays
  const getProductTypeLabel = (type) => {
    switch (type) {
      case "HEALTH": return "Sức khỏe";
      case "LIFE": return "Nhân thọ";
      case "VEHICLE": return "Phương tiện";
      default: return type;
    }
  };

  return (
    <div className="min-h-screen bg-[#f8f9fa] flex text-gray-800 font-sans">
      
      {/* 1. LEFT SIDEBAR */}
      <aside className="w-64 bg-slate-900 text-slate-300 border-r border-slate-800 hidden md:flex flex-col justify-between shrink-0">
        <div>
          {/* Logo Brand */}
          <div className="p-6 border-b border-slate-800 flex items-center space-x-3">
            <div className="w-10 h-10 rounded-xl bg-blue-600 flex items-center justify-center text-white shadow-md shadow-blue-200">
              <Shield className="w-6 h-6" />
            </div>
            <div>
              <h2 className="text-xl font-bold tracking-tight text-white">InsuCare</h2>
              <p className="text-[10px] text-slate-500 font-medium tracking-wide uppercase">Admin Workspace</p>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="p-4 space-y-1">
            {NAV_ITEMS.map(({ id, label, icon: Icon }) => (
              <button
                key={id}
                onClick={() => handleTabChange(id)}
                className={`w-full flex items-center space-x-3 px-4 py-2.5 rounded-xl text-sm font-medium transition-all ${
                  activeTab === id
                    ? "bg-slate-800 text-white shadow-sm"
                    : "text-slate-400 hover:bg-slate-800/50 hover:text-white"
                }`}
              >
                <Icon className="w-4.5 h-4.5 shrink-0" />
                <span className="text-left leading-tight">{label}</span>
              </button>
            ))}
          </nav>
        </div>

        {/* User profile segment & Log out */}
        <div className="p-4 border-t border-slate-800 space-y-3">
          <div className="px-4 py-2">
            <p className="text-xs text-slate-500 font-semibold uppercase tracking-wider">Tài khoản admin</p>
            <p className="text-sm font-bold text-white truncate mt-1">{user?.email || "admin@insucare.com"}</p>
          </div>
          <button
            onClick={handleLogout}
            className="w-full flex items-center space-x-3 px-4 py-3 rounded-xl text-sm font-medium text-red-400 hover:bg-red-950/20 hover:text-red-300 transition-all"
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
            <h1 className="text-lg font-bold text-gray-900">Trang quản trị</h1>
          </div>
          <div className="text-sm text-gray-500 font-semibold bg-gray-50 px-3 py-1.5 rounded-lg border border-gray-200">
            Vai trò: ADMIN
          </div>
        </header>

        {/* Content Area */}
        <main className="flex-grow p-6 md:p-8 max-w-6xl w-full mx-auto space-y-8">
          
          {/* Toast Messages */}
          {successMsg && (
            <div className="bg-emerald-50 border border-emerald-100 rounded-xl p-4 flex items-center space-x-3 text-emerald-800 text-sm shadow-sm animate-fade-in">
              <CheckCircle className="w-5 h-5 text-emerald-600 shrink-0" />
              <span className="font-medium">{successMsg}</span>
            </div>
          )}
          {errorMsg && (
            <div className="bg-red-50 border border-red-100 rounded-xl p-4 flex items-center space-x-3 text-red-800 text-sm shadow-sm animate-fade-in">
              <AlertCircle className="w-5 h-5 text-red-600 shrink-0" />
              <span className="font-medium">{errorMsg}</span>
            </div>
          )}

          {/* ================================================================ */}
          {/* TAB 1: OVERVIEW */}
          {/* ================================================================ */}
          {activeTab === "overview" && (
            <div className="space-y-6">
              <div>
                <h2 className="text-2xl font-bold text-gray-900">Tổng quan hệ thống</h2>
                <p className="text-gray-500 text-sm mt-0.5">Quản lý toàn bộ sản phẩm bảo hiểm và cấu hình định giá</p>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <div className="bg-white rounded-2xl border border-gray-100 p-6 flex items-center space-x-4 shadow-sm">
                  <div className="w-12 h-12 rounded-xl bg-blue-50 flex items-center justify-center text-blue-600 shrink-0">
                    <Shield className="w-6 h-6" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Tổng sản phẩm</p>
                    <h3 className="text-3xl font-extrabold text-gray-900 mt-1">
                      {productsLoading ? "..." : products.length}
                    </h3>
                  </div>
                </div>

                <div className="bg-white rounded-2xl border border-gray-100 p-6 flex items-center space-x-4 shadow-sm">
                  <div className="w-12 h-12 rounded-xl bg-emerald-50 flex items-center justify-center text-emerald-600 shrink-0">
                    <CheckCircle className="w-6 h-6" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Đang phát hành</p>
                    <h3 className="text-3xl font-extrabold text-gray-900 mt-1">
                      {productsLoading ? "..." : products.filter(p => p.status === "ACTIVE").length}
                    </h3>
                  </div>
                </div>

                <div className="bg-white rounded-2xl border border-gray-100 p-6 flex items-center space-x-4 shadow-sm">
                  <div className="w-12 h-12 rounded-xl bg-indigo-50 flex items-center justify-center text-indigo-600 shrink-0">
                    <FileCode2 className="w-6 h-6" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Risk Schemas</p>
                    <p className="text-xs text-indigo-600 font-bold mt-2">Xem chi tiết →</p>
                  </div>
                </div>

                <div className="bg-white rounded-2xl border border-gray-100 p-6 flex items-center space-x-4 shadow-sm">
                  <div className="w-12 h-12 rounded-xl bg-violet-50 flex items-center justify-center text-violet-600 shrink-0">
                    <Briefcase className="w-6 h-6" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-gray-400 uppercase tracking-wide">Occupation Map</p>
                    <p className="text-xs text-violet-600 font-bold mt-2">Xem chi tiết →</p>
                  </div>
                </div>
              </div>

              {/* Navigation Shortcuts */}
              <div className="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm space-y-4">
                <h4 className="font-bold text-gray-950">Phím tắt quản trị</h4>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {NAV_ITEMS.filter(n => n.id !== "overview").map(({ id, label, icon: Icon }) => (
                    <button
                      key={id}
                      onClick={() => handleTabChange(id)}
                      className="flex items-center justify-between p-4 bg-gray-50 hover:bg-gray-100 rounded-xl transition-all border border-gray-200 text-left"
                    >
                      <div className="flex items-center space-x-3">
                        <Icon className="w-5 h-5 text-gray-500" />
                        <span className="font-bold text-sm text-gray-900">{label}</span>
                      </div>
                      <ChevronRight className="w-5 h-5 text-gray-400" />
                    </button>
                  ))}
                </div>
              </div>
            </div>
          )}

          {/* ================================================================ */}
          {/* TAB 2: PRODUCTS MANAGEMENT */}
          {/* ================================================================ */}
          {activeTab === "products" && (
            <div className="space-y-6">
              <div className="flex justify-between items-center flex-wrap gap-4">
                <div>
                  <h2 className="text-2xl font-bold text-gray-900">Danh mục sản phẩm bảo hiểm</h2>
                  <p className="text-gray-500 text-sm mt-0.5">Thêm, sửa đổi thông tin sản phẩm bảo hiểm</p>
                </div>
                <button
                  onClick={handleOpenProductCreate}
                  className="flex items-center space-x-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold transition-all shadow-sm"
                >
                  <Plus className="w-4.5 h-4.5" />
                  <span>Thêm sản phẩm</span>
                </button>
              </div>

              {productsLoading ? (
                <div className="flex flex-col justify-center items-center py-20 bg-white rounded-2xl border border-gray-100">
                  <Loader2 className="w-10 h-10 text-blue-600 animate-spin" />
                  <span className="text-sm text-gray-400 mt-3">Đang tải danh sách sản phẩm...</span>
                </div>
              ) : products.length === 0 ? (
                <div className="py-20 text-center text-gray-400 space-y-4 bg-white rounded-2xl border border-gray-100">
                  <p className="text-sm">Chưa có sản phẩm bảo hiểm nào được khai báo.</p>
                </div>
              ) : (() => {
                const totalProductPages = Math.max(1, Math.ceil(products.length / productPageSize));
                const safeProductPage = Math.min(productPage, totalProductPages);
                const paginatedProducts = products.slice((safeProductPage - 1) * productPageSize, safeProductPage * productPageSize);
                return (
                <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
                  <div className="overflow-x-auto">
                    <table className="w-full border-collapse text-left text-sm">
                      <thead>
                        <tr className="border-b border-gray-100 text-gray-400 font-semibold text-xs uppercase bg-gray-50/50">
                          <th className="py-4 px-6">Ảnh sản phẩm</th>
                          <th className="py-4 px-6">Thông tin sản phẩm</th>
                          <th className="py-4 px-6">Loại sản phẩm</th>
                          <th className="py-4 px-6">Trạng thái</th>
                          <th className="py-4 px-6 text-right">Thao tác</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100">
                        {paginatedProducts.map((p) => (
                          <tr key={p.productId} className="hover:bg-gray-50/20 transition-colors">
                            <td className="py-4 px-6">
                              <div className="w-16 h-12 bg-gray-100 border border-gray-200 rounded-lg overflow-hidden flex items-center justify-center">
                                {p.imageUrl ? (
                                  <img src={p.imageUrl} alt={p.name} className="w-full h-full object-cover" />
                                ) : (
                                  <Shield className="w-5 h-5 text-gray-300" />
                                )}
                              </div>
                            </td>
                            <td className="py-4 px-6 max-w-xs">
                              <h4 className="font-bold text-gray-900 text-sm">{p.name}</h4>
                              <p className="text-xs text-gray-400 mt-1 line-clamp-2">{p.description || "Chưa có mô tả chi tiết."}</p>
                              <span className="text-[10px] text-gray-400 font-mono block mt-1">ID: {p.productId}</span>
                            </td>
                            <td className="py-4 px-6">
                              <span className="px-2.5 py-1 bg-gray-150 text-gray-700 text-xs font-semibold rounded-lg border border-gray-200">
                                {getProductTypeLabel(p.productType)}
                              </span>
                            </td>
                            <td className="py-4 px-6">
                              <button
                                onClick={() => handleToggleProductStatus(p)}
                                className={`px-2.5 py-1 text-xs font-bold rounded-lg border transition-all ${
                                  p.status === "ACTIVE"
                                    ? "bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100"
                                    : "bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100"
                                  }`}
                              >
                                {p.status === "ACTIVE" ? "ĐANG BÁN" : "TẠM ẨN"}
                              </button>
                            </td>
                            <td className="py-4 px-6 text-right">
                              <button
                                onClick={() => handleOpenProductEdit(p)}
                                className="p-2 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors inline-flex items-center space-x-1"
                                title="Chỉnh sửa thông tin"
                              >
                                <Edit2 className="w-4 h-4" />
                                <span className="text-xs font-bold">Sửa</span>
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <Pagination
                    currentPage={safeProductPage}
                    totalPages={totalProductPages}
                    onPageChange={setProductPage}
                    pageSize={productPageSize}
                    onPageSizeChange={setProductPageSize}
                    totalItems={products.length}
                  />
                </div>
                );
              })()}
              {productsError && (
                <div className="bg-red-50 border border-red-100 rounded-xl p-4 flex items-center space-x-3 text-red-800 text-sm shadow-sm">
                  <AlertCircle className="w-5 h-5 text-red-600 shrink-0" />
                  <span className="font-medium">{productsError}</span>
                </div>
              )}
            </div>
          )}

          {/* ================================================================ */}
          {/* TAB 3: COVERAGE PLANS MANAGEMENT */}
          {/* ================================================================ */}
          {activeTab === "plans" && (
            <div className="space-y-6">
              
              <div className="flex justify-between items-center flex-wrap gap-4">
                <div>
                  <h2 className="text-2xl font-bold text-gray-900">Quản lý các gói bảo hiểm</h2>
                  <p className="text-gray-500 text-sm mt-0.5">Quy định mức phí đóng cơ bản và số tiền được bảo hiểm</p>
                </div>
                {selectedProductId && (
                  <button
                    onClick={handleOpenPlanCreate}
                    className="flex items-center space-x-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold transition-all shadow-sm"
                  >
                    <Plus className="w-4.5 h-4.5" />
                    <span>Thêm gói mới</span>
                  </button>
                )}
              </div>

              {/* Selector Product */}
              <div className="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                <div className="space-y-1">
                  <label className="text-xs font-bold text-gray-400 uppercase tracking-wider">Chọn sản phẩm bảo hiểm liên kết</label>
                  <p className="text-xs text-gray-500">Các gói bảo hiểm sẽ được thêm vào sản phẩm này</p>
                </div>
                <div className="flex flex-col sm:flex-row gap-3">
                  <select
                    value={selectedProductId}
                    onChange={(e) => {
                      setSelectedProductId(e.target.value);
                      setPlanPage(1);
                    }}
                    className="bg-gray-50 border border-gray-250 rounded-xl px-4 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-blue-500 min-w-[280px]"
                  >
                    {products.length === 0 ? (
                      <option value="">(Không có sản phẩm nào)</option>
                    ) : (
                      products.map((p) => (
                        <option key={p.productId} value={p.productId}>
                          [{getProductTypeLabel(p.productType)}] {p.name}
                        </option>
                      ))
                    )}
                  </select>
                  <select
                    value={planStatusFilter}
                    onChange={(e) => {
                      setPlanStatusFilter(e.target.value);
                      setPlanPage(1);
                    }}
                    className="bg-gray-50 border border-gray-250 rounded-xl px-4 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="">Tất cả trạng thái</option>
                    <option value="ACTIVE">Đang áp dụng</option>
                    <option value="INACTIVE">Tạm dừng</option>
                  </select>
                </div>
              </div>

              {plansError && (
                <div className="bg-red-50 border border-red-100 rounded-xl p-4 flex items-center space-x-3 text-red-800 text-sm shadow-sm">
                  <AlertCircle className="w-5 h-5 text-red-600 shrink-0" />
                  <span className="font-medium">{plansError}</span>
                </div>
              )}

              {/* Plans Table list */}
              {!selectedProductId ? (
                <div className="py-12 text-center text-gray-400 bg-white border border-gray-100 rounded-2xl shadow-sm">
                  Vui lòng chọn hoặc tạo mới 1 sản phẩm bảo hiểm trước.
                </div>
              ) : plansLoading ? (
                <div className="flex flex-col justify-center items-center py-20 bg-white rounded-2xl border border-gray-100">
                  <Loader2 className="w-10 h-10 text-blue-600 animate-spin" />
                  <span className="text-sm text-gray-400 mt-3">Đang tải danh sách gói bảo hiểm...</span>
                </div>
              ) : plans.length === 0 ? (
                <div className="py-20 text-center text-gray-400 space-y-4 bg-white rounded-2xl border border-gray-100">
                  <p className="text-sm">Sản phẩm này hiện tại chưa có gói bảo hiểm nào.</p>
                  <button
                    onClick={handleOpenPlanCreate}
                    className="px-4 py-2 border border-blue-100 text-blue-600 bg-blue-50/50 hover:bg-blue-50 rounded-xl text-xs font-bold transition-all"
                  >
                    Thêm gói đầu tiên
                  </button>
                </div>
              ) : (() => {
                const totalPlanPages = Math.max(1, Math.ceil(plans.length / planPageSize));
                const safePlanPage = Math.min(planPage, totalPlanPages);
                const paginatedPlans = plans.slice((safePlanPage - 1) * planPageSize, safePlanPage * planPageSize);
                return (
                <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
                  <div className="overflow-x-auto">
                    <table className="w-full border-collapse text-left text-sm">
                      <thead>
                        <tr className="border-b border-gray-100 text-gray-400 font-semibold text-xs uppercase bg-gray-50/50">
                          <th className="py-4 px-6">Tên gói bảo hiểm</th>
                          <th className="py-4 px-6">Mô tả quyền lợi</th>
                          <th className="py-4 px-6">Phí đóng cơ bản</th>
                          <th className="py-4 px-6">Số tiền bảo hiểm</th>
                          <th className="py-4 px-6">Trạng thái</th>
                          <th className="py-4 px-6 text-right">Thao tác</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-gray-100">
                        {paginatedPlans.map((pl) => (
                          <tr key={pl.coveragePlanId} className="hover:bg-gray-50/20 transition-colors">
                            <td className="py-4 px-6 font-bold text-gray-900">{pl.planName}</td>
                            <td className="py-4 px-6 text-xs text-gray-500 max-w-xs">{pl.description || "Không có mô tả."}</td>
                            <td className="py-4 px-6 font-bold text-blue-600">
                              {(pl.basePremium || 0).toLocaleString("vi-VN")}đ
                            </td>
                            <td className="py-4 px-6 font-bold text-gray-900">
                              {(pl.sumInsured || 0).toLocaleString("vi-VN")}đ
                            </td>
                            <td className="py-4 px-6">
                              <button
                                onClick={() => handleTogglePlanStatus(pl)}
                                className={`px-2.5 py-1 text-xs font-bold rounded-lg border transition-all ${
                                  pl.status === "ACTIVE"
                                    ? "bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100"
                                    : "bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100"
                                }`}
                              >
                                {pl.status === "ACTIVE" ? "ĐANG ÁP DỤNG" : "TẠM DỪNG"}
                              </button>
                            </td>
                            <td className="py-4 px-6 text-right">
                              <button
                                onClick={() => handleOpenPlanEdit(pl)}
                                className="p-2 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors inline-flex items-center space-x-1"
                              >
                                <Edit2 className="w-4 h-4" />
                                <span className="text-xs font-bold">Sửa</span>
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <Pagination
                    currentPage={safePlanPage}
                    totalPages={totalPlanPages}
                    onPageChange={setPlanPage}
                    pageSize={planPageSize}
                    onPageSizeChange={setPlanPageSize}
                    totalItems={plans.length}
                  />
                </div>
                );
              })()}

            </div>
          )}

          {/* ================================================================ */}
          {/* TAB 4: RISK INPUT SCHEMA */}
          {/* ================================================================ */}
          {activeTab === "risk-schemas" && (
            <RiskSchemaTab products={products} triggerToast={triggerToast} />
          )}

          {/* ================================================================ */}
          {/* TAB 5: OCCUPATION RISK MAPPING */}
          {/* ================================================================ */}
          {activeTab === "occupation-mappings" && (
            <OccupationMappingTab products={products} triggerToast={triggerToast} />
          )}

        </main>
      </div>

      {/* ================================================================ */}
      {/* 3. PRODUCT FORM DIALOG (MODAL) */}
      {/* ================================================================ */}
      {productModalOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg border border-gray-100 overflow-hidden transform scale-100 transition-transform">
            
            <div className="p-6 border-b border-gray-150 flex justify-between items-center bg-gray-50">
              <h3 className="text-lg font-bold text-gray-900">
                {editingProduct ? "Cập nhật sản phẩm bảo hiểm" : "Thêm sản phẩm bảo hiểm mới"}
              </h3>
              <button 
                onClick={() => setProductModalOpen(false)}
                className="text-gray-400 hover:text-gray-600 p-1.5 hover:bg-gray-200 rounded-lg transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleProductSubmit} className="p-6 space-y-4">
              
              {/* Product Type (Show only on create) */}
              {!editingProduct && (
                <div>
                  <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Phân loại sản phẩm</label>
                  <select
                    value={productForm.productType}
                    onChange={(e) => setProductForm({ ...productForm, productType: e.target.value })}
                    className="w-full bg-gray-50 border border-gray-255 rounded-xl px-4 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="HEALTH">Sức khỏe (HEALTH)</option>
                    <option value="LIFE">Nhân thọ (LIFE)</option>
                    <option value="VEHICLE">Phương tiện (VEHICLE)</option>
                  </select>
                </div>
              )}

              {/* Name */}
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Tên sản phẩm bảo hiểm</label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Bảo hiểm sức khỏe an tâm"
                  value={productForm.name}
                  onChange={(e) => setProductForm({ ...productForm, name: e.target.value })}
                  className="w-full bg-gray-50 border border-gray-255 rounded-xl px-4 py-2.5 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              {/* Description */}
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Mô tả chi tiết</label>
                <textarea
                  rows="3"
                  placeholder="Chi tiết sản phẩm, phạm vi quyền lợi bảo hiểm..."
                  value={productForm.description}
                  onChange={(e) => setProductForm({ ...productForm, description: e.target.value })}
                  className="w-full bg-gray-50 border border-gray-255 rounded-xl px-4 py-2.5 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
                ></textarea>
              </div>

              {/* Status */}
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Trạng thái phát hành</label>
                <select
                  value={productForm.status}
                  onChange={(e) => setProductForm({ ...productForm, status: e.target.value })}
                  className="w-full bg-gray-50 border border-gray-255 rounded-xl px-4 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="ACTIVE">ACTIVE (Đang bán)</option>
                  <option value="INACTIVE">INACTIVE (Tạm ẩn)</option>
                </select>
              </div>

              {/* Product Image File Input */}
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Ảnh sản phẩm (Tải lên MinIO)</label>
                <div className="flex items-center space-x-4">
                  {/* Preview of current image or newly selected file */}
                  <div className="w-16 h-16 bg-gray-50 border border-gray-250 rounded-xl overflow-hidden flex items-center justify-center shrink-0">
                    {modalImageFile ? (
                      <img src={URL.createObjectURL(modalImageFile)} alt="Preview" className="w-full h-full object-cover" />
                    ) : productForm.imageUrl ? (
                      <img src={productForm.imageUrl} alt="Current" className="w-full h-full object-cover" />
                    ) : (
                      <Shield className="w-6 h-6 text-gray-300" />
                    )}
                  </div>
                  <label className="flex-grow flex items-center justify-center space-x-2 px-4 py-3 bg-gray-50 hover:bg-gray-100 border border-gray-300 border-dashed rounded-xl cursor-pointer transition-all">
                    <Upload className="w-4 h-4 text-gray-500" />
                    <span className="text-xs font-semibold text-gray-600 truncate max-w-[200px]">
                      {modalImageFile ? modalImageFile.name : "Chọn file ảnh"}
                    </span>
                    <input
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={(e) => setModalImageFile(e.target.files[0] || null)}
                    />
                  </label>
                  {modalImageFile && (
                    <button
                      type="button"
                      onClick={() => setModalImageFile(null)}
                      className="p-2 text-red-500 hover:bg-red-50 rounded-lg transition-colors"
                      title="Hủy chọn file"
                    >
                      <X className="w-5 h-5" />
                    </button>
                  )}
                </div>
              </div>

              {/* Action buttons */}
              <div className="flex space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setProductModalOpen(false)}
                  className="flex-1 py-2.5 border border-gray-200 hover:bg-gray-50 rounded-xl text-sm font-bold transition-all text-gray-600"
                >
                  Đóng
                </button>
                <button
                  type="submit"
                  disabled={productSubmitting}
                  className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white rounded-xl text-sm font-bold transition-all flex items-center justify-center space-x-1.5 shadow-sm"
                >
                  {productSubmitting && <Loader2 className="w-4 h-4 animate-spin" />}
                  <span>{editingProduct ? "Lưu thay đổi" : "Tạo sản phẩm"}</span>
                </button>
              </div>

            </form>
          </div>
        </div>
      )}

      {/* ================================================================ */}
      {/* 4. COVERAGE PLAN FORM DIALOG (MODAL) */}
      {/* ================================================================ */}
      {planModalOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-fade-in">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg border border-gray-100 overflow-hidden transform scale-100 transition-transform">
            
            <div className="p-6 border-b border-gray-150 flex justify-between items-center bg-gray-50">
              <h3 className="text-lg font-bold text-gray-900">
                {editingPlan ? "Cập nhật gói bảo hiểm" : "Thêm gói bảo hiểm mới"}
              </h3>
              <button 
                onClick={() => setPlanModalOpen(false)}
                className="text-gray-400 hover:text-gray-600 p-1.5 hover:bg-gray-200 rounded-lg transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handlePlanSubmit} className="p-6 space-y-4">
              
              {/* Plan Name */}
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Tên gói bảo hiểm</label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Gói Cơ Bản, Gói Toàn Diện"
                  value={planForm.planName}
                  onChange={(e) => setPlanForm({ ...planForm, planName: e.target.value })}
                  className="w-full bg-gray-50 border border-gray-255 rounded-xl px-4 py-2.5 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              {/* Description */}
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Quyền lợi chi tiết</label>
                <textarea
                  rows="3"
                  placeholder="Mô tả các hạn mức, quyền lợi hỗ trợ nội/ngoại trú của gói..."
                  value={planForm.description}
                  onChange={(e) => setPlanForm({ ...planForm, description: e.target.value })}
                  className="w-full bg-gray-50 border border-gray-255 rounded-xl px-4 py-2.5 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
                ></textarea>
              </div>

              {/* Base Premium */}
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Phí đóng cơ bản (VND)</label>
                <input
                  type="number"
                  required
                  placeholder="Ví dụ: 1000000"
                  value={planForm.basePremium}
                  onChange={(e) => setPlanForm({ ...planForm, basePremium: e.target.value })}
                  className="w-full bg-gray-50 border border-gray-255 rounded-xl px-4 py-2.5 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              {/* Sum Insured */}
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Số tiền bảo hiểm tối đa (VND)</label>
                <input
                  type="number"
                  required
                  placeholder="Ví dụ: 200000000"
                  value={planForm.sumInsured}
                  onChange={(e) => setPlanForm({ ...planForm, sumInsured: e.target.value })}
                  className="w-full bg-gray-50 border border-gray-255 rounded-xl px-4 py-2.5 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>

              {/* Status */}
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Trạng thái gói bảo hiểm</label>
                <select
                  value={planForm.status}
                  onChange={(e) => setPlanForm({ ...planForm, status: e.target.value })}
                  className="w-full bg-gray-50 border border-gray-255 rounded-xl px-4 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="ACTIVE">ACTIVE (Đang áp dụng)</option>
                  <option value="INACTIVE">INACTIVE (Tạm dừng)</option>
                </select>
              </div>

              {/* Action buttons */}
              <div className="flex space-x-3 pt-2">
                <button
                  type="button"
                  onClick={() => setPlanModalOpen(false)}
                  className="flex-1 py-2.5 border border-gray-200 hover:bg-gray-50 rounded-xl text-sm font-bold transition-all text-gray-600"
                >
                  Đóng
                </button>
                <button
                  type="submit"
                  disabled={planSubmitting}
                  className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white rounded-xl text-sm font-bold transition-all flex items-center justify-center space-x-1.5 shadow-sm"
                >
                  {planSubmitting && <Loader2 className="w-4 h-4 animate-spin" />}
                  <span>{editingPlan ? "Lưu thay đổi" : "Thêm gói"}</span>
                </button>
              </div>

            </form>
          </div>
        </div>
      )}

    </div>
  );
}
