import React, { useState, useEffect } from 'react';
import {
  Search,
  Plus,
  Edit2,
  Eye,
  EyeOff,
  History,
  RefreshCw,
} from 'lucide-react';
import { api } from '../api/client';
import type { Product, Category, Supplier, StockMovement, ProductRequest } from '../types';
import { normalizeProduct, normalizeCategory, normalizeSupplier } from '../types';
import { formatVND, formatDateTime } from '../utils/formatters';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Badge } from '../components/ui/Badge';
import { Modal } from '../components/ui/Modal';
import { Card } from '../components/ui/Card';
import { useToast } from '../components/ui/Toast';
import { useAuth } from '../contexts/AuthContext';

export const ProductsPage: React.FC = () => {
  const { role } = useAuth();
  const canManage = role === 'ADMIN' || role === 'MANAGER' || role === 'WAREHOUSE';
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [selectedStatus, setSelectedStatus] = useState('ALL');
  const [isSaving, setIsSaving] = useState(false);
  const [changingProductId, setChangingProductId] = useState<number | null>(null);
  const [formError, setFormError] = useState('');
  const [listError, setListError] = useState('');

  // Modal State
  const [isFormOpen, setIsFormOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<Product | null>(null);
  const [formData, setFormData] = useState<ProductRequest>({
    skuCode: '',
    productName: '',
    price: 15000,
    stockQuantity: 0,
    reorderPoint: 5,
    imageUrl: '',
    categoryId: 1,
    supplierId: 1,
    isActive: true,
  });

  // Stock Movement History Modal
  const [isMovementOpen, setIsMovementOpen] = useState(false);
  const [movementList, setMovementList] = useState<StockMovement[]>([]);
  const [selectedProductName, setSelectedProductName] = useState('');
  const [isLoadingMovements, setIsLoadingMovements] = useState(false);

  const { success, error } = useToast();

  const loadAll = async (endpoint: string): Promise<unknown[]> => {
    const entries: unknown[] = [];
    for (let page = 0; ; page++) {
      const response = await api.get<{ content: unknown[]; totalPages: number } | unknown[]>(`${endpoint}?page=${page}&size=100`);
      entries.push(...(Array.isArray(response) ? response : response.content));
      if (Array.isArray(response) || page + 1 >= response.totalPages) return entries;
    }
  };

  const notifyShop = () => {
    try { localStorage.setItem('shop-catalog-updated-at', String(Date.now())); }
    catch { /* Storage notification is optional; the API update already succeeded. */ }
  };

  const productPayload = (product: Product, isActive: boolean): ProductRequest => ({
    skuCode: product.skuCode, productName: product.name, price: product.sellingPrice,
    stockQuantity: product.stockQuantity ?? product.totalQuantity, reorderPoint: product.minStockLevel,
    imageUrl: product.imageUrl ?? '', expiryDate: product.expiryDate,
    categoryId: product.categoryId, supplierId: product.supplierId, isActive,
  });

  const fetchInitialData = async () => {
    setIsLoading(true);
    setListError('');
    try {
      const [prodsRes, catsRes, suppsRes] = await Promise.allSettled([
        loadAll('/products'),
        loadAll('/categories'),
        loadAll('/suppliers'),
      ]);

      if (prodsRes.status === 'fulfilled') {
        setProducts(prodsRes.value.map(normalizeProduct));
      } else {
        setProducts([]);
        setListError('Không thể tải sản phẩm. Vui lòng bấm Tải lại.');
      }
      if (catsRes.status === 'fulfilled') {
        setCategories(catsRes.value.map(normalizeCategory));
      } else {
        error('Không thể tải danh mục sản phẩm');
      }
      if (suppsRes.status === 'fulfilled') {
        setSuppliers(suppsRes.value.map(normalizeSupplier));
      } else {
        error('Không thể tải nhà cung cấp');
      }
    } catch {
      error('Không thể tải danh sách sản phẩm');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchInitialData();
  }, []);

  const handleOpenCreate = () => {
    setFormError('');
    setEditingProduct(null);
    const numericSkus = products.map((product) => Number(product.skuCode)).filter(Number.isFinite);
    const nextSku = String(Math.max(0, ...numericSkus) + 1);
    setFormData({
      skuCode: nextSku,
      productName: '',
      price: 15000,
      stockQuantity: 0,
      reorderPoint: 10,
      imageUrl: '',
      categoryId: categories[0]?.id || 1,
      supplierId: suppliers[0]?.id || 1,
      isActive: true,
    });
    setIsFormOpen(true);
  };

  const handleOpenEdit = (p: Product) => {
    setFormError('');
    setEditingProduct(p);
    setFormData(productPayload(p, p.active));
    setIsFormOpen(true);
  };

  const handleSaveProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSaving || !canManage) return;
    setFormError(''); setIsSaving(true);
    try {
      const payload = { ...formData, productName: formData.productName.trim(), skuCode: formData.skuCode.trim(), imageUrl: formData.imageUrl?.trim() };
      if (editingProduct) {
        // Stock is controlled by batch operations. Read its current value for
        // the existing full-update API; never write a stale form quantity.
        const current = normalizeProduct(await api.get(`/products/${editingProduct.id}`));
        payload.stockQuantity = current.stockQuantity ?? current.totalQuantity;
        payload.expiryDate = current.expiryDate;
        await api.put(`/products/${editingProduct.id}`, payload);
        success('Cập nhật thông tin sản phẩm thành công');
      } else {
        payload.stockQuantity = 0;
        await api.post('/products', payload);
        success('Thêm sản phẩm mới thành công');
      }
      setIsFormOpen(false);
      notifyShop();
      await fetchInitialData();
    } catch (err: unknown) {
      setFormError(err instanceof Error ? err.message : 'Không thể lưu sản phẩm');
    } finally {
      setIsSaving(false);
    }
  };

  const handleToggleProduct = async (product: Product) => {
    if (!canManage || changingProductId != null) return;
    if (product.active && !window.confirm(`Ẩn “${product.name}” khỏi shop và chặn đặt mới? Tồn kho và các đơn đã tạo được giữ nguyên.`)) return;
    setChangingProductId(product.id);
    try {
      if (product.active) {
        await api.delete(`/products/${product.id}`);
        success('Đã ẩn sản phẩm khỏi shop và ngừng nhận đơn mới');
      } else {
        const current = normalizeProduct(await api.get(`/products/${product.id}`));
        const updated = normalizeProduct(await api.put(`/products/${product.id}`, productPayload(current, true)));
        success(updated.availableQuantity > 0 ? 'Đã mở bán lại sản phẩm' : 'Đã hiện lại sản phẩm; cần nhập lô có thể bán để khách đặt hàng');
      }
      notifyShop();
      await fetchInitialData();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    } finally {
      setChangingProductId(null);
    }
  };

  const handleViewMovements = async (p: Product) => {
    setSelectedProductName(p.name);
    setIsMovementOpen(true);
    setIsLoadingMovements(true);
    try {
      const res = await api.get<StockMovement[]>(`/products/${p.id}/stock-movements`);
      setMovementList(Array.isArray(res) ? res : []);
    } catch {
      setMovementList([]);
    } finally {
      setIsLoadingMovements(false);
    }
  };

  // Filter products by search and category
  const filteredProducts = products.filter((p: Product) => {
    const pName = p.name || p.productName || '';
    const pSku = p.skuCode || '';
    const pBarcode = p.barcode || '';
    const kw = searchKeyword.toLowerCase();

    const matchesKeyword =
      pName.toLowerCase().includes(kw) ||
      pSku.toLowerCase().includes(kw) ||
      pBarcode.toLowerCase().includes(kw);

    const matchesCategory =
      selectedCategory === 'ALL' ||
      (p.categoryId != null && p.categoryId.toString() === selectedCategory);

    const matchesStatus = selectedStatus === 'ALL'
      || (selectedStatus === 'HIDDEN' && !p.active)
      || (selectedStatus === 'SELLING' && p.active && p.availableQuantity > 0)
      || (selectedStatus === 'OUT' && p.active && p.availableQuantity <= 0);
    return matchesKeyword && matchesCategory && matchesStatus;
  });

  return (
    <div className="space-y-6">
      {/* Header Bar */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 bg-white p-4 rounded-xl border border-gray-200/80 shadow-2xs">
        <div>
          <h2 className="text-base font-bold text-gray-900">Danh mục sản phẩm</h2>
          <p className="text-xs text-gray-500">
            Quản lý mã hàng, đơn giá, ngưỡng tồn kho và theo dõi lịch sử biến động thẻ kho
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
            onClick={fetchInitialData}
          >
            Tải lại
          </Button>
          {canManage && (
            <Button
              size="sm"
              variant="primary"
              leftIcon={<Plus className="w-3.5 h-3.5" />}
              onClick={handleOpenCreate}
            >
              Thêm sản phẩm
            </Button>
          )}
        </div>
      </div>

      {/* Filter and Search Bar */}
      <Card>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div>
            <Input
              placeholder="Tìm kiếm theo tên sản phẩm, mã SKU hoặc mã vạch..."
              leftIcon={<Search className="w-4 h-4" />}
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
            />
          </div>
          <div>
            <Select
              value={selectedCategory}
              onChange={(e) => setSelectedCategory(e.target.value)}
              options={[
                { value: 'ALL', label: 'Tất cả danh mục' },
                ...categories.map((c: Category) => ({ value: c.id.toString(), label: c.categoryName })),
              ]}
            />
          </div>
          <Select aria-label="Lọc trạng thái sản phẩm" value={selectedStatus} onChange={event => setSelectedStatus(event.target.value)} options={[
            { value: 'ALL', label: 'Tất cả trạng thái' }, { value: 'SELLING', label: 'Đang bán · còn hàng' },
            { value: 'OUT', label: 'Đang hiện · hết hàng' }, { value: 'HIDDEN', label: 'Đã ẩn khỏi shop' },
          ]} />
        </div>
        <p className="mt-3 text-xs leading-relaxed text-gray-500">Hết hàng: vẫn hiện trên shop nhưng không đặt được. Đã ẩn: không hiện trên shop và chặn đặt mới. Ẩn/mở bán không thay đổi tồn kho hoặc tự hủy đơn đã tạo.</p>
      </Card>

      {/* Products Table */}
      <Card>
        <div className="overflow-x-auto -mx-5 -my-5">
          <table className="w-full text-left text-xs">
            <thead className="bg-gray-50/75 border-b border-gray-200 text-gray-500 font-semibold uppercase tracking-wider">
              <tr>
                <th className="px-4 py-3">Mã SKU</th>
                <th className="px-4 py-3">Tên sản phẩm</th>
                <th className="px-4 py-3">Danh mục</th>
                <th className="px-4 py-3 text-right">Giá bán</th>
                <th className="px-4 py-3 text-right">Tồn khả dụng</th>
                <th className="px-4 py-3 text-center">Trạng thái</th>
                <th className="px-4 py-3 text-center">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {filteredProducts.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-10 text-gray-400">
                    {isLoading ? 'Đang tải danh sách sản phẩm...' : listError || 'Không tìm thấy sản phẩm phù hợp'}
                  </td>
                </tr>
              ) : (
                filteredProducts.map((p: Product) => {
                  const available = p.availableQuantity ?? p.totalQuantity;
                  return (
                    <tr key={p.id} className="hover:bg-gray-50/50 transition-colors">
                      <td className="px-4 py-3.5 font-mono font-medium text-emerald-800">
                        {p.skuCode}
                      </td>
                      <td className="px-4 py-3.5 font-medium text-gray-900 max-w-[200px] truncate">
                        {p.name}
                      </td>
                      <td className="px-4 py-3.5 text-gray-600">
                        {categories.find((c: Category) => c.id === p.categoryId)?.categoryName || p.categoryName || 'Chưa phân loại'}
                      </td>
                      <td className="px-4 py-3.5 text-right font-bold text-gray-900">
                        {formatVND(p.sellingPrice)}
                      </td>
                      <td className="px-4 py-3.5 text-right font-bold">
                        <span
                          className={
                            available <= 0
                              ? 'text-rose-600'
                              : available <= p.minStockLevel
                              ? 'text-amber-600'
                              : 'text-emerald-700'
                          }
                        >
                          {available}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-center">
                        <Badge variant={!p.active ? 'neutral' : available > 0 ? 'success' : 'warning'}>
                          {!p.active ? 'Đã ẩn khỏi shop' : available > 0 ? 'Đang bán' : 'Tạm hết hàng'}
                        </Badge>
                      </td>
                      <td className="px-4 py-3.5 text-center">
                        <div className="flex items-center justify-center gap-1">
                          <button
                            type="button"
                            onClick={() => handleViewMovements(p)}
                            title="Lịch sử thẻ kho"
                            className="p-1.5 text-gray-400 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg cursor-pointer"
                          >
                            <History className="w-3.5 h-3.5" />
                          </button>
                          {canManage && (
                            <>
                              <button
                                type="button"
                                onClick={() => handleOpenEdit(p)}
                                disabled={changingProductId != null || isSaving}
                                title="Sửa thông tin"
                                className="p-1.5 text-gray-400 hover:text-emerald-600 hover:bg-emerald-50 rounded-lg cursor-pointer"
                              >
                                <Edit2 className="w-3.5 h-3.5" />
                              </button>
                              <button
                                type="button"
                                onClick={() => handleToggleProduct(p)}
                                title={p.active ? 'Ẩn khỏi shop' : 'Mở bán lại'}
                                aria-label={`${p.active ? 'Ẩn khỏi shop' : 'Mở bán lại'}: ${p.name}`}
                                disabled={changingProductId != null || isSaving}
                                className={`inline-flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-xs font-semibold focus-visible:ring-2 focus-visible:ring-emerald-600 disabled:opacity-40 ${p.active ? 'text-rose-600 hover:bg-rose-50' : 'text-emerald-700 hover:bg-emerald-50'}`}
                              >
                                {changingProductId === p.id ? <RefreshCw className="h-3.5 w-3.5 animate-spin" /> : p.active ? <EyeOff className="h-3.5 w-3.5" /> : <Eye className="h-3.5 w-3.5" />}
                                {p.active ? 'Ẩn khỏi shop' : 'Mở bán lại'}
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Add / Edit Product Modal */}
      <Modal
        isOpen={isFormOpen}
        onClose={() => { if (!isSaving) setIsFormOpen(false); }}
        title={editingProduct ? 'Chỉnh sửa thông tin sản phẩm' : 'Thêm sản phẩm mới'}
        maxWidth="lg"
        footer={
          <>
            <Button variant="outline" size="sm" disabled={isSaving} onClick={() => setIsFormOpen(false)}>
              Hủy bỏ
            </Button>
            <Button variant="primary" size="sm" type="submit" form="product-editor" isLoading={isSaving}>
              {editingProduct ? 'Lưu thay đổi' : 'Tạo sản phẩm'}
            </Button>
          </>
        }
      >
        <form id="product-editor" onSubmit={handleSaveProduct} className="space-y-4">
          {formError && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-700">{formError}</p>}
          <fieldset disabled={isSaving} className="space-y-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <Input label="Mã sản phẩm" value={formData.skuCode} onChange={event => setFormData({ ...formData, skuCode: event.target.value })} required maxLength={50} />
              <Input label="Tên sản phẩm" value={formData.productName} onChange={event => setFormData({ ...formData, productName: event.target.value })} required maxLength={150} />
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <Select label="Danh mục ngành hàng" value={formData.categoryId} onChange={event => setFormData({ ...formData, categoryId: Number(event.target.value) })} required options={[{ value: '', label: 'Chọn danh mục' }, ...categories.map(value => ({ value: value.id, label: value.categoryName }))]} />
              <Select label="Nhà cung cấp chính" value={formData.supplierId} onChange={event => setFormData({ ...formData, supplierId: Number(event.target.value) })} required options={[{ value: '', label: 'Chọn nhà cung cấp' }, ...suppliers.map(value => ({ value: value.id, label: value.supplierName }))]} />
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <Input label="Giá bán (₫)" type="number" min="1" step="1" value={formData.price} onChange={event => setFormData({ ...formData, price: Number(event.target.value) })} required />
              <Input label="Ngưỡng cảnh báo tồn" type="number" min="0" step="1" value={formData.reorderPoint} onChange={event => setFormData({ ...formData, reorderPoint: Number(event.target.value) })} required />
            </div>
            <Input label="Đường dẫn ảnh sản phẩm" type="url" value={formData.imageUrl || ''} onChange={event => setFormData({ ...formData, imageUrl: event.target.value })} placeholder="https://... (không bắt buộc)" />
            <Select label="Hiển thị trên shop" value={formData.isActive ? 'true' : 'false'} onChange={event => setFormData({ ...formData, isActive: event.target.value === 'true' })} options={[{ value: 'true', label: 'Hiện sản phẩm · cho phép đặt nếu còn hàng' }, { value: 'false', label: 'Ẩn sản phẩm · ngừng nhận đơn mới' }]} />
          </fieldset>
          <p className="rounded-lg bg-gray-50 p-3 text-xs leading-relaxed text-gray-600">{editingProduct ? 'Sửa thông tin và trạng thái không thay đổi số lượng kho. Nhập hoặc điều chỉnh hàng tại Kho & Lô hàng FEFO.' : 'Sản phẩm mới có tồn bằng 0. Nhập lô tại Kho & Lô hàng FEFO để có hàng bán.'} Hết hàng không tự chuyển sang trạng thái ẩn.</p>
        </form>
      </Modal>

      {/* Stock Movements History Modal */}
      <Modal
        isOpen={isMovementOpen}
        onClose={() => setIsMovementOpen(false)}
        title={`Lịch sử biến động thẻ kho: ${selectedProductName}`}
        maxWidth="xl"
      >
        {isLoadingMovements ? (
          <div className="py-8 text-center text-xs text-gray-500">Đang tải thẻ kho...</div>
        ) : movementList.length === 0 ? (
          <div className="py-8 text-center text-xs text-gray-400">
            Chưa có biến động xuất nhập kho nào được ghi nhận cho sản phẩm này.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-gray-50 border-b border-gray-200 text-gray-500 font-semibold">
                <tr>
                  <th className="px-3 py-2">Thời gian</th>
                  <th className="px-3 py-2">Loại biến động</th>
                  <th className="px-3 py-2 text-right">Số lượng</th>
                  <th className="px-3 py-2 text-right">Tồn trước</th>
                  <th className="px-3 py-2 text-right">Tồn sau</th>
                  <th className="px-3 py-2">Ghi chú</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {movementList.map((m: StockMovement) => (
                  <tr key={m.id}>
                    <td className="px-3 py-2 text-gray-600">{formatDateTime(m.createdAt)}</td>
                    <td className="px-3 py-2 font-semibold">
                      <span
                        className={
                          m.movementType === 'IN'
                            ? 'text-emerald-700'
                            : m.movementType === 'OUT'
                            ? 'text-rose-700'
                            : 'text-indigo-700'
                        }
                      >
                        {m.movementType === 'IN'
                          ? 'Nhập hàng'
                          : m.movementType === 'OUT'
                          ? 'Xuất bán'
                          : m.movementType}
                      </span>
                    </td>
                    <td className="px-3 py-2 text-right font-bold">{m.quantity}</td>
                    <td className="px-3 py-2 text-right text-gray-500">{m.balanceBefore}</td>
                    <td className="px-3 py-2 text-right font-medium text-gray-900">{m.balanceAfter}</td>
                    <td className="px-3 py-2 text-gray-500">{m.note || '--'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Modal>
    </div>
  );
};
