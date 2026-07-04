import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { 
  Mail, 
  Lock, 
  Eye, 
  EyeOff, 
  Sparkles, 
  FolderSync, 
  CreditCard, 
  Bell, 
  ShieldCheck 
} from 'lucide-react'
import { useAuthStore } from '../../store/authStore'
import { authService } from '../../services/authService'
import familyImg from '../../assets/family_insurance_login.png'

export default function Login() {
  const [emailOrPhone, setEmailOrPhone] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(false)
  const [showPassword, setShowPassword] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')
  
  const navigate = useNavigate()
  const { login, loading } = useAuthStore()

  const handleLogin = async (e) => {
    e.preventDefault()
    if (!emailOrPhone || !password) {
      setErrorMsg('Vui lòng điền đầy đủ thông tin đăng nhập.')
      return
    }
    
    try {
      setErrorMsg('')
      await login(emailOrPhone, password)
      navigate('/')
    } catch (err) {
      setErrorMsg(err.message || 'Đăng nhập không thành công.')
    }
  }

  const handleGoogleLogin = () => {
    const authUrl = authService.getGoogleAuthUrl()
    window.location.href = authUrl
  }

  return (
    <div className="flex-grow grid grid-cols-1 lg:grid-cols-12 bg-white">
      {/* Left Side: Login Form (5/12) */}
      <div className="lg:col-span-5 flex flex-col justify-center items-center px-6 py-12 sm:px-12">
        <div className="w-full max-w-md space-y-8">
          
          {/* Logo */}
          <div className="flex flex-col items-center justify-center text-center">
            <div className="flex items-center space-x-2 text-blue-600">
              <ShieldCheck className="w-10 h-10 stroke-[2]" />
              <span className="text-xl font-bold text-gray-900 tracking-tight">Dynamic Insurance</span>
            </div>
            <h2 className="mt-6 text-3xl font-extrabold text-gray-900 tracking-tight">
              Đăng nhập vào tài khoản
            </h2>
            <p className="mt-2 text-sm text-gray-500 max-w-sm">
              Truy cập hợp đồng, sản phẩm bảo hiểm và thông tin của bạn tại Dynamic Insurance.
            </p>
          </div>

          {/* Error Message */}
          {errorMsg && (
            <div className="bg-red-50 border-l-4 border-red-500 p-4 rounded text-sm text-red-700">
              {errorMsg}
            </div>
          )}

          {/* Form */}
          <form className="mt-8 space-y-6" onSubmit={handleLogin}>
            <div className="space-y-4">
              {/* Input Email/Phone */}
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-gray-400">
                  <Mail className="h-5 w-5" />
                </div>
                <input
                  type="text"
                  required
                  value={emailOrPhone}
                  onChange={(e) => setEmailOrPhone(e.target.value)}
                  className="block w-full pl-10 pr-3 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm placeholder-gray-400 transition-all"
                  placeholder="Email hoặc số điện thoại"
                />
              </div>

              {/* Input Password */}
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-gray-400">
                  <Lock className="h-5 w-5" />
                </div>
                <input
                  type={showPassword ? 'text' : 'password'}
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="block w-full pl-10 pr-10 py-3 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm placeholder-gray-400 transition-all"
                  placeholder="Mật khẩu"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600 focus:outline-none"
                >
                  {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
                </button>
              </div>
            </div>

            {/* Remember me & Forgot password */}
            <div className="flex items-center justify-between text-sm">
              <label className="flex items-center space-x-2 text-gray-600 select-none cursor-pointer">
                <input
                  type="checkbox"
                  checked={rememberMe}
                  onChange={(e) => setRememberMe(e.target.checked)}
                  className="h-4 w-4 text-blue-600 border-gray-300 rounded focus:ring-blue-500"
                />
                <span>Ghi nhớ đăng nhập</span>
              </label>
              <a href="#forgot" className="font-medium text-blue-600 hover:text-blue-700 hover:underline">
                Quên mật khẩu?
              </a>
            </div>

            {/* Submit Button */}
            <div>
              <button
                type="submit"
                disabled={loading}
                className="w-full py-3 px-4 border border-transparent rounded-lg text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 font-medium text-sm shadow transition-colors disabled:bg-blue-400"
              >
                {loading ? 'Đang xử lý...' : 'Đăng nhập'}
              </button>
            </div>
          </form>

          {/* Separator */}
          <div className="relative my-6">
            <div className="absolute inset-0 flex items-center" aria-hidden="true">
              <div className="w-full border-t border-gray-200"></div>
            </div>
            <div className="relative flex justify-center text-sm">
              <span className="px-4 bg-white text-gray-400">hoặc</span>
            </div>
          </div>

          {/* Google Login Button */}
          <div>
            <button
              type="button"
              onClick={handleGoogleLogin}
              className="w-full flex items-center justify-center py-3 px-4 border border-gray-300 rounded-lg text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 font-medium text-sm shadow-sm transition-colors"
            >
              {/* Google Icon SVG */}
              <svg className="h-5 w-5 mr-3" viewBox="0 0 24 24" width="24" height="24" xmlns="http://www.w3.org/2000/svg">
                <g transform="matrix(1, 0, 0, 1, 0, 0)">
                  <path d="M21.35,11.1H12v2.7h5.38C16.88,15.75,14.77,17,12,17c-3.31,0-6-2.69-6-6s2.69-6,6-6c1.66,0,3.14,0.67,4.24,1.76l2.06-2.06C16.59,2.83,14.44,2,12,2C7.03,2,3,6.03,3,11s4.03,9,9,9c4.54,0,8.38-3,9-7.3A4.59,4.59,0,0,0,21.35,11.1Z" fill="#EA4335" />
                  <path d="M12,20c4.54,0,8.38-3,9-7.3H12V10h9.35A4.59,4.59,0,0,1,21.4,12.7C20.66,17,16.82,20,12,20Z" fill="#4285F4" />
                  <path d="M6,11a6,6,0,0,1,6-6,5.88,5.88,0,0,1,4.24,1.76l2.06-2.06A8.93,8.93,0,0,0,12,2,9,9,0,0,0,3,11,9,9,0,0,0,6,11Z" fill="#FBBC05" />
                  <path d="M3,11a9,9,0,0,0,9,9,8.91,8.91,0,0,0,6.3-2.3L16.24,14.6A5.87,5.87,0,0,1,12,17,6,6,0,0,1,3,11Z" fill="#34A853" />
                </g>
              </svg>
              Đăng nhập với Google
            </button>
          </div>

          {/* Footer Registration Link */}
          <div className="text-center text-sm text-gray-500 mt-8">
            Chưa có tài khoản?{' '}
            <Link to="/register" className="font-semibold text-blue-600 hover:text-blue-700 hover:underline">
              Tạo tài khoản
            </Link>
          </div>

        </div>
      </div>

      {/* Right Side: Features & Banner (7/12) */}
      <div className="hidden lg:col-span-7 lg:flex lg:flex-col lg:justify-between bg-gradient-to-br from-[#052b66] to-[#011430] p-12 lg:p-16 text-white relative overflow-hidden">
        
        {/* Curved Background Mask/Overlay Effect */}
        <div className="absolute top-0 right-0 w-[80%] h-[120%] bg-[#08387f]/20 rounded-bl-[100px] pointer-events-none transform rotate-3 scale-110"></div>

        {/* Header Features */}
        <div className="z-10 space-y-2">
          <h1 className="text-4xl font-bold tracking-tight leading-tight max-w-lg">
            Bảo vệ sức khỏe toàn diện
          </h1>
          <p className="text-blue-200 text-sm max-w-md">
            Giải pháp bảo hiểm số hóa giúp bạn an tâm tận hưởng cuộc sống mỗi ngày.
          </p>
        </div>

        {/* Dynamic Graphic Container containing Family Image */}
        <div className="z-10 my-10 flex justify-center items-center relative">
          <div className="relative w-full max-w-lg aspect-[4/3] rounded-3xl overflow-hidden shadow-2xl border-4 border-white/10 group">
            <img 
              src={familyImg} 
              alt="Happy Family" 
              className="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent"></div>
          </div>
        </div>

        {/* Benefit items list */}
        <div className="z-10 grid grid-cols-1 md:grid-cols-2 gap-6 mt-2">
          {/* Item 1 */}
          <div className="flex space-x-3 items-start">
            <div className="flex-shrink-0 p-2 bg-white/10 border border-white/20 rounded-xl">
              <Sparkles className="h-5 w-5 text-blue-300" />
            </div>
            <div>
              <h4 className="font-semibold text-sm">Báo giá AI nhanh chóng</h4>
              <p className="text-xs text-blue-200 mt-1 leading-relaxed">
                Nhận gợi ý sản phẩm phù hợp chỉ trong vài phút với công nghệ AI.
              </p>
            </div>
          </div>

          {/* Item 2 */}
          <div className="flex space-x-3 items-start">
            <div className="flex-shrink-0 p-2 bg-white/10 border border-white/20 rounded-xl">
              <FolderSync className="h-5 w-5 text-blue-300" />
            </div>
            <div>
              <h4 className="font-semibold text-sm">Quản lý hợp đồng tập trung</h4>
              <p className="text-xs text-blue-200 mt-1 leading-relaxed">
                Xem hợp đồng đang hiệu lực, ngày hiệu lực, ngày hết hạn và thông tin chi tiết tại một nơi duy nhất.
              </p>
            </div>
          </div>

          {/* Item 3 */}
          <div className="flex space-x-3 items-start">
            <div className="flex-shrink-0 p-2 bg-white/10 border border-white/20 rounded-xl">
              <CreditCard className="h-5 w-5 text-blue-300" />
            </div>
            <div>
              <h4 className="font-semibold text-sm">Thanh toán trực tuyến thuận tiện</h4>
              <p className="text-xs text-blue-200 mt-1 leading-relaxed">
                Thanh toán phí bảo hiểm trực tuyến và theo dõi trạng thái thanh toán dễ dàng.
              </p>
            </div>
          </div>

          {/* Item 4 */}
          <div className="flex space-x-3 items-start">
            <div className="flex-shrink-0 p-2 bg-white/10 border border-white/20 rounded-xl">
              <Bell className="h-5 w-5 text-blue-300" />
            </div>
            <div>
              <h4 className="font-semibold text-sm">Thông báo & cập nhật</h4>
              <p className="text-xs text-blue-200 mt-1 leading-relaxed">
                Nhận thông báo phát hành hợp đồng, kết quả thanh toán và nhắc nhở sắp hết hạn.
              </p>
            </div>
          </div>
        </div>

        {/* Bottom quote */}
        <div className="z-10 border-t border-white/10 pt-4 mt-6 flex items-center space-x-2 text-xs text-blue-200">
          <ShieldCheck className="h-4 w-4 text-blue-300 flex-shrink-0" />
          <span>Dynamic Insurance đồng hành cùng bạn và gia đình trên hành trình xây dựng cuộc sống an tâm và vững vàng.</span>
        </div>

      </div>
    </div>
  )
}
