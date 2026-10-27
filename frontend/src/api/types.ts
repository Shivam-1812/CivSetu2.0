// Readable names for the API types generated from the backend's OpenAPI document (schema.d.ts).
// Regenerate with `npm run api:types` while the backend runs; never edit schema.d.ts by hand.
import type { components } from './schema';

type Schemas = components['schemas'];

export type AuthResponse = Schemas['AuthResponse'];
export type User = Schemas['UserResponse'];
export type Role = NonNullable<User['role']>;

export type Category = Schemas['CategoryResponse'];
export type Department = Schemas['DepartmentResponse'];

export type IssueSummary = Schemas['IssueSummaryResponse'];
export type IssueDetail = Schemas['IssueDetailResponse'];
export type IssueStatus = NonNullable<IssueSummary['status']>;
export type Priority = NonNullable<IssueSummary['priority']>;
export type SlaState = NonNullable<IssueSummary['slaState']>;
export type MapPoint = Schemas['Point'];
export type MapData = Schemas['MapResponse'];
export type SimilarIssue = Schemas['SimilarIssueResponse'];
export type HistoryEntry = Schemas['HistoryEntryResponse'];
export type Media = Schemas['MediaResponse'];

export type VoteTally = Schemas['VoteTally'];
export type Comment = Schemas['CommentResponse'];

export type Notification = Schemas['NotificationResponse'];

export type IssueCluster = Schemas['IssueCluster'];
export type Survey = Schemas['SurveyDetailResponse'];
export type SurveyResults = Schemas['SurveyResultsResponse'];
export type QuestionType = NonNullable<Schemas['NewQuestion']['type']>;

export type AnalyticsSummary = Schemas['AnalyticsSummaryResponse'];
export type TrendPoint = Schemas['TrendPoint'];
export type Hotspot = Schemas['Hotspot'];
export type OverdueIssue = Schemas['OverdueIssue'];
export type AuditEvent = Schemas['AuditEventResponse'];

/** Every list endpoint returns this page shape. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
