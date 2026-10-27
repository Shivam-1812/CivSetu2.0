import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { ApiError } from '../../api/client';
import { adminApi, referenceApi } from '../../api/endpoints';
import type { Role } from '../../api/types';
import { DataTable } from '../../components/charts';
import { Alert, Button, Card, ErrorMessage, PageHeader, Pagination, SelectField, Spinner, TextField } from '../../components/shared';
import { formatDate } from '../../lib/format';

/** Admins create Authority and Admin accounts; citizens sign up themselves. */
export function AdminUsersPage() {
  const [role, setRole] = useState<Role | ''>('');
  const [page, setPage] = useState(0);
  const users = useQuery({
    queryKey: ['admin', 'users', role, page],
    queryFn: () => adminApi.users(role || undefined, page),
    placeholderData: keepPreviousData,
  });
  const departments = useQuery({ queryKey: ['departments'], queryFn: referenceApi.departments });
  const departmentName = (id?: string) => departments.data?.find((department) => department.id === id)?.name ?? '';

  return (
    <div className="space-y-6">
      <PageHeader title="Users" description="Create staff accounts. Citizens register themselves." />
      <CreateStaffForm />
      <Card
        title="Accounts"
        actions={
          <SelectField
            label="Role"
            value={role}
            onChange={(e) => {
              setRole(e.target.value as Role | '');
              setPage(0);
            }}
          >
            <option value="">All</option>
            <option value="CITIZEN">Citizens</option>
            <option value="AUTHORITY">Authorities</option>
            <option value="ADMIN">Admins</option>
          </SelectField>
        }
      >
        {users.isPending ? (
          <Spinner />
        ) : users.error ? (
          <ErrorMessage error={users.error} />
        ) : (
          <>
            <DataTable
              headers={['Name', 'Email', 'Role', 'Department', 'Active', 'Since']}
              rows={users.data.content.map((user) => [
                user.name!,
                user.email!,
                user.role!.toLowerCase(),
                departmentName(user.departmentId),
                user.active ? 'yes' : 'no',
                formatDate(user.createdAt),
              ])}
            />
            <Pagination page={page} totalPages={users.data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
    </div>
  );
}

function CreateStaffForm() {
  const queryClient = useQueryClient();
  const departments = useQuery({ queryKey: ['departments'], queryFn: referenceApi.departments });
  const empty = { name: '', email: '', password: '', phone: '', role: 'AUTHORITY' as 'AUTHORITY' | 'ADMIN', departmentId: '' };
  const [form, setForm] = useState(empty);
  const [created, setCreated] = useState<string>();

  const create = useMutation({
    mutationFn: () =>
      adminApi.createStaff({
        ...form,
        phone: form.phone || undefined,
        departmentId: form.role === 'AUTHORITY' ? form.departmentId : undefined,
      }),
    onSuccess: (user) => {
      setCreated(`${user.name} can now sign in as ${user.role?.toLowerCase()}.`);
      setForm(empty);
      queryClient.invalidateQueries({ queryKey: ['admin', 'users'] });
    },
  });

  const set = (field: keyof typeof form) => (event: { target: { value: string } }) =>
    setForm((current) => ({ ...current, [field]: event.target.value }));
  const fieldError = (field: string) => (create.error instanceof ApiError ? create.error.fieldMessage(field) : undefined);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setCreated(undefined);
    create.mutate();
  };

  return (
    <Card title="New staff account">
      <form onSubmit={submit} className="grid gap-4 md:grid-cols-3">
        <TextField label="Name" value={form.name} onChange={set('name')} error={fieldError('name')} />
        <TextField label="Email" type="email" value={form.email} onChange={set('email')} error={fieldError('email')} />
        <TextField
          label="Temporary password"
          type="password"
          autoComplete="new-password"
          value={form.password}
          onChange={set('password')}
          error={fieldError('password')}
          hint="At least 8 characters; share it securely."
        />
        <TextField label="Phone (optional)" type="tel" value={form.phone} onChange={set('phone')} error={fieldError('phone')} />
        <SelectField label="Role" value={form.role} onChange={set('role')}>
          <option value="AUTHORITY">Authority (handles issues)</option>
          <option value="ADMIN">Admin</option>
        </SelectField>
        {form.role === 'AUTHORITY' && (
          <SelectField label="Department" value={form.departmentId} onChange={set('departmentId')}>
            <option value="">Choose a department</option>
            {departments.data?.map((department) => (
              <option key={department.id} value={department.id}>
                {department.name}
              </option>
            ))}
          </SelectField>
        )}
        <div className="space-y-2 md:col-span-3">
          {created && <Alert tone="success">{created}</Alert>}
          {!(create.error instanceof ApiError && create.error.fieldErrors.length) && <ErrorMessage error={create.error} />}
          <Button type="submit" busy={create.isPending}>
            Create account
          </Button>
        </div>
      </form>
    </Card>
  );
}
