import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router";
import { AlertTriangle, CheckCircle2, Clock, FileText, Loader2, TrendingUp } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { useAuth } from "@/auth/AuthProvider";
import { analyticsApi, issueApi } from "@/api/endpoints";
import { formatDate, formatDateTime } from "@/lib/format";
import { deadlineText, isOpen, priorityConfig, statusConfig } from "@/lib/issues";
import { IssueDetailModal } from "@/components/issues/IssueDetailModal";

/** The portal overview: this month's numbers, the newest reports and what is overdue. */
export function PortalDashboard({ portal }: { portal: "authority" | "admin" }) {
  const { user } = useAuth();
  const [detailsIssueId, setDetailsIssueId] = useState<string | null>(null);
  const department = portal === "authority" ? user?.departmentId : undefined;

  const summary = useQuery({ queryKey: ["analytics", "summary", 30], queryFn: () => analyticsApi.summary() });
  const recent = useQuery({
    queryKey: ["issues", "recent", department],
    queryFn: () => issueApi.search({ department }, 0, 8),
  });
  const overdue = useQuery({ queryKey: ["analytics", "overdue"], queryFn: () => analyticsApi.overdue(0) });

  const s = summary.data;
  const byStatus = s?.byStatus ?? {};
  const statCards = [
    {
      label: "Reported (30 days)",
      value: s?.reported ?? 0,
      icon: FileText,
      color: "bg-primary/10 text-primary",
    },
    {
      label: "Pending",
      value: (byStatus.REPORTED ?? 0) + (byStatus.ASSIGNED ?? 0),
      icon: Clock,
      color: "bg-warning/10 text-warning",
    },
    {
      label: "In Progress",
      value: byStatus.IN_PROGRESS ?? 0,
      icon: TrendingUp,
      color: "bg-info/10 text-info",
    },
    {
      label: "Resolved",
      value: s?.resolved ?? 0,
      icon: CheckCircle2,
      color: "bg-success/10 text-success",
    },
    {
      label: "SLA Breaches (open now)",
      value: s?.openOverdueNow ?? 0,
      icon: AlertTriangle,
      color: "bg-destructive/10 text-destructive",
    },
  ];
  const issuesHref = portal === "admin" ? "/admin/issues" : "/authority/issues";

  return (
    <div>
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
        <div>
          <h2 className="font-display text-2xl md:text-3xl font-bold text-foreground">Overview</h2>
          <p className="text-muted-foreground mt-1 text-base">
            {portal === "admin" ? "Real-time municipal operations overview" : "Your department's work at a glance"}
          </p>
        </div>
        {s && (
          <p className="text-sm text-muted-foreground">
            On time: <strong className="text-foreground">{s.slaCompliancePercent != null ? `${s.slaCompliancePercent}%` : "—"}</strong>
            {" · "}Typical fix: <strong className="text-foreground">{s.medianResolutionHours != null ? `${s.medianResolutionHours} h` : "—"}</strong>
          </p>
        )}
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-5 gap-4 mb-8">
        {statCards.map((stat) => (
          <div
            key={stat.label}
            className="bg-card rounded-xl border border-border/50 p-6 shadow-sm hover:shadow-md transition-all duration-200"
          >
            <div className="flex items-start justify-between mb-4">
              <div className={cn("w-12 h-12 rounded-xl flex items-center justify-center border border-border/50", stat.color)}>
                <stat.icon className="w-6 h-6" />
              </div>
            </div>
            <div className="font-display text-3xl font-bold text-foreground mb-1 tabular-nums">
              {summary.isPending ? "…" : stat.value.toLocaleString("en-IN")}
            </div>
            <div className="text-sm text-muted-foreground font-medium">{stat.label}</div>
          </div>
        ))}
      </div>

      <div className="grid gap-6 2xl:grid-cols-3">
        {/* Issues Table */}
        <div className="2xl:col-span-2 bg-card rounded-2xl border border-border/50 overflow-hidden shadow-sm min-h-[300px]">
          <div className="p-6 border-b border-border/50 flex items-center justify-between gap-4">
            <h3 className="font-display text-lg font-bold">Recent Issues</h3>
            <Link to={issuesHref} className="text-sm font-medium text-primary hover:underline">
              {portal === "admin" ? "All issues" : "Manage issues"} →
            </Link>
          </div>
          {recent.isPending ? (
            <div className="flex justify-center py-20">
              <Loader2 className="w-6 h-6 animate-spin text-primary" />
            </div>
          ) : recent.data?.content.length === 0 ? (
            <div className="w-full h-full flex flex-col items-center justify-center py-20 text-muted-foreground">
              <FileText className="w-12 h-12 mb-4 opacity-20" />
              <p>No issues reported yet.</p>
            </div>
          ) : (
            <ul className="divide-y divide-border/50">
              {recent.data?.content.map((issue) => (
                <li key={issue.id}>
                  <button
                    type="button"
                    onClick={() => setDetailsIssueId(issue.id!)}
                    className="w-full text-left px-6 py-4 hover:bg-muted/30 transition-colors flex flex-wrap items-center gap-3"
                  >
                    <div className="flex-1 min-w-[200px]">
                      <p className="font-medium text-sm">{issue.title}</p>
                      <p className="text-xs text-muted-foreground">
                        {issue.categoryLabel} · {formatDate(issue.createdAt)}
                        {portal === "admin" && <> · {issue.departmentName}</>}
                      </p>
                    </div>
                    <Badge className={cn("text-xs border", statusConfig[issue.status!].class)}>
                      {statusConfig[issue.status!].label}
                    </Badge>
                    <Badge variant="outline" className={cn("text-xs", priorityConfig[issue.priority!].class)}>
                      {priorityConfig[issue.priority!].label}
                    </Badge>
                    {isOpen(issue.status) && (
                      <span className={cn("text-xs font-semibold w-32 text-right", issue.slaState === "BREACHED" ? "text-red-600" : "text-muted-foreground")}>
                        {deadlineText(issue.slaDueAt)}
                      </span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        {/* Overdue */}
        <div className="bg-card rounded-2xl border border-border/50 overflow-hidden shadow-sm">
          <div className="p-6 border-b border-border/50 flex items-center gap-2">
            <AlertTriangle className="w-5 h-5 text-destructive" />
            <h3 className="font-display text-lg font-bold">Overdue</h3>
          </div>
          {overdue.data?.content.length === 0 ? (
            <p className="p-6 text-sm text-muted-foreground">Nothing is overdue. 🎉</p>
          ) : (
            <ul className="divide-y divide-border/50 max-h-[480px] overflow-y-auto">
              {overdue.data?.content.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => setDetailsIssueId(item.id!)}
                    className="w-full text-left px-6 py-3 hover:bg-muted/30 transition-colors"
                  >
                    <p className="font-medium text-sm">{item.title}</p>
                    <p className="text-xs text-muted-foreground">
                      {portal === "admin" && <>{item.departmentName} · </>}due {formatDateTime(item.slaDueAt)} ·{" "}
                      <span className="font-semibold text-red-600">{Math.round(item.hoursOverdue ?? 0)} h late</span>
                    </p>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>

      <IssueDetailModal issueId={detailsIssueId} isOpen={!!detailsIssueId} onClose={() => setDetailsIssueId(null)} />
    </div>
  );
}
