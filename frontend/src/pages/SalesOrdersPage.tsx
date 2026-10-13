import React, { useState, useEffect } from 'react';
import {
  Search,
  Eye,
  RotateCcw,
  CheckCircle2,
  Truck,
  XCircle,
  RefreshCw,
  DollarSign,
} from 'lucide-react';
import { api } from '../api/client';
import type {
  SalesOrder,
  ReturnOrderRequest,
  SalesOrderReturnResponse,
} from '../types';
import { normalizeOrder } from '../types';
import {
  formatVND,
  formatDateTime,
  ORDER_STATUS_LABELS,
  PAYMENT_STATUS_LABELS,
  PAYMENT_METHOD_LABELS,
} from '../utils/formatters';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Modal } from '../components/ui/Modal';
import { Card } from '../components/ui/Card';
import { useToast } from '../components/ui/Toast';

export const SalesOrdersPage: React.FC = () => {
  const [orders, setOrders] = useState<SalesOrder[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Detail Modal
  const [selectedOrder, setSelectedOrder] = useState<SalesOrder | null>(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);

  // Partial Return Modal
  const [isReturnOpen, setIsReturnOpen] = useState(false);
  const [returnItems, setReturnItems] = useState<
    Array<{
      orderItemId: number;
      productName: string;
      maxQty: number;
      quantity: number;
      restock: boolean;
      reason: string;
    }>
  >([]);

  const { success, error, warning } = useToast();

  const fetchOrders = async () => {
    setIsLoading(true);
    try {
      const res = await api.get<any>('/orders?page=0&size=100');
      const raw = Array.isArray(res) ? res : (res as any)?.content || [];
      setOrders(raw.map(normalizeOrder));
    } catch {
      error('Lỗi khi tải danh sách đơn hàng');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders();
  }, []);

  // Actions on Orders
  const handleConfirmPayment = async (orderId: number) => {
    try {
      await api.put(`/orders/${orderId}/pay`, {
        reference: `PAY-${Date.now().toString().slice(-6)}`,
        amount: selectedOrder?.finalAmount || 0,
      });
      success('Đã xác nhận thanh toán chuyển khoản thành công');
      setIsDetailOpen(false);
      fetchOrders();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  const handleShipOrder = async (orderId: number) => {
    try {
      await api.put(`/orders/${orderId}/ship`);
      success('Đã chuyển đơn hàng sang trạng thái đang giao');
      setIsDetailOpen(false);
      fetchOrders();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  const handleDeliverOrder = async (orderId: number) => {
    try {
      await api.put(`/orders/${orderId}/deliver`);
      success('Đã xác nhận giao hàng thành công! Đã thu tiền (nếu COD)');
      setIsDetailOpen(false);
      fetchOrders();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  const handleCancelOrder = async (orderId: number) => {
    if (!window.confirm('Bạn có chắc muốn hủy đơn hàng này? Hệ thống sẽ giải phóng hàng tồn.')) return;
    try {
      await api.put(`/orders/${orderId}/cancel`);
      success('Đã hủy đơn hàng');
      setIsDetailOpen(false);
      fetchOrders();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  const handleFailDelivery = async (orderId: number) => {
    if (!window.confirm('Xác nhận giao hàng không thành công? Đơn hàng sẽ chuyển sang "Hàng đang trả về" (chưa cộng lại tồn kho cho tới khi kiểm nhận thực tế tại kho).')) return;
    try {
      await api.put(`/orders/${orderId}/delivery-failed`);
      success('Đã ghi nhận giao thất bại: Hàng đang quay về siêu thị');
      setIsDetailOpen(false);
      fetchOrders();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  const handleDirectRefund = async (orderId: number) => {
    const refundAmount = selectedOrder?.finalAmount || selectedOrder?.totalAmount || 0;
    if (!window.confirm(`Xác nhận hoàn tiền cho đơn hủy #${orderId} (Số tiền: ${formatVND(refundAmount)})?`)) return;
    try {
      await api.put(`/orders/${orderId}/refund`, {
        reference: `REFUND-CANCEL-${Date.now().toString().slice(-6)}`,
        amount: refundAmount,
      });
      success('Đã xác nhận hoàn tiền cho đơn hàng hủy thành công!');
      setIsDetailOpen(false);
      fetchOrders();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  // Open Partial Return Modal
  const handleOpenReturnModal = (order: SalesOrder) => {
    setSelectedOrder(order);
    setReturnItems(
      order.items.map((i) => ({
        orderItemId: i.id,
        productName: i.productName,
        maxQty: i.quantity,
        quantity: 0,
        restock: true,
        reason: 'Khách đổi ý',
      }))
    );
    setIsReturnOpen(true);
  };

  const handleSubmitReturn = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedOrder) return;

    const itemsToReturn = returnItems.filter((i: { quantity: number }) => i.quantity > 0);
    if (itemsToReturn.length === 0) {
      warning('Vui lòng chọn số lượng trả > 0 cho ít nhất một sản phẩm!');
      return;
    }

    const payload: ReturnOrderRequest = {
      requestKey: `ret-${selectedOrder.id}-${Date.now().toString().slice(-6)}`,
      items: itemsToReturn.map((i: { orderItemId: number; quantity: number; restock: boolean; reason: string }) => ({
        orderItemId: i.orderItemId,
        quantity: i.quantity,
        restock: i.restock,
        reason: i.reason,
      })),
    };

    try {
      const res = await api.put<SalesOrderReturnResponse>(`/orders/${selectedOrder.id}/return`, payload);
      success(`Tạo phiếu trả hàng #${res.returnId} thành công! Số tiền hoàn: ${formatVND(res.refundAmount)}`);

      // If refund is pending, automatically prompt refund confirmation
      if (res.refundStatus === 'REFUND_PENDING') {
        const confirmNow = window.confirm(
          `Phiếu trả đang ở trạng thái Chờ Hoàn Tiền (Số tiền: ${formatVND(res.refundAmount)}). Bạn có muốn xác nhận chi tiền hoàn trả khách ngay bây giờ không?`
        );
        if (confirmNow) {
          await api.put(`/orders/returns/${res.returnId}/refund`);
          success('Đã xác nhận chi tiền hoàn tất cho khách!');
        }
      }

      setIsReturnOpen(false);
      setIsDetailOpen(false);
      fetchOrders();
    } catch (err: unknown) {
      if (err instanceof Error) error(err.message);
    }
  };

  const filteredOrders = orders.filter((o: SalesOrder) => {
    const matchesSearch =
      o.orderCode.toLowerCase().includes(searchTerm.toLowerCase()) ||
      (o.customerPhone && o.customerPhone.includes(searchTerm)) ||
      (o.customerName && o.customerName.toLowerCase().includes(searchTerm.toLowerCase()));

    const matchesStatus = statusFilter === 'ALL' || o.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

  return (
    <div className="space-y-6">
      {/* Top Banner */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 bg-white p-4 rounded-xl border border-gray-200/80 shadow-2xs">
        <div>
          <h2 className="text-base font-bold text-gray-900">Quản lý Đơn hàng & Trả hàng</h2>
          <p className="text-xs text-gray-500">
            Theo dõi trạng thái giao hàng, xác nhận chuyển khoản ngân hàng và thực hiện đổi trả từng món
          </p>
        </div>
        <Button
          size="sm"
          variant="outline"
          leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isLoading ? 'animate-spin' : ''}`} />}
          onClick={fetchOrders}
        >
          Tải lại
        </Button>
      </div>

      {/* Filter Bar */}
      <Card>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <div className="sm:col-span-2">
            <Input
              placeholder="Tìm theo mã đơn hàng, số điện thoại hoặc tên khách hàng..."
              leftIcon={<Search className="w-4 h-4" />}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
            />
          </div>
          <div>
            <Select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              options={[
                { value: 'ALL', label: 'Tất cả trạng thái đơn' },
                { value: 'PAID', label: 'Đã thanh toán (PAID)' },
                { value: 'PENDING_PAYMENT', label: 'Chờ thanh toán chuyển khoản' },
                { value: 'COD_PENDING', label: 'Chờ thu tiền COD' },
                { value: 'DELIVERED', label: 'Đã giao hàng (DELIVERED)' },
                { value: 'CANCELLED', label: 'Đã hủy đơn (CANCELLED)' },
                { value: 'PARTIALLY_RETURNED', label: 'Đã trả một phần' },
                { value: 'RETURNED', label: 'Đã trả toàn bộ' },
              ]}
            />
          </div>
        </div>
      </Card>

      {/* Orders Table */}
      <Card>
        <div className="overflow-x-auto -mx-5 -my-5">
          <table className="w-full text-left text-xs">
            <thead className="bg-gray-50/75 border-b border-gray-200 text-gray-500 font-semibold uppercase tracking-wider">
              <tr>
                <th className="px-4 py-3">Mã Đơn</th>
                <th className="px-4 py-3">Thời gian</th>
                <th className="px-4 py-3">Kênh bán</th>
                <th className="px-4 py-3">Khách hàng</th>
                <th className="px-4 py-3 text-right">Tổng tiền</th>
                <th className="px-4 py-3 text-center">Trạng thái đơn</th>
                <th className="px-4 py-3 text-center">Thanh toán</th>
                <th className="px-4 py-3 text-center">Giao hàng</th>
                <th className="px-4 py-3 text-center">Thao tác</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 bg-white">
              {filteredOrders.length === 0 ? (
                <tr>
                  <td colSpan={9} className="text-center py-10 text-gray-400">
                    {isLoading ? 'Đang tải danh sách đơn...' : 'Không có đơn hàng nào phù hợp'}
                  </td>
                </tr>
              ) : (
                filteredOrders.map((o: SalesOrder) => {
                  const statusObj = ORDER_STATUS_LABELS[o.status] || {
                    label: o.status,
                    color: 'text-gray-700',
                    bg: 'bg-gray-50 border-gray-200',
                  };
                  const payObj = PAYMENT_STATUS_LABELS[o.paymentStatus] || {
                    label: o.paymentStatus,
                    color: 'text-gray-700',
                    bg: 'bg-gray-50 border-gray-200',
                  };

                  return (
                    <tr key={o.id} className="hover:bg-gray-50/50 transition-colors">
                      <td className="px-4 py-3.5 font-mono font-medium text-emerald-800">
                        {o.orderCode}
                      </td>
                      <td className="px-4 py-3.5 text-gray-500">{formatDateTime(o.createdAt)}</td>
                      <td className="px-4 py-3.5">
                        <span
                          className={`text-[10px] font-semibold px-2 py-0.5 rounded-full ${
                            o.orderType === 'IN_STORE'
                              ? 'bg-emerald-50 text-emerald-700'
                              : 'bg-blue-50 text-blue-700'
                          }`}
                        >
                          {o.orderType === 'IN_STORE' ? 'Tại quầy POS' : 'Trực tuyến'}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-gray-900 font-medium">
                        {o.customerName || 'Khách lẻ vãng lai'}
                      </td>
                      <td className="px-4 py-3.5 text-right font-bold text-gray-900">
                        {formatVND(o.finalAmount || o.totalAmount)}
                      </td>
                      <td className="px-4 py-3.5 text-center">
                        <span
                          className={`inline-flex px-2 py-0.5 rounded-full text-[11px] font-medium border ${statusObj.bg} ${statusObj.color}`}
                        >
                          {statusObj.label}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-center">
                        <span
                          className={`inline-flex px-2 py-0.5 rounded-full text-[11px] font-medium border ${payObj.bg} ${payObj.color}`}
                        >
                          {payObj.label}
                        </span>
                      </td>
                      <td className="px-4 py-3.5 text-center text-gray-600">
                        {o.deliveryStatus === 'NOT_REQUIRED'
                          ? 'Tại quầy'
                          : o.deliveryStatus === 'DELIVERED'
                          ? 'Đã nhận'
                          : o.deliveryStatus === 'SHIPPED'
                          ? 'Đang giao'
                          : 'Chờ giao'}
                      </td>
                      <td className="px-4 py-3.5 text-center">
                        <div className="flex items-center justify-center gap-1">
                          <Button
                            size="sm"
                            variant="ghost"
                            onClick={() => {
                              setSelectedOrder(o);
                              setIsDetailOpen(true);
                            }}
                          >
                            <Eye className="w-3.5 h-3.5 mr-1" />
                            Xem
                          </Button>
                          {['PAID', 'DELIVERED', 'PARTIALLY_RETURNED'].includes(o.status) && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => handleOpenReturnModal(o)}
                              title="Tạo phiếu trả hàng"
                              className="text-amber-700 border-amber-300 hover:bg-amber-50"
                            >
                              <RotateCcw className="w-3 h-3 mr-1" />
                              Trả hàng
                            </Button>
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

      {/* Order Details Modal */}
      <Modal
        isOpen={isDetailOpen}
        onClose={() => setIsDetailOpen(false)}
        title={`Chi tiết đơn hàng: ${selectedOrder?.orderCode || ''}`}
        maxWidth="xl"
        footer={
          selectedOrder && (
            <div className="flex items-center gap-2">
              {/* Payment confirm for manual BANK_TRANSFER */}
              {selectedOrder.status === 'PENDING_PAYMENT' && (
                <Button
                  size="sm"
                  variant="primary"
                  leftIcon={<DollarSign className="w-4 h-4" />}
                  onClick={() => handleConfirmPayment(selectedOrder.id)}
                >
                  Xác nhận đã nhận chuyển khoản
                </Button>
              )}

              {/* Shipping action */}
              {selectedOrder.status === 'PAID' &&
                selectedOrder.deliveryStatus === 'PENDING' && (
                  <Button
                    size="sm"
                    variant="primary"
                    leftIcon={<Truck className="w-4 h-4" />}
                    onClick={() => handleShipOrder(selectedOrder.id)}
                  >
                    Xuất kho & Giao hàng
                  </Button>
                )}

              {/* Delivery complete or failed */}
              {selectedOrder.deliveryStatus === 'SHIPPED' && (
                <>
                  <Button
                    size="sm"
                    variant="primary"
                    leftIcon={<CheckCircle2 className="w-4 h-4" />}
                    onClick={() => handleDeliverOrder(selectedOrder.id)}
                  >
                    Xác nhận khách đã nhận hàng
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    className="border-rose-300 text-rose-700 hover:bg-rose-50"
                    leftIcon={<RotateCcw className="w-4 h-4 text-rose-600" />}
                    onClick={() => handleFailDelivery(selectedOrder.id)}
                  >
                    Báo giao thất bại (Hàng quay đầu)
                  </Button>
                </>
              )}

              {/* Direct Refund for cancelled online orders that were paid */}
              {(selectedOrder.paymentStatus === 'REFUND_PENDING' ||
                (selectedOrder.status === 'CANCELLED' && selectedOrder.paymentStatus === 'PAID')) && (
                <Button
                  size="sm"
                  variant="primary"
                  className="bg-purple-700 hover:bg-purple-800 text-white"
                  leftIcon={<DollarSign className="w-4 h-4" />}
                  onClick={() => handleDirectRefund(selectedOrder.id)}
                >
                  Xác nhận hoàn tiền đơn hủy ({formatVND(selectedOrder.finalAmount || selectedOrder.totalAmount)})
                </Button>
              )}

              {/* Cancel order */}
              {['PENDING_PAYMENT', 'COD_PENDING'].includes(selectedOrder.status) && (
                <Button
                  size="sm"
                  variant="danger"
                  leftIcon={<XCircle className="w-4 h-4" />}
                  onClick={() => handleCancelOrder(selectedOrder.id)}
                >
                  Hủy đơn
                </Button>
              )}

              <Button size="sm" variant="outline" onClick={() => setIsDetailOpen(false)}>
                Đóng
              </Button>
            </div>
          )
        }
      >
        {selectedOrder && (
          <div className="space-y-4 text-xs">
            {/* Meta summary */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 p-3 bg-gray-50 rounded-xl">
              <div>
                <span className="text-gray-400 block">Thời gian tạo:</span>
                <span className="font-semibold text-gray-900">
                  {formatDateTime(selectedOrder.createdAt)}
                </span>
              </div>
              <div>
                <span className="text-gray-400 block">Kênh bán:</span>
                <span className="font-semibold text-gray-900">
                  {selectedOrder.orderType === 'IN_STORE' ? 'Bán tại quầy' : 'Online'}
                </span>
              </div>
              <div>
                <span className="text-gray-400 block">Thanh toán:</span>
                <span className="font-semibold text-gray-900">
                  {PAYMENT_METHOD_LABELS[selectedOrder.paymentMethod]}
                </span>
              </div>
              <div>
                <span className="text-gray-400 block">Khách hàng:</span>
                <span className="font-semibold text-gray-900">
                  {selectedOrder.customerName || 'Khách lẻ'}
                </span>
              </div>
            </div>

            {/* Online shipping details if any */}
            {selectedOrder.orderType === 'ONLINE' && (
              <div className="p-3 border border-blue-100 bg-blue-50/40 rounded-xl space-y-1">
                <p className="font-semibold text-blue-900">Thông tin giao hàng:</p>
                <p className="text-gray-700">
                  Người nhận: {selectedOrder.receiverName} ({selectedOrder.receiverPhone})
                </p>
                <p className="text-gray-700">Địa chỉ: {selectedOrder.shippingAddress}</p>
              </div>
            )}

            {/* Line items table */}
            <div className="border border-gray-200 rounded-lg overflow-hidden">
              <table className="w-full text-left">
                <thead className="bg-gray-50 border-b border-gray-200 text-gray-500 font-semibold">
                  <tr>
                    <th className="p-2.5">Sản phẩm</th>
                    <th className="p-2.5">Mã SKU</th>
                    <th className="p-2.5 text-center">SL</th>
                    <th className="p-2.5 text-right">Đơn giá</th>
                    <th className="p-2.5 text-right">Thành tiền</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {selectedOrder.items.map((i: { id: number; productName: string; skuCode: string; quantity: number; unitPrice: number; totalPrice: number }) => (
                    <tr key={i.id}>
                      <td className="p-2.5 font-medium text-gray-900">{i.productName}</td>
                      <td className="p-2.5 font-mono text-gray-500">{i.skuCode}</td>
                      <td className="p-2.5 text-center font-bold">{i.quantity}</td>
                      <td className="p-2.5 text-right text-gray-600">{formatVND(i.unitPrice)}</td>
                      <td className="p-2.5 text-right font-bold text-gray-900">
                        {formatVND(i.totalPrice)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Total footer */}
            <div className="p-3 bg-gray-50 rounded-xl flex justify-between items-center text-sm font-bold">
              <span>TỔNG TIỀN ĐƠN HÀNG:</span>
              <span className="text-emerald-700 text-base">
                {formatVND(selectedOrder.finalAmount || selectedOrder.totalAmount)}
              </span>
            </div>
          </div>
        )}
      </Modal>

      {/* Partial Return & Quarantine Modal */}
      <Modal
        isOpen={isReturnOpen}
        onClose={() => setIsReturnOpen(false)}
        title={`Tạo phiếu trả hàng & cách ly: ${selectedOrder?.orderCode}`}
        maxWidth="lg"
        footer={
          <>
            <Button variant="outline" size="sm" onClick={() => setIsReturnOpen(false)}>
              Hủy
            </Button>
            <Button variant="primary" size="sm" onClick={handleSubmitReturn}>
              Xác nhận trả hàng & tạo phiếu hoàn tiền
            </Button>
          </>
        }
      >
        <form onSubmit={handleSubmitReturn} className="space-y-4 text-xs">
          <p className="text-gray-500">
            Chọn số lượng món muốn trả và cờ kiểm định:{' '}
            <b className="text-emerald-700">Bán lại</b> (nhập lại tồn khả dụng FEFO) hoặc{' '}
            <b className="text-rose-700">Cách ly</b> (hàng móp méo/hỏng không thể bán).
          </p>

          <div className="space-y-3">
            {returnItems.map((item: { orderItemId: number; productName: string; maxQty: number; quantity: number; restock: boolean; reason: string }, idx: number) => (
              <div
                key={item.orderItemId}
                className="p-3 border border-gray-200 rounded-xl space-y-2 bg-gray-50/40"
              >
                <div className="flex justify-between items-center">
                  <span className="font-semibold text-gray-900">{item.productName}</span>
                  <span className="text-gray-400">Đã mua: {item.maxQty}</span>
                </div>

                <div className="grid grid-cols-3 gap-2">
                  <Input
                    label="Số lượng trả"
                    type="number"
                    min={0}
                    max={item.maxQty}
                    value={item.quantity}
                    onChange={(e) => {
                      const updated = [...returnItems];
                      updated[idx].quantity = Number(e.target.value);
                      setReturnItems(updated);
                    }}
                  />

                  <Select
                    label="Quy trình xử lý"
                    value={item.restock ? 'RESTOCK' : 'QUARANTINE'}
                    onChange={(e) => {
                      const updated = [...returnItems];
                      updated[idx].restock = e.target.value === 'RESTOCK';
                      setReturnItems(updated);
                    }}
                    options={[
                      { value: 'RESTOCK', label: 'Bán lại (Restock)' },
                      { value: 'QUARANTINE', label: 'Cách ly/Hỏng (Quarantine)' },
                    ]}
                  />

                  <Input
                    label="Lý do trả"
                    value={item.reason}
                    onChange={(e) => {
                      const updated = [...returnItems];
                      updated[idx].reason = e.target.value;
                      setReturnItems(updated);
                    }}
                  />
                </div>
              </div>
            ))}
          </div>
        </form>
      </Modal>
    </div>
  );
};
