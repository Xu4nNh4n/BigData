import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ChevronDown, KeyRound, LayoutDashboard, LogOut, Package, UserRound } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { api } from '../../api/client';
import type { AccountProfile } from '../../types';
import { Button } from '../ui/Button';
import { Input } from '../ui/Input';
import { useToast } from '../ui/Toast';
import { ShopOverlay } from './ShopOverlay';

export function AccountMenu({ onOrders }: { onOrders: () => void }) {
  const { user, role, logout, refreshProfile, updateProfile } = useAuth();
  const navigate = useNavigate();
  const { success } = useToast();
  const [expanded, setExpanded] = useState(false);
  const [panel, setPanel] = useState<'profile' | 'password' | null>(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(false);
  const [profileReady, setProfileReady] = useState(false);
  const requestVersion = useRef(0);
  const [failure, setFailure] = useState('');
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', address: '' });
  const [password, setPassword] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const root = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const customer = role === 'CUSTOMER';

  useEffect(() => {
    if (!expanded) return;
    const outside = (event: PointerEvent) => { if (!root.current?.contains(event.target as Node)) setExpanded(false); };
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') { setExpanded(false); trigger.current?.focus(); } };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    menu.current?.querySelector<HTMLButtonElement>('button')?.focus();
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape); };
  }, [expanded]);

  const open = async (next: 'profile' | 'password') => {
    const version = ++requestVersion.current;
    setExpanded(false);
    trigger.current?.focus();
    setFailure('');
    setPassword({ currentPassword: '', newPassword: '', confirmPassword: '' });
    setPanel(next);
    setLoading(false);
    if (next === 'profile') {
      setProfileReady(false);
      setLoading(true);
      try {
        const profile = await refreshProfile();
        if (requestVersion.current !== version) return;
        setForm({ fullName: profile.fullName ?? '', email: profile.email ?? '', phone: profile.phone ?? '', address: profile.address ?? '' });
        setProfileReady(true);
      } catch (err) { if (requestVersion.current === version) setFailure(err instanceof Error ? err.message : 'Không thể tải thông tin tài khoản'); }
      finally { if (requestVersion.current === version) setLoading(false); }
    }
  };

  const saveProfile = async (event: FormEvent) => {
    event.preventDefault(); setFailure(''); setBusy(true);
    try {
      const updated = await api.put<AccountProfile>('/auth/me', {
        fullName: form.fullName.trim(), email: form.email.trim(), phone: form.phone.trim(),
        ...(customer ? { address: form.address.trim() } : {}),
      });
      updateProfile(updated); setPanel(null); success('Đã cập nhật thông tin tài khoản');
    } catch (err) { setFailure(err instanceof Error ? err.message : 'Không thể lưu thông tin'); }
    finally { setBusy(false); }
  };

  const savePassword = async (event: FormEvent) => {
    event.preventDefault(); setFailure('');
    if (password.newPassword !== password.confirmPassword) { setFailure('Xác nhận mật khẩu mới không khớp'); return; }
    setBusy(true);
    try {
      await api.put('/auth/password', password);
      await logout(); setPanel(null); success('Đã đổi mật khẩu. Vui lòng đăng nhập lại.'); navigate('/login');
    } catch (err) { setFailure(err instanceof Error ? err.message : 'Không thể đổi mật khẩu'); }
    finally { setBusy(false); }
  };

  const menuButton = 'flex w-full items-center gap-3 rounded-lg px-3 py-3 text-left text-sm hover:bg-gray-50 focus-visible:ring-2 focus-visible:ring-emerald-600';
  return <>
    <div ref={root} className="relative" onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget as Node)) setExpanded(false); }}>
      <button ref={trigger} type="button" aria-label="Mở menu tài khoản" aria-expanded={expanded} aria-controls="shop-account-menu" onClick={() => setExpanded(value => !value)} className="flex items-center gap-2 rounded-full border border-gray-200 bg-white p-1.5 pr-2 hover:border-emerald-400 focus-visible:ring-2 focus-visible:ring-emerald-600">
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-emerald-100 font-bold text-emerald-800">{(user?.fullName || user?.username || 'U').slice(0, 1).toUpperCase()}</span>
        <span className="hidden max-w-32 truncate text-sm font-semibold lg:block">{user?.fullName || user?.username}</span><ChevronDown size={16} aria-hidden="true" />
      </button>
      {expanded && <div ref={menu} id="shop-account-menu" className="absolute right-0 top-full z-50 mt-3 w-64 rounded-2xl border border-gray-200 bg-white p-2 shadow-xl">
        <div className="border-b border-gray-100 px-3 py-3"><p className="truncate font-semibold">{user?.fullName || user?.username}</p><p className="mt-1 text-xs text-gray-500">{customer ? 'Tài khoản khách hàng' : 'Tài khoản nội bộ'} · {user?.username}</p></div>
        <button className={menuButton} onClick={() => void open('profile')}><UserRound size={18} />Thông tin tài khoản</button>
        <button className={menuButton} onClick={() => void open('password')}><KeyRound size={18} />Đổi mật khẩu</button>
        {customer ? <button className={menuButton} onClick={() => { setExpanded(false); trigger.current?.focus(); onOrders(); }}><Package size={18} />Đơn hàng của tôi</button>
          : <button className={menuButton} onClick={() => navigate(role === 'STAFF' ? '/pos' : role === 'WAREHOUSE' ? '/inventory' : '/admin')}><LayoutDashboard size={18} />Về khu vực làm việc</button>}
        <div className="border-t border-gray-100 pt-1"><button className={`${menuButton} text-rose-600`} onClick={async () => { setExpanded(false); await logout(); success('Đã đăng xuất'); }}><LogOut size={18} />Đăng xuất</button></div>
      </div>}
    </div>
    <ShopOverlay open={panel === 'profile'} onClose={() => { requestVersion.current++; setPanel(null); }} title="Thông tin tài khoản" description="Cập nhật thông tin liên hệ của bạn." busy={busy}>
      {loading ? <p role="status" className="py-8 text-center text-gray-500">Đang tải thông tin…</p> : !profileReady ? <div className="space-y-4"><p role="alert" className="text-sm text-rose-700">{failure}</p><Button onClick={() => void open('profile')}>Tải lại thông tin</Button></div> : <form onSubmit={saveProfile} className="space-y-4">
        {failure && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{failure}</p>}
        <Input label="Tên đăng nhập" value={user?.username || ''} disabled helperText="Tên đăng nhập được giữ cố định." />
        <Input label="Họ và tên" value={form.fullName} onChange={event => setForm({ ...form, fullName: event.target.value })} required maxLength={150} autoComplete="name" disabled={busy} />
        <Input label="Email" type="email" value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} required={customer} maxLength={100} autoComplete="email" disabled={busy} />
        <Input label="Số điện thoại" type="tel" value={form.phone} onChange={event => setForm({ ...form, phone: event.target.value })} required={customer} pattern="[+]?[0-9]{9,15}" maxLength={16} autoComplete="tel" disabled={busy} />
        {customer && <Input label="Địa chỉ nhận hàng mặc định" value={form.address} onChange={event => setForm({ ...form, address: event.target.value })} maxLength={255} autoComplete="street-address" disabled={busy} />}
        <div className="flex justify-end gap-2 pt-2"><Button type="button" variant="outline" onClick={() => setPanel(null)} disabled={busy}>Đóng</Button><Button type="submit" isLoading={busy} disabled={!form.fullName.trim()}>Lưu thông tin</Button></div>
      </form>}
    </ShopOverlay>
    <ShopOverlay open={panel === 'password'} onClose={() => setPanel(null)} title="Đổi mật khẩu" description="Sau khi đổi thành công, bạn cần đăng nhập lại." busy={busy}>
      <form onSubmit={savePassword} className="space-y-4">
        {failure && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{failure}</p>}
        <Input label="Mật khẩu hiện tại" type="password" value={password.currentPassword} onChange={event => setPassword({ ...password, currentPassword: event.target.value })} required autoComplete="current-password" disabled={busy} />
        <Input label="Mật khẩu mới" type="password" value={password.newPassword} onChange={event => setPassword({ ...password, newPassword: event.target.value })} required minLength={8} maxLength={72} autoComplete="new-password" helperText="Từ 8 đến 72 ký tự, khác mật khẩu hiện tại." disabled={busy} />
        <Input label="Nhập lại mật khẩu mới" type="password" value={password.confirmPassword} onChange={event => setPassword({ ...password, confirmPassword: event.target.value })} required minLength={8} maxLength={72} autoComplete="new-password" disabled={busy} />
        <Button type="submit" isLoading={busy} className="w-full">Đổi mật khẩu</Button>
      </form>
    </ShopOverlay>
  </>;
}
