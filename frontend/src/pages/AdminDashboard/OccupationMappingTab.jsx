import React, { useEffect, useState } from "react";
import {
  Briefcase, Plus, Edit2, Loader2, X, Save,
  AlertCircle, Search, Filter
} from "lucide-react";
import { adminService } from "../../services/adminService";
import Pagination from "../../components/Pagination";

const RISK_LEVELS = ["LOW", "MODERATE", "HIGH"];

const RISK_LEVEL_STYLE = {
  LOW: "bg-emerald-50 text-emerald-700 border-emerald-200",
  MODERATE: "bg-amber-50 text-amber-700 border-amber-200",
  HIGH: "bg-red-50 text-red-700 border-red-200",
};

const RISK_LEVEL_LABEL = {
  LOW: "Thấp",
  MODERATE: "Trung bình",
  HIGH: "Cao",
};

const EMPTY_FORM = { occupationCode: "", occupationName: "", riskLevel: "LOW", status: "ACTIVE" };
const DEFAULT_PAGE_SIZE = 10;

export default function OccupationMappingTab({ products, triggerToast }) {
  const [selectedProductId, setSelectedProductId] = useState(products[0]?.productId || "");
  const [mappings, setMappings] = useState([]);
  const [loading, setLoading] = useState(false);
  const [search, setSearch] = useState("");
  const [filterStatus, setFilterStatus] = useState("ALL");

  // Pagination state
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const [modalOpen, setModalOpen] = useState(false);
  const [editingMapping, setEditingMapping] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);

  const fetchMappings = async (productId) => {
    if (!productId) return;
    setLoading(true);
    try {
      const data = await adminService.getOccupationRiskMappings(productId);
      setMappings(Array.isArray(data) ? data : []);
    } catch {
      setMappings([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (selectedProductId) fetchMappings(selectedProductId);
  }, [selectedProductId]);

  useEffect(() => {
    if (products.length > 0 && !selectedProductId) {
      setSelectedProductId(products[0].productId);
    }
  }, [products]);

  // Reset về trang 1 khi search/filter/product thay đổi
  useEffect(() => { setCurrentPage(1); }, [search, filterStatus, selectedProductId]);

  const openCreate = () => {
    setEditingMapping(null);
    setForm(EMPTY_FORM);
    setModalOpen(true);
  };

  const openEdit = (mapping) => {
    setEditingMapping(mapping);
    setForm({
      occupationCode: mapping.occupationCode,
      occupationName: mapping.occupationName,
      riskLevel: mapping.riskLevel,
      status: mapping.status,
    });
    setModalOpen(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      if (editingMapping) {
        await adminService.updateOccupationRiskMapping(editingMapping.mappingId, form);
        triggerToast("Cập nhật Occupation Risk Mapping thành công!");
      } else {
        await adminService.createOccupationRiskMapping(selectedProductId, form);
        triggerToast("Thêm Occupation Risk Mapping thành công!");
      }
      setModalOpen(false);
      fetchMappings(selectedProductId);
    } catch {
      triggerToast("Thao tác thất bại. Vui lòng kiểm tra lại dữ liệu.", true);
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleStatus = async (mapping) => {
    const newStatus = mapping.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    try {
      await adminService.updateOccupationRiskMappingStatus(mapping.mappingId, newStatus);
      triggerToast(`Đã chuyển trạng thái sang ${newStatus}!`);
      fetchMappings(selectedProductId);
    } catch {
      triggerToast("Cập nhật trạng thái thất bại.", true);
    }
  };

  const getProductTypeLabel = (type) => {
    const map = { HEALTH: "Sức khỏe", LIFE: "Nhân thọ", VEHICLE: "Phương tiện" };
    return map[type] || type;
  };

  // Filtered mappings
  const filtered = mappings.filter((m) => {
    const matchSearch = !search ||
      m.occupationCode.toLowerCase().includes(search.toLowerCase()) ||
      m.occupationName.toLowerCase().includes(search.toLowerCase());
    const matchStatus = filterStatus === "ALL" || m.status === filterStatus;
    return matchSearch && matchStatus;
  });

  // Paginated slice
  const totalPages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const safePage = Math.min(currentPage, totalPages);
  const paginated = filtered.slice((safePage - 1) * pageSize, safePage * pageSize);

  const stats = {
    total: mappings.length,
    active: mappings.filter(m => m.status === "ACTIVE").length,
    byLevel: RISK_LEVELS.reduce((acc, lvl) => ({
      ...acc,
      [lvl]: mappings.filter(m => m.riskLevel === lvl).length
    }), {})
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center flex-wrap gap-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Quản lý Occupation Risk Mapping</h2>
          <p className="text-gray-500 text-sm mt-0.5">
            Ánh xạ nghề nghiệp với mức độ rủi ro tương ứng để tính phí bảo hiểm
          </p>
        </div>
        {selectedProductId && (
          <button
            onClick={openCreate}
            className="flex items-center space-x-2 px-4 py-2.5 bg-violet-600 hover:bg-violet-700 text-white rounded-xl text-sm font-semibold transition-all shadow-sm"
          >
            <Plus className="w-4 h-4" />
            <span>Thêm nghề nghiệp</span>
          </button>
        )}
      </div>

      {/* Product Selector */}
      <div className="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="space-y-1">
          <label className="text-xs font-bold text-gray-400 uppercase tracking-wider">Chọn sản phẩm bảo hiểm</label>
          <p className="text-xs text-gray-500">Danh sách nghề nghiệp được áp dụng cho sản phẩm này</p>
        </div>
        <select
          value={selectedProductId}
          onChange={(e) => { setSelectedProductId(e.target.value); setSearch(""); setFilterStatus("ALL"); }}
          className="bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-violet-500 min-w-[280px]"
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

      {/* Stats Summary */}
      {!loading && mappings.length > 0 && (
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
          <div className="bg-white rounded-xl border border-gray-100 p-4 shadow-sm text-center">
            <p className="text-xs font-semibold text-gray-400 uppercase">Tổng</p>
            <p className="text-2xl font-extrabold text-gray-900 mt-1">{stats.total}</p>
          </div>
          <div className="bg-white rounded-xl border border-gray-100 p-4 shadow-sm text-center">
            <p className="text-xs font-semibold text-gray-400 uppercase">Đang dùng</p>
            <p className="text-2xl font-extrabold text-emerald-600 mt-1">{stats.active}</p>
          </div>
          {RISK_LEVELS.map(lvl => (
            <div key={lvl} className="bg-white rounded-xl border border-gray-100 p-4 shadow-sm text-center">
              <p className="text-xs font-semibold text-gray-400 uppercase">{RISK_LEVEL_LABEL[lvl]}</p>
              <p className="text-2xl font-extrabold text-gray-700 mt-1">{stats.byLevel[lvl]}</p>
            </div>
          ))}
        </div>
      )}

      {/* Search & Filter bar */}
      {selectedProductId && (
        <div className="bg-white rounded-2xl border border-gray-100 p-4 shadow-sm flex flex-wrap gap-3 items-center">
          <div className="flex items-center space-x-2 bg-gray-50 border border-gray-200 rounded-xl px-3 py-2 flex-1 min-w-[200px]">
            <Search className="w-4 h-4 text-gray-400 shrink-0" />
            <input
              type="text"
              placeholder="Tìm theo mã hoặc tên nghề nghiệp..."
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="bg-transparent text-sm focus:outline-none w-full"
            />
          </div>
          <div className="flex items-center space-x-2 bg-gray-50 border border-gray-200 rounded-xl px-3 py-2">
            <Filter className="w-4 h-4 text-gray-400" />
            <select
              value={filterStatus}
              onChange={(e) => setFilterStatus(e.target.value)}
              className="bg-transparent text-sm font-semibold focus:outline-none"
            >
              <option value="ALL">Tất cả trạng thái</option>
              <option value="ACTIVE">ACTIVE</option>
              <option value="INACTIVE">INACTIVE</option>
            </select>
          </div>
        </div>
      )}

      {/* Mappings Table */}
      {!selectedProductId ? (
        <div className="py-12 text-center text-gray-400 bg-white border border-gray-100 rounded-2xl shadow-sm">
          Vui lòng chọn một sản phẩm bảo hiểm trước.
        </div>
      ) : loading ? (
        <div className="flex flex-col items-center py-20 bg-white rounded-2xl border border-gray-100">
          <Loader2 className="w-10 h-10 text-violet-600 animate-spin" />
          <span className="text-sm text-gray-400 mt-3">Đang tải danh sách nghề nghiệp...</span>
        </div>
      ) : filtered.length === 0 ? (
        <div className="py-20 text-center bg-white rounded-2xl border border-gray-100 space-y-4">
          <Briefcase className="w-12 h-12 text-gray-200 mx-auto" />
          <p className="text-gray-400 text-sm">
            {mappings.length === 0 ? "Chưa có dữ liệu nghề nghiệp nào." : "Không tìm thấy kết quả phù hợp."}
          </p>
          {mappings.length === 0 && (
            <button
              onClick={openCreate}
              className="px-4 py-2 border border-violet-100 text-violet-600 bg-violet-50/50 hover:bg-violet-50 rounded-xl text-xs font-bold transition-all"
            >
              Thêm nghề nghiệp đầu tiên
            </button>
          )}
        </div>
      ) : (
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-gray-100 text-gray-400 font-semibold text-xs uppercase bg-gray-50/50">
                  <th className="py-4 px-6">Mã nghề nghiệp</th>
                  <th className="py-4 px-6">Tên nghề nghiệp</th>
                  <th className="py-4 px-6">Mức độ rủi ro</th>
                  <th className="py-4 px-6">Trạng thái</th>
                  <th className="py-4 px-6">Cập nhật</th>
                  <th className="py-4 px-6 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {paginated.map((m) => (
                  <tr key={m.mappingId} className="hover:bg-gray-50/30 transition-colors">
                    <td className="py-4 px-6">
                      <code className="text-xs bg-gray-100 text-gray-700 px-2 py-1 rounded-md font-mono">
                        {m.occupationCode}
                      </code>
                    </td>
                    <td className="py-4 px-6 font-medium text-gray-800">{m.occupationName}</td>
                    <td className="py-4 px-6">
                      <span className={`px-2.5 py-1 text-xs font-bold rounded-lg border ${RISK_LEVEL_STYLE[m.riskLevel] || "bg-gray-50 text-gray-600 border-gray-200"}`}>
                        {RISK_LEVEL_LABEL[m.riskLevel] || m.riskLevel}
                      </span>
                    </td>
                    <td className="py-4 px-6">
                      <button
                        onClick={() => handleToggleStatus(m)}
                        className={`px-2.5 py-1 text-xs font-bold rounded-lg border transition-all ${
                          m.status === "ACTIVE"
                            ? "bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100"
                            : "bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100"
                        }`}
                      >
                        {m.status === "ACTIVE" ? "ĐANG DÙNG" : "TẠM DỪNG"}
                      </button>
                    </td>
                    <td className="py-4 px-6 text-xs text-gray-400">
                      {new Date(m.updatedAt).toLocaleDateString("vi-VN")}
                    </td>
                    <td className="py-4 px-6 text-right">
                      <button
                        onClick={() => openEdit(m)}
                        className="p-2 text-violet-600 hover:bg-violet-50 rounded-lg transition-colors inline-flex items-center space-x-1"
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

          {/* Pagination */}
          <Pagination
            currentPage={safePage}
            totalPages={totalPages}
            onPageChange={setCurrentPage}
            pageSize={pageSize}
            onPageSizeChange={setPageSize}
            totalItems={filtered.length}
          />
        </div>
      )}

      {/* Modal */}
      {modalOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg border border-gray-100 overflow-hidden">
            <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-gray-50">
              <h3 className="text-lg font-bold text-gray-900">
                {editingMapping ? "Cập nhật Occupation Risk Mapping" : "Thêm Occupation Risk Mapping mới"}
              </h3>
              <button onClick={() => setModalOpen(false)} className="p-1.5 hover:bg-gray-200 rounded-lg transition-colors">
                <X className="w-5 h-5 text-gray-400" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">
                  Mã nghề nghiệp
                </label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: OCC_001, DRIVER, ENGINEER"
                  value={form.occupationCode}
                  onChange={(e) => setForm({ ...form, occupationCode: e.target.value })}
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-mono focus:outline-none focus:ring-2 focus:ring-violet-500"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">
                  Tên nghề nghiệp
                </label>
                <input
                  type="text"
                  required
                  placeholder="Ví dụ: Lái xe tải, Kỹ sư xây dựng"
                  value={form.occupationName}
                  onChange={(e) => setForm({ ...form, occupationName: e.target.value })}
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-violet-500"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">
                  Mức độ rủi ro
                </label>
                <div className="grid grid-cols-3 gap-2">
                  {RISK_LEVELS.map(lvl => (
                    <button
                      key={lvl}
                      type="button"
                      onClick={() => setForm({ ...form, riskLevel: lvl })}
                      className={`py-2 px-3 rounded-xl text-xs font-bold border transition-all ${
                        form.riskLevel === lvl
                          ? `${RISK_LEVEL_STYLE[lvl]} ring-2 ring-offset-1 ring-violet-400`
                          : "bg-gray-50 text-gray-500 border-gray-200 hover:bg-gray-100"
                      }`}
                    >
                      {RISK_LEVEL_LABEL[lvl]}
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Trạng thái</label>
                <select
                  value={form.status}
                  onChange={(e) => setForm({ ...form, status: e.target.value })}
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-violet-500"
                >
                  <option value="ACTIVE">ACTIVE (Đang áp dụng)</option>
                  <option value="INACTIVE">INACTIVE (Tạm dừng)</option>
                </select>
              </div>
              <div className="flex space-x-3 pt-2">
                <button type="button" onClick={() => setModalOpen(false)}
                  className="flex-1 py-2.5 border border-gray-200 hover:bg-gray-50 rounded-xl text-sm font-bold text-gray-600 transition-all">
                  Đóng
                </button>
                <button type="submit" disabled={submitting}
                  className="flex-1 py-2.5 bg-violet-600 hover:bg-violet-700 disabled:bg-violet-300 text-white rounded-xl text-sm font-bold transition-all flex items-center justify-center space-x-2 shadow-sm">
                  {submitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Save className="w-4 h-4" />}
                  <span>{editingMapping ? "Lưu thay đổi" : "Thêm mới"}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
