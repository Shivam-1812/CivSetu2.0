import { api, jsonPart } from './client';
import type {
  AnalyticsSummary,
  AuditEvent,
  AuthResponse,
  Category,
  Comment,
  Department,
  HistoryEntry,
  Hotspot,
  IssueCluster,
  IssueDetail,
  IssueStatus,
  IssueSummary,
  MapData,
  Notification,
  OverdueIssue,
  Page,
  Priority,
  QuestionType,
  Role,
  SimilarIssue,
  Survey,
  SurveyResults,
  TrendPoint,
  User,
  VoteTally,
} from './types';

/** All API calls, grouped by backend feature. Paths match the Spring controllers. */

export const authApi = {
  register: (body: { name: string; email: string; password: string; phone?: string }) =>
    api<AuthResponse>('/auth/register', { method: 'POST', body }),
  login: (body: { email: string; password: string }) => api<AuthResponse>('/auth/login', { method: 'POST', body }),
  logout: () => api<void>('/auth/logout', { method: 'POST' }),
  me: () => api<User>('/auth/me'),
  updateProfile: (body: { name?: string; phone?: string }) => api<User>('/users/me', { method: 'PATCH', body }),
};

export const referenceApi = {
  categories: () => api<Category[]>('/categories'),
  departments: () => api<Department[]>('/departments'),
};

export interface IssueFilters {
  status?: IssueStatus[];
  priority?: Priority[];
  category?: string;
  department?: string;
  q?: string;
  bbox?: string;
  from?: string;
  to?: string;
  /** Only open issues past their deadline. */
  overdue?: boolean;
}

export interface NewIssue {
  title: string;
  description: string;
  category: string;
  latitude: number;
  longitude: number;
  address: string;
}

export const issueApi = {
  search: (filters: IssueFilters, page = 0, size = 20) =>
    api<Page<IssueSummary>>('/issues', { query: { ...filters, page, size } }),
  map: (filters: IssueFilters & { bbox: string }) => api<MapData>('/issues/map', { query: { ...filters } }),
  get: (id: string) => api<IssueDetail>(`/issues/${id}`),
  mine: (page = 0) => api<Page<IssueSummary>>('/issues/mine', { query: { page, size: 20 } }),
  assigned: (status?: IssueStatus[], page = 0) =>
    api<Page<IssueSummary>>('/issues/assigned', { query: { status, page, size: 20 } }),
  history: (id: string) => api<HistoryEntry[]>(`/issues/${id}/history`),
  similarTo: (id: string) => api<SimilarIssue[]>(`/issues/${id}/similar`),
  similarAt: (query: { latitude: number; longitude: number; category: string; title: string }) =>
    api<SimilarIssue[]>('/issues/similar', { query: { ...query } }),
  report: (issue: NewIssue, images: File[], voice: Blob | null) => {
    const form = new FormData();
    form.append('data', jsonPart(issue));
    images.forEach((image) => form.append('images', image));
    if (voice) form.append('voice', voice, 'voice-note.webm');
    return api<IssueDetail>('/issues', { method: 'POST', body: form });
  },
};

/** Workflow actions; each sends the issue version the user saw, so stale views get a clear 409. */
export const workflowApi = {
  plan: (
    id: string,
    data: { version: number; actionTaken: string; staffAllocated?: string; resourcesUsed?: string; remarks?: string },
    documents: File[],
  ) => multipartAction(id, 'plan', data, 'documents', documents),
  resolve: (id: string, data: { version: number; remarks: string }, proofs: File[]) =>
    multipartAction(id, 'resolve', data, 'proofs', proofs),
  reject: (id: string, data: { version: number; reason: string }, evidence: File[]) =>
    multipartAction(id, 'reject', data, 'evidence', evidence),
  returnIssue: (id: string, body: { version: number; reason: string }) =>
    api<IssueDetail>(`/issues/${id}/return`, { method: 'POST', body }),
  markDuplicate: (id: string, body: { version: number; duplicateOfId: string; remarks?: string }) =>
    api<IssueDetail>(`/issues/${id}/duplicate`, { method: 'POST', body }),
  reopen: (id: string, body: { version: number; reason: string }) =>
    api<IssueDetail>(`/issues/${id}/reopen`, { method: 'POST', body }),
};

function multipartAction(id: string, action: string, data: object, filePart: string, files: File[]) {
  const form = new FormData();
  form.append('data', jsonPart(data));
  files.forEach((file) => form.append(filePart, file));
  return api<IssueDetail>(`/issues/${id}/${action}`, { method: 'POST', body: form });
}

export const engagementApi = {
  votes: (id: string) => api<VoteTally>(`/issues/${id}/votes`),
  vote: (id: string, value: 'AGREE' | 'DISAGREE') =>
    api<VoteTally>(`/issues/${id}/vote`, { method: 'PUT', body: { value } }),
  removeVote: (id: string) => api<VoteTally>(`/issues/${id}/vote`, { method: 'DELETE' }),
  comments: (id: string, page = 0) => api<Page<Comment>>(`/issues/${id}/comments`, { query: { page, size: 50 } }),
  comment: (id: string, body: string) => api<Comment>(`/issues/${id}/comments`, { method: 'POST', body: { body } }),
  moderate: (commentId: string, hidden: boolean, reason?: string) =>
    api<Comment>(`/admin/comments/${commentId}`, { method: 'PATCH', body: { hidden, reason } }),
};

export const notificationApi = {
  list: (unreadOnly = false, page = 0) =>
    api<Page<Notification>>('/notifications', { query: { unreadOnly, page, size: 20 } }),
  unreadCount: () => api<{ count: number }>('/notifications/unread-count'),
  markRead: (id: string) => api<Notification>(`/notifications/${id}/read`, { method: 'PATCH' }),
  markAllRead: () => api<{ updated: number }>('/notifications/read-all', { method: 'POST' }),
  streamTicket: () =>
    api<{ ticket: string; expiresInSeconds: number; streamUrl: string }>('/notifications/stream-ticket', {
      method: 'POST',
    }),
};

export interface NewSurvey {
  title: string;
  description?: string;
  centerLatitude: number;
  centerLongitude: number;
  radiusMeters: number;
  closesAt: string;
  questions: { text: string; type: QuestionType }[];
}

export interface SurveyAnswer {
  questionId: string;
  rating?: number;
  yesNo?: boolean;
  text?: string;
}

export const surveyApi = {
  clusters: (radius = 500, minIssues = 3) =>
    api<IssueCluster[]>('/surveys/clusters', { query: { radius, minIssues } }),
  create: (body: NewSurvey) => api<Survey>('/surveys', { method: 'POST', body }),
  mine: () => api<Survey[]>('/surveys/mine'),
  managed: () => api<Survey[]>('/surveys/managed'),
  get: (id: string) => api<Survey>(`/surveys/${id}`),
  respond: (id: string, answers: SurveyAnswer[]) =>
    api<Survey>(`/surveys/${id}/responses`, { method: 'POST', body: { answers } }),
  results: (id: string) => api<SurveyResults>(`/surveys/${id}/results`),
  close: (id: string) => api<Survey>(`/surveys/${id}/close`, { method: 'POST' }),
};

export const analyticsApi = {
  summary: (from?: string, to?: string) => api<AnalyticsSummary>('/analytics/summary', { query: { from, to } }),
  trend: (days = 30) => api<TrendPoint[]>('/analytics/trend', { query: { days } }),
  hotspots: (limit = 10) => api<Hotspot[]>('/analytics/hotspots', { query: { limit } }),
  overdue: (page = 0) => api<Page<OverdueIssue>>('/analytics/overdue', { query: { page, size: 20 } }),
};

export const adminApi = {
  users: (role?: Role, page = 0) => api<Page<User>>('/admin/users', { query: { role, page, size: 20 } }),
  createStaff: (body: {
    name: string;
    email: string;
    password: string;
    phone?: string;
    role: 'AUTHORITY' | 'ADMIN';
    departmentId?: string;
  }) => api<User>('/admin/users', { method: 'POST', body }),
  departments: () => api<Department[]>('/admin/departments'),
  createDepartment: (body: { code: string; name: string; area?: string }) =>
    api<Department>('/admin/departments', { method: 'POST', body }),
  updateDepartment: (id: string, body: { name?: string; area?: string; active?: boolean }) =>
    api<Department>(`/admin/departments/${id}`, { method: 'PATCH', body }),
  categories: () => api<Category[]>('/admin/categories'),
  createCategory: (body: { code: string; label: string; departmentId: string; defaultPriority: Priority }) =>
    api<Category>('/admin/categories', { method: 'POST', body }),
  updateCategory: (
    id: string,
    body: { label?: string; departmentId?: string; defaultPriority?: Priority; active?: boolean },
  ) => api<Category>(`/admin/categories/${id}`, { method: 'PATCH', body }),
  audit: (page = 0, entityType?: string) =>
    api<Page<AuditEvent>>('/admin/audit', { query: { page, size: 30, entityType } }),
};
