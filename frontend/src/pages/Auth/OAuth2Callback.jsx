import React, { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuthStore } from "../../store/authStore";
import { authService } from "../../services/authService";
import axiosClient from "../../services/axiosClient";

const tokenExchangeInflight = new Map();

function exchangeCodeForToken(code) {
  if (tokenExchangeInflight.has(code)) {
    return tokenExchangeInflight.get(code);
  }

  const promise = (async () => {
    return authService.exchangeGoogleCode(code);
  })();

  tokenExchangeInflight.set(code, promise);
  promise.finally(() => {
    setTimeout(() => tokenExchangeInflight.delete(code), 5 * 60 * 1000);
  });

  return promise;
}

export default function OAuth2Callback() {
  const [searchParams] = useSearchParams();
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();
  const loginWithTokenResponse = useAuthStore((state) => state.loginWithTokenResponse);
  const [hasProcessed, setHasProcessed] = useState(false);

  useEffect(() => {
    if (hasProcessed) {
      return;
    }

    const handleCallback = async () => {
      setHasProcessed(true);
      const code = searchParams.get("code");
      const errorParam = searchParams.get("error");

      if (errorParam) {
        setError(`Authentication failed: ${errorParam}`);
        setLoading(false);
        setTimeout(() => navigate("/login"), 3000);
        return;
      }

      if (!code) {
        setError("No authorization code received");
        setLoading(false);
        setTimeout(() => navigate("/login"), 3000);
        return;
      }

      try {
        console.log("Exchanging Google authorization code via backend...");
        const tokenData = await exchangeCodeForToken(code);
        loginWithTokenResponse(tokenData);

        // Fetch (or automatically create) user profile from user-service
        console.log("Fetching backend user profile...");
        let isProfileComplete = false;
        try {
          const profile = await axiosClient.get("/users/me");
          console.log("User profile loaded/created:", profile);
          if (profile && profile.phoneNumber && profile.identityNumber && profile.dateOfBirth && profile.gender) {
            isProfileComplete = true;
          }
        } catch (profileErr) {
          console.warn("Failed to retrieve or create user profile on backend", profileErr);
        }

        setLoading(false);
        setTimeout(() => {
          if (isProfileComplete) {
            navigate("/");
          } else {
            navigate("/complete-profile");
          }
        }, 1000);
      } catch (err) {
        console.error("OAuth2 callback error:", err);
        setError(err.message || "Failed to complete authentication. Please try again.");
        setLoading(false);
        setTimeout(() => navigate("/login"), 3000);
      }
    };

    handleCallback();
  }, [searchParams, navigate, loginWithTokenResponse, hasProcessed]);

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4 text-gray-900">
      <div className="w-full max-w-md rounded-2xl border border-gray-200 bg-white p-8 shadow-xl text-center">
        {error ? (
          <div>
            <div className="mb-4">
              <svg className="mx-auto h-16 w-16 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            </div>
            <h2 className="mb-2 text-2xl font-bold text-gray-900">Lỗi xác thực</h2>
            <p className="mb-4 text-sm text-gray-600 whitespace-pre-line text-left bg-gray-50 p-4 rounded-lg border border-gray-200 font-mono leading-relaxed">{error}</p>
            <p className="text-sm font-semibold text-blue-600">Đang quay lại trang đăng nhập...</p>
          </div>
        ) : loading ? (
          <div>
            <div className="flex justify-center mb-4">
              <svg className="h-16 w-16 animate-spin text-blue-600" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
              </svg>
            </div>
            <h2 className="mb-2 text-2xl font-bold text-gray-900">Đang kết nối...</h2>
            <p className="text-sm text-gray-500">Vui lòng chờ trong giây lát trong khi chúng tôi đăng nhập tài khoản của bạn</p>
          </div>
        ) : (
          <div>
            <div className="mb-4">
              <svg className="mx-auto h-16 w-16 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <h2 className="mb-2 text-2xl font-bold text-gray-900">Thành công!</h2>
            <p className="text-sm text-gray-500">Đang chuyển hướng về trang chủ...</p>
          </div>
        )}
      </div>
    </div>
  );
}
