import { useEffect, useId, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
  side?: boolean;
  busy?: boolean;
}

// Native modal dialogs keep keyboard focus inside, make the page inert,
// and return focus to the trigger after closing.
export function ShopOverlay({ open, onClose, title, description, children, footer, side = false, busy = false }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const element = dialog.current;
    if (!element || !open) return;
    element.showModal();
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      element.close();
      document.body.style.overflow = previousOverflow;
    };
  }, [open]);

  return <dialog ref={dialog} aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); if (!busy) onClose(); }}
    onClick={event => { if (event.target === event.currentTarget && !busy) onClose(); }}
    className={`shop-overlay fixed inset-0 m-0 h-dvh max-h-none w-screen max-w-none bg-transparent p-0 text-gray-900 ${side ? 'shop-overlay-side' : ''}`}>
    {open && <div className={`pointer-events-none flex h-full w-full ${side ? 'justify-end' : 'items-center justify-center p-3 sm:p-6'}`}>
      <section className={`pointer-events-auto flex w-full flex-col overflow-hidden bg-white shadow-2xl ${side ? 'h-full max-w-lg shop-drawer-panel' : 'max-h-[92dvh] max-w-xl rounded-2xl'}`}>
        <header className="flex shrink-0 items-start justify-between gap-4 border-b border-gray-100 px-5 py-5 sm:px-6">
          <div><h2 id={titleId} className="text-lg font-bold">{title}</h2>{description && <p className="mt-1 text-sm text-gray-500">{description}</p>}</div>
          <button type="button" aria-label={`Đóng ${title}`} disabled={busy} onClick={onClose} className="rounded-full p-2 text-gray-500 hover:bg-gray-100 focus-visible:ring-2 focus-visible:ring-emerald-600 disabled:opacity-40"><X size={20} /></button>
        </header>
        <div className="min-h-0 flex-1 overflow-y-auto p-5 sm:p-6">{children}</div>
        {footer && <footer className="shrink-0 border-t border-gray-100 bg-gray-50 p-5 sm:px-6">{footer}</footer>}
      </section>
    </div>}
  </dialog>;
}
