import { useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { analyticsApi } from "@/api/endpoints";
import type { IssueStatus, Priority } from "@/api/types";
import { BarList, DataTable, LineChart, StatTile } from "@/components/charts";
import { Card, ErrorMessage, PageHeader, SelectField, Spinner } from "@/components/shared";
import { priorityLabels, statusLabels } from "@/lib/format";

/** Figures scoped by the API to the caller: the city (Admin) or the department (Authority). */
export function Analytics() {
  const [days, setDays] = useState(30);
  const from = new Date(Date.now() - days * 864e5).toISOString();

  const summary = useQuery({ queryKey: ["analytics", "summary", days], queryFn: () => analyticsApi.summary(from) });
  const trend = useQuery({ queryKey: ["analytics", "trend", days], queryFn: () => analyticsApi.trend(Math.min(days, 90)) });
  const hotspots = useQuery({ queryKey: ["analytics", "hotspots"], queryFn: () => analyticsApi.hotspots(8) });

  if (summary.isPending) return <Spinner />;
  if (summary.error) return <ErrorMessage error={summary.error} />;
  const s = summary.data;
  const dayLabel = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short" });

  return (
    <div className="space-y-6">
      <PageHeader
        title={s.scope === "CITY" ? "City Analytics" : "Department Analytics"}
        description={`Issues reported in the last ${days} days`}
        actions={
          <div className="w-44">
            <SelectField label="Period" value={days} onChange={(e) => setDays(Number(e.target.value))}>
              <option value={7}>Last 7 days</option>
              <option value={30}>Last 30 days</option>
              <option value={90}>Last 90 days</option>
            </SelectField>
          </div>
        }
      />

      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
        <StatTile label="Reported" value={s.reported ?? 0} />
        <StatTile label="Resolved" value={s.resolved ?? 0} />
        <StatTile label="Typical fix time" value={s.medianResolutionHours != null ? `${s.medianResolutionHours} h` : "—"} hint="median" />
        <StatTile label="Fixed on time" value={s.slaCompliancePercent != null ? `${s.slaCompliancePercent}%` : "—"} />
        <StatTile label="Open now" value={s.openNow ?? 0} />
        <StatTile label="Overdue now" value={s.openOverdueNow ?? 0} hint={s.openOverdueNow ? "past their deadline" : undefined} />
      </div>

      <Card title="Reported and resolved per day">
        {trend.data ? (
          <LineChart
            labels={trend.data.map((point) => dayLabel.format(new Date(point.day!)))}
            series={[
              { name: "Reported", values: trend.data.map((point) => point.reported ?? 0) },
              { name: "Resolved", values: trend.data.map((point) => point.resolved ?? 0) },
            ]}
          />
        ) : (
          <Spinner />
        )}
      </Card>

      <div className="grid gap-6 xl:grid-cols-3">
        <Card title="By status">
          <BarList
            items={Object.entries(s.byStatus ?? {}).map(([status, count]) => ({
              label: statusLabels[status as IssueStatus] ?? status,
              value: count,
            }))}
          />
        </Card>
        <Card title="By priority">
          <BarList
            items={(["CRITICAL", "HIGH", "MEDIUM", "LOW"] as Priority[]).map((priority) => ({
              label: priorityLabels[priority],
              value: s.byPriority?.[priority] ?? 0,
            }))}
          />
        </Card>
        <Card title="By category">
          <BarList items={(s.byCategory ?? []).slice(0, 8).map((item) => ({ label: item.label!, value: item.count ?? 0 }))} />
        </Card>
      </div>

      {s.scope === "CITY" && (
        <Card title="By department">
          <BarList items={(s.byDepartment ?? []).map((item) => ({ label: item.label!, value: item.count ?? 0 }))} />
        </Card>
      )}

      <Card title="Hotspots (areas with most reports)">
        {hotspots.data?.length === 0 && <p className="text-sm text-muted-foreground">No reports in this period.</p>}
        {hotspots.data && hotspots.data.length > 0 && (
          <DataTable
            headers={["Area (lat, lng)", "Reported", "Still open"]}
            rows={hotspots.data.map((spot) => [
              `${spot.latitude?.toFixed(3)}, ${spot.longitude?.toFixed(3)}`,
              spot.reported ?? 0,
              spot.stillOpen ?? 0,
            ])}
          />
        )}
      </Card>
    </div>
  );
}
