import type { IssueStatus, Priority, SlaState } from "@/api/types";
import { OPEN_STATUSES } from "@/lib/format";

/** Badge styles and labels shared by the issue cards, tables and dialogs (the original CivSetu look). */

export const statusConfig: Record<IssueStatus, { label: string; class: string }> = {
  REPORTED: { label: "Pending", class: "status-pending" },
  ASSIGNED: { label: "Assigned", class: "status-pending" },
  IN_PROGRESS: { label: "In Progress", class: "status-in-progress" },
  RESOLVED: { label: "Resolved", class: "status-resolved" },
  REJECTED: { label: "Rejected", class: "bg-destructive/10 text-destructive border-destructive/20" },
  DUPLICATE: { label: "Duplicate", class: "bg-muted text-muted-foreground border-border" },
};

export const priorityConfig: Record<Priority, { label: string; class: string }> = {
  LOW: { label: "Low", class: "bg-muted text-muted-foreground" },
  MEDIUM: { label: "Medium", class: "bg-warning/20 text-warning" },
  HIGH: { label: "High", class: "bg-destructive/20 text-destructive" },
  CRITICAL: { label: "Critical", class: "bg-destructive text-destructive-foreground" },
};

export const slaConfig: Record<SlaState, { label: string; class: string }> = {
  ON_TRACK: { label: "On Track", class: "bg-green-100 text-green-700 border-green-200" },
  AT_RISK: { label: "At Risk", class: "bg-yellow-100 text-yellow-700 border-yellow-200" },
  BREACHED: { label: "Breached", class: "bg-red-100 text-red-700 border-red-200" },
  MET: { label: "Fixed on time", class: "bg-green-100 text-green-700 border-green-200" },
  MISSED: { label: "Fixed late", class: "bg-red-100 text-red-700 border-red-200" },
};

/** Marker colours on the maps, by priority. */
export const priorityColors: Record<Priority, string> = {
  CRITICAL: "#dc2626",
  HIGH: "#ea580c",
  MEDIUM: "#d97706",
  LOW: "#64748b",
};

const categoryIcons: [RegExp, string][] = [
  [/POTHOLE|ROAD|FOOTPATH/, "🛣️"],
  [/GARBAGE|DUMPING|WASTE/, "🗑️"],
  [/WATER_LEAK|NO_WATER|WATER/, "💧"],
  [/DRAIN|WATERLOGGING|SEWAGE/, "🚰"],
  [/STREETLIGHT|WIRE|ELECTRIC|LIGHT/, "💡"],
  [/TREE|PARK/, "🌳"],
  [/ANIMAL/, "🐕"],
  [/MOSQUITO|HEALTH/, "🦟"],
  [/TRAFFIC|SIGNAL/, "🚦"],
];

/** An emoji for a category code; admin-created categories fall back to the clipboard. */
export function categoryIcon(code?: string): string {
  if (!code) return "📋";
  return categoryIcons.find(([pattern]) => pattern.test(code))?.[1] ?? "📋";
}

export { OPEN_STATUSES };

export function isOpen(status?: IssueStatus): boolean {
  return !!status && OPEN_STATUSES.includes(status);
}

/** Days until the deadline (negative when overdue), or undefined without a deadline. */
export function daysRemaining(slaDueAt?: string, now: Date = new Date()): number | undefined {
  if (!slaDueAt) return undefined;
  return (new Date(slaDueAt).getTime() - now.getTime()) / 86_400_000;
}

/** "2 days left", "Overdue by 3 days", "5 hours left" */
export function deadlineText(slaDueAt?: string, now: Date = new Date()): string {
  const days = daysRemaining(slaDueAt, now);
  if (days === undefined) return "";
  if (days <= 0) {
    const late = Math.max(1, Math.ceil(-days));
    return `Overdue by ${late} day${late === 1 ? "" : "s"}`;
  }
  if (days < 1) {
    const hours = Math.max(1, Math.round(days * 24));
    return `${hours} hour${hours === 1 ? "" : "s"} left`;
  }
  const left = Math.ceil(days);
  return `${left} day${left === 1 ? "" : "s"} left`;
}
