import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowRight, CheckCircle2, ChevronLeft, ChevronRight, CreditCard, LayoutDashboard, Minus, Package, Plus, Search, ShoppingBag, ShoppingCart, Store, Trash2, Truck } from 'lucide-react';
import { api } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { useCart } from '../contexts/CartContext';
import type { Category, Product, SalesOrder } from '../types';
import { normalizeCategory, normalizeOrder, normalizeProduct } from '../types';
import { formatDateTime, formatVND } from '../utils/formatters';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { useToast } from '../components/ui/Toast';
import { AccountMenu } from '../components/shop/AccountMenu';
import { ShopOverlay } from '../components/shop/ShopOverlay';

type PageData = { content: unknown[]; totalElements: number; totalPages: number };
const orderLabels: Record<string, string> = {
  PENDING_PAYMENT: 'Chờ chuyển khoản', COD_PENDING: 'Chờ xử lý · COD', PAID: 'Đã thanh toán',
  SHIPPED: 'Đang giao hàng', DELIVERED: 'Đã giao hàng', CANCELLED: 'Đã hủy',
  RETURN_IN_TRANSIT: 'Hàng đang quay về', PARTIALLY_RETURNED: 'Đã trả một phần', RETURNED: 'Đã trả hàng',
};
const paymentLabels: Record<string, string> = {
  PENDING: 'Chờ thanh toán', COD_PENDING: 'Trả tiền khi nhận hàng', PAID: 'Đã thu tiền', FAILED: 'Thanh toán thất bại',
  REFUND_PENDING: 'Chờ hoàn tiền', REFUNDED: 'Đã hoàn tiền', PARTIALLY_REFUNDED: 'Đã hoàn một phần', CANCELLED: 'Đã hủy thanh toán',
};

function ProductImage({ product, small = false }: { product: Product; small?: boolean }) {
  const [broken, setBroken] = useState(false);
  useEffect(() => setBroken(false), [product.imageUrl]);
  return <div className={`flex items-center justify-center overflow-hidden bg-stone-50 ${small ? 'h-16 w-16 shrink-0 rounded-xl' : 'aspect-[4/3] rounded-xl'}`}>
    {product.imageUrl && !broken ? <img src={product.imageUrl} alt={product.name} loading="lazy" onError={() => setBroken(true)} className="h-full w-full object-contain p-3" />
      : <ShoppingBag size={small ? 24 : 44} strokeWidth={1.3} className="text-emerald-700/40" aria-hidden="true" />}
  </div>;
}

export function ShopPage() {
  const navigate = useNavigate();
  const { user, role, isAuthenticated, isLoading: authLoading } = useAuth();
  const { items, addItem, updateQuantity, removeItem, clearCart, refreshProducts, subtotal, itemCount } = useCart();
  const { success, warning } = useToast();
  const internal = isAuthenticated && role !== 'CUSTOMER';
  const customer = isAuthenticated && role === 'CUSTOMER';
  const workplace = role === 'STAFF' ? '/pos' : role === 'WAREHOUSE' ? '/inventory' : '/admin';
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [catalogError, setCatalogError] = useState('');
  const [categoryError, setCategoryError] = useState('');
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<number | 'ALL'>('ALL');
  const [sort, setSort] = useState('default');
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [pages, setPages] = useState(0);
  const [reload, setReload] = useState(0);
  const [cartOpen, setCartOpen] = useState(false);
  const [cartChecking, setCartChecking] = useState(false);
  const [cartCheckError, setCartCheckError] = useState('');
  const [cartReview, setCartReview] = useState(0);
  const currentItems = useRef(items);
  currentItems.current = items;
  const cartProductIds = items.map(item => item.product.id).join(',');
  const cartProblems = items.filter(item => !item.product.active || item.product.availableQuantity < item.quantity);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const submitLock = useRef(false);
  const readyCart = useRef<{ id: number; signature: string } | null>(null);
  const [checkoutError, setCheckoutError] = useState('');
  const [payment, setPayment] = useState<'COD' | 'BANK_TRANSFER'>('COD');
  const [receiver, setReceiver] = useState({ name: '', phone: '', address: '', note: '' });
  const [placedOrder, setPlacedOrder] = useState<SalesOrder | null>(null);
  const [ordersOpen, setOrdersOpen] = useState(false);
  const [orders, setOrders] = useState<SalesOrder[]>([]);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [ordersError, setOrdersError] = useState('');
  const [orderPage, setOrderPage] = useState(0);
  const [orderPages, setOrderPages] = useState(0);
  const [orderCodeInput, setOrderCodeInput] = useState('');
  const [searchedOrderCode, setSearchedOrderCode] = useState('');
  const [ordersReload, setOrdersReload] = useState(0);
  const previousUser = useRef(user?.id);
  const hasPreparedCart = readyCart.current?.signature === JSON.stringify(items.map(item => ({ productId: item.product.id, quantity: item.quantity })));

  useEffect(() => {
    const refresh = () => { if (document.visibilityState === 'visible' && !submitLock.current) setReload(value => value + 1); };
    const changed = (event: StorageEvent) => { if (event.key === 'shop-catalog-updated-at') refresh(); };
    window.addEventListener('focus', refresh);
    window.addEventListener('storage', changed);
    return () => { window.removeEventListener('focus', refresh); window.removeEventListener('storage', changed); };
  }, []);

  useEffect(() => {
    if ((!cartOpen && !checkoutOpen) || !cartProductIds) {
      setCartChecking(false); setCartCheckError(''); return;
    }
    const controller = new AbortController();
    setCartChecking(true); setCartCheckError('');
    const selection = currentItems.current;
    void Promise.allSettled(selection.map(item => api.get<unknown>(`/products/${item.product.id}`, { signal: controller.signal })))
      .then(results => {
        if (controller.signal.aborted) return;
        const updated = results.flatMap(result => result.status === 'fulfilled' ? [normalizeProduct(result.value)] : []);
        refreshProducts(updated);
        if (results.some(result => result.status === 'rejected')) setCartCheckError('Chưa kiểm tra được đầy đủ sản phẩm. Bấm Cập nhật giỏ để thử lại.');
      }).finally(() => { if (!controller.signal.aborted) setCartChecking(false); });
    return () => controller.abort();
  }, [cartOpen, checkoutOpen, cartProductIds, cartReview, refreshProducts]);

  useEffect(() => {
    const timer = window.setTimeout(() => { setSearch(keyword.trim()); setPage(0); }, 300);
    return () => window.clearTimeout(timer);
  }, [keyword]);

  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      try {
        const all: Category[] = [];
        for (let current = 0; ; current++) {
          const response = await api.get<PageData | unknown[]>(`/categories?page=${current}&size=100&active=true`, { signal: controller.signal });
          const entries = Array.isArray(response) ? response : response.content;
          all.push(...entries.map(normalizeCategory).filter(value => value.active));
          if (Array.isArray(response) || current + 1 >= response.totalPages) break;
        }
        if (!controller.signal.aborted) { setCategories(all); setCategoryError(''); }
      } catch { if (!controller.signal.aborted) setCategoryError('Chưa tải được nhóm hàng. Bạn vẫn có thể tìm sản phẩm theo tên.'); }
    })();
    return () => controller.abort();
  }, [reload]);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setCatalogError('');
    const params = new URLSearchParams({ page: String(page), size: '24', active: 'true', sortBy: sort === 'default' ? 'productId' : 'price', sortDir: sort === 'price-desc' ? 'desc' : 'asc' });
    if (search) params.set('keyword', search);
    if (category !== 'ALL') params.set('categoryId', String(category));
    api.get<PageData>(`/products?${params}`, { signal: controller.signal }).then(response => {
      if (controller.signal.aborted) return;
      const latest = response.content.map(normalizeProduct);
      setProducts(latest); refreshProducts(latest); setTotal(response.totalElements); setPages(response.totalPages);
    }).catch(err => {
      if (!controller.signal.aborted) { setProducts([]); setTotal(0); setPages(0); setCatalogError(err instanceof Error ? err.message : 'Không thể tải sản phẩm'); }
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [search, category, page, sort, reload, refreshProducts]);

  useEffect(() => {
    if (previousUser.current !== user?.id) {
      setCheckoutOpen(false); setCartOpen(false); setOrdersOpen(false); setPlacedOrder(null); setOrders([]);
      readyCart.current = null;
      setReceiver({ name: '', phone: '', address: '', note: '' });
      setOrderCodeInput(''); setSearchedOrderCode('');
      setCartCheckError('');
    }
    previousUser.current = user?.id;
  }, [user?.id]);

  useEffect(() => {
    if (!ordersOpen || !customer) return;
    const controller = new AbortController();
    setOrdersLoading(true); setOrdersError('');
    setOrders([]); setOrderPages(0);
    void (async () => {
      try {
        if (searchedOrderCode) {
          const response = await api.get<unknown>(`/orders/code/${encodeURIComponent(searchedOrderCode)}`, { signal: controller.signal });
          if (!controller.signal.aborted) { setOrders([normalizeOrder(response)]); setOrderPages(1); }
        } else {
          const response = await api.get<PageData>(`/orders/mine?page=${orderPage}&size=10`, { signal: controller.signal });
          if (!controller.signal.aborted) { setOrders(response.content.map(normalizeOrder)); setOrderPages(response.totalPages); }
        }
      } catch (err) {
        if (!controller.signal.aborted) setOrdersError(err instanceof Error ? err.message : 'Không thể tải đơn hàng');
      } finally {
        if (!controller.signal.aborted) setOrdersLoading(false);
      }
    })();
    return () => controller.abort();
  }, [ordersOpen, customer, orderPage, searchedOrderCode, ordersReload]);

  const openOrders = () => { if (customer) { setOrderPage(0); setOrderCodeInput(''); setSearchedOrderCode(''); setOrdersOpen(true); } };
  const searchOrders = (event: FormEvent) => {
    event.preventDefault();
    setSearchedOrderCode(orderCodeInput.trim().replace(/^#\s*/, ''));
    setOrderPage(0);
    setOrdersReload(value => value + 1);
  };
  const resetOrderSearch = () => {
    setOrderCodeInput(''); setSearchedOrderCode(''); setOrderPage(0);
    setOrdersReload(value => value + 1);
  };
  const add = (product: Product) => {
    if (internal || authLoading) return;
    const existing = items.find(item => item.product.id === product.id);
    if ((existing?.quantity ?? 0) >= product.availableQuantity) { warning('Bạn đã chọn hết số lượng hiện có của sản phẩm này.'); return; }
    if (addItem(product)) success(`Đã thêm ${product.name} vào giỏ`);
    else warning('Sản phẩm hiện chưa có hàng');
  };
  const openCheckout = () => {
    if (internal || authLoading) return;
    if (!items.length) { warning('Hãy chọn sản phẩm trước khi đặt hàng'); return; }
    if (cartChecking || (!hasPreparedCart && (cartCheckError || cartProblems.length))) { warning('Vui lòng cập nhật và xử lý các món không còn đủ hàng trong giỏ.'); return; }
    if (!customer) { navigate('/login'); return; }
    setReceiver(previous => ({ ...previous, name: previous.name || user?.fullName || '', phone: previous.phone || user?.phone || '', address: previous.address || user?.address || '' }));
    setCheckoutError(''); setCartOpen(false); setCheckoutOpen(true);
  };

  const confirmOrder = async (event: FormEvent) => {
    event.preventDefault();
    if (!customer || submitLock.current) return;
    if (cartChecking || (!hasPreparedCart && (cartCheckError || cartProblems.length))) {
      setCheckoutError('Giỏ có sản phẩm ngừng bán, không đủ hàng hoặc chưa kiểm tra được. Vui lòng quay về giỏ để cập nhật.'); return;
    }
    if (!items.length || !receiver.name.trim() || !receiver.address.trim() || !/^\+?[0-9]{9,15}$/.test(receiver.phone.trim())) {
      setCheckoutError('Vui lòng kiểm tra sản phẩm, tên người nhận, số điện thoại và địa chỉ.'); return;
    }
    submitLock.current = true; setSubmitting(true); setCheckoutError('');
    try {
      const selection = items.map(item => ({ productId: item.product.id, quantity: item.quantity }));
      const signature = JSON.stringify(selection);
      // Keep the completed backend cart for checkout retries. The backend returns
      // its existing order if a previous checkout response was lost.
      if (!readyCart.current || readyCart.current.signature !== signature) {
        readyCart.current = null;
        const cart = await api.post<{ cartId: number }>('/carts', { cartType: 'ONLINE' });
        for (const item of selection) await api.post(`/carts/${cart.cartId}/items`, item);
        readyCart.current = { id: cart.cartId, signature };
      }
      const response = await api.post<unknown>(`/carts/${readyCart.current.id}/checkout`, {
        paymentMethod: payment, shippingAddress: receiver.address.trim(), receiverName: receiver.name.trim(), receiverPhone: receiver.phone.trim(), note: receiver.note.trim(),
      });
      setPlacedOrder(normalizeOrder(response)); readyCart.current = null; clearCart(); setCheckoutOpen(false); setReload(value => value + 1);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Đặt hàng chưa thành công. Vui lòng thử lại.';
      setCheckoutError(message);
      if (/ngừng kinh doanh|không đủ tồn|vượt tồn|không đủ hàng|không đủ số lượng/i.test(message)) {
        readyCart.current = null;
        setCartReview(value => value + 1);
        setReload(value => value + 1);
      }
    }
    finally { submitLock.current = false; setSubmitting(false); }
  };

  const searchBox = <div className="relative w-full"><Search className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-gray-400" size={19} aria-hidden="true" /><input aria-label="Tìm sản phẩm theo tên" type="search" value={keyword} onChange={event => setKeyword(event.target.value)} placeholder="Bạn muốn mua gì hôm nay?" className="h-11 w-full rounded-full border border-gray-200 bg-gray-50 pl-11 pr-4 text-sm focus:bg-white focus:outline-none focus:ring-2 focus:ring-emerald-600" /></div>;
  const pagination = (current: number, count: number, change: (value: number) => void, busy: boolean) => count > 1 && <nav aria-label="Phân trang" className="flex items-center justify-center gap-4 pt-6"><Button variant="outline" disabled={current <= 0 || busy} onClick={() => change(current - 1)} leftIcon={<ChevronLeft size={16} />}>Trước</Button><span className="text-sm text-gray-600">{current + 1} / {count}</span><Button variant="outline" disabled={current + 1 >= count || busy} onClick={() => change(current + 1)} rightIcon={<ChevronRight size={16} />}>Sau</Button></nav>;
  const cartNotice = items.length > 0 && <div className="mb-5 space-y-3 rounded-xl border border-gray-200 bg-gray-50 p-4">
    <div className="flex items-center justify-between gap-3"><p className="text-xs text-gray-600">Kiểm tra giá và hàng có thể bán</p><Button type="button" size="sm" variant="outline" disabled={cartChecking || submitting} onClick={() => { setCartReview(value => value + 1); setReload(value => value + 1); }}>Cập nhật giỏ</Button></div>
    {cartChecking && <p role="status" className="text-sm text-gray-600">Đang kiểm tra sản phẩm trong giỏ…</p>}
    {hasPreparedCart && <p role="status" className="text-sm text-amber-800">Chưa nhận được kết quả đặt hàng. Bấm xác nhận lại để hệ thống kiểm tra giỏ đã gửi và trả về đơn nếu đã tạo.</p>}
    {cartCheckError && !hasPreparedCart && <p role="alert" className="text-sm text-rose-700">{cartCheckError}</p>}
    {!cartChecking && !hasPreparedCart && cartProblems.length > 0 && <div role="alert" className="text-sm text-rose-700"><p className="font-semibold">Một số món chưa thể đặt:</p><ul className="mt-2 space-y-1">{cartProblems.map(item => <li key={item.product.id}>{item.product.name}: {!item.product.active ? 'đã ngừng bán, vui lòng xóa khỏi giỏ' : item.product.availableQuantity <= 0 ? 'đã hết hàng, vui lòng xóa khỏi giỏ' : `chỉ còn ${item.product.availableQuantity}, hãy giảm số lượng từ ${item.quantity}` }.</li>)}</ul></div>}
  </div>;

  return <div className="min-h-screen bg-[#f7f8f5]">
    <header className="sticky top-0 z-30 border-b border-gray-200 bg-white">
      <div className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-4 sm:px-6 lg:gap-8">
        <a href="/shop" className="flex shrink-0 items-center gap-2 rounded-lg focus-visible:ring-2 focus-visible:ring-emerald-600"><span className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-700 text-white"><Store size={23} /></span><span className="text-sm font-extrabold tracking-tight sm:text-lg">Supermarket<span className="block text-[11px] font-medium tracking-normal text-gray-500">Cửa hàng online</span></span></a>
        <div className="hidden max-w-xl flex-1 md:block">{searchBox}</div>
        <div className="flex shrink-0 items-center gap-2 sm:gap-3">
          {isAuthenticated ? <AccountMenu onOrders={openOrders} /> : <Button variant="outline" disabled={authLoading} onClick={() => navigate('/login')}>Đăng nhập</Button>}
          {!internal && <button type="button" disabled={authLoading} aria-label={`Mở giỏ hàng, ${itemCount} món`} onClick={() => setCartOpen(true)} className="relative flex h-11 items-center gap-2 rounded-full bg-emerald-700 px-3 text-white hover:bg-emerald-800 focus-visible:ring-2 focus-visible:ring-emerald-600 focus-visible:ring-offset-2 disabled:opacity-50"><ShoppingCart size={20} /><span className="hidden text-sm font-semibold sm:block">Giỏ hàng</span>{itemCount > 0 && <span className="flex h-5 min-w-5 items-center justify-center rounded-full bg-white px-1 text-xs font-bold text-emerald-800">{itemCount}</span>}</button>}
        </div>
      </div>
      <div className="px-4 pb-3 md:hidden">{searchBox}</div>
    </header>

    <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 sm:py-8">
      {internal && <div className="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4"><p className="max-w-2xl text-sm text-amber-900">Bạn đang dùng tài khoản nội bộ. Có thể xem sản phẩm; mua online cần tài khoản khách hàng.</p><Button variant="outline" onClick={() => navigate(workplace)} leftIcon={<LayoutDashboard size={17} />}>Về khu vực làm việc</Button></div>}
      <section className="mb-7 flex flex-col justify-between gap-6 rounded-3xl bg-emerald-900 px-6 py-7 text-white sm:px-9 sm:py-9 md:flex-row md:items-center">
        <div className="max-w-xl"><p className="mb-3 text-xs font-semibold uppercase tracking-[0.15em] text-emerald-200">Mua sắm hằng ngày</p><h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Chọn món cần mua.<br />Đặt hàng thật dễ dàng.</h1><p className="mt-4 max-w-lg text-sm leading-relaxed text-emerald-100">Tìm sản phẩm, thêm vào giỏ và chọn cách thanh toán phù hợp với bạn.</p></div>
        <div className="flex flex-wrap gap-3 md:max-w-xs"><div className="flex items-center gap-3 rounded-2xl bg-white/10 px-4 py-3"><Truck size={22} /><span className="text-sm">Tiền mặt khi nhận hàng</span></div><div className="flex items-center gap-3 rounded-2xl bg-white/10 px-4 py-3"><CreditCard size={22} /><span className="text-sm">Chuyển khoản thủ công</span></div></div>
      </section>

      <section aria-label="Danh mục sản phẩm">
        <div className="mb-5 flex flex-col justify-between gap-4 sm:flex-row sm:items-end"><div><h2 className="text-xl font-bold text-gray-900">Sản phẩm của cửa hàng</h2><p role="status" className="mt-1 text-sm text-gray-500">{loading ? 'Đang tải sản phẩm…' : catalogError ? 'Chưa tải được dữ liệu' : `${total} sản phẩm${search ? ` cho “${search}”` : ''}`}</p></div><label className="flex items-center gap-2 text-sm text-gray-600">Sắp xếp<select value={sort} onChange={event => { setSort(event.target.value); setPage(0); }} className="rounded-lg border border-gray-200 bg-white px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-600"><option value="default">Mặc định</option><option value="price-asc">Giá thấp đến cao</option><option value="price-desc">Giá cao đến thấp</option></select></label></div>
        {categoryError && <p role="alert" className="mb-3 text-sm text-amber-800">{categoryError}</p>}
        <div className="mb-6 flex flex-wrap gap-2"><button onClick={() => { setCategory('ALL'); setPage(0); }} aria-pressed={category === 'ALL'} className={`rounded-full border px-4 py-2 text-sm focus-visible:ring-2 focus-visible:ring-emerald-600 ${category === 'ALL' ? 'border-emerald-700 bg-emerald-700 text-white' : 'border-gray-200 bg-white text-gray-600 hover:border-emerald-400'}`}>Tất cả</button>{categories.map(value => <button key={value.id} aria-pressed={category === value.id} onClick={() => { setCategory(value.id); setPage(0); }} className={`rounded-full border px-4 py-2 text-sm focus-visible:ring-2 focus-visible:ring-emerald-600 ${category === value.id ? 'border-emerald-700 bg-emerald-700 text-white' : 'border-gray-200 bg-white text-gray-600 hover:border-emerald-400'}`}>{value.categoryName}</button>)}</div>
        {loading ? <div aria-busy="true" className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-5 lg:grid-cols-4">{Array.from({ length: 8 }, (_, index) => <div key={index} className="rounded-2xl border border-gray-200 bg-white p-4"><div className="aspect-[4/3] rounded-xl bg-gray-100" /><div className="mt-4 h-4 w-3/4 rounded bg-gray-100" /><div className="mt-3 h-4 w-1/2 rounded bg-gray-100" /></div>)}</div>
          : catalogError ? <div role="alert" className="rounded-2xl border border-rose-200 bg-white p-8 text-center"><p className="mb-4 text-sm text-rose-700">{catalogError}</p><Button onClick={() => setReload(value => value + 1)}>Tải lại sản phẩm</Button></div>
          : !products.length ? <div className="rounded-2xl border border-gray-200 bg-white p-10 text-center"><ShoppingBag size={40} className="mx-auto text-gray-300" /><h3 className="mt-4 font-semibold">Chưa có sản phẩm phù hợp</h3><p className="mt-2 text-sm text-gray-500">Thử đổi từ khóa hoặc chọn một nhóm hàng khác.</p><Button className="mt-5" variant="outline" onClick={() => { setKeyword(''); setSearch(''); setCategory('ALL'); setPage(0); }}>Xóa bộ lọc</Button></div>
          : <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-5 lg:grid-cols-4">{products.map(product => {
            const quantity = items.find(item => item.product.id === product.id)?.quantity ?? 0;
            const soldOut = product.availableQuantity <= 0;
            return <article key={product.id} className="flex flex-col rounded-2xl border border-gray-200 bg-white p-3 sm:p-4">
              <ProductImage product={product} /><div className="mt-4 flex-1"><p className="truncate text-xs text-gray-500">{product.categoryName || categories.find(value => value.id === product.categoryId)?.categoryName || 'Sản phẩm'}</p><h3 className="mt-1 min-h-10 text-sm font-semibold leading-5 text-gray-900 sm:text-base sm:leading-6">{product.name}</h3><p className="mt-2 text-xs text-gray-500">{product.unit} <span aria-hidden="true">·</span> <span className={soldOut ? 'text-gray-500' : 'text-emerald-700'}>{soldOut ? 'Tạm hết hàng' : 'Còn hàng'}</span></p></div>
              <p className="mb-3 mt-4 text-base font-bold text-emerald-800 sm:text-lg">{formatVND(product.sellingPrice)}</p>
              {!internal && <button disabled={soldOut || quantity >= product.availableQuantity || authLoading} onClick={() => add(product)} className="flex min-h-10 items-center justify-center gap-1 rounded-xl bg-emerald-50 px-2 py-2 text-xs font-semibold text-emerald-800 hover:bg-emerald-100 focus-visible:ring-2 focus-visible:ring-emerald-600 disabled:bg-gray-100 disabled:text-gray-500 sm:text-sm"><Plus size={16} />{soldOut ? 'Tạm hết hàng' : quantity >= product.availableQuantity ? 'Đã chọn hết hàng' : quantity ? `Thêm nữa · đã chọn ${quantity}` : 'Thêm vào giỏ'}</button>}
            </article>;
          })}</div>}
        {!catalogError && pagination(page, pages, setPage, loading)}
      </section>
    </main>
    <footer className="mt-8 border-t border-gray-200 bg-white px-4 py-6 text-center text-sm text-gray-500">Supermarket Online · Mua sắm và theo dõi đơn hàng của bạn</footer>

    <ShopOverlay open={cartOpen && !internal} onClose={() => setCartOpen(false)} title="Giỏ hàng của bạn" description={`${items.length} loại sản phẩm · ${itemCount} món`} side footer={items.length ? <div className="space-y-4"><div className="flex justify-between font-semibold"><span>Tạm tính</span><span className="text-lg text-emerald-800">{formatVND(subtotal)}</span></div><p className="text-xs leading-relaxed text-gray-500">Giá và số lượng được kiểm tra lại khi đặt đơn. Thêm vào giỏ chưa giữ hàng trong kho.</p><Button className="w-full" size="lg" disabled={cartChecking || (!hasPreparedCart && (!!cartCheckError || cartProblems.length > 0))} onClick={openCheckout} rightIcon={<ArrowRight size={18} />}>{customer ? 'Tiếp tục đặt hàng' : 'Đăng nhập để đặt hàng'}</Button></div> : undefined}>
      {cartNotice}
      {!items.length ? <div className="py-16 text-center"><ShoppingCart size={48} className="mx-auto text-gray-300" /><h3 className="mt-5 text-lg font-semibold">Giỏ hàng đang trống</h3><p className="mt-2 text-sm text-gray-500">Chọn vài món cần mua để bắt đầu.</p><Button className="mt-6" onClick={() => setCartOpen(false)}>Tiếp tục mua sắm</Button></div>
        : <div className="space-y-5">{items.map(item => <article key={item.product.id} className="flex gap-4 border-b border-gray-100 pb-5"><ProductImage product={item.product} small /><div className="min-w-0 flex-1"><div className="flex items-start justify-between gap-2"><h3 className="text-sm font-semibold leading-5">{item.product.name}</h3><button aria-label={`Xóa ${item.product.name} khỏi giỏ`} onClick={() => removeItem(item.product.id)} className="rounded-lg p-1 text-gray-400 hover:text-rose-600 focus-visible:ring-2 focus-visible:ring-emerald-600"><Trash2 size={17} /></button></div><p className="mt-1 text-xs text-gray-500">{formatVND(item.product.sellingPrice)} / {item.product.unit}</p><div className="mt-3 flex flex-wrap items-center justify-between gap-2"><div className="flex items-center rounded-lg border border-gray-200"><button aria-label={`Giảm số lượng ${item.product.name}`} onClick={() => updateQuantity(item.product.id, item.quantity - 1)} className="rounded-l-lg p-2 hover:bg-gray-100 focus-visible:ring-2 focus-visible:ring-emerald-600"><Minus size={15} /></button><span className="min-w-8 text-center text-sm font-semibold">{item.quantity}</span><button aria-label={`Tăng số lượng ${item.product.name}`} disabled={item.quantity >= item.product.availableQuantity} onClick={() => updateQuantity(item.product.id, item.quantity + 1)} className="rounded-r-lg p-2 hover:bg-gray-100 focus-visible:ring-2 focus-visible:ring-emerald-600 disabled:opacity-30"><Plus size={15} /></button></div><strong className="text-sm text-emerald-800">{formatVND(item.quantity * item.product.sellingPrice)}</strong></div></div></article>)}<Button variant="ghost" className="text-rose-600" onClick={clearCart}>Xóa toàn bộ giỏ hàng</Button></div>}
    </ShopOverlay>

    <ShopOverlay open={checkoutOpen && customer} onClose={() => setCheckoutOpen(false)} title="Thông tin đặt hàng" description="Kiểm tra người nhận và chọn cách thanh toán." busy={submitting}>
      {cartNotice}
      <form onSubmit={confirmOrder} className="space-y-5">
        {checkoutError && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{checkoutError}</p>}
        <fieldset disabled={submitting} className="space-y-4"><div className="grid gap-4 sm:grid-cols-2"><Input label="Tên người nhận" required value={receiver.name} onChange={event => setReceiver({ ...receiver, name: event.target.value })} maxLength={150} autoComplete="name" /><Input label="Số điện thoại" required type="tel" pattern="[+]?[0-9]{9,15}" maxLength={16} value={receiver.phone} onChange={event => setReceiver({ ...receiver, phone: event.target.value })} autoComplete="tel" /></div><Input label="Địa chỉ giao hàng" required value={receiver.address} onChange={event => setReceiver({ ...receiver, address: event.target.value })} maxLength={255} autoComplete="street-address" placeholder="Số nhà, đường, phường/xã, tỉnh/thành phố" /><Input label="Ghi chú cho cửa hàng" value={receiver.note} onChange={event => setReceiver({ ...receiver, note: event.target.value })} maxLength={255} placeholder="Không bắt buộc" /></fieldset>
        <fieldset disabled={submitting}><legend className="mb-3 text-sm font-semibold">Phương thức thanh toán</legend><div className="space-y-3">{(['COD', 'BANK_TRANSFER'] as const).map(method => <label key={method} className={`flex cursor-pointer items-start gap-3 rounded-xl border p-4 ${payment === method ? 'border-emerald-600 bg-emerald-50' : 'border-gray-200'}`}><input type="radio" name="shop-payment" checked={payment === method} onChange={() => setPayment(method)} className="mt-1 accent-emerald-700" /><span><strong className="text-sm">{method === 'COD' ? 'Tiền mặt khi nhận hàng' : 'Chuyển khoản thủ công'}</strong><span className="mt-1 block text-xs leading-relaxed text-gray-600">{method === 'COD' ? 'Thanh toán tiền mặt khi nhận đơn, không cần thẻ ngân hàng.' : 'Đơn chờ cửa hàng xác nhận đã nhận tiền. Liên hệ cửa hàng để nhận thông tin chuyển khoản.'}</span></span></label>)}</div></fieldset>
        <div className="rounded-xl bg-gray-50 p-4"><div className="flex justify-between text-sm"><span>{itemCount} món · tạm tính</span><strong>{formatVND(subtotal)}</strong></div><p className="mt-2 text-xs text-gray-500">Tổng tiền chính thức sẽ hiển thị sau khi hệ thống tạo đơn.</p></div>
        <div className="flex justify-end gap-3"><Button type="button" variant="outline" disabled={submitting} onClick={() => { setCheckoutOpen(false); setCartOpen(true); }}>Về giỏ hàng</Button><Button type="submit" isLoading={submitting} disabled={cartChecking || (!hasPreparedCart && (!!cartCheckError || cartProblems.length > 0))}>Xác nhận đặt hàng</Button></div>
      </form>
    </ShopOverlay>

    <ShopOverlay open={!!placedOrder && customer} onClose={() => setPlacedOrder(null)} title="Đã tiếp nhận đơn hàng">
      {placedOrder && <div className="space-y-5"><CheckCircle2 size={48} className="mx-auto text-emerald-600" /><div className="text-center"><h3 className="text-xl font-bold">Đơn hàng #{placedOrder.orderCode}</h3><p className="mt-2 text-sm text-gray-600">{placedOrder.paymentMethod === 'COD' ? 'Bạn sẽ thanh toán tiền mặt khi nhận hàng.' : 'Đơn đang chờ chuyển khoản và xác nhận của cửa hàng.'}</p></div><div className="space-y-2 rounded-xl bg-gray-50 p-4 text-sm"><p><strong>Người nhận:</strong> {placedOrder.receiverName} · {placedOrder.receiverPhone}</p><p><strong>Địa chỉ:</strong> {placedOrder.shippingAddress}</p><p><strong>Tổng tiền:</strong> {formatVND(placedOrder.finalAmount)}</p>{placedOrder.paymentExpiresAt && <p><strong>Hạn thanh toán:</strong> {formatDateTime(placedOrder.paymentExpiresAt)}</p>}</div><div className="flex flex-wrap justify-center gap-3"><Button variant="outline" onClick={() => setPlacedOrder(null)}>Tiếp tục mua sắm</Button><Button onClick={() => { setPlacedOrder(null); openOrders(); }}>Xem đơn của tôi</Button></div></div>}
    </ShopOverlay>

    <ShopOverlay open={ordersOpen && customer} onClose={() => setOrdersOpen(false)} title="Đơn hàng của tôi" description="Lịch sử đơn mua online của tài khoản này." side>
      <form onSubmit={searchOrders} className="mb-5 space-y-3 rounded-xl bg-gray-50 p-4">
        <label htmlFor="shop-order-code" className="block text-sm font-semibold text-gray-800">Tìm theo mã đơn hàng</label>
        <div className="flex gap-2"><input id="shop-order-code" type="search" value={orderCodeInput} onChange={event => setOrderCodeInput(event.target.value)} placeholder="Ví dụ: 27 hoặc #27" maxLength={100} className="min-w-0 flex-1 rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-600" /><Button type="submit" leftIcon={<Search size={16} />}>Tìm</Button></div>
        <p className="text-xs leading-relaxed text-gray-500">Nhập đầy đủ mã đơn. Chỉ xem được đơn online thuộc tài khoản của bạn.</p>
        {searchedOrderCode && <div className="flex flex-wrap items-center justify-between gap-2 border-t border-gray-200 pt-3"><p role="status" className="break-all text-sm text-gray-600">Đang tra cứu mã <strong>#{searchedOrderCode}</strong></p><Button type="button" variant="outline" size="sm" onClick={resetOrderSearch}>Xem tất cả đơn</Button></div>}
      </form>
      {ordersLoading ? <p role="status" className="py-10 text-center text-gray-500">Đang tải đơn hàng…</p> : ordersError ? <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{ordersError}</p> : !orders.length ? <div className="py-14 text-center"><Package size={44} className="mx-auto text-gray-300" /><p className="mt-4 text-gray-500">Bạn chưa có đơn hàng online.</p></div> : <div className="space-y-4">{orders.map(order => <article key={order.id} className="rounded-xl border border-gray-200 p-4"><div className="flex flex-wrap items-center justify-between gap-2"><strong>Đơn #{order.orderCode}</strong><span className={`rounded-full px-3 py-1 text-xs font-semibold ${order.status === 'CANCELLED' ? 'bg-gray-100 text-gray-600' : 'bg-emerald-50 text-emerald-800'}`}>{orderLabels[order.status] || order.status}</span></div><p className="mt-2 text-xs text-gray-500">{formatDateTime(order.createdAt)}</p><p className="mt-3 font-bold">{formatVND(order.finalAmount)}</p><p className="mt-2 text-sm text-gray-600">{order.receiverName} · {order.receiverPhone}</p><p className="mt-1 text-sm leading-relaxed text-gray-600">{order.shippingAddress}</p><p className="mt-3 text-xs text-gray-500">{order.paymentMethod === 'COD' ? 'COD' : 'Chuyển khoản'} · {paymentLabels[order.paymentStatus] || order.paymentStatus}</p>{order.paymentExpiresAt && order.paymentStatus === 'PENDING' && <p className="mt-2 text-xs text-amber-800">Hạn thanh toán: {formatDateTime(order.paymentExpiresAt)}</p>}<details className="mt-3 border-t border-gray-100 pt-3"><summary className="cursor-pointer text-sm font-medium text-emerald-800">Sản phẩm trong đơn</summary><ul className="mt-3 space-y-2 text-sm">{order.items.map((item, index) => <li key={`${item.id}-${index}`} className="flex justify-between gap-3"><span>{item.productName} × {item.quantity}</span><span className="shrink-0">{formatVND(item.totalPrice)}</span></li>)}</ul></details></article>)}</div>}
      {!ordersError && pagination(orderPage, orderPages, setOrderPage, ordersLoading)}
    </ShopOverlay>
  </div>;
}
