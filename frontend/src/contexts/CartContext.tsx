import React, { createContext, useContext, useState, useMemo, useEffect, useRef, useCallback } from 'react';
import { useAuth } from './AuthContext';
import type { Product, Customer, PaymentMethod, OrderType } from '../types';
import { normalizeProduct } from '../types';

export interface CartItem {
  product: Product;
  quantity: number;
}

interface CartContextType {
  items: CartItem[];
  selectedCustomer: Customer | null;
  setSelectedCustomer: (customer: Customer | null) => void;
  orderType: OrderType;
  setOrderType: (type: OrderType) => void;
  paymentMethod: PaymentMethod;
  setPaymentMethod: (method: PaymentMethod) => void;
  note: string;
  setNote: (note: string) => void;
  shippingAddress: string;
  setShippingAddress: (address: string) => void;
  receiverName: string;
  setReceiverName: (name: string) => void;
  receiverPhone: string;
  setReceiverPhone: (phone: string) => void;
  addItem: (product: Product, quantity?: number) => boolean;
  updateQuantity: (productId: number, quantity: number) => void;
  removeItem: (productId: number) => void;
  clearCart: () => void;
  refreshProducts: (products: Product[]) => void;
  subtotal: number;
  total: number;
  itemCount: number;
}

const CartContext = createContext<CartContextType | undefined>(undefined);

export const CartProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user } = useAuth();
  const previousOwner = useRef(user?.id);
  const [items, setItems] = useState<CartItem[]>([]);
  const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(null);
  const [orderType, setOrderType] = useState<OrderType>('IN_STORE');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('CASH');
  const [note, setNote] = useState<string>('Bán tại quầy');
  const [shippingAddress, setShippingAddress] = useState<string>('');
  const [receiverName, setReceiverName] = useState<string>('');
  const [receiverPhone, setReceiverPhone] = useState<string>('');

  useEffect(() => {
    // Keep a guest's shopping selection when they sign in as a customer,
    // but never carry another account's cart into a new session.
    if (previousOwner.current !== user?.id && (previousOwner.current != null || user?.role !== 'CUSTOMER')) {
      setItems([]);
      setSelectedCustomer(null);
      setNote('Bán tại quầy');
      setPaymentMethod('CASH');
      setShippingAddress('');
      setReceiverName('');
      setReceiverPhone('');
    }
    previousOwner.current = user?.id;
  }, [user?.id, user?.role]);

  const addItem = (product: Product, quantity: number = 1): boolean => {
    const norm = normalizeProduct(product);
    const prodId = norm.id || norm.productId || 0;
    const available = norm.availableQuantity ?? norm.totalQuantity ?? 0;
    if (!norm.active || available <= 0) {
      return false;
    }

    setItems((prev) => {
      const existingIndex = prev.findIndex(
        (item) => (item.product.id || item.product.productId) === prodId
      );
      if (existingIndex > -1) {
        const currentQty = prev[existingIndex].quantity;
        const newQty = Math.min(currentQty + quantity, available);
        const updated = [...prev];
        updated[existingIndex] = { ...updated[existingIndex], quantity: newQty };
        return updated;
      }
      return [...prev, { product: norm, quantity: Math.min(quantity, available) }];
    });
    return true;
  };

  const updateQuantity = (productId: number, quantity: number) => {
    if (quantity <= 0) {
      removeItem(productId);
      return;
    }
    setItems((prev) =>
      prev.map((item) => {
        const id = item.product.id || item.product.productId;
        if (id === productId) {
          const available = item.product.availableQuantity ?? item.product.totalQuantity ?? 0;
          if (!item.product.active || available <= 0) return item;
          return { ...item, quantity: Math.min(quantity, available) };
        }
        return item;
      })
    );
  };

  const removeItem = (productId: number) => {
    setItems((prev) =>
      prev.filter((item) => (item.product.id || item.product.productId) !== productId)
    );
  };

  const clearCart = () => {
    setItems([]);
    setNote('Bán tại quầy');
    setPaymentMethod('CASH');
    setSelectedCustomer(null);
    setShippingAddress('');
    setReceiverName('');
    setReceiverPhone('');
  };

  const refreshProducts = useCallback((products: Product[]) => {
    const latest = new Map(products.map(product => [product.id, product]));
    // Preserve quantities so shoppers can see and resolve shortages explicitly.
    setItems(previous => previous.map(item => {
      const product = latest.get(item.product.id);
      return product ? { ...item, product } : item;
    }));
  }, []);

  const subtotal = useMemo(() => {
    return items.reduce((sum, item) => {
      const price = item.product.sellingPrice ?? item.product.price ?? 0;
      return sum + price * item.quantity;
    }, 0);
  }, [items]);

  const total = subtotal;

  const itemCount = useMemo(() => {
    return items.reduce((sum, item) => sum + item.quantity, 0);
  }, [items]);

  return (
    <CartContext.Provider
      value={{
        items,
        selectedCustomer,
        setSelectedCustomer,
        orderType,
        setOrderType,
        paymentMethod,
        setPaymentMethod,
        note,
        setNote,
        shippingAddress,
        setShippingAddress,
        receiverName,
        setReceiverName,
        receiverPhone,
        setReceiverPhone,
        addItem,
        updateQuantity,
        removeItem,
        clearCart,
        refreshProducts,
        subtotal,
        total,
        itemCount,
      }}
    >
      {children}
    </CartContext.Provider>
  );
};

export function useCart() {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used within a CartProvider');
  }
  return context;
}
