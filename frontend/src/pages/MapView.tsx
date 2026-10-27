import { useMemo, useState } from "react";
import { keepPreviousData, useInfiniteQuery } from "@tanstack/react-query";
import { Footer } from "@/components/layout/Footer";
import { IssueCard } from "@/components/issues/IssueCard";
import { IssueDetailModal } from "@/components/issues/IssueDetailModal";
import { IssueCategoryFilter } from "@/components/issues/IssueCategoryFilter";
import { IssueStatusFilter, statusesFor } from "@/components/issues/IssueStatusFilter";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Search, Filter, X, Loader2, AlertTriangle } from "lucide-react";
import { CivicMap } from "@/components/CivicMap";
import { MapErrorBoundary } from "@/components/MapErrorBoundary";
import { useDebounced } from "@/hooks/useDebounced";
import { issueApi, type IssueFilters } from "@/api/endpoints";
import type { IssueDetail } from "@/api/types";

const PAGE_SIZE = 12;

/** Public map of every issue, with the community feed below it. */
export function MapView() {
  const [selectedIssueId, setSelectedIssueId] = useState<string | null>(null);
  const [focus, setFocus] = useState<[number, number] | null>(null);
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [selectedStatus, setSelectedStatus] = useState("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [showFilters, setShowFilters] = useState(false);
  const [showSlaBreachedOnly, setShowSlaBreachedOnly] = useState(false);
  const q = useDebounced(searchQuery.trim());

  const filters = useMemo<IssueFilters>(
    () => ({
      category: selectedCategory === "all" ? undefined : selectedCategory,
      status: statusesFor(selectedStatus),
      q: q || undefined,
      overdue: showSlaBreachedOnly || undefined,
    }),
    [selectedCategory, selectedStatus, q, showSlaBreachedOnly],
  );

  const feed = useInfiniteQuery({
    queryKey: ["issues", "feed", filters],
    queryFn: ({ pageParam }) => issueApi.search(filters, pageParam, PAGE_SIZE),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    placeholderData: keepPreviousData,
  });
  const issues = feed.data?.pages.flatMap((page) => page.content) ?? [];
  const total = feed.data?.pages[0]?.totalElements ?? 0;

  const handleViewOnMap = (issue: IssueDetail) => {
    setSelectedIssueId(null);
    setFocus([issue.latitude!, issue.longitude!]);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  return (
    <div className="min-h-screen bg-background">
      <main className="pb-16">
        {/* Header */}
        <div className="bg-card border-b border-border/50">
          <div className="container mx-auto px-4 py-6">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <h1 className="font-display text-2xl md:text-3xl font-bold">Issue Map</h1>
                <p className="text-muted-foreground">View and track civic issues across your city</p>
              </div>

              {/* Search */}
              <div className="flex items-center gap-3">
                <div className="relative flex-1 md:w-64">
                  <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                  <Input
                    aria-label="Search issues"
                    placeholder="Search issues..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="pl-10"
                  />
                </div>

                <Button
                  variant={showFilters ? "default" : "outline"}
                  size="icon"
                  aria-label={showFilters ? "Hide filters" : "Show filters"}
                  onClick={() => setShowFilters(!showFilters)}
                  className="md:hidden"
                >
                  {showFilters ? <X className="w-4 h-4" /> : <Filter className="w-4 h-4" />}
                </Button>
              </div>
            </div>

            {/* Filters */}
            <div className={`mt-6 space-y-4 ${showFilters ? "block" : "hidden md:block"}`}>
              <div>
                <span className="text-sm font-medium text-muted-foreground mb-2 block">Category</span>
                <IssueCategoryFilter selected={selectedCategory} onChange={setSelectedCategory} />
              </div>
              <div className="flex flex-wrap items-end gap-4">
                <div>
                  <span className="text-sm font-medium text-muted-foreground mb-2 block">Status</span>
                  <IssueStatusFilter value={selectedStatus} onChange={setSelectedStatus} />
                </div>
                <Button
                  variant={showSlaBreachedOnly ? "destructive" : "outline"}
                  size="sm"
                  aria-pressed={showSlaBreachedOnly}
                  onClick={() => setShowSlaBreachedOnly(!showSlaBreachedOnly)}
                  className="gap-2 rounded-full border-dashed"
                >
                  <AlertTriangle className="w-4 h-4" />
                  SLA Breached
                </Button>
              </div>
            </div>
          </div>
        </div>

        {/* Content */}
        <div className="container mx-auto px-4 py-8 space-y-8">
          {/* Full Width Map Section */}
          <div className="w-full">
            <MapErrorBoundary>
              <CivicMap filters={filters} onIssueSelect={setSelectedIssueId} focus={focus} />
            </MapErrorBoundary>
          </div>

          {/* Issue Grid Section */}
          <div>
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-2xl font-display font-bold">Reported Issues</h2>
              <span className="text-muted-foreground">
                {total} {total === 1 ? "issue" : "issues"}
              </span>
            </div>

            {feed.isPending ? (
              <div className="flex flex-col items-center justify-center py-20">
                <Loader2 className="w-12 h-12 animate-spin text-primary mb-4" />
                <p className="text-muted-foreground font-medium">Loading issues...</p>
              </div>
            ) : feed.error ? (
              <div className="text-center py-12 text-muted-foreground">
                <p className="font-medium text-foreground">Issues could not be loaded.</p>
                <Button variant="outline" size="sm" className="mt-4" onClick={() => feed.refetch()}>
                  Try again
                </Button>
              </div>
            ) : issues.length === 0 ? (
              <div className="text-center py-12 text-muted-foreground">
                <p>No issues match these filters.</p>
              </div>
            ) : (
              <>
                <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6 auto-rows-fr">
                  {issues.map((issue) => (
                    <IssueCard
                      key={issue.id}
                      issue={issue}
                      onClick={() => setSelectedIssueId(issue.id!)}
                    />
                  ))}
                </div>
                {feed.hasNextPage && (
                  <div className="flex justify-center mt-8">
                    <Button
                      variant="outline"
                      onClick={() => feed.fetchNextPage()}
                      disabled={feed.isFetchingNextPage}
                      className="gap-2"
                    >
                      {feed.isFetchingNextPage && <Loader2 className="w-4 h-4 animate-spin" />}
                      Load more issues
                    </Button>
                  </div>
                )}
              </>
            )}
          </div>

          <IssueDetailModal
            issueId={selectedIssueId}
            isOpen={!!selectedIssueId}
            onClose={() => setSelectedIssueId(null)}
            onViewOnMap={handleViewOnMap}
          />
        </div>
      </main>

      <Footer />
    </div>
  );
}
