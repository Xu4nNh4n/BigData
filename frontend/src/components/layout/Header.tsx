import { Menu, ShoppingCart, LogOut, ShieldCheck, User as UserIcon, Store } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { Button } from '../ui/Button';

interface HeaderProps {
  onToggleMobileMenu: () => void;
  pageTitle?: string;
}

export const Header: React.FC<HeaderProps> = ({ onToggleMobileMenu, pageTitle }) => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <header className="h-16 bg-white border-b border-gray-200/80 sticky top-0 z-30 flex items-center justify-between px-4 sm:px-6">
      {/* Left: Hamburger & Page Title */}
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onToggleMobileMenu}
          className="p-1.5 rounded-lg text-gray-500 hover:text-gray-700 hover:bg-gray-100 lg:hidden cursor-pointer"
        >
          <Menu className="w-5 h-5" />
        </button>
        <div className="flex items-center gap-2">
          <h2 className="text-base sm:text-lg font-semibold text-gray-900 tracking-tight">
            {pageTitle || 'Hệ thống Quản lý Siêu thị'}
          </h2>
          <span className="hidden sm:inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-emerald-50 text-emerald-700 border border-emerald-200">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
            MySQL Ready
          </span>
        </div>
      </div>

      {/* Right: Actions & User Info */}
      <div className="flex items-center gap-3">
        {/* Fast Shop Button */}
        <Button
          size="sm"
          variant="outline"
          leftIcon={<Store className="w-4 h-4 text-emerald-600" />}
          onClick={() => navigate('/')}
          className="hidden sm:inline-flex border-gray-200 text-gray-700 hover:bg-gray-50 text-xs"
        >
          Shop Online
        </Button>

        {/* Fast POS Button */}
        <Button
          size="sm"
          variant="outline"
          leftIcon={<ShoppingCart className="w-4 h-4 text-emerald-600" />}
          onClick={() => navigate('/pos')}
          className="hidden md:inline-flex border-emerald-300 text-emerald-700 hover:bg-emerald-50 text-xs"
        >
          Mở quầy POS
        </Button>

        {/* User Card */}
        <div className="flex items-center gap-2.5 pl-3 border-l border-gray-200">
          <div className="w-8 h-8 rounded-full bg-emerald-100 text-emerald-800 flex items-center justify-center font-semibold text-xs border border-emerald-200">
            {user?.username ? user.username.charAt(0).toUpperCase() : <UserIcon className="w-4 h-4" />}
          </div>
          <div className="hidden sm:block text-left">
            <p className="text-xs font-semibold text-gray-900 leading-tight">
              {user?.fullName || user?.username || 'Nhân viên'}
            </p>
            <div className="flex items-center gap-1 text-[11px] text-gray-500">
              <ShieldCheck className="w-3 h-3 text-emerald-600" />
              <span>{user?.role || 'STAFF'}</span>
            </div>
          </div>

          {/* Logout */}
          <button
            type="button"
            onClick={handleLogout}
            title="Đăng xuất"
            className="p-1.5 rounded-lg text-gray-400 hover:text-rose-600 hover:bg-rose-50 transition-colors cursor-pointer ml-1"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </header>
  );
};
