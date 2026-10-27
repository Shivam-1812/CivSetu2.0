import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";
import type { IssueStatus } from "@/api/types";

/** The status groups shown as filter pills, and the API statuses each one stands for. */
export const STATUS_FILTERS = [
  { id: "all", label: "All", color: "bg-muted", statuses: [] as IssueStatus[] },
  { id: "pending", label: "Pending", color: "bg-warning", statuses: ["REPORTED", "ASSIGNED"] as IssueStatus[] },
  { id: "in-progress", label: "In Progress", color: "bg-info", statuses: ["IN_PROGRESS"] as IssueStatus[] },
  { id: "resolved", label: "Resolved", color: "bg-success", statuses: ["RESOLVED"] as IssueStatus[] },
  { id: "closed", label: "Rejected", color: "bg-destructive", statuses: ["REJECTED", "DUPLICATE"] as IssueStatus[] },
];

export type StatusFilterId = (typeof STATUS_FILTERS)[number]["id"];

export function statusesFor(id: string): IssueStatus[] | undefined {
  const statuses = STATUS_FILTERS.find((filter) => filter.id === id)?.statuses;
  return statuses && statuses.length > 0 ? statuses : undefined;
}

interface IssueStatusFilterProps {
  value: string;
  onChange: (status: string) => void;
}

export function IssueStatusFilter({ value, onChange }: IssueStatusFilterProps) {
  return (
    <div className="flex flex-wrap gap-2">
      {STATUS_FILTERS.map((status) => (
        <Button
          key={status.id}
          variant={value === status.id ? "default" : "outline"}
          size="sm"
          aria-pressed={value === status.id}
          onClick={() => onChange(status.id)}
          className={cn(
            "rounded-full gap-2",
            value !== status.id && "text-muted-foreground"
          )}
        >
          <span className={cn("w-2 h-2 rounded-full", status.color)} />
          {status.label}
        </Button>
      ))}
    </div>
  );
}
