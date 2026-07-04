import React, { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { 
  Mail, 
  Lock, 
  Eye, 
  EyeOff, 
  User, 
  Phone, 
  CreditCard, 
  Calendar, 
  ShieldCheck, 
  Sparkles, 
  FolderSync, 
  Bell 
} from 'lucide-react'
import { authService } from '../../services/authService'
import familyImg from '../../assets/family_insurance_login.png'

export default function Register() {
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [phoneNumber, setPhoneNumber] = useState('')
  const [password, setPassword] = useState('')
  const [identityNumber, setIdentityNumber] = useState('')
  const [dateOfBirth, setDateOfBirth] = useState('')
  const [gender, setGender] = useState('MALE')
  const [showPassword, setShowPassword] = useState(false)
  const [errorMsg, setErrorMsg] = useState('')
  const [successMsg, setSuccessMsg] = useState('')
  const [loading, setLoading] = useState(false)

  const navigate = useNavigate()

  const handleRegister = async (e) => {
    e.preventDefault()
    if (!fullName || !email || !phoneNumber || !password || !identityNumber || !dateOfBirth) {
      setErrorMsg('Vui lòng điền đầy đủ các thông tin bắt buộc.')
      return
    }

    try {
      setErrorMsg('')
      setSuccessMsg('')
      setLoading(true)
      
      await authService.register({
        email,
        phoneNumber,
        password,
        fullName,
        identityNumber,
        dateOfBirth,
        gender
      })

      setSuccessMsg('Đăng ký tài khoản thành công! Đang chuyển hướng về trang đăng nhập...')
      setTimeout(() => {
        navigate('/login')
      }, 2500)
    } catch (err) {
      setErrorMsg(err.message || 'Đăng ký thất bại. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex-grow grid grid-cols-1 lg:grid-cols-12 bg-white">
      {/* Left Side: Register Form (5/12) */}
      <div className="lg:col-span-5 flex flex-col justify-center items-center px-6 py-12 sm:px-12">
        <div className="w-full max-w-md space-y-6">
          
          {/* Logo */}
          <div className="flex flex-col items-center justify-center text-center">
            <div className="flex items-center space-x-2 text-blue-600">
              <ShieldCheck className="w-10 h-10 stroke-[2]" />
              <span className="text-xl font-bold text-gray-900 tracking-tight">Dynamic Insurance</span>
            </div>
            <h2 className="mt-4 text-2xl font-extrabold text-gray-900 tracking-tight">
              Đăng ký tài khoản mới
            </h2>
            <p className="mt-2 text-sm text-gray-500 max-w-sm">
              Bắt đầu hành trình bảo vệ bản thân và gia đình cùng Dynamic Insurance.
            </p>
          </div>

          {/* Messages */}
          {errorMsg && (
            <div className="bg-red-50 border-l-4 border-red-500 p-4 rounded text-sm text-red-700">
              {errorMsg}
            </div>
          )}
          {successMsg && (
            <div className="bg-green-50 border-l-4 border-green-500 p-4 rounded text-sm text-green-700 font-medium">
              {successMsg}
            </div>
          )}

          {/* Form */}
          <form className="mt-4 space-y-4" onSubmit={handleRegister}>
            
            {/* Full Name */}
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-gray-400">
                <User className="h-5 w-5" />
              </div>
              <input
                type="text"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                className="block w-full pl-10 pr-3 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm placeholder-gray-400 transition-all"
                placeholder="Họ và tên khách hàng"
              />
            </div>

            {/* Email */}
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-gray-400">
                <Mail className="h-5 w-5" />
              </div>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="block w-full pl-10 pr-3 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm placeholder-gray-400 transition-all"
                placeholder="Địa chỉ Email"
              />
            </div>

            {/* Phone Number */}
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-gray-400">
                <Phone className="h-5 w-5" />
              </div>
              <input
                type="tel"
                required
                value={phoneNumber}
                onChange={(e) => setPhoneNumber(e.target.value)}
                className="block w-full pl-10 pr-3 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm placeholder-gray-400 transition-all"
                placeholder="Số điện thoại"
              />
            </div>

            {/* Password */}
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-gray-400">
                <Lock className="h-5 w-5" />
              </div>
              <input
                type={showPassword ? 'text' : 'password'}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="block w-full pl-10 pr-10 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm placeholder-gray-400 transition-all"
                placeholder="Mật khẩu bảo mật"
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-400 hover:text-gray-600 focus:outline-none"
              >
                {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
              </button>
            </div>

            {/* Identity Number */}
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-gray-400">
                <CreditCard className="h-5 w-5" />
              </div>
              <input
                type="text"
                required
                value={identityNumber}
                onChange={(e) => setIdentityNumber(e.target.value)}
                className="block w-full pl-10 pr-3 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm placeholder-gray-400 transition-all"
                placeholder="Số CCCD / Hộ chiếu"
              />
            </div>

            {/* Date of Birth */}
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-gray-400">
                <Calendar className="h-5 w-5" />
              </div>
              <input
                type="date"
                required
                value={dateOfBirth}
                onChange={(e) => setDateOfBirth(e.target.value)}
                className="block w-full pl-10 pr-3 py-2.5 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-blue-500 text-sm text-gray-700 transition-all"
              />
            </div>

            {/* Gender selection */}
            <div className="flex items-center space-x-6 text-sm text-gray-600 py-1">
              <span className="font-medium">Giới tính:</span>
              <label className="flex items-center space-x-1.5 cursor-pointer select-none">
                <input 
                  type="radio" 
                  name="gender" 
                  value="MALE" 
                  checked={gender === 'MALE'}
                  onChange={() => setGender('MALE')}
                  className="text-blue-600 focus:ring-blue-500" 
                />
                <span>Nam</span>
              </label>
              <label className="flex items-center space-x-1.5 cursor-pointer select-none">
                <input 
                  type="radio" 
                  name="gender" 
                  value="FEMALE" 
                  checked={gender === 'FEMALE'}
                  onChange={() => setGender('FEMALE')}
                  className="text-blue-600 focus:ring-blue-500" 
                />
                <span>Nữ</span>
              </label>
            </div>

            {/* Submit Button */}
            <div>
              <button
                type="submit"
                disabled={loading}
                className="w-full py-3 px-4 border border-transparent rounded-lg text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 font-medium text-sm shadow transition-colors disabled:bg-blue-400"
              >
                {loading ? 'Đang đăng ký...' : 'Đăng ký tài khoản'}
              </button>
            </div>
          </form>

          {/* Footer Registration Link */}
          <div className="text-center text-sm text-gray-500 pt-2">
            Đã có tài khoản?{' '}
            <Link to="/login" className="font-semibold text-blue-600 hover:text-blue-700 hover:underline">
              Đăng nhập ngay
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

        {/* Image Container */}
        <div className="z-10 my-8 flex justify-center items-center relative">
          <div className="relative w-full max-w-md aspect-[4/3] rounded-3xl overflow-hidden shadow-2xl border-4 border-white/10">
            <img 
              src={familyImg} 
              alt="Happy Family" 
              className="w-full h-full object-cover"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/40 via-transparent to-transparent"></div>
          </div>
        </div>

        {/* Benefit list */}
        <div className="z-10 grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="flex space-x-2 items-start">
            <div className="flex-shrink-0 p-1.5 bg-white/10 border border-white/20 rounded-lg">
              <Sparkles className="h-4 w-4 text-blue-300" />
            </div>
            <div>
              <h4 className="font-semibold text-xs">Báo giá AI nhanh chóng</h4>
              <p className="text-[11px] text-blue-200 mt-0.5">
                Nhận gợi ý sản phẩm phù hợp chỉ trong vài phút.
              </p>
            </div>
          </div>

          <div className="flex space-x-2 items-start">
            <div className="flex-shrink-0 p-1.5 bg-white/10 border border-white/20 rounded-lg">
              <FolderSync className="h-4 w-4 text-blue-300" />
            </div>
            <div>
              <h4 className="font-semibold text-xs">Quản lý hợp đồng tập trung</h4>
              <p className="text-[11px] text-blue-200 mt-0.5">
                Xem hợp đồng hiệu lực tại một nơi duy nhất.
              </p>
            </div>
          </div>

          <div className="flex space-x-2 items-start">
            <div className="flex-shrink-0 p-1.5 bg-white/10 border border-white/20 rounded-lg">
              <CreditCard className="h-4 w-4 text-blue-300" />
            </div>
            <div>
              <h4 className="font-semibold text-xs">Thanh toán trực tuyến thuận tiện</h4>
              <p className="text-[11px] text-blue-200 mt-0.5">
                Thanh toán an toàn, nhanh chóng và dễ dàng.
              </p>
            </div>
          </div>

          <div className="flex space-x-2 items-start">
            <div className="flex-shrink-0 p-1.5 bg-white/10 border border-white/20 rounded-lg">
              <Bell className="h-4 w-4 text-blue-300" />
            </div>
            <div>
              <h4 className="font-semibold text-xs">Thông báo & cập nhật</h4>
              <p className="text-[11px] text-blue-200 mt-0.5">
                Nhận thông báo phát hành và nhắc nhở đóng phí.
              </p>
            </div>
          </div>
        </div>

        {/* Bottom quote */}
        <div className="z-10 border-t border-white/10 pt-3 mt-4 flex items-center space-x-2 text-[10px] text-blue-200">
          <ShieldCheck className="h-3.5 w-3.5 text-blue-300 flex-shrink-0" />
          <span>Dynamic Insurance đồng hành cùng bạn trên hành trình xây dựng cuộc sống an tâm và vững vàng.</span>
        </div>

      </div>
    </div>
  )
}
