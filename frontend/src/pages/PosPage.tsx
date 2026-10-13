import React, { useState, useEffect, useRef } from 'react';
import {
  Search,
  Barcode,
  ShoppingCart,
  Trash2,
  Plus,
  Minus,
  Banknote,
  QrCode,
  Printer,
  X,
} from 'lucide-react';
import { api } from '../api/client';
import { useCart } from '../contexts/CartContext';
import { useAuth } from '../contexts/AuthContext';
import type { Product, Customer, SalesOrder, CreateOrderRequest } from '../types';
import { normalizeProduct, normalizeCustomer, normalizeOrder } from '../types';
import { formatVND, formatDateTime, PAYMENT_METHOD_LABELS } from '../utils/formatters';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Modal } from '../components/ui/Modal';
import { useToast } from '../components/ui/Toast';

export const PosPage: React.FC = () => {
  const [products, setProducts] = useState<Product[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [barcodeInput, setBarcodeInput] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [categories, setCategories] = useState<{ id: number; name: string }[]>([]);

  // Checkout and Receipt
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [lastOrder, setLastOrder] = useState<SalesOrder | null>(null);
  const [isReceiptOpen, setIsReceiptOpen] = useState(false);

  const barcodeInputRef = useRef<HTMLInputElement>(null);

  const {
    items,
    selectedCustomer,
    setSelectedCustomer,
    paymentMethod,
    setPaymentMethod,
    note,
    addItem,
    updateQuantity,
    removeItem,
    clearCart,
    subtotal,
    total,
    itemCount,
  } = useCart();

  const { user } = useAuth();
  const { success, error, warning } = useToast();

  const loadData = async () => {
    setIsLoading(true);
    try {
      const [prodsRes, custsRes] = await Promise.allSettled([
        api.get<any>('/products/active'),
        api.get<any>('/customers?page=0&size=100'),
      ]);

      if (prodsRes.status === 'fulfilled') {
        const rawProds = Array.isArray(prodsRes.value)
          ? prodsRes.value
          : (prodsRes.value as any)?.content || [];
        const prodList = rawProds.map(normalizeProduct);
        setProducts(prodList);

        // Extract distinct categories
        const catMap = new Map<number, string>();
        prodList.forEach((p: Product) => {
          if (p.categoryId && p.categoryName) {
            catMap.set(p.categoryId, p.categoryName);
          }
        });
        setCategories(
          Array.from(catMap.entries()).map(([id, name]) => ({ id, name }))
        );
      }

      if (custsRes.status === 'fulfilled') {
        const rawCusts = Array.isArray(custsRes.value)
          ? custsRes.value
          : (custsRes.value as any)?.content || [];
        setCustomers(rawCusts.map(normalizeCustomer));
      }
    } catch {
      error('Lỗi khi tải danh mục sản phẩm cho thu ngân');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    barcodeInputRef.current?.focus();
  }, []);

  // Handle barcode scanner Enter press
  const handleBarcodeSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!barcodeInput.trim()) return;

    const term = barcodeInput.trim().toLowerCase();
    const matched = products.find(
      (p: Product) =>
        (p.barcode && p.barcode.toLowerCase() === term) ||
        (p.skuCode && p.skuCode.toLowerCase() === term)
    );

    if (matched) {
      const added = addItem(matched, 1);
      if (added) {
        success(`Đã thêm: ${matched.name || matched.productName}`);
        setBarcodeInput('');
      } else {
        warning(`Sản phẩm [${matched.name || matched.productName}] đã hết tồn khả dụng!`);
      }
    } else {
      error(`Không tìm thấy mã vạch / SKU: "${barcodeInput}"`);
    }
  };

  // Perform real checkout via Spring Boot /api/orders
  const handleCheckout = async () => {
    if (items.length === 0) {
      warning('Giỏ hàng đang trống!');
      return;
    }

    setIsSubmitting(true);
    try {
      const payload: CreateOrderRequest = {
        customerId: selectedCustomer ? (selectedCustomer.id || selectedCustomer.customerId) : undefined,
        orderType: 'IN_STORE',
        paymentMethod: paymentMethod,
        note: note || 'Bán tại quầy thu ngân',
        items: items.map((i) => ({
          productId: i.product.id || i.product.productId || 0,
          quantity: i.quantity,
        })),
      };

      const createdOrder = await api.post<any>('/orders', payload);
      setLastOrder(normalizeOrder(createdOrder));
      setIsReceiptOpen(true);
      clearCart();
      success('Thanh toán đơn hàng thành công! Đã tự động xuất kho theo FEFO.');
      loadData(); // Refresh product stock
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handlePrintReceipt = () => {
    window.print();
  };

  // Filter products by search and category
  const filteredProducts = products.filter((p: Product) => {
    const pName = p.name || p.productName || '';
    const pSku = p.skuCode || '';
    const pBarcode = p.barcode || '';
    const kw = searchKeyword.toLowerCase();

    const matchesSearch =
      pName.toLowerCase().includes(kw) ||
      pSku.toLowerCase().includes(kw) ||
      pBarcode.toLowerCase().includes(kw);

    const matchesCat =
      selectedCategory === 'ALL' ||
      (p.categoryId != null && p.categoryId.toString() === selectedCategory);

    return matchesSearch && matchesCat;
  });

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 h-[calc(100vh-100px)]">
      {/* LEFT COLUMN: Catalog & Fast Barcode Entry (7 cols) */}
      <div className="lg:col-span-7 xl:col-span-8 flex flex-col h-full bg-white rounded-xl border border-gray-200/80 shadow-2xs overflow-hidden">
        {/* Search & Barcode Scan Bar */}
        <div className="p-3.5 border-b border-gray-100 space-y-3 bg-white">
          <div className="grid grid-cols-1 sm:grid-cols-12 gap-2.5">
            {/* Barcode scanner input */}
            <form onSubmit={handleBarcodeSubmit} className="sm:col-span-5">
              <div className="relative">
                <Barcode className="w-4 h-4 text-emerald-600 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  ref={barcodeInputRef}
                  type="text"
                  placeholder="Quét mã vạch / SKU (Enter)..."
                  value={barcodeInput}
                  onChange={(e) => setBarcodeInput(e.target.value)}
                  className="w-full pl-9 pr-3 py-2 text-xs font-mono rounded-lg border border-emerald-300 focus:outline-none focus:ring-2 focus:ring-emerald-500 bg-emerald-50/20"
                />
              </div>
            </form>

            {/* Keyword search input */}
            <div className="sm:col-span-7">
              <Input
                placeholder="Tìm tên sản phẩm..."
                leftIcon={<Search className="w-4 h-4 text-gray-400" />}
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
              />
            </div>
          </div>

          {/* Category Tabs */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 scrollbar-none text-xs">
            <button
              type="button"
              onClick={() => setSelectedCategory('ALL')}
              className={`px-3 py-1.5 rounded-lg font-medium whitespace-nowrap transition-colors cursor-pointer ${
                selectedCategory === 'ALL'
                  ? 'bg-emerald-600 text-white shadow-2xs'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              Tất cả ngành hàng
            </button>
            {categories.map((c: { id: number; name: string }) => (
              <button
                key={c.id}
                type="button"
                onClick={() => setSelectedCategory(c.id.toString())}
                className={`px-3 py-1.5 rounded-lg font-medium whitespace-nowrap transition-colors cursor-pointer ${
                  selectedCategory === c.id.toString()
                    ? 'bg-emerald-600 text-white shadow-2xs'
                    : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                }`}
              >
                {c.name}
              </button>
            ))}
          </div>
        </div>

        {/* Product Grid */}
        <div className="flex-1 overflow-y-auto p-4 bg-gray-50/40">
          {isLoading ? (
            <div className="h-full flex items-center justify-center text-xs text-gray-400">
              Đang tải danh mục hàng...
            </div>
          ) : filteredProducts.length === 0 ? (
            <div className="h-full flex items-center justify-center text-xs text-gray-400">
              Không tìm thấy sản phẩm phù hợp
            </div>
          ) : (
            <div className="grid grid-cols-2 sm:grid-cols-3 xl:grid-cols-4 gap-3">
              {filteredProducts.map((p: Product) => {
                const pId = p.id || p.productId || 0;
                const pName = p.name || p.productName || 'Sản phẩm';
                const pPrice = p.sellingPrice ?? p.price ?? 0;
                const available = p.availableQuantity ?? p.totalQuantity ?? p.stockQuantity ?? 0;
                const isOutOfStock = available <= 0;

                return (
                  <button
                    key={pId}
                    type="button"
                    disabled={isOutOfStock}
                    onClick={() => addItem(p, 1)}
                    className={`p-3 rounded-xl border text-left flex flex-col justify-between transition-all cursor-pointer ${
                      isOutOfStock
                        ? 'bg-gray-100 border-gray-200 opacity-60 cursor-not-allowed'
                        : 'bg-white border-gray-200 hover:border-emerald-500 hover:shadow-xs active:scale-[0.98]'
                    }`}
                  >
                    <div>
                      <div className="flex items-center justify-between gap-1 mb-1">
                        <span className="font-mono text-[10px] text-gray-400 truncate">
                          {p.skuCode}
                        </span>
                        <span
                          className={`text-[10px] font-semibold px-1.5 py-0.2 rounded ${
                            isOutOfStock
                              ? 'bg-rose-100 text-rose-700'
                              : 'bg-emerald-50 text-emerald-700'
                          }`}
                        >
                          {isOutOfStock ? 'Hết hàng' : `Còn ${available}`}
                        </span>
                      </div>
                      <h4 className="text-xs font-semibold text-gray-900 line-clamp-2 leading-tight">
                        {pName}
                      </h4>
                    </div>

                    <div className="mt-3 flex items-center justify-between">
                      <span className="text-xs font-bold text-emerald-700">
                        {formatVND(pPrice)}
                      </span>
                      <span className="text-[10px] text-gray-400">/{p.unit || 'Cái'}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>
      </div>

      {/* RIGHT COLUMN: Active Cart & Checkout Panel (5 cols) */}
      <div className="lg:col-span-5 xl:col-span-4 flex flex-col h-full bg-white rounded-xl border border-gray-200/80 shadow-2xs overflow-hidden">
        {/* Cart Header */}
        <div className="px-4 py-3 border-b border-gray-100 flex items-center justify-between bg-white">
          <div className="flex items-center gap-2">
            <ShoppingCart className="w-4 h-4 text-emerald-600" />
            <h3 className="text-sm font-bold text-gray-900">Giỏ hàng thanh toán</h3>
            <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-emerald-100 text-emerald-800">
              {itemCount}
            </span>
          </div>
          {items.length > 0 && (
            <button
              type="button"
              onClick={clearCart}
              className="text-xs text-rose-600 hover:underline flex items-center gap-1 cursor-pointer"
            >
              <Trash2 className="w-3.5 h-3.5" />
              Xóa sạch
            </button>
          )}
        </div>

        {/* Customer & Note Select */}
        <div className="p-3 border-b border-gray-100 bg-gray-50/50 space-y-2">
          <Select
            options={[
              { value: '', label: 'Khách mua lẻ tại quầy' },
              ...customers.map((c: Customer) => ({
                value: (c.id ?? c.customerId ?? '').toString(),
                label: `${c.name || c.fullName || 'Khách hàng'} - ${c.phone || ''}`,
              })),
            ]}
            value={(selectedCustomer?.id ?? selectedCustomer?.customerId ?? '').toString()}
            onChange={(e) => {
              const val = e.target.value;
              const cust = customers.find(
                (c: Customer) => (c.id ?? c.customerId ?? '').toString() === val
              );
              setSelectedCustomer(cust || null);
            }}
          />
        </div>

        {/* Cart Item List */}
        <div className="flex-1 overflow-y-auto p-3 space-y-2 divide-y divide-gray-100">
          {items.length === 0 ? (
            <div className="h-full flex flex-col items-center justify-center text-gray-400 py-12 text-xs">
              <ShoppingCart className="w-10 h-10 stroke-1 text-gray-300 mb-2" />
              <p>Giỏ hàng đang trống</p>
              <p className="text-[11px] text-gray-400 mt-0.5">
                Quét mã vạch hoặc chọn sản phẩm bên trái
              </p>
            </div>
          ) : (
            items.map((item) => {
              const pId = item.product.id || item.product.productId || 0;
              const pPrice = item.product.sellingPrice ?? item.product.price ?? 0;
              const pName = item.product.name || item.product.productName || 'Sản phẩm';
              return (
                <div key={pId} className="pt-2 first:pt-0 flex items-center justify-between gap-2">
                  <div className="flex-1 min-w-0">
                    <h5 className="text-xs font-semibold text-gray-900 truncate">
                      {pName}
                    </h5>
                    <div className="flex items-center gap-2 mt-0.5 text-[11px] text-gray-500">
                      <span>{formatVND(pPrice)}</span>
                      <span>×</span>
                      <span className="font-semibold text-gray-800">{item.quantity}</span>
                      <span>=</span>
                      <span className="font-bold text-emerald-700">
                        {formatVND(pPrice * item.quantity)}
                      </span>
                    </div>
                  </div>

                  {/* Quantity Controls */}
                  <div className="flex items-center gap-1">
                    <button
                      type="button"
                      onClick={() => updateQuantity(pId, item.quantity - 1)}
                      className="w-6 h-6 rounded border border-gray-200 flex items-center justify-center hover:bg-gray-100 text-gray-600 cursor-pointer"
                    >
                      <Minus className="w-3 h-3" />
                    </button>
                    <span className="w-7 text-center font-bold text-xs text-gray-900">
                      {item.quantity}
                    </span>
                    <button
                      type="button"
                      onClick={() => updateQuantity(pId, item.quantity + 1)}
                      className="w-6 h-6 rounded border border-gray-200 flex items-center justify-center hover:bg-gray-100 text-gray-600 cursor-pointer"
                    >
                      <Plus className="w-3 h-3" />
                    </button>
                    <button
                      type="button"
                      onClick={() => removeItem(pId)}
                      className="p-1 text-gray-300 hover:text-rose-600 cursor-pointer ml-1"
                    >
                      <X className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Payment Methods & Totals */}
        <div className="p-4 border-t border-gray-200 bg-gray-50/70 space-y-3">
          {/* Payment Method Selector */}
          <div>
            <span className="block text-[11px] font-semibold text-gray-500 uppercase tracking-wide mb-1.5">
              Phương thức thanh toán
            </span>
            <div className="grid grid-cols-2 gap-1.5">
              <button
                type="button"
                onClick={() => setPaymentMethod('CASH')}
                className={`p-2 rounded-lg border text-xs font-medium flex flex-col items-center gap-1 transition-colors cursor-pointer ${
                  paymentMethod === 'CASH'
                    ? 'border-emerald-600 bg-emerald-50 text-emerald-800 font-bold'
                    : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
                }`}
              >
                <Banknote className="w-4 h-4 text-emerald-600" />
                <span>Tiền mặt</span>
              </button>
              <button
                type="button"
                onClick={() => setPaymentMethod('BANK_TRANSFER')}
                className={`p-2 rounded-lg border text-xs font-medium flex flex-col items-center gap-1 transition-colors cursor-pointer ${
                  paymentMethod === 'BANK_TRANSFER'
                    ? 'border-emerald-600 bg-emerald-50 text-emerald-800 font-bold'
                    : 'border-gray-200 bg-white text-gray-600 hover:bg-gray-50'
                }`}
              >
                <QrCode className="w-4 h-4 text-blue-600" />
                <span>Chuyển khoản</span>
              </button>
            </div>
          </div>

          {/* Financial summary */}
          <div className="pt-2 border-t border-gray-200 space-y-1 text-xs">
            <div className="flex justify-between text-gray-500">
              <span>Tạm tính ({itemCount} món):</span>
              <span className="font-medium text-gray-900">{formatVND(subtotal)}</span>
            </div>
            <div className="flex justify-between items-baseline pt-1">
              <span className="text-sm font-bold text-gray-900">TỔNG THANH TOÁN:</span>
              <span className="text-lg font-bold text-emerald-700">{formatVND(total)}</span>
            </div>
          </div>

          {/* Submit Checkout Button */}
          <Button
            size="lg"
            variant="primary"
            className="w-full text-base font-bold shadow-md"
            disabled={items.length === 0 || isSubmitting}
            isLoading={isSubmitting}
            onClick={handleCheckout}
          >
            {isSubmitting ? 'Đang thanh toán...' : `THANH TOÁN (${formatVND(total)})`}
          </Button>
        </div>
      </div>

      {/* POS Receipt Modal (Printable) */}
      <Modal
        isOpen={isReceiptOpen}
        onClose={() => setIsReceiptOpen(false)}
        title="Hóa đơn thanh toán bán lẻ"
        maxWidth="md"
        footer={
          <>
            <Button variant="outline" size="sm" onClick={() => setIsReceiptOpen(false)}>
              Đóng
            </Button>
            <Button
              variant="primary"
              size="sm"
              leftIcon={<Printer className="w-4 h-4" />}
              onClick={handlePrintReceipt}
            >
              In hóa đơn (K80)
            </Button>
          </>
        }
      >
        {lastOrder && (
          <div id="pos-receipt-print" className="p-4 border border-dashed border-gray-300 rounded-lg text-xs space-y-3 font-mono">
            <div className="text-center space-y-0.5 border-b border-gray-200 pb-3">
              <h3 className="font-bold text-sm text-gray-900 uppercase">SIÊU THỊ TIỆN LỢI</h3>
              <p className="text-[11px] text-gray-500">Hóa đơn thanh toán tiền lẻ tại quầy</p>
              <p className="text-[10px] text-gray-400">Mã đơn: {lastOrder.orderCode}</p>
              <p className="text-[10px] text-gray-400">Ngày: {formatDateTime(lastOrder.createdAt)}</p>
              <p className="text-[10px] text-gray-400">Thu ngân: {user?.username || 'Thu ngân'}</p>
            </div>

            <table className="w-full text-[11px]">
              <thead className="border-b border-gray-200 text-gray-500">
                <tr>
                  <th className="text-left py-1">Tên món</th>
                  <th className="text-center py-1">SL</th>
                  <th className="text-right py-1">Đơn giá</th>
                  <th className="text-right py-1">Thành tiền</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {(lastOrder.items || []).map((i: any, idx: number) => (
                  <tr key={i.id || i.orderItemId || idx}>
                    <td className="py-1 text-gray-900">{i.productName}</td>
                    <td className="text-center py-1 font-bold">{i.quantity}</td>
                    <td className="text-right py-1 text-gray-600">{formatVND(i.unitPrice)}</td>
                    <td className="text-right py-1 font-bold text-gray-900">{formatVND(i.totalPrice)}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            <div className="border-t border-gray-200 pt-2 space-y-1 text-right text-xs">
              <div className="flex justify-between">
                <span>Tổng tiền hàng:</span>
                <span>{formatVND(lastOrder.totalAmount)}</span>
              </div>
              <div className="flex justify-between font-bold text-sm text-emerald-800">
                <span>THANH TOÁN:</span>
                <span>{formatVND(lastOrder.finalAmount)}</span>
              </div>
              <div className="flex justify-between text-[11px] text-gray-500">
                <span>Hình thức:</span>
                <span>{PAYMENT_METHOD_LABELS[lastOrder.paymentMethod] || lastOrder.paymentMethod}</span>
              </div>
            </div>

            <div className="border-t border-gray-200 pt-3 text-center text-[10px] text-gray-400">
              <p>Cảm ơn quý khách và hẹn gặp lại!</p>
              <p>Hàng hóa đổi trả trong vòng 24h kèm hóa đơn</p>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};
