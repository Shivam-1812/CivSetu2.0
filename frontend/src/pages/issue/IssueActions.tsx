import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent, type ReactNode } from 'react';

import { ApiError } from '../../api/client';
import { issueApi, workflowApi } from '../../api/endpoints';
import type { IssueDetail } from '../../api/types';
import { useAuth } from '../../auth/AuthProvider';
import { Alert, Button, Card, ErrorMessage, SelectField, TextArea, TextField } from '../../components/shared';

type Action = 'plan' | 'resolve' | 'reject' | 'return' | 'duplicate' | 'reopen';

/**
 * What the signed-in person may do with this issue: the Authorities of its department work on it
 * (the system picks one of them as the lead), the reporter may reopen it. Routing, priority and deadlines are never editable by anyone.
 */
export function IssueActions({ issue }: { issue: IssueDetail }) {
  const { user } = useAuth();
  const isDepartmentAuthority = user?.role === 'AUTHORITY' && !!user.departmentId && user.departmentId === issue.departmentId;
  const isClosed = issue.status === 'RESOLVED' || issue.status === 'REJECTED' || issue.status === 'DUPLICATE';

  const available: { action: Action; label: string }[] = [];
  if (isDepartmentAuthority && issue.status === 'ASSIGNED') available.push({ action: 'plan', label: 'Start work' });
  if (isDepartmentAuthority && issue.status === 'IN_PROGRESS') available.push({ action: 'resolve', label: 'Mark resolved' });
  if (isDepartmentAuthority && (issue.status === 'ASSIGNED' || issue.status === 'IN_PROGRESS')) {
    available.push(
      { action: 'reject', label: 'Close without action' },
      { action: 'return', label: 'Not our department' },
      { action: 'duplicate', label: 'Duplicate' },
    );
  }
  if (issue.reportedByMe && isClosed) available.push({ action: 'reopen', label: 'Reopen' });

  const [active, setActive] = useState<Action | null>(null);
  if (available.length === 0) return null;

  return (
    <Card title={isDepartmentAuthority ? 'Your actions' : 'Not fixed?'}>
      {issue.flaggedForReview && (
        <div className="mb-3">
          <Alert tone="info">The automatic check flagged this report as possibly invalid. Please verify before acting.</Alert>
        </div>
      )}
      <div className="flex flex-wrap gap-2">
        {available.map(({ action, label }) => (
          <Button
            key={action}
            variant={active === action ? 'primary' : 'secondary'}
            aria-expanded={active === action}
            onClick={() => setActive(active === action ? null : action)}
          >
            {label}
          </Button>
        ))}
      </div>
      {active && (
        <div className="mt-4 border-t border-slate-100 pt-4">
          <ActionForm action={active} issue={issue} onDone={() => setActive(null)} />
        </div>
      )}
    </Card>
  );
}

function ActionForm({ action, issue, onDone }: { action: Action; issue: IssueDetail; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [text, setText] = useState('');
  const [staff, setStaff] = useState('');
  const [resources, setResources] = useState('');
  const [files, setFiles] = useState<File[]>([]);
  const [original, setOriginal] = useState('');
  const similar = useQuery({
    queryKey: ['issue', issue.id, 'similar'],
    queryFn: () => issueApi.similarTo(issue.id!),
    enabled: action === 'duplicate',
  });

  const id = issue.id!;
  const version = issue.version!;
  const mutation = useMutation({
    mutationFn: () => {
      switch (action) {
        case 'plan':
          return workflowApi.plan(id, { version, actionTaken: text, staffAllocated: staff, resourcesUsed: resources }, files);
        case 'resolve':
          return workflowApi.resolve(id, { version, remarks: text }, files);
        case 'reject':
          return workflowApi.reject(id, { version, reason: text }, files);
        case 'return':
          return workflowApi.returnIssue(id, { version, reason: text });
        case 'duplicate':
          return workflowApi.markDuplicate(id, { version, duplicateOfId: original, remarks: text || undefined });
        case 'reopen':
          return workflowApi.reopen(id, { version, reason: text });
      }
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(['issue', id], updated);
      queryClient.invalidateQueries({ queryKey: ['issue', id] });
      queryClient.invalidateQueries({ queryKey: ['issues'] });
      onDone();
    },
    onError: (error) => {
      // Someone else changed the issue first: load the current version
      if (error instanceof ApiError && error.code === 'STALE_ISSUE') {
        queryClient.invalidateQueries({ queryKey: ['issue', id] });
      }
    },
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    mutation.mutate();
  };

  const fieldError = (field: string) =>
    mutation.error instanceof ApiError ? mutation.error.fieldMessage(field) : undefined;

  const filePicker = (label: string, accept: string, hint: string): ReactNode => (
    <div>
      <label className="block text-sm font-medium text-slate-700" htmlFor="action-files">
        {label}
      </label>
      <input
        id="action-files"
        type="file"
        multiple
        accept={accept}
        className="mt-1 block text-sm"
        onChange={(e) => setFiles(Array.from(e.target.files ?? []).slice(0, 5))}
      />
      <p className="mt-1 text-xs text-slate-500">{hint}</p>
    </div>
  );

  return (
    <form onSubmit={submit} className="space-y-3">
      {action === 'plan' && (
        <>
          <TextArea label="What will be done" rows={3} value={text} onChange={(e) => setText(e.target.value)} error={fieldError('actionTaken')} />
          <div className="grid gap-3 sm:grid-cols-2">
            <TextField label="Staff (optional)" placeholder="e.g. 2 electricians" value={staff} onChange={(e) => setStaff(e.target.value)} />
            <TextField label="Resources (optional)" placeholder="e.g. ladder truck" value={resources} onChange={(e) => setResources(e.target.value)} />
          </div>
          {filePicker('Plan documents (optional)', 'image/jpeg,image/png,image/webp,application/pdf', 'Images or PDF, up to 5. Visible to the reporter and staff.')}
        </>
      )}
      {action === 'resolve' && (
        <>
          <TextArea label="What was done" rows={3} value={text} onChange={(e) => setText(e.target.value)} error={fieldError('remarks')} />
          {filePicker('Proof photos (required)', 'image/jpeg,image/png,image/webp', 'At least one photo showing the fix. Shown publicly.')}
        </>
      )}
      {action === 'reject' && (
        <>
          <TextArea label="Reason (shown publicly)" rows={3} value={text} onChange={(e) => setText(e.target.value)} error={fieldError('reason')} />
          {filePicker('Evidence (optional)', 'image/jpeg,image/png,image/webp', 'Visible to the reporter and staff only.')}
        </>
      )}
      {action === 'return' && (
        <>
          <Alert tone="info">The system will route it to another department. The deadline does not change.</Alert>
          <TextArea label="Why it is not your department's responsibility" rows={3} value={text} onChange={(e) => setText(e.target.value)} error={fieldError('reason')} />
        </>
      )}
      {action === 'duplicate' && (
        <>
          <SelectField label="Already reported as" value={original} onChange={(e) => setOriginal(e.target.value)}>
            <option value="">Choose the original report</option>
            {similar.data?.map((match) => (
              <option key={match.issue?.id} value={match.issue?.id}>
                {match.issue?.title} ({match.distanceMeters} m away)
              </option>
            ))}
          </SelectField>
          <TextField label="Or paste the original issue's id" value={original} onChange={(e) => setOriginal(e.target.value.trim())} />
          <TextArea label="Note (optional)" rows={2} value={text} onChange={(e) => setText(e.target.value)} />
        </>
      )}
      {action === 'reopen' && (
        <TextArea label="Why is it not fixed?" rows={3} value={text} onChange={(e) => setText(e.target.value)} error={fieldError('reason')} />
      )}

      {mutation.error instanceof ApiError && mutation.error.code === 'STALE_ISSUE' ? (
        <Alert>Someone else updated this issue a moment ago. It has been reloaded; please check it and try again.</Alert>
      ) : (
        !(mutation.error instanceof ApiError && mutation.error.fieldErrors.length) && <ErrorMessage error={mutation.error} />
      )}
      <div className="flex gap-2">
        <Button type="submit" busy={mutation.isPending}>
          Confirm
        </Button>
        <Button variant="ghost" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  );
}
