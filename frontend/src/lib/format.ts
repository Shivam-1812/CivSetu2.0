import type { IssueStatus, Priority, SlaState } from '../api/types';

/** Display helpers. Dates are shown in the viewer's local time, in Indian English format. */

const dateTime = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
const dateOnly = new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' });

export function formatDateTime(value?: string | null): string {
  return value ? dateTime.format(new Date(value)) : '—';
}

export function formatDate(value?: string | null): string {
  return value ? dateOnly.format(new Date(value)) : '—';
}

/** "in 3 days", "2 hours ago" */
export function formatRelative(value?: string | null, now: Date = new Date()): string {
  if (!value) return '—';
  const seconds = Math.round((new Date(value).getTime() - now.getTime()) / 1000);
  const units: [Intl.RelativeTimeFormatUnit, number][] = [
    ['day', 86400],
    ['hour', 3600],
    ['minute', 60],
  ];
  const format = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });
  for (const [unit, size] of units) {
    if (Math.abs(seconds) >= size) return format.format(Math.round(seconds / size), unit);
  }
  return format.format(seconds, 'second');
}

export const statusLabels: Record<IssueStatus, string> = {
  REPORTED: 'Reported',
  ASSIGNED: 'Assigned',
  IN_PROGRESS: 'In progress',
  RESOLVED: 'Resolved',
  REJECTED: 'Closed without action',
  DUPLICATE: 'Duplicate',
};

export const priorityLabels: Record<Priority, string> = {
  CRITICAL: 'Critical',
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
};

export const slaLabels: Record<SlaState, string> = {
  ON_TRACK: 'On track',
  AT_RISK: 'At risk',
  BREACHED: 'Overdue',
  MET: 'Fixed on time',
  MISSED: 'Fixed late',
};

/** Statuses in which an issue is still waiting to be fixed. */
export const OPEN_STATUSES: IssueStatus[] = ['REPORTED', 'ASSIGNED', 'IN_PROGRESS'];
