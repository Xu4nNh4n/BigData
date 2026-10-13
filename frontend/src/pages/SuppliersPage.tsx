import React, { useEffect, useState } from 'react';
import { Plus, Edit2, Trash2, RefreshCw, Truck, Phone, Mail, MapPin } from 'lucide-react';
import { api } from '../api/client';
import type { Supplier } from '../types';
import { normalizeSupplier } from '../types';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Badge } from '../components/ui/Badge';
import { Modal } from '../components/ui/Modal';
import { Card } from '../components/ui/Card';
import { useToast } from '../components/ui/Toast';

export const SuppliersPage: React.FC = () => {
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingSupplier, setEditingSupplier] = useState<Supplier | null>(null);
  const [formData, setFormData] = useState<Partial<Supplier>>({
    supplierName: '',
    contactPerson: '',
    phone: '',
    email: '',
    address: '',
    active: true,
  });

  const { success, error } = useToast();

  const fetchSuppliers = async () => {
    setIsLoading(true);
    try {
      const res = await api.get<any>('/suppliers?page=0&size=100');
      const raw = Array.isArray(res) ? res : (res as any)?.content || [];
      setSuppliers(raw.map(normalizeSupplier));
    } catch {
      error('Lỗi khi tải danh sách nhà cung cấp');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchSuppliers();
  }, []);

  const handleOpenCreate = () => {
    setEditingSupplier(null);
    setFormData({
      supplierName: '',
      contactPerson: '',
      phone: '',
      email: '',
      address: '',
      active: true,
    });
    setIsModalOpen(true);
  };

  const handleOpenEdit = (s: Supplier) => {
    setEditingSupplier(s);
    setFormData({
      supplierName: s.supplierName,
      contactPerson: s.contactPerson || '',
      phone: s.phone || '',
      email: s.email || '',
      address: s.address || '',
      active: s.active,
    });
    setIsModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = {
        supplierName: formData.supplierName || '',
        supplierPhone: formData.phone || (formData as any).supplierPhone || '',
        supplierEmail: formData.email || (formData as any).supplierEmail || '',
        supplierAddress: formData.address || (formData as any).supplierAddress || '',
        isActive: formData.active ?? (formData as any).isActive ?? true,
      };

      if (editingSupplier) {
        const suppId = editingSupplier.id || editingSupplier.supplierId;
        await api.put(`/suppliers/${suppId}`, payload);
        success('Cập nhật nhà cung cấp thành công');
      } else {
        await api.post('/suppliers', payload);
        success('Thêm nhà cung cấp mới thành công');
      }
      setIsModalOpen(false);
      fetchSuppliers();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Bạn có chắc muốn xóa nhà cung cấp này?')) return;
    try {
      await api.delete(`/suppliers/${id}`);
      success('Đã xóa nhà cung cấp');
      fetchSuppliers();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 bg-white p-4 rounded-xl border border-gray-200/80 shadow-2xs">
        <div>
          <h2 className="text-base font-bold text-gray-900">Danh sách Nhà cung cấp</h2>
          <p className="text-xs text-gray-500">Đối tác cung ứng hàng hóa cho chuỗi siêu thị</p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
            onClick={fetchSuppliers}
          >
            Tải lại
          </Button>
          <Button size="sm" variant="primary" leftIcon={<Plus className="w-3.5 h-3.5" />} onClick={handleOpenCreate}>
            Thêm nhà cung cấp
          </Button>
        </div>
      </div>

      <Card>
        <div className="overflow-x-auto -mx-5 -my-5">
          <table className="w-full text-left text-xs">
            <thead className="bg-gray-50/75 border-b border-gray-200 text-gray-500 font-semibold uppercase tracking-wider">
              <tr>
                <th className="px-5 py-3">Mã NCC</th>
                <th className="px-5 py-3">Tên Nhà Cung Cấp</th>
                <th className="px-5 py-3">Người liên hệ</th>
                <th className="px-5 py-3">Điện thoại / Email</th>
                <th className="px-5 py-3">Địa chỉ</th>
                <th className="px-5 py-3 text-center">Trạng thái</th>
                <th className="px-5 py-3 text-center">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {suppliers.length === 0 ? (
                <tr>
                  <td colSpan={7} className="text-center py-8 text-gray-400">
                    {isLoading ? 'Đang tải dữ liệu...' : 'Chưa có nhà cung cấp nào'}
                  </td>
                </tr>
              ) : (
                suppliers.map((s) => (
                  <tr key={s.id} className="hover:bg-gray-50/50 transition-colors">
                    <td className="px-5 py-3.5 font-mono text-gray-400">#{s.id}</td>
                    <td className="px-5 py-3.5 font-semibold text-gray-900 flex items-center gap-2">
                      <Truck className="w-4 h-4 text-emerald-600" />
                      {s.supplierName}
                    </td>
                    <td className="px-5 py-3.5 text-gray-700">{s.contactPerson || '--'}</td>
                    <td className="px-5 py-3.5 space-y-0.5">
                      {s.phone && (
                        <div className="flex items-center gap-1 text-gray-600">
                          <Phone className="w-3 h-3 text-gray-400" />
                          <span>{s.phone}</span>
                        </div>
                      )}
                      {s.email && (
                        <div className="flex items-center gap-1 text-gray-500">
                          <Mail className="w-3 h-3 text-gray-400" />
                          <span>{s.email}</span>
                        </div>
                      )}
                    </td>
                    <td className="px-5 py-3.5 text-gray-500 max-w-[200px] truncate">
                      {s.address ? (
                        <span className="flex items-center gap-1">
                          <MapPin className="w-3 h-3 text-gray-400 shrink-0" />
                          {s.address}
                        </span>
                      ) : (
                        '--'
                      )}
                    </td>
                    <td className="px-5 py-3.5 text-center">
                      <Badge variant={s.active ? 'success' : 'neutral'}>
                        {s.active ? 'Đang hợp tác' : 'Tạm dừng'}
                      </Badge>
                    </td>
                    <td className="px-5 py-3.5 text-center">
                      <div className="flex items-center justify-center gap-1">
                        <button
                          type="button"
                          onClick={() => handleOpenEdit(s)}
                          className="p-1.5 text-gray-400 hover:text-emerald-600 rounded-lg cursor-pointer"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                        </button>
                        <button
                          type="button"
                          onClick={() => handleDelete(s.id)}
                          className="p-1.5 text-gray-400 hover:text-rose-600 rounded-lg cursor-pointer"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </Card>

      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={editingSupplier ? 'Chỉnh sửa nhà cung cấp' : 'Thêm nhà cung cấp mới'}
        footer={
          <>
            <Button variant="outline" size="sm" onClick={() => setIsModalOpen(false)}>
              Hủy
            </Button>
            <Button variant="primary" size="sm" onClick={handleSave}>
              {editingSupplier ? 'Lưu thay đổi' : 'Thêm mới'}
            </Button>
          </>
        }
      >
        <form onSubmit={handleSave} className="space-y-4">
          <Input
            label="Tên nhà cung cấp / Công ty"
            value={formData.supplierName}
            onChange={(e) => setFormData({ ...formData, supplierName: e.target.value })}
            required
            placeholder="Ví dụ: Công ty TNHH Nước Giải Khát ABC"
          />
          <div className="grid grid-cols-2 gap-3">
            <Input
              label="Người liên hệ đại diện"
              value={formData.contactPerson}
              onChange={(e) => setFormData({ ...formData, contactPerson: e.target.value })}
              placeholder="Nguyễn Văn A"
            />
            <Input
              label="Số điện thoại liên hệ"
              value={formData.phone}
              onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
              placeholder="0901234567"
            />
          </div>
          <Input
            label="Email"
            type="email"
            value={formData.email}
            onChange={(e) => setFormData({ ...formData, email: e.target.value })}
            placeholder="contact@supplier.vn"
          />
          <Input
            label="Địa chỉ kho hàng / trụ sở"
            value={formData.address}
            onChange={(e) => setFormData({ ...formData, address: e.target.value })}
            placeholder="Khu công nghiệp Tân Bình, TP.HCM"
          />
        </form>
      </Modal>
    </div>
  );
};
