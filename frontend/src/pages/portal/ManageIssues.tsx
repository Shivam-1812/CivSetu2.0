import { useMemo, useState } from "react";
import { keepPreviousData, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { AlertTriangle, Clock, MapPin, MoreVertical, Search, ThumbsUp, MessageSquare, Loader2 } from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import { useAuth } from "@/auth/AuthProvider";
import { issueApi, referenceApi } from "@/api/endpoints";
import type { IssueDetail, IssueStatus, IssueSummary } from "@/api/types";
import { formatDate } from "@/lib/format";
import { categoryIcon, deadlineText, daysRemaining, isOpen, priorityConfig, slaConfig, statusConfig } from "@/lib/issues";
import { useDebounced } from "@/hooks/useDebounced";
import { Pagination } from "@/components/shared";
import { IssueDetailModal } from "@/components/issues/IssueDetailModal";
import { PlanningModal } from "@/components/issues/PlanningModal";
import { ResolutionModal } from "@/components/issues/ResolutionModal";
import { DuplicateModal, RejectionModal, ReturnModal } from "@/components/issues/RejectionModal";

const TABS: { id: string; label: string; statuses: IssueStatus[] }[] = [
  { id: "pending", label: "Pending", statuses: ["REPORTED", "ASSIGNED"] },
  { id: "in-progress", label: "In Progress", statuses: ["IN_PROGRESS"] },
  { id: "resolved", label: "Resolved", statuses: ["RESOLVED"] },
  { id: "closed", label: "Closed", statuses: ["REJECTED", "DUPLICATE"] },
];

const PRIORITY_ORDER = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 } as const;

type Action = "plan" | "resolve" | "reject" | "return" | "duplicate";

/**
 * Department officials: every issue of their department, to plan, resolve or reject.
 * Admins: every issue in the city, read-only (routing, priority and deadlines are automatic).
 */
export function ManageIssues({ portal }: { portal: "authority" | "admin" }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [activeTab, setActiveTab] = useState(TABS[0].id);
  const [searchQuery, setSearchQuery] = useState("");
  const [overdueOnly, setOverdueOnly] = useState(false);
  const [department, setDepartment] = useState("");
  const [page, setPage] = useState(0);
  const [detailsIssueId, setDetailsIssueId] = useState<string | null>(null);
  const [acting, setActing] = useState<{ action: Action; issue: IssueDetail } | null>(null);
  const q = useDebounced(searchQuery.trim());

  const departments = useQuery({ queryKey: ["departments"], queryFn: referenceApi.departments });
  const departmentFilter = portal === "authority" ? user?.departmentId : department || undefined;
  const statuses = TABS.find((tab) => tab.id === activeTab)!.statuses;

  const issues = useQuery({
    queryKey: ["issues", "manage", departmentFilter, activeTab, q, overdueOnly, page],
    queryFn: () =>
      issueApi.search(
        { department: departmentFilter, status: statuses, q: q || undefined, overdue: overdueOnly || undefined },
        page,
        20,
      ),
    placeholderData: keepPreviousData,
  });

  // The API pages newest first; within a page the most urgent come first (priority, then deadline)
  const rows = useMemo(
    () =>
      [...(issues.data?.content ?? [])].sort(
        (a, b) =>
          PRIORITY_ORDER[a.priority ?? "LOW"] - PRIORITY_ORDER[b.priority ?? "LOW"] ||
          (a.slaDueAt ?? "").localeCompare(b.slaDueAt ?? ""),
      ),
    [issues.data],
  );

  const canAct = portal === "authority";

  const startAction = async (action: Action, issue: IssueSummary) => {
    try {
      // Actions need the current version of the issue
      const detail = await queryClient.fetchQuery({
        queryKey: ["issue", issue.id],
        queryFn: () => issueApi.get(issue.id!),
        staleTime: 0,
      });
      setActing({ action, issue: detail });
    } catch {
      toast.error("The issue could not be loaded. Please try again.");
    }
  };

  const switchAction = (action: Action) => {
    const issue = acting?.issue;
    setActing(null);
    // Let the first dialog close before the next opens
    if (issue) setTimeout(() => setActing({ action, issue }), 150);
  };

  const changeTab = (id: string) => {
    setActiveTab(id);
    setPage(0);
  };

  return (
    <div>
      <div className="flex flex-col xl:flex-row xl:items-center justify-between gap-4 mb-8">
        <div>
          <h2 className="font-display text-2xl font-bold">{portal === "admin" ? "All Issues" : "Manage Issues"}</h2>
          <p className="text-muted-foreground text-sm mt-1">
            {portal === "admin"
              ? "Every report in the city. Routing, priority and deadlines are set automatically."
              : "Every issue routed to your department. Each page lists the most urgent first."}
          </p>
        </div>

        <div className="flex bg-muted/50 p-1 rounded-lg self-start" role="tablist" aria-label="Status">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              type="button"
              role="tab"
              aria-selected={activeTab === tab.id}
              onClick={() => changeTab(tab.id)}
              className={cn(
                "px-4 py-1.5 text-sm font-medium rounded-md transition-all",
                activeTab === tab.id
                  ? "bg-background shadow-sm text-foreground"
                  : "text-muted-foreground hover:text-foreground",
              )}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-3 mb-4">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <Input
            aria-label="Search issues"
            placeholder="Search issues..."
            className="pl-10 w-64 h-9 bg-background"
            value={searchQuery}
            onChange={(e) => {
              setSearchQuery(e.target.value);
              setPage(0);
            }}
          />
        </div>
        {portal === "admin" && (
          <select
            aria-label="Department"
            value={department}
            onChange={(e) => {
              setDepartment(e.target.value);
              setPage(0);
            }}
            className="h-9 rounded-md border border-input bg-background px-3 text-sm"
          >
            <option value="">All departments</option>
            {departments.data?.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        )}
        {(activeTab === "pending" || activeTab === "in-progress") && (
          <Button
            variant={overdueOnly ? "destructive" : "outline"}
            size="sm"
            aria-pressed={overdueOnly}
            onClick={() => {
              setOverdueOnly(!overdueOnly);
              setPage(0);
            }}
            className="gap-2 rounded-full border-dashed"
          >
            <AlertTriangle className="w-4 h-4" /> Overdue only
          </Button>
        )}
        <span className="text-sm text-muted-foreground ml-auto">
          {issues.data ? `${issues.data.totalElements} issues` : ""}
        </span>
      </div>

      <div className="bg-card rounded-2xl border border-border/50 overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-muted/30 border-b border-border/50">
              <tr>
                {["Issue", "Category", "Location", "Status", "Priority", "SLA / Days Left", "Community"].map((h) => (
                  <th key={h} scope="col" className="text-left px-4 py-4 text-xs font-semibold text-muted-foreground uppercase whitespace-nowrap">
                    {h}
                  </th>
                ))}
                <th scope="col" className="text-right px-4 py-4 text-xs font-semibold text-muted-foreground uppercase">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border/50">
              {issues.isPending && (
                <tr>
                  <td colSpan={8} className="px-6 py-12 text-center">
                    <Loader2 className="w-6 h-6 animate-spin text-primary mx-auto" />
                  </td>
                </tr>
              )}
              {!issues.isPending && rows.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-6 py-12 text-center text-muted-foreground">
                    No {TABS.find((tab) => tab.id === activeTab)!.label.toLowerCase()} issues found.
                  </td>
                </tr>
              )}
              {rows.map((issue) => {
                const days = daysRemaining(issue.slaDueAt);
                const open = isOpen(issue.status);
                return (
                  <tr key={issue.id} className="hover:bg-muted/30 transition-colors group">
                    <td className="px-4 py-4">
                      <button
                        type="button"
                        onClick={() => setDetailsIssueId(issue.id!)}
                        className="flex flex-col gap-1 text-left min-w-[220px]"
                      >
                        <span className="font-medium text-sm group-hover:text-primary">{issue.title}</span>
                        <span className="text-xs text-muted-foreground flex items-center gap-1">
                          <Clock className="w-3 h-3" /> {formatDate(issue.createdAt)}
                          {portal === "admin" && <> · {issue.departmentName}</>}
                        </span>
                      </button>
                    </td>
                    <td className="px-4 py-4">
                      <Badge variant="outline" className="font-normal whitespace-nowrap">
                        {categoryIcon(issue.categoryCode)} {issue.categoryLabel}
                      </Badge>
                    </td>
                    <td className="px-4 py-4 text-sm text-muted-foreground">
                      <div className="flex items-center gap-1 max-w-[160px]">
                        <MapPin className="w-3.5 h-3.5 shrink-0" />
                        <span className="truncate">{issue.address}</span>
                      </div>
                    </td>
                    <td className="px-4 py-4">
                      <Badge className={cn("text-xs border font-medium whitespace-nowrap", statusConfig[issue.status!].class)}>
                        {statusConfig[issue.status!].label}
                      </Badge>
                    </td>
                    <td className="px-4 py-4">
                      <Badge variant="outline" className={cn("text-xs font-medium w-fit", priorityConfig[issue.priority!].class)}>
                        {priorityConfig[issue.priority!].label}
                      </Badge>
                    </td>
                    <td className="px-4 py-4">
                      {open && days !== undefined ? (
                        <div className="flex flex-col">
                          <span
                            className={cn(
                              "font-bold text-sm whitespace-nowrap",
                              days <= 0 ? "text-red-600" : days <= 2 ? "text-yellow-600" : "text-green-600",
                            )}
                          >
                            {deadlineText(issue.slaDueAt)}
                          </span>
                          <span className="text-[10px] text-muted-foreground">
                            {issue.slaState && slaConfig[issue.slaState].label}
                          </span>
                        </div>
                      ) : (
                        <span className="text-muted-foreground text-xs">
                          {issue.slaState ? slaConfig[issue.slaState].label : "-"}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-4 text-xs text-muted-foreground">
                      <div className="flex items-center gap-3">
                        <span className="flex items-center gap-1" title="Citizens who see this problem">
                          <ThumbsUp className="w-3.5 h-3.5" /> {issue.agreeCount ?? 0}
                        </span>
                        <span className="flex items-center gap-1" title="Comments">
                          <MessageSquare className="w-3.5 h-3.5" /> {issue.commentCount ?? 0}
                        </span>
                      </div>
                    </td>
                    <td className="px-4 py-4 text-right">
                      <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                          <Button variant="ghost" size="icon" aria-label={`Actions for ${issue.title}`}>
                            <MoreVertical className="w-4 h-4" />
                          </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                          <DropdownMenuItem onClick={() => setDetailsIssueId(issue.id!)}>View Details</DropdownMenuItem>
                          {canAct && issue.status === "ASSIGNED" && (
                            <DropdownMenuItem onClick={() => startAction("plan", issue)} className="text-blue-600 font-medium">
                              Plan Resolution
                            </DropdownMenuItem>
                          )}
                          {canAct && issue.status === "IN_PROGRESS" && (
                            <DropdownMenuItem onClick={() => startAction("resolve", issue)} className="text-green-600 font-medium">
                              Finalize & Resolve
                            </DropdownMenuItem>
                          )}
                          {canAct && (issue.status === "ASSIGNED" || issue.status === "IN_PROGRESS") && (
                            <>
                              <DropdownMenuSeparator />
                              <DropdownMenuItem onClick={() => startAction("return", issue)}>Not our department</DropdownMenuItem>
                              <DropdownMenuItem onClick={() => startAction("duplicate", issue)}>Mark as duplicate</DropdownMenuItem>
                              <DropdownMenuItem onClick={() => startAction("reject", issue)} className="text-destructive">
                                Reject Issue
                              </DropdownMenuItem>
                            </>
                          )}
                        </DropdownMenuContent>
                      </DropdownMenu>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {issues.data && <Pagination page={page} totalPages={issues.data.totalPages} onPage={setPage} />}

      <IssueDetailModal issueId={detailsIssueId} isOpen={!!detailsIssueId} onClose={() => setDetailsIssueId(null)} />

      <PlanningModal
        issue={acting?.action === "plan" ? acting.issue : null}
        isOpen={acting?.action === "plan"}
        onClose={() => setActing(null)}
        onReject={() => switchAction("reject")}
        onReturn={() => switchAction("return")}
      />
      <ResolutionModal
        issue={acting?.action === "resolve" ? acting.issue : null}
        isOpen={acting?.action === "resolve"}
        onClose={() => setActing(null)}
      />
      <RejectionModal
        issue={acting?.action === "reject" ? acting.issue : null}
        isOpen={acting?.action === "reject"}
        onClose={() => setActing(null)}
      />
      <ReturnModal
        issue={acting?.action === "return" ? acting.issue : null}
        isOpen={acting?.action === "return"}
        onClose={() => setActing(null)}
      />
      <DuplicateModal
        issue={acting?.action === "duplicate" ? acting.issue : null}
        isOpen={acting?.action === "duplicate"}
        onClose={() => setActing(null)}
      />
    </div>
  );
}
