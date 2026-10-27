import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { Footer } from "@/components/layout/Footer";
import { IssueCard } from "@/components/issues/IssueCard";
import { IssueDetailModal } from "@/components/issues/IssueDetailModal";
import { IssueStatusFilter, statusesFor } from "@/components/issues/IssueStatusFilter";
import { SurveyList } from "@/components/surveys/SurveyList";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { FileText, CheckCircle2, Clock, AlertTriangle, ClipboardList, Plus, Loader2 } from "lucide-react";
import { useAuth } from "@/auth/AuthProvider";
import { analyticsApi, issueApi, surveyApi } from "@/api/endpoints";
import { formatDate } from "@/lib/format";

const YEAR_MS = 365 * 86_400_000;

/** A citizen's own reports, their progress, and surveys waiting for an answer. */
export function Dashboard() {
  const { user } = useAuth();
  const [params, setParams] = useSearchParams();
  const tab = params.get("tab") === "surveys" ? "surveys" : "my-issues";
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const [selectedIssueId, setSelectedIssueId] = useState<string | null>(null);

  const mine = useInfiniteQuery({
    queryKey: ["issues", "mine"],
    queryFn: ({ pageParam }) => issueApi.mine(pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });
  // Counts over all of the citizen's reports (the API allows up to one year per query)
  const since = new Date(Math.max(new Date(user?.createdAt ?? 0).getTime(), Date.now() - YEAR_MS)).toISOString();
  const summary = useQuery({ queryKey: ["analytics", "summary", "mine"], queryFn: () => analyticsApi.summary(since) });
  const surveys = useQuery({ queryKey: ["surveys", "mine"], queryFn: surveyApi.mine });

  const userIssues = mine.data?.pages.flatMap((page) => page.content) ?? [];
  const wanted = statusesFor(statusFilter);
  const filteredIssues = wanted ? userIssues.filter((issue) => wanted.includes(issue.status!)) : userIssues;
  const byStatus = summary.data?.byStatus ?? {};
  const surveysToAnswer =
    surveys.data?.filter((survey) => survey.status === "OPEN" && !survey.answeredByMe).length ?? 0;

  const statCards = [
    {
      icon: FileText,
      label: "Issues Reported",
      value: mine.data?.pages[0]?.totalElements ?? 0,
      color: "bg-primary/10 text-primary",
    },
    {
      icon: CheckCircle2,
      label: "Issues Resolved",
      value: byStatus.RESOLVED ?? 0,
      color: "bg-success/10 text-success",
    },
    {
      icon: Clock,
      label: "In Progress",
      value: byStatus.IN_PROGRESS ?? 0,
      color: "bg-info/10 text-info",
    },
    {
      icon: AlertTriangle,
      label: "Pending Review",
      value: (byStatus.REPORTED ?? 0) + (byStatus.ASSIGNED ?? 0),
      color: "bg-warning/10 text-warning",
    },
  ];

  const name = user?.name || "User";

  return (
    <div className="min-h-screen bg-background">
      <main className="pt-8 pb-16">
        <div className="container mx-auto px-4">
          {/* Header */}
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 mb-8">
            <div>
              <h1 className="font-display text-2xl md:text-3xl font-bold">My Dashboard</h1>
              <p className="text-muted-foreground">Track your civic contributions and issue status</p>
            </div>
            <Link to="/report">
              <Button variant="hero" className="gap-2">
                <Plus className="w-4 h-4" />
                Report New Issue
              </Button>
            </Link>
          </div>

          {/* User Card */}
          <div className="bg-card rounded-2xl border border-border/50 p-6 mb-8">
            <div className="flex flex-col md:flex-row md:items-center gap-6">
              {/* Avatar */}
              <div className="w-20 h-20 rounded-2xl hero-gradient flex items-center justify-center text-3xl font-bold text-primary-foreground">
                {name.charAt(0).toUpperCase()}
              </div>

              {/* Info */}
              <div className="flex-1">
                <h2 className="font-display text-xl font-bold">{name}</h2>
                <p className="text-sm text-muted-foreground mb-2">{user?.email}</p>
                <div className="flex flex-wrap items-center gap-3">
                  <Badge className="bg-accent/20 text-accent capitalize hover:bg-accent/20">Citizen</Badge>
                  <span className="text-sm text-muted-foreground">Member since {formatDate(user?.createdAt)}</span>
                </div>
              </div>

              {/* Surveys waiting */}
              <button
                type="button"
                onClick={() => setParams({ tab: "surveys" })}
                className="bg-muted rounded-2xl p-4 text-center hover:bg-muted/70 transition-colors"
              >
                <div className="flex items-center justify-center gap-2 mb-1">
                  <ClipboardList className="w-5 h-5 text-accent" />
                  <span className="font-display text-2xl font-bold">{surveysToAnswer}</span>
                </div>
                <span className="text-sm text-muted-foreground">
                  {surveysToAnswer === 1 ? "Survey to answer" : "Surveys to answer"}
                </span>
              </button>
            </div>
          </div>

          {/* Stats Grid */}
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
            {statCards.map((stat) => (
              <div key={stat.label} className="bg-card rounded-2xl border border-border/50 p-5">
                <div className={`w-10 h-10 rounded-xl ${stat.color} flex items-center justify-center mb-3`}>
                  <stat.icon className="w-5 h-5" />
                </div>
                <div className="font-display text-2xl font-bold">{stat.value}</div>
                <div className="text-sm text-muted-foreground">{stat.label}</div>
              </div>
            ))}
          </div>

          {/* Issues Tabs */}
          <Tabs
            value={tab}
            onValueChange={(value) => setParams(value === "surveys" ? { tab: "surveys" } : {})}
            className="space-y-6"
          >
            <TabsList className="bg-muted p-1 rounded-xl">
              <TabsTrigger value="my-issues" className="rounded-lg">
                My Issues
              </TabsTrigger>
              <TabsTrigger value="surveys" className="rounded-lg">
                Surveys
                {surveysToAnswer > 0 && (
                  <span className="ml-2 rounded-full bg-primary px-1.5 text-[10px] font-bold text-primary-foreground">
                    {surveysToAnswer}
                  </span>
                )}
              </TabsTrigger>
            </TabsList>

            <TabsContent value="my-issues" className="space-y-6">
              {/* Status Filter */}
              <IssueStatusFilter value={statusFilter} onChange={setStatusFilter} />

              {/* Issues List */}
              {mine.isPending ? (
                <div className="flex items-center justify-center py-12">
                  <Loader2 className="w-8 h-8 animate-spin text-primary" />
                  <span className="ml-3 text-muted-foreground">Loading your issues...</span>
                </div>
              ) : filteredIssues.length === 0 ? (
                <div className="text-center py-12">
                  <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-muted flex items-center justify-center">
                    <FileText className="w-8 h-8 text-muted-foreground" />
                  </div>
                  <h3 className="font-display font-semibold text-lg mb-2">No issues found</h3>
                  <p className="text-muted-foreground mb-4">
                    {statusFilter === "all"
                      ? "You haven't reported any issues yet."
                      : "No issues with this status."}
                  </p>
                  <Link to="/report">
                    <Button variant="hero">Report Your First Issue</Button>
                  </Link>
                </div>
              ) : (
                <>
                  <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6 auto-rows-fr">
                    {filteredIssues.map((issue) => (
                      <IssueCard key={issue.id} issue={issue} onClick={() => setSelectedIssueId(issue.id!)} />
                    ))}
                  </div>
                  {mine.hasNextPage && (
                    <div className="flex justify-center">
                      <Button variant="outline" onClick={() => mine.fetchNextPage()} disabled={mine.isFetchingNextPage}>
                        {mine.isFetchingNextPage && <Loader2 className="w-4 h-4 animate-spin" />}
                        Load more
                      </Button>
                    </div>
                  )}
                </>
              )}
            </TabsContent>

            <TabsContent value="surveys">
              <SurveyList />
            </TabsContent>
          </Tabs>

          {/* Modal */}
          <IssueDetailModal
            issueId={selectedIssueId}
            isOpen={!!selectedIssueId}
            onClose={() => setSelectedIssueId(null)}
          />
        </div>
      </main>

      <Footer />
    </div>
  );
}
