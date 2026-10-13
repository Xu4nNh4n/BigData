import React, { useEffect, useState } from 'react';
import { Plus, Edit2, Trash2, RefreshCw, Users, Phone } from 'lucide-react';
import { api } from '../api/client';
import type { Customer } from '../types';
import { normalizeCustomer } from '../types';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Badge } from '../components/ui/Badge';
import { Modal } from '../components/ui/Modal';
import { Card } from '../components/ui/Card';
import { useToast } from '../components/ui/Toast';

export const CustomersPage: React.FC = () => {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCustomer, setEditingCustomer] = useState<Customer | null>(null);
  const [formData, setFormData] = useState<Partial<Customer>>({
    name: '',
    phone: '',
    email: '',
    address: '',
    active: true,
  });

  const { success, error } = useToast();

  const fetchCustomers = async () => {
    setIsLoading(true);
    try {
      const res = await api.get<any>('/customers?page=0&size=100');
      const raw = Array.isArray(res) ? res : (res as any)?.content || [];
      setCustomers(raw.map(normalizeCustomer));
    } catch {
      error('Lỗi khi tải danh sách khách hàng');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchCustomers();
  }, []);

  const handleOpenCreate = () => {
    setEditingCustomer(null);
    setFormData({
      name: '',
      phone: '',
      email: '',
      address: '',
      active: true,
    });
    setIsModalOpen(true);
  };

  const handleOpenEdit = (c: Customer) => {
    setEditingCustomer(c);
    setFormData({
      name: c.name || c.fullName || '',
      phone: c.phone || '',
      email: c.email || '',
      address: c.address || '',
      active: c.active ?? c.isActive ?? true,
    });
    setIsModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const payload = {
        fullName: formData.name || formData.fullName || '',
        phone: formData.phone || '',
        email: formData.email || null,
        address: formData.address || null,
        isActive: formData.active ?? formData.isActive ?? true,
      };

      if (editingCustomer) {
        const custId = editingCustomer.id || editingCustomer.customerId;
        await api.put(`/customers/${custId}`, payload);
        success('Cập nhật khách hàng thành công');
      } else {
        await api.post('/customers', payload);
        success('Thêm khách hàng thành viên mới thành công');
      }
      setIsModalOpen(false);
      fetchCustomers();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Bạn có chắc muốn xóa khách hàng này?')) return;
    try {
      await api.delete(`/customers/${id}`);
      success('Đã xóa khách hàng');
      fetchCustomers();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 bg-white p-4 rounded-xl border border-gray-200/80 shadow-2xs">
        <div>
        <h2 className="text-base font-bold text-gray-900">Danh sách khách hàng</h2>
          <p className="text-xs text-gray-500">Quản lý thông tin liên hệ và địa chỉ giao hàng</p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
            onClick={fetchCustomers}
          >
            Tải lại
          </Button>
          <Button size="sm" variant="primary" leftIcon={<Plus className="w-3.5 h-3.5" />} onClick={handleOpenCreate}>
            Thêm khách hàng
          </Button>
        </div>
      </div>

      <Card>
        <div className="overflow-x-auto -mx-5 -my-5">
          <table className="w-full text-left text-xs">
            <thead className="bg-gray-50/75 border-b border-gray-200 text-gray-500 font-semibold uppercase tracking-wider">
              <tr>
                <th className="px-5 py-3">Mã KH</th>
                <th className="px-5 py-3">Họ và Tên</th>
                <th className="px-5 py-3">Số điện thoại</th>
                <th className="px-5 py-3">Email</th>
                <th className="px-5 py-3 text-center">Trạng thái</th>
                <th className="px-5 py-3 text-center">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {customers.length === 0 ? (
                <tr>
                  <td colSpan={6} className="text-center py-8 text-gray-400">
                    {isLoading ? 'Đang tải dữ liệu...' : 'Chưa có khách hàng nào'}
                  </td>
                </tr>
              ) : (
                customers.map((c) => {
                  const custId = c.id || c.customerId || 0;
                  return (
                    <tr key={custId} className="hover:bg-gray-50/50 transition-colors">
                      <td className="px-5 py-3.5 font-mono text-gray-400">#{custId}</td>
                      <td className="px-5 py-3.5 font-semibold text-gray-900 flex items-center gap-2">
                        <Users className="w-4 h-4 text-emerald-600" />
                        {c.name || c.fullName}
                      </td>
                      <td className="px-5 py-3.5 font-mono text-gray-700">
                        <div className="flex items-center gap-1">
                          <Phone className="w-3 h-3 text-gray-400" />
                          <span>{c.phone}</span>
                        </div>
                      </td>
                      <td className="px-5 py-3.5 text-gray-500">{c.email || '--'}</td>
                      <td className="px-5 py-3.5 text-center">
                        <Badge variant={(c.active ?? c.isActive) ? 'success' : 'neutral'}>
                          {(c.active ?? c.isActive) ? 'Thành viên' : 'Đóng'}
                        </Badge>
                      </td>
                      <td className="px-5 py-3.5 text-center">
                        <div className="flex items-center justify-center gap-1">
                          <button
                            type="button"
                            onClick={() => handleOpenEdit(c)}
                            className="p-1.5 text-gray-400 hover:text-emerald-600 rounded-lg cursor-pointer"
                          >
                            <Edit2 className="w-3.5 h-3.5" />
                          </button>
                          <button
                            type="button"
                            onClick={() => handleDelete(custId)}
                            className="p-1.5 text-gray-400 hover:text-rose-600 rounded-lg cursor-pointer"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
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

      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={editingCustomer ? 'Chỉnh sửa khách hàng' : 'Thêm khách hàng mới'}
        footer={
          <>
            <Button variant="outline" size="sm" onClick={() => setIsModalOpen(false)}>
              Hủy
            </Button>
            <Button variant="primary" size="sm" onClick={handleSave}>
              {editingCustomer ? 'Lưu thay đổi' : 'Thêm mới'}
            </Button>
          </>
        }
      >
        <form onSubmit={handleSave} className="space-y-4">
          <Input
            label="Họ và tên khách hàng"
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            required
            placeholder="Ví dụ: Trần Văn B"
          />
          <div>
            <Input
              label="Số điện thoại"
              value={formData.phone}
              onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
              required
              placeholder="0987654321"
            />
          </div>
          <Input
            label="Email"
            type="email"
            value={formData.email}
            onChange={(e) => setFormData({ ...formData, email: e.target.value })}
            placeholder="customer@gmail.com"
          />
          <Input
            label="Địa chỉ giao hàng"
            value={formData.address}
            onChange={(e) => setFormData({ ...formData, address: e.target.value })}
            placeholder="123 Lê Lợi, Quận 1, TP.HCM"
          />
        </form>
      </Modal>
    </div>
  );
};
