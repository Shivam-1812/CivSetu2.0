import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';

import { adminApi } from '../../api/endpoints';
import type { Priority } from '../../api/types';
import { Alert, Button, Card, ErrorMessage, PageHeader, SelectField, Spinner, TextField } from '../../components/shared';
import { priorityLabels } from '../../lib/format';

/**
 * Departments and the categories citizens choose from. These only set up the system; individual
 * issues are routed and prioritised automatically, and every change here is audited.
 */
export function AdminRoutingPage() {
  const queryClient = useQueryClient();
  const departments = useQuery({ queryKey: ['admin', 'departments'], queryFn: adminApi.departments });
  const categories = useQuery({ queryKey: ['admin', 'categories'], queryFn: adminApi.categories });
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['admin'] });
    queryClient.invalidateQueries({ queryKey: ['categories'] });
    queryClient.invalidateQueries({ queryKey: ['departments'] });
  };

  const toggleDepartment = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => adminApi.updateDepartment(id, { active }),
    onSuccess: refresh,
  });
  const toggleCategory = useMutation({
    mutationFn: ({ id, active }: { id: string; active: boolean }) => adminApi.updateCategory(id, { active }),
    onSuccess: refresh,
  });

  const departmentName = (id?: string) => departments.data?.find((department) => department.id === id)?.name ?? '—';

  return (
    <div className="space-y-6">
      <PageHeader title="Departments and categories" />
      <Alert tone="info">
        These lists only set up the system. Issues are routed, prioritised and scheduled automatically; nobody can change
        that for an individual issue. Every change here is recorded in the audit log.
      </Alert>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card title="Departments">
          {departments.isPending ? (
            <Spinner />
          ) : (
            <ul className="divide-y divide-slate-100">
              {departments.data?.map((department) => (
                <li key={department.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                  <span className={department.active ? '' : 'text-slate-400 line-through'}>
                    {department.name} <span className="text-xs text-slate-400">{department.code}</span>
                  </span>
                  <Button
                    variant="ghost"
                    onClick={() => toggleDepartment.mutate({ id: department.id!, active: !department.active })}
                  >
                    {department.active ? 'Deactivate' : 'Activate'}
                  </Button>
                </li>
              ))}
            </ul>
          )}
          <ErrorMessage error={toggleDepartment.error} />
          <NewDepartmentForm onCreated={refresh} />
        </Card>

        <Card title="Categories">
          {categories.isPending ? (
            <Spinner />
          ) : (
            <ul className="divide-y divide-slate-100">
              {categories.data?.map((category) => (
                <li key={category.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                  <span className={category.active ? '' : 'text-slate-400 line-through'}>
                    {category.label}
                    <span className="block text-xs text-slate-500">
                      → {departmentName(category.departmentId)} · {category.defaultPriority && priorityLabels[category.defaultPriority]}{' '}
                      by default
                    </span>
                  </span>
                  <Button variant="ghost" onClick={() => toggleCategory.mutate({ id: category.id!, active: !category.active })}>
                    {category.active ? 'Deactivate' : 'Activate'}
                  </Button>
                </li>
              ))}
            </ul>
          )}
          <ErrorMessage error={toggleCategory.error} />
          <NewCategoryForm departments={departments.data ?? []} onCreated={refresh} />
        </Card>
      </div>
    </div>
  );
}

function NewDepartmentForm({ onCreated }: { onCreated: () => void }) {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const create = useMutation({
    mutationFn: () => adminApi.createDepartment({ code, name }),
    onSuccess: () => {
      setCode('');
      setName('');
      onCreated();
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    create.mutate();
  };
  return (
    <form onSubmit={submit} className="mt-4 grid gap-3 border-t border-slate-100 pt-4 sm:grid-cols-[8rem_1fr_auto]">
      <TextField label="Code" placeholder="e.g. METRO" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} />
      <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} />
      <Button type="submit" className="self-end" busy={create.isPending}>
        Add
      </Button>
      <div className="sm:col-span-3">
        <ErrorMessage error={create.error} />
      </div>
    </form>
  );
}

function NewCategoryForm({
  departments,
  onCreated,
}: {
  departments: { id?: string; name?: string; active?: boolean }[];
  onCreated: () => void;
}) {
  const [code, setCode] = useState('');
  const [label, setLabel] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [priority, setPriority] = useState<Priority>('MEDIUM');
  const create = useMutation({
    mutationFn: () => adminApi.createCategory({ code, label, departmentId, defaultPriority: priority }),
    onSuccess: () => {
      setCode('');
      setLabel('');
      onCreated();
    },
  });
  const submit = (event: FormEvent) => {
    event.preventDefault();
    create.mutate();
  };
  return (
    <form onSubmit={submit} className="mt-4 grid gap-3 border-t border-slate-100 pt-4 sm:grid-cols-2">
      <TextField label="Code" placeholder="e.g. BROKEN_BENCH" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} />
      <TextField label="Label" value={label} onChange={(e) => setLabel(e.target.value)} />
      <SelectField label="Usually handled by" value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
        <option value="">Choose a department</option>
        {departments
          .filter((department) => department.active)
          .map((department) => (
            <option key={department.id} value={department.id}>
              {department.name}
            </option>
          ))}
      </SelectField>
      <SelectField
        label="Fallback priority"
        hint="Used when the AI is off or unsure."
        value={priority}
        onChange={(e) => setPriority(e.target.value as Priority)}
      >
        {Object.entries(priorityLabels).map(([value, text]) => (
          <option key={value} value={value}>
            {text}
          </option>
        ))}
      </SelectField>
      <div className="space-y-2 sm:col-span-2">
        <ErrorMessage error={create.error} />
        <Button type="submit" busy={create.isPending}>
          Add category
        </Button>
      </div>
    </form>
  );
}
