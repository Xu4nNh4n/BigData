import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, Outlet } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { CartProvider } from './contexts/CartContext';
import { ToastProvider } from './components/ui/Toast';
import { ErrorBoundary } from './components/ui/ErrorBoundary';
import { AppLayout } from './components/layout/AppLayout';
import type { RoleName } from './types';

import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { ProductsPage } from './pages/ProductsPage';
import { CategoriesPage } from './pages/CategoriesPage';
import { InventoryPage } from './pages/InventoryPage';
import { PosPage } from './pages/PosPage';
import { SalesOrdersPage } from './pages/SalesOrdersPage';
import { SuppliersPage } from './pages/SuppliersPage';
import { CustomersPage } from './pages/CustomersPage';
import { ReportsPage } from './pages/ReportsPage';
import { UsersPage } from './pages/UsersPage';
import { ShopPage } from './pages/ShopPage';

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen bg-[#F8FAF9] flex items-center justify-center">
        <div className="flex items-center gap-2 text-sm text-gray-500 font-medium">
          <svg className="animate-spin h-5 w-5 text-emerald-600" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
          Đang khởi tạo phiên làm việc...
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  return <>{children}</>;
};

const RoleRoute: React.FC<{ allowedRoles: RoleName[]; children: React.ReactNode }> = ({
  allowedRoles,
  children,
}) => {
  const { role } = useAuth();

  if (role && !allowedRoles.includes(role)) {
    // If cashier/staff attempts to open an admin/manager route, redirect smoothly to POS
    return <Navigate to={role === 'STAFF' ? '/pos' : '/'} replace />;
  }

  return <>{children}</>;
};

export const App: React.FC = () => {
  return (
    <ErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
          <CartProvider>
            <ToastProvider>
              <Routes>
                {/* 1. Kênh Cửa hàng Online công khai cho tất cả khách hàng (Không cần đăng nhập) */}
                <Route element={<CartProvider><Outlet /></CartProvider>}>
                  <Route path="/" element={<ShopPage />} />
                  <Route path="/shop" element={<ShopPage />} />
                  <Route path="/login" element={<LoginPage />} />
                </Route>

                {/* 3. Không gian làm việc nội bộ (Admin Dashboard, Thu ngân POS, Quản lý kho, Nhân viên...)
                    Chỉ xuất hiện và cho phép truy cập khi nhân viên/quản trị viên đăng nhập hợp lệ */}
                <Route
                  element={
                    <ProtectedRoute>
                      <AppLayout />
                    </ProtectedRoute>
                  }
                >
                  <Route
                    path="/admin"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER']}>
                        <DashboardPage />
                      </RoleRoute>
                    }
                  />
                  <Route path="/dashboard" element={<Navigate to="/admin" replace />} />
                  <Route
                    path="/pos"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER', 'STAFF']}>
                        <PosPage />
                      </RoleRoute>
                    }
                  />
                  <Route
                    path="/products"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER', 'STAFF', 'WAREHOUSE']}>
                        <ProductsPage />
                      </RoleRoute>
                    }
                  />
                  <Route
                    path="/categories"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER']}>
                        <CategoriesPage />
                      </RoleRoute>
                    }
                  />
                  <Route
                    path="/inventory"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER', 'WAREHOUSE']}>
                        <InventoryPage />
                      </RoleRoute>
                    }
                  />
                  <Route
                    path="/orders"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER', 'STAFF']}>
                        <SalesOrdersPage />
                      </RoleRoute>
                    }
                  />
                  <Route
                    path="/suppliers"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER', 'WAREHOUSE']}>
                        <SuppliersPage />
                      </RoleRoute>
                    }
                  />
                  <Route
                    path="/customers"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER', 'STAFF']}>
                        <CustomersPage />
                      </RoleRoute>
                    }
                  />
                  <Route
                    path="/reports"
                    element={
                      <RoleRoute allowedRoles={['ADMIN', 'MANAGER']}>
                        <ReportsPage />
                      </RoleRoute>
                    }
                  />
                  <Route
                    path="/users"
                    element={
                      <RoleRoute allowedRoles={['ADMIN']}>
                        <UsersPage />
                      </RoleRoute>
                    }
                  />
                </Route>

                {/* 4. Điều hướng mặc định nếu gõ sai đường dẫn -> Về Cửa hàng Online */}
                <Route path="*" element={<Navigate to="/" replace />} />
              </Routes>
            </ToastProvider>
          </CartProvider>
        </AuthProvider>
      </BrowserRouter>
    </ErrorBoundary>
  );
};

export default App;
