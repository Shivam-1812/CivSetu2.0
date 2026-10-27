import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';
import { useId } from 'react';
import { Loader2 } from 'lucide-react';

import { ApiError } from '../api/client';
import { buttonVariants } from './ui/button';
import { cn } from '../lib/utils';

/**
 * Small, accessible form and layout blocks shared by the staff and admin pages, styled like the
 * shadcn components in ./ui so every screen looks the same.
 */

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';

const buttonVariant: Record<ButtonVariant, 'default' | 'outline' | 'destructive' | 'ghost'> = {
  primary: 'default',
  secondary: 'outline',
  danger: 'destructive',
  ghost: 'ghost',
};

export function Button({
  variant = 'primary',
  busy = false,
  className = '',
  children,
  disabled,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant; busy?: boolean }) {
  return (
    <button
      type="button"
      {...props}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      className={cn(buttonVariants({ variant: buttonVariant[variant], size: 'sm' }), 'h-10 px-4 text-sm', className)}
    >
      {busy && <Loader2 className="animate-spin" />}
      {children}
    </button>
  );
}

interface FieldProps {
  label: string;
  error?: string;
  hint?: string;
}

const inputStyles =
  'flex w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 aria-[invalid=true]:border-destructive';

function FieldShell({ id, label, error, hint, children }: FieldProps & { id: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium leading-none">
        {label}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-muted-foreground">{hint}</p>}
      {error && (
        <p id={`${id}-error`} className="text-xs text-destructive">
          {error}
        </p>
      )}
    </div>
  );
}

export function TextField({ label, error, hint, ...props }: FieldProps & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <FieldShell id={id} label={label} error={error} hint={hint}>
      <input
        id={id}
        aria-invalid={!!error}
        aria-describedby={error ? `${id}-error` : undefined}
        className={cn(inputStyles, 'h-10')}
        {...props}
      />
    </FieldShell>
  );
}

export function TextArea({ label, error, hint, ...props }: FieldProps & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId();
  return (
    <FieldShell id={id} label={label} error={error} hint={hint}>
      <textarea id={id} aria-invalid={!!error} rows={4} className={cn(inputStyles, 'min-h-[80px]')} {...props} />
    </FieldShell>
  );
}

export function SelectField({
  label,
  error,
  hint,
  children,
  ...props
}: FieldProps & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId();
  return (
    <FieldShell id={id} label={label} error={error} hint={hint}>
      <select id={id} aria-invalid={!!error} className={cn(inputStyles, 'h-10')} {...props}>
        {children}
      </select>
    </FieldShell>
  );
}

export function Card({ title, actions, children, className = '' }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={cn('rounded-2xl border border-border/50 bg-card p-6 shadow-sm', className)}>
      {(title || actions) && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          {title && <h2 className="font-display text-lg font-bold">{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

export function Alert({ tone = 'error', children }: { tone?: 'error' | 'success' | 'info'; children: ReactNode }) {
  const styles = {
    error: 'border-destructive/20 bg-destructive/5 text-destructive',
    success: 'border-success/20 bg-success/10 text-success',
    info: 'border-info/20 bg-info/10 text-foreground',
  }[tone];
  return (
    <div role={tone === 'error' ? 'alert' : 'status'} className={cn('rounded-lg border px-4 py-3 text-sm', styles)}>
      {children}
    </div>
  );
}

/** Shows an API or network error in plain words. */
export function ErrorMessage({ error }: { error: unknown }) {
  if (!error) return null;
  const message =
    error instanceof ApiError ? error.message : error instanceof Error ? error.message : 'Something went wrong.';
  return <Alert>{message}</Alert>;
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div role="status" className="flex items-center justify-center gap-3 p-8 text-muted-foreground">
      <Loader2 className="h-6 w-6 animate-spin text-primary" />
      <span className="text-sm">{label}…</span>
    </div>
  );
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="rounded-2xl border border-dashed border-border bg-card p-8 text-center">
      <p className="font-medium">{title}</p>
      {children && <div className="mt-2 text-sm text-muted-foreground">{children}</div>}
    </div>
  );
}

export function Pagination({ page, totalPages, onPage }: { page: number; totalPages: number; onPage: (page: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <nav aria-label="Pages" className="flex items-center justify-center gap-3 pt-4 text-sm">
      <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>
        Previous
      </Button>
      <span className="text-muted-foreground">
        Page {page + 1} of {totalPages}
      </span>
      <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>
        Next
      </Button>
    </nav>
  );
}

export function PageHeader({ title, description, actions }: { title: string; description?: string; actions?: ReactNode }) {
  return (
    <div className="mb-8 flex flex-col justify-between gap-4 md:flex-row md:items-end">
      <div>
        <h1 className="font-display text-2xl font-bold md:text-3xl">{title}</h1>
        {description && <p className="mt-1 text-muted-foreground">{description}</p>}
      </div>
      {actions}
    </div>
  );
}
