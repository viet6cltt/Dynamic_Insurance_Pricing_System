import React, { useEffect, useState } from "react";
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from "react-router-dom";
import { useAuthStore } from "../store/authStore";
import { authService } from "../services/authService";
import Login from "../pages/Login/Login";
import Register from "../pages/Register/Register";
import OAuth2Callback from "../pages/Auth/OAuth2Callback";
import CompleteProfile from "../pages/Auth/CompleteProfile";
import { ShieldCheck, LogOut, User, Phone, Mail, CreditCard, Calendar, UserCheck, Edit3 } from "lucide-react";

// Protected Route Component
function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuthStore();
  return isAuthenticated ? children : <Navigate to="/login" replace />;
}

// Dashboard Page Component (with Profile update form)
function Dashboard() {
  const { user, logout } = useAuthStore();
  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState("");
  const [successMsg, setSuccessMsg] = useState("");
  
  // Edit Form Fields
  const [fullName, setFullName] = useState("");
  const [phoneNumber, setPhoneNumber] = useState("");
  const [identityNumber, setIdentityNumber] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [gender, setGender] = useState("MALE");
  const [isEditing, setIsEditing] = useState(false);

  const navigate = useNavigate();

  const fetchProfile = async () => {
    try {
      setLoading(true);
      const data = await authService.getUserProfile();
      setProfile(data);
      setFullName(data.fullName || "");
      setPhoneNumber(data.phoneNumber || "");
      setIdentityNumber(data.identityNumber || "");
      setDateOfBirth(data.dateOfBirth || "");
      setGender(data.gender || "MALE");
    } catch (err) {
      console.error(err);
      setErrorMsg("Không thể tải thông tin hồ sơ từ hệ thống.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProfile();
  }, []);

  const handleUpdate = async (e) => {
    e.preventDefault();
    try {
      setErrorMsg("");
      setSuccessMsg("");
      const updated = await authService.updateUserProfile({
        fullName,
        phoneNumber,
        identityNumber,
        dateOfBirth: dateOfBirth || null,
        gender
      });
      setProfile(updated);
      setSuccessMsg("Cập nhật thông tin hồ sơ thành công!");
      setIsEditing(false);
    } catch (err) {
      setErrorMsg(err.message || "Cập nhật hồ sơ thất bại.");
    }
  };

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      {/* Navigation Header */}
      <nav className="bg-white border-b border-gray-200 py-4 px-6 md:px-12 flex justify-between items-center shadow-sm">
        <div className="flex items-center space-x-2 text-blue-600">
          <ShieldCheck className="w-8 h-8" />
          <span className="text-lg font-bold text-gray-900 tracking-tight">Dynamic Insurance Portal</span>
        </div>
        <div className="flex items-center space-x-4">
          <div className="text-right hidden sm:block">
            <p className="text-sm font-semibold text-gray-900">{user?.name || "Khách hàng"}</p>
            <p className="text-xs text-gray-500">{user?.email}</p>
          </div>
          <button
            onClick={handleLogout}
            className="flex items-center space-x-1 px-3.5 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 transition-colors shadow-sm"
          >
            <LogOut className="w-4 h-4" />
            <span>Đăng xuất</span>
          </button>
        </div>
      </nav>

      {/* Main Container */}
      <main className="flex-grow max-w-4xl w-full mx-auto px-4 py-8 md:py-12">
        <div className="bg-white rounded-2xl border border-gray-200 shadow-sm overflow-hidden">
          
          {/* Cover/Banner */}
          <div className="bg-gradient-to-r from-blue-600 to-indigo-800 h-32 px-6 py-4 flex items-end">
            <h1 className="text-2xl font-bold text-white tracking-tight">Hồ sơ khách hàng</h1>
          </div>

          <div className="p-6 md:p-8">
            {errorMsg && (
              <div className="bg-red-50 border-l-4 border-red-500 p-4 rounded text-sm text-red-700 mb-6">
                {errorMsg}
              </div>
            )}
            {successMsg && (
              <div className="bg-green-50 border-l-4 border-green-500 p-4 rounded text-sm text-green-700 font-medium mb-6">
                {successMsg}
              </div>
            )}

            {loading ? (
              <div className="flex flex-col justify-center items-center py-12">
                <svg className="animate-spin h-10 w-10 text-blue-600 mb-3" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span className="text-sm text-gray-500">Đang tải thông tin hồ sơ...</span>
              </div>
            ) : (
              <div>
                {!isEditing ? (
                  // Read-Only Profile View
                  <div className="space-y-6">
                    <div className="flex justify-between items-center pb-4 border-b border-gray-100">
                      <div>
                        <h2 className="text-xl font-bold text-gray-900">{profile?.fullName || "Chưa thiết lập tên"}</h2>
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800 mt-1">
                          Trạng thái: {profile?.customerStatus || "ACTIVE"}
                        </span>
                      </div>
                      <button
                        onClick={() => setIsEditing(true)}
                        className="flex items-center space-x-1.5 px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors shadow-sm"
                      >
                        <Edit3 className="w-4 h-4" />
                        <span>Chỉnh sửa</span>
                      </button>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                      <div className="flex items-center space-x-3 text-gray-700">
                        <Mail className="w-5 h-5 text-gray-400" />
                        <div>
                          <p className="text-xs text-gray-400">Email</p>
                          <p className="text-sm font-medium">{profile?.email || "N/A"}</p>
                        </div>
                      </div>

                      <div className="flex items-center space-x-3 text-gray-700">
                        <Phone className="w-5 h-5 text-gray-400" />
                        <div>
                          <p className="text-xs text-gray-400">Số điện thoại</p>
                          <p className="text-sm font-medium">{profile?.phoneNumber || "N/A"}</p>
                        </div>
                      </div>

                      <div className="flex items-center space-x-3 text-gray-700">
                        <CreditCard className="w-5 h-5 text-gray-400" />
                        <div>
                          <p className="text-xs text-gray-400">Số CCCD / Hộ chiếu</p>
                          <p className="text-sm font-medium">{profile?.identityNumber || "N/A"}</p>
                        </div>
                      </div>

                      <div className="flex items-center space-x-3 text-gray-700">
                        <Calendar className="w-5 h-5 text-gray-400" />
                        <div>
                          <p className="text-xs text-gray-400">Ngày sinh</p>
                          <p className="text-sm font-medium">{profile?.dateOfBirth || "N/A"}</p>
                        </div>
                      </div>

                      <div className="flex items-center space-x-3 text-gray-700">
                        <UserCheck className="w-5 h-5 text-gray-400" />
                        <div>
                          <p className="text-xs text-gray-400">Giới tính</p>
                          <p className="text-sm font-medium">{profile?.gender === 'MALE' ? 'Nam' : profile?.gender === 'FEMALE' ? 'Nữ' : "Chưa xác định"}</p>
                        </div>
                      </div>
                    </div>
                  </div>
                ) : (
                  // Edit Profile View Form
                  <form onSubmit={handleUpdate} className="space-y-6">
                    <div className="flex justify-between items-center pb-4 border-b border-gray-100">
                      <h2 className="text-xl font-bold text-gray-900">Cập nhật hồ sơ</h2>
                      <div className="flex space-x-2">
                        <button
                          type="button"
                          onClick={() => setIsEditing(false)}
                          className="px-4 py-2 border border-gray-300 rounded-lg text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 transition-colors shadow-sm"
                        >
                          Hủy
                        </button>
                        <button
                          type="submit"
                          className="px-4 py-2 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors shadow-sm"
                        >
                          Lưu thay đổi
                        </button>
                      </div>
                    </div>

                    <div className="space-y-4 max-w-lg">
                      {/* Full Name */}
                      <div>
                        <label className="block text-xs font-semibold text-gray-500 mb-1">HỌ VÀ TÊN</label>
                        <input
                          type="text"
                          required
                          value={fullName}
                          onChange={(e) => setFullName(e.target.value)}
                          className="block w-full border border-gray-300 rounded-lg px-3.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                      </div>

                      {/* Phone Number */}
                      <div>
                        <label className="block text-xs font-semibold text-gray-500 mb-1">SỐ ĐIỆN THOẠI</label>
                        <input
                          type="tel"
                          required
                          value={phoneNumber}
                          onChange={(e) => setPhoneNumber(e.target.value)}
                          className="block w-full border border-gray-300 rounded-lg px-3.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                      </div>

                      {/* Identity Number */}
                      <div>
                        <label className="block text-xs font-semibold text-gray-500 mb-1">SỐ CCCD / HỘ CHIẾU</label>
                        <input
                          type="text"
                          required
                          value={identityNumber}
                          onChange={(e) => setIdentityNumber(e.target.value)}
                          className="block w-full border border-gray-300 rounded-lg px-3.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                      </div>

                      {/* Date of Birth */}
                      <div>
                        <label className="block text-xs font-semibold text-gray-500 mb-1">NGÀY SINH</label>
                        <input
                          type="date"
                          value={dateOfBirth}
                          onChange={(e) => setDateOfBirth(e.target.value)}
                          className="block w-full border border-gray-300 rounded-lg px-3.5 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                        />
                      </div>

                      {/* Gender */}
                      <div className="flex items-center space-x-6 text-sm text-gray-700 py-1">
                        <span className="font-semibold text-xs text-gray-500">GIỚI TÍNH:</span>
                        <label className="flex items-center space-x-1.5 cursor-pointer">
                          <input
                            type="radio"
                            name="dashboard-gender"
                            value="MALE"
                            checked={gender === 'MALE'}
                            onChange={() => setGender('MALE')}
                            className="text-blue-600 focus:ring-blue-500"
                          />
                          <span>Nam</span>
                        </label>
                        <label className="flex items-center space-x-1.5 cursor-pointer">
                          <input
                            type="radio"
                            name="dashboard-gender"
                            value="FEMALE"
                            checked={gender === 'FEMALE'}
                            onChange={() => setGender('FEMALE')}
                            className="text-blue-600 focus:ring-blue-500"
                          />
                          <span>Nữ</span>
                        </label>
                      </div>
                    </div>
                  </form>
                )}
              </div>
            )}
          </div>
        </div>
      </main>
    </div>
  );
}

export default function AppRoutes() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/login/oauth2/code/google" element={<OAuth2Callback />} />
        <Route 
          path="/complete-profile" 
          element={
            <ProtectedRoute>
              <CompleteProfile />
            </ProtectedRoute>
          } 
        />
        <Route 
          path="/" 
          element={
            <ProtectedRoute>
              <Dashboard />
            </ProtectedRoute>
          } 
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
