import React, { useEffect, useState } from 'react';
import { Plus, Edit2, Trash2, RefreshCw, Layers } from 'lucide-react';
import { api } from '../api/client';
import type { Category } from '../types';
import { normalizeCategory } from '../types';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Badge } from '../components/ui/Badge';
import { Modal } from '../components/ui/Modal';
import { Card } from '../components/ui/Card';
import { useToast } from '../components/ui/Toast';

export const CategoriesPage: React.FC = () => {
  const [categories, setCategories] = useState<Category[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<Category | null>(null);
  const [categoryName, setCategoryName] = useState('');
  const [description, setDescription] = useState('');
  const { success, error } = useToast();

  const fetchCategories = async () => {
    setIsLoading(true);
    try {
      const res = await api.get<any>('/categories?page=0&size=100');
      const raw = Array.isArray(res) ? res : (res as any)?.content || [];
      setCategories(raw.map(normalizeCategory));
    } catch {
      error('Không thể tải danh sách ngành hàng');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchCategories();
  }, []);

  const handleOpenCreate = () => {
    setEditingCategory(null);
    setCategoryName('');
    setDescription('');
    setIsModalOpen(true);
  };

  const handleOpenEdit = (c: Category) => {
    setEditingCategory(c);
    setCategoryName(c.categoryName);
    setDescription(c.description || '');
    setIsModalOpen(true);
  };

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      if (editingCategory) {
        const catId = editingCategory.id || editingCategory.categoryId;
        await api.put(`/categories/${catId}`, { categoryName, description, active: true });
        success('Cập nhật ngành hàng thành công');
      } else {
        await api.post('/categories', { categoryName, description, active: true });
        success('Thêm ngành hàng mới thành công');
      }
      setIsModalOpen(false);
      fetchCategories();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('Bạn có chắc muốn xóa ngành hàng này?')) return;
    try {
      await api.delete(`/categories/${id}`);
      success('Đã xóa ngành hàng');
      fetchCategories();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 bg-white p-4 rounded-xl border border-gray-200/80 shadow-2xs">
        <div>
          <h2 className="text-base font-bold text-gray-900">Danh mục ngành hàng</h2>
          <p className="text-xs text-gray-500">Phân loại hàng hóa trong siêu thị (Nước ngọt, Sữa, Đồ khô, Thực phẩm...)</p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant="outline"
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
            onClick={fetchCategories}
          >
            Tải lại
          </Button>
          <Button size="sm" variant="primary" leftIcon={<Plus className="w-3.5 h-3.5" />} onClick={handleOpenCreate}>
            Thêm danh mục
          </Button>
        </div>
      </div>

      <Card>
        <div className="overflow-x-auto -mx-5 -my-5">
          <table className="w-full text-left text-xs">
            <thead className="bg-gray-50/75 border-b border-gray-200 text-gray-500 font-semibold uppercase tracking-wider">
              <tr>
                <th className="px-5 py-3">Mã ID</th>
                <th className="px-5 py-3">Tên ngành hàng</th>
                <th className="px-5 py-3">Mô tả</th>
                <th className="px-5 py-3 text-center">Trạng thái</th>
                <th className="px-5 py-3 text-center">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {categories.length === 0 ? (
                <tr>
                  <td colSpan={5} className="text-center py-8 text-gray-400">
                    {isLoading ? 'Đang tải danh mục...' : 'Chưa có danh mục nào'}
                  </td>
                </tr>
              ) : (
                categories.map((c) => (
                  <tr key={c.id} className="hover:bg-gray-50/50 transition-colors">
                    <td className="px-5 py-3.5 font-mono text-gray-400">#{c.id}</td>
                    <td className="px-5 py-3.5 font-semibold text-gray-900 flex items-center gap-2">
                      <Layers className="w-4 h-4 text-emerald-600" />
                      {c.categoryName}
                    </td>
                    <td className="px-5 py-3.5 text-gray-500">{c.description || '--'}</td>
                    <td className="px-5 py-3.5 text-center">
                      <Badge variant={c.active ? 'success' : 'neutral'}>
                        {c.active ? 'Đang áp dụng' : 'Khóa'}
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
                          onClick={() => handleDelete(c.id)}
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
        title={editingCategory ? 'Chỉnh sửa ngành hàng' : 'Thêm ngành hàng mới'}
        footer={
          <>
            <Button variant="outline" size="sm" onClick={() => setIsModalOpen(false)}>
              Hủy
            </Button>
            <Button variant="primary" size="sm" onClick={handleSave}>
              {editingCategory ? 'Lưu thay đổi' : 'Tạo mới'}
            </Button>
          </>
        }
      >
        <form onSubmit={handleSave} className="space-y-4">
          <Input
            label="Tên danh mục / ngành hàng"
            value={categoryName}
            onChange={(e) => setCategoryName(e.target.value)}
            required
            placeholder="Ví dụ: Đồ uống đóng chai"
          />
          <div>
            <label className="block text-xs font-semibold text-gray-700 mb-1">Mô tả tóm tắt</label>
            <textarea
              className="w-full rounded-lg border border-gray-300 text-sm p-2.5 focus:outline-none focus:ring-2 focus:ring-emerald-500"
              rows={3}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Thông tin quy chuẩn bảo quản hoặc phân nhóm..."
            />
          </div>
        </form>
      </Modal>
    </div>
  );
};
