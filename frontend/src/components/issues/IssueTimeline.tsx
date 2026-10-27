import { useQuery } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";

import { issueApi } from "@/api/endpoints";
import type { HistoryEntry } from "@/api/types";
import { formatDateTime } from "@/lib/format";

const ACTION_LABELS: Record<NonNullable<HistoryEntry["action"]>, string> = {
  REPORTED: "Reported",
  ASSIGNED: "Routed and assigned",
  PLANNED: "Work planned",
  RESOLVED: "Resolved",
  REJECTED: "Closed without action",
  RETURNED: "Returned by the department",
  REOPENED: "Reopened by the reporter",
  REASSIGNED: "Handed over",
  PRIORITY_RAISED: "Priority raised by the community",
  MARKED_DUPLICATE: "Marked as duplicate",
  SLA_BREACHED: "Deadline missed",
};

const ACTOR_LABELS: Record<NonNullable<HistoryEntry["actor"]>, string> = {
  SYSTEM: "System",
  CITIZEN: "Reporter",
  AUTHORITY: "Official",
};

/** The public, append-only timeline of every step taken on the issue. */
export function IssueTimeline({ issueId }: { issueId: string }) {
  const history = useQuery({ queryKey: ["issue", issueId, "history"], queryFn: () => issueApi.history(issueId) });

  return (
    <div>
      <h3 className="font-semibold text-sm mb-4">Timeline</h3>
      {history.isPending ? (
        <Loader2 className="w-5 h-5 animate-spin text-primary" />
      ) : history.error ? (
        <p className="text-sm text-muted-foreground">The timeline could not be loaded.</p>
      ) : (
        <ol className="relative space-y-4 border-l border-border pl-5">
          {history.data.map((step) => (
            <li key={step.id} className="relative">
              <span className="absolute top-1.5 -left-[25px] h-2.5 w-2.5 rounded-full bg-primary ring-4 ring-card" />
              <p className="text-sm font-medium">
                {step.action ? ACTION_LABELS[step.action] : step.action}
              </p>
              <p className="text-[11px] text-muted-foreground">
                {step.actor && ACTOR_LABELS[step.actor]}
                {step.actor === "AUTHORITY" && step.departmentName ? `, ${step.departmentName}` : ""} ·{" "}
                {formatDateTime(step.createdAt)}
              </p>
              {step.remarks && <p className="mt-1 text-sm text-muted-foreground">{step.remarks}</p>}
              {step.actionTaken && <p className="mt-1 text-sm text-muted-foreground">{step.actionTaken}</p>}
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
