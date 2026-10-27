import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { adminApi } from '../../api/endpoints';
import { DataTable } from '../../components/charts';
import { Card, ErrorMessage, PageHeader, Pagination, SelectField, Spinner } from '../../components/shared';
import { formatDateTime } from '../../lib/format';

const ENTITY_TYPES = ['ISSUE', 'USER', 'DEPARTMENT', 'CATEGORY', 'COMMENT', 'SURVEY'];

/** Read-only record of Admin actions and automatic decisions. Nobody can edit it. */
export function AdminAuditPage() {
  const [entityType, setEntityType] = useState('');
  const [page, setPage] = useState(0);
  const events = useQuery({
    queryKey: ['admin', 'audit', entityType, page],
    queryFn: () => adminApi.audit(page, entityType || undefined),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="space-y-6">
      <PageHeader title="Audit log" description="Every Admin action and every automatic decision, newest first." />
      <Card
        actions={
          <SelectField
            label="Show"
            value={entityType}
            onChange={(e) => {
              setEntityType(e.target.value);
              setPage(0);
            }}
          >
            <option value="">Everything</option>
            {ENTITY_TYPES.map((type) => (
              <option key={type} value={type}>
                {type.toLowerCase()}
              </option>
            ))}
          </SelectField>
        }
      >
        {events.isPending ? (
          <Spinner />
        ) : events.error ? (
          <ErrorMessage error={events.error} />
        ) : (
          <>
            <DataTable
              headers={['When', 'Who', 'Action', 'Subject', 'Details']}
              rows={events.data.content.map((event) => [
                formatDateTime(event.createdAt),
                event.actorRole === 'SYSTEM' ? 'System' : `${event.actorRole?.toLowerCase()} ${event.actorId?.slice(0, 8)}`,
                event.action!.toLowerCase().replaceAll('_', ' '),
                `${event.entityType?.toLowerCase()} ${event.entityId?.slice(0, 8) ?? ''}`,
                event.details ?? '',
              ])}
            />
            <Pagination page={page} totalPages={events.data.totalPages} onPage={setPage} />
          </>
        )}
      </Card>
    </div>
  );
}
