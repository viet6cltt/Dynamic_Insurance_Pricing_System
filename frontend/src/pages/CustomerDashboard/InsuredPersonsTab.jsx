import { useCallback, useEffect, useState } from "react";
import { AlertCircle, CheckCircle, Edit2, Loader2, Plus, UserRound, X } from "lucide-react";
import { customerService } from "../../services/customerService";
import Pagination from "../../components/Pagination";

const EMPTY_FORM = {
  fullName: "",
  dateOfBirth: "",
  gender: "MALE",
  identityNumber: "",
  relationshipToOwner: "FAMILY",
};

function relationshipLabel(value) {
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

function normalizeInsuredForm(form) {
  return {
    ...form,
    fullName: form.fullName.trim(),
    identityNumber: form.identityNumber.trim(),
    dateOfBirth: form.dateOfBirth || null,
    linkedUserProfileId: null,
  };
}

function validateInsuredForm(form) {
  if (!form.fullName.trim()) return "Vui lòng nhập họ tên người được bảo hiểm.";
  if (!form.dateOfBirth) return "Vui lòng nhập ngày sinh.";
  if (!form.identityNumber.trim()) return "Vui lòng nhập CCCD/CMND.";
  return "";
}

export default function InsuredPersonsTab() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState("ACTIVE");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingPerson, setEditingPerson] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [toast, setToast] = useState(null);

  const showToast = (msg, isError = false) => {
    setToast({ msg, isError });
    setTimeout(() => setToast(null), 4000);
  };

  const fetchPeople = useCallback(async () => {
    setLoading(true);
    try {
      const response = await customerService.getMyInsuredPersons({
        page: page - 1,
        size: pageSize,
        status: statusFilter || undefined,
      });
      const nextItems = response?.items || response?.content || response || [];
      setItems(nextItems);
      setTotalItems(response?.totalElements ?? nextItems.length);
      setTotalPages(response?.totalPages ?? Math.max(1, Math.ceil(nextItems.length / pageSize)));
    } catch {
      setItems([]);
      setTotalItems(0);
      setTotalPages(1);
      showToast("Không thể tải danh sách người được bảo hiểm.", true);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, statusFilter]);

  useEffect(() => {
    let alive = true;
    async function load() {
      await Promise.resolve();
      if (alive) fetchPeople();
    }
    load();
    return () => { alive = false; };
  }, [fetchPeople]);

  const openCreate = () => {
    setEditingPerson(null);
    setForm(EMPTY_FORM);
    setModalOpen(true);
  };

  const openEdit = (person) => {
    setEditingPerson(person);
    setForm({
      fullName: person.fullName || "",
      dateOfBirth: person.dateOfBirth || "",
      gender: person.gender || "MALE",
      identityNumber: person.identityNumber || "",
      relationshipToOwner: person.relationshipToOwner || "FAMILY",
    });
    setModalOpen(true);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    const validationMessage = validateInsuredForm(form);
    if (validationMessage) {
      showToast(validationMessage, true);
      return;
    }
    setSubmitting(true);
    try {
      const payload = normalizeInsuredForm(form);
      if (editingPerson) {
        await customerService.updateInsuredPerson(editingPerson.insuredPersonId, payload);
        showToast("Đã cập nhật người được bảo hiểm.");
      } else {
        await customerService.createInsuredPerson(payload);
        showToast("Đã thêm người được bảo hiểm.");
      }
      setModalOpen(false);
      fetchPeople();
    } catch (err) {
      showToast(err.response?.data?.message || err.message || "Thao tác thất bại.", true);
    } finally {
      setSubmitting(false);
    }
  };

  const handleDeactivate = async (person) => {
    try {
      await customerService.deactivateInsuredPerson(person.insuredPersonId);
      showToast("Đã ngưng sử dụng hồ sơ người được bảo hiểm.");
      fetchPeople();
    } catch (err) {
      showToast(err.response?.data?.message || err.message || "Không thể ngưng sử dụng hồ sơ.", true);
    }
  };

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

      <div className="flex justify-between items-center flex-wrap gap-4">
        <div>
          <h2 className="text-2xl font-bold text-gray-900">Người được bảo hiểm</h2>
          <p className="text-gray-500 text-sm mt-0.5">Quản lý các hồ sơ có thể được chọn khi đăng ký gói bảo hiểm</p>
        </div>
        <button
          onClick={openCreate}
          className="flex items-center space-x-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-700 text-white rounded-xl text-sm font-semibold transition-all shadow-sm"
        >
          <Plus className="w-4 h-4" />
          <span>Thêm người</span>
        </button>
      </div>

      <div className="bg-white rounded-2xl border border-gray-100 p-4 shadow-sm flex justify-end">
        <select
          value={statusFilter}
          onChange={(event) => {
            setStatusFilter(event.target.value);
            setPage(1);
          }}
          className="bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-blue-500"
        >
          <option value="">Tất cả trạng thái</option>
          <option value="ACTIVE">Đang sử dụng</option>
          <option value="INACTIVE">Ngưng sử dụng</option>
        </select>
      </div>

      {loading ? (
        <div className="flex flex-col justify-center items-center py-20 bg-white rounded-2xl border border-gray-100">
          <Loader2 className="w-10 h-10 text-blue-600 animate-spin" />
          <span className="text-sm text-gray-400 mt-3">Đang tải danh sách...</span>
        </div>
      ) : items.length === 0 ? (
        <div className="py-20 text-center text-gray-400 space-y-4 bg-white rounded-2xl border border-gray-100">
          <UserRound className="w-12 h-12 text-gray-200 mx-auto" />
          <p className="text-sm">Chưa có người được bảo hiểm nào.</p>
        </div>
      ) : (
        <div className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-gray-100 text-gray-400 font-semibold text-xs uppercase bg-gray-50/50">
                  <th className="py-4 px-6">Họ tên</th>
                  <th className="py-4 px-6">Ngày sinh</th>
                  <th className="py-4 px-6">Giới tính</th>
                  <th className="py-4 px-6">Quan hệ</th>
                  <th className="py-4 px-6">Trạng thái</th>
                  <th className="py-4 px-6 text-right">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {items.map((person) => (
                  <tr key={person.insuredPersonId} className="hover:bg-gray-50/30 transition-colors">
                    <td className="py-4 px-6">
                      <p className="font-bold text-gray-900">{person.fullName}</p>
                      <p className="text-[10px] text-gray-400 font-mono mt-1">{person.identityNumber || person.insuredPersonId}</p>
                    </td>
                    <td className="py-4 px-6 text-gray-600">{person.dateOfBirth || "-"}</td>
                    <td className="py-4 px-6 text-gray-600">{person.gender === "FEMALE" ? "Nữ" : "Nam"}</td>
                    <td className="py-4 px-6">
                      <span className="px-2.5 py-1 bg-blue-50 text-blue-700 text-xs font-bold rounded-lg">
                        {relationshipLabel(person.relationshipToOwner)}
                      </span>
                    </td>
                    <td className="py-4 px-6">
                      <span className={`px-2.5 py-1 text-xs font-bold rounded-lg border ${
                        person.status === "ACTIVE"
                          ? "bg-emerald-50 text-emerald-700 border-emerald-100"
                          : "bg-gray-50 text-gray-500 border-gray-200"
                      }`}>
                        {person.status === "ACTIVE" ? "Đang sử dụng" : "Ngưng sử dụng"}
                      </span>
                    </td>
                    <td className="py-4 px-6 text-right space-x-2">
                      <button
                        onClick={() => openEdit(person)}
                        className="p-2 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors inline-flex items-center space-x-1"
                      >
                        <Edit2 className="w-4 h-4" />
                        <span className="text-xs font-bold">Sửa</span>
                      </button>
                      {person.status === "ACTIVE" && person.relationshipToOwner !== "SELF" && (
                        <button
                          onClick={() => handleDeactivate(person)}
                          className="p-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors inline-flex items-center space-x-1"
                        >
                          <X className="w-4 h-4" />
                          <span className="text-xs font-bold">Ngưng</span>
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            currentPage={page}
            totalPages={totalPages}
            onPageChange={setPage}
            pageSize={pageSize}
            onPageSizeChange={(nextSize) => {
              setPageSize(nextSize);
              setPage(1);
            }}
            totalItems={totalItems}
          />
        </div>
      )}

      {modalOpen && (
        <div className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-lg border border-gray-100 overflow-hidden">
            <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-gray-50">
              <h3 className="text-lg font-bold text-gray-900">
                {editingPerson ? "Cập nhật người được bảo hiểm" : "Thêm người được bảo hiểm"}
              </h3>
              <button onClick={() => setModalOpen(false)} className="p-1.5 hover:bg-gray-200 rounded-lg text-gray-400 hover:text-gray-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSubmit} className="p-6 space-y-4">
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Họ tên</label>
                <input
                  required
                  value={form.fullName}
                  onChange={(event) => setForm({ ...form, fullName: event.target.value })}
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Ngày sinh</label>
                  <input
                    type="date"
                    required
                    value={form.dateOfBirth}
                    onChange={(event) => setForm({ ...form, dateOfBirth: event.target.value })}
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Giới tính</label>
                  <select
                    value={form.gender}
                    onChange={(event) => setForm({ ...form, gender: event.target.value })}
                    className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-blue-500"
                  >
                    <option value="MALE">Nam</option>
                    <option value="FEMALE">Nữ</option>
                  </select>
                </div>
              </div>
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">CCCD/CMND <span className="text-red-500">*</span></label>
                <input
                  required
                  value={form.identityNumber}
                  onChange={(event) => setForm({ ...form, identityNumber: event.target.value.trimStart() })}
                  placeholder="Nhập số CCCD/CMND"
                  inputMode="numeric"
                  autoComplete="off"
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div>
                <label className="block text-xs font-bold text-gray-400 uppercase tracking-wider mb-1.5">Quan hệ với chủ tài khoản</label>
                <select
                  value={form.relationshipToOwner}
                  disabled={editingPerson?.relationshipToOwner === "SELF"}
                  onChange={(event) => setForm({ ...form, relationshipToOwner: event.target.value })}
                  className="w-full bg-gray-50 border border-gray-200 rounded-xl px-4 py-2.5 text-sm font-semibold focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:text-gray-400"
                >
                  <option value="SELF">Bản thân</option>
                  <option value="SPOUSE">Vợ/chồng</option>
                  <option value="CHILD">Con</option>
                  <option value="PARENT">Cha/mẹ</option>
                  <option value="FAMILY">Người thân</option>
                  <option value="OTHER">Khác</option>
                </select>
              </div>

              <div className="flex space-x-3 pt-2">
                <button type="button" onClick={() => setModalOpen(false)} className="flex-1 py-2.5 border border-gray-200 hover:bg-gray-50 rounded-xl text-sm font-bold text-gray-600">
                  Đóng
                </button>
                <button type="submit" disabled={submitting} className="flex-1 py-2.5 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-300 text-white rounded-xl text-sm font-bold flex items-center justify-center space-x-2">
                  {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
                  <span>{editingPerson ? "Lưu thay đổi" : "Thêm người"}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
