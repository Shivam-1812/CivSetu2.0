import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Loader2 } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { CivicMap } from "@/components/CivicMap";
import { MapErrorBoundary } from "@/components/MapErrorBoundary";
import { IssueDetailModal } from "@/components/issues/IssueDetailModal";
import { SurveyCreationForm } from "@/components/surveys/SurveyCreationForm";
import { useAuth } from "@/auth/AuthProvider";
import { surveyApi } from "@/api/endpoints";
import type { IssueCluster } from "@/api/types";
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";

const RADII = [250, 500, 1000];

/** Spatial analysis: clusters of open issues, and community surveys for the people who reported them. */
export function PortalMap() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [radius, setRadius] = useState(500);
  const [selectedCluster, setSelectedCluster] = useState<IssueCluster | null>(null);
  const [isCreatingSurvey, setIsCreatingSurvey] = useState(false);
  const [detailsIssueId, setDetailsIssueId] = useState<string | null>(null);
  const [viewResponseSurveyId, setViewResponseSurveyId] = useState<string | null>(null);

  const clusters = useQuery({ queryKey: ["clusters", radius], queryFn: () => surveyApi.clusters(radius, 3) });
  const surveys = useQuery({ queryKey: ["surveys", "managed"], queryFn: surveyApi.managed });
  const department = user?.role === "AUTHORITY" ? user.departmentId : undefined;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div>
          <h2 className="font-display text-2xl font-bold">Spatial Analysis</h2>
          <p className="text-muted-foreground text-sm">
            Identify issue clusters (3 or more open issues close together) and plan surveys
          </p>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex bg-muted/50 p-1 rounded-lg" role="group" aria-label="Cluster radius">
            {RADII.map((value) => (
              <button
                key={value}
                type="button"
                aria-pressed={radius === value}
                onClick={() => setRadius(value)}
                className={cn(
                  "px-3 py-1 text-sm font-medium rounded-md",
                  radius === value ? "bg-background shadow-sm" : "text-muted-foreground",
                )}
              >
                {value} m
              </button>
            ))}
          </div>
          <div className="bg-orange-100 text-orange-700 px-3 py-1 rounded-full text-sm font-medium border border-orange-200">
            {clusters.data?.length ?? 0} Active Clusters Found
          </div>
        </div>
      </div>

      <div className="relative">
        {clusters.isPending && (
          <div className="absolute inset-0 flex items-center justify-center bg-background/50 backdrop-blur-sm z-10 rounded-2xl">
            <Loader2 className="w-8 h-8 animate-spin text-primary" />
          </div>
        )}
        <MapErrorBoundary>
          <CivicMap
            className="lg:w-full h-[520px]"
            filters={{ department, status: ["REPORTED", "ASSIGNED", "IN_PROGRESS"] }}
            onIssueSelect={setDetailsIssueId}
            clusters={clusters.data ?? []}
            clusterRadius={radius}
            onClusterSelect={(cluster) => {
              setSelectedCluster(cluster);
              setIsCreatingSurvey(false);
            }}
          />
        </MapErrorBoundary>
      </div>

      {isCreatingSurvey && selectedCluster && (
        <SurveyCreationForm
          cluster={selectedCluster}
          radius={radius}
          onCancel={() => {
            setIsCreatingSurvey(false);
            setSelectedCluster(null);
          }}
          onSuccess={(survey) => {
            setIsCreatingSurvey(false);
            setSelectedCluster(null);
            queryClient.invalidateQueries({ queryKey: ["surveys"] });
            toast.success(`Survey "${survey.title}" sent to ${survey.invitedCount} citizens.`);
          }}
        />
      )}

      {!isCreatingSurvey && (
        <div className="bg-card border border-border/50 rounded-xl p-6 mt-8">
          <div className="flex items-center justify-between mb-4">
            <h3 className="font-display font-semibold text-lg">Community Surveys</h3>
            <Button variant="ghost" size="sm" onClick={() => surveys.refetch()}>
              Refresh List
            </Button>
          </div>
          {surveys.data?.length === 0 ? (
            <p className="text-sm text-muted-foreground">No surveys yet. Create one from a cluster above.</p>
          ) : (
            <div className="space-y-4">
              {surveys.data?.map((survey) => (
                <div
                  key={survey.id}
                  className="flex flex-wrap items-start justify-between gap-3 p-4 border rounded-lg bg-background/50 hover:bg-background transition-colors"
                >
                  <div>
                    <h4 className="font-medium">{survey.title}</h4>
                    <p className="text-xs text-muted-foreground mb-2">
                      {survey.status === "OPEN" ? `Open until ${formatDate(survey.closesAt)}` : "Closed"}
                    </p>
                    <div className="text-xs bg-primary/10 text-primary px-2 py-1 rounded inline-block">
                      {survey.responseCount} of {survey.invitedCount} citizens answered
                    </div>
                  </div>
                  <Button size="sm" variant="outline" onClick={() => setViewResponseSurveyId(survey.id!)}>
                    View Responses
                  </Button>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Cluster Action Dialog */}
      <Dialog open={!!selectedCluster && !isCreatingSurvey} onOpenChange={(open) => !open && setSelectedCluster(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Issue Cluster Detected</DialogTitle>
            <DialogDescription>
              This area has {selectedCluster?.issueCount} open issues within a {radius}m radius, reported by{" "}
              {selectedCluster?.reporterCount} citizens. Would you like to initiate a survey for this region?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSelectedCluster(null)}>
              Cancel
            </Button>
            <Button onClick={() => setIsCreatingSurvey(true)}>Create Survey</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <SurveyResultsDialog surveyId={viewResponseSurveyId} onClose={() => setViewResponseSurveyId(null)} />
      <IssueDetailModal issueId={detailsIssueId} isOpen={!!detailsIssueId} onClose={() => setDetailsIssueId(null)} />
    </div>
  );
}

/** Aggregated answers (individual answers stay anonymous), with an option to close the survey. */
function SurveyResultsDialog({ surveyId, onClose }: { surveyId: string | null; onClose: () => void }) {
  const queryClient = useQueryClient();
  const results = useQuery({
    queryKey: ["survey", surveyId, "results"],
    queryFn: () => surveyApi.results(surveyId!),
    enabled: !!surveyId,
  });
  const survey = useQuery({ queryKey: ["survey", surveyId], queryFn: () => surveyApi.get(surveyId!), enabled: !!surveyId });
  const close = useMutation({
    mutationFn: () => surveyApi.close(surveyId!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["survey", surveyId] });
      queryClient.invalidateQueries({ queryKey: ["surveys"] });
      toast.success("Survey closed");
    },
  });
  const data = results.data;

  return (
    <Dialog open={!!surveyId} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-2xl max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Survey Responses: {data?.title}</DialogTitle>
          <DialogDescription>
            {data ? `${data.responseCount} of ${data.invitedCount} invited citizens answered.` : "Loading…"}
          </DialogDescription>
        </DialogHeader>
        <div className="py-4 space-y-4">
          {results.isPending ? (
            <div className="flex justify-center p-4">
              <Loader2 className="animate-spin" />
            </div>
          ) : data?.responseCount === 0 ? (
            <p className="text-muted-foreground text-center">No responses received yet.</p>
          ) : (
            data?.questions?.map((question, index) => (
              <div key={question.id} className="bg-muted/30 p-4 rounded-lg border">
                <p className="font-semibold text-xs uppercase tracking-wide opacity-70 mb-2">
                  {index + 1}. {question.text}
                </p>
                {question.type === "RATING" && (
                  <div>
                    <p className="text-sm text-muted-foreground">
                      Average <strong className="text-foreground">{question.averageRating ?? "—"}</strong> from{" "}
                      {question.answered} answers
                    </p>
                    <ul className="mt-3 space-y-1">
                      {[5, 4, 3, 2, 1].map((rating) => {
                        const count = question.ratingCounts?.[String(rating)] ?? 0;
                        const share = question.answered ? (count / question.answered) * 100 : 0;
                        return (
                          <li key={rating} className="flex items-center gap-2 text-sm">
                            <span className="w-4">{rating}</span>
                            <span className="h-3 flex-1 rounded bg-muted">
                              <span className="block h-3 rounded bg-primary" style={{ width: `${share}%` }} />
                            </span>
                            <span className="w-8 text-right text-muted-foreground">{count}</span>
                          </li>
                        );
                      })}
                    </ul>
                  </div>
                )}
                {question.type === "YES_NO" && (
                  <p className="text-sm">
                    Yes <strong>{question.yes}</strong> · No <strong>{question.no}</strong>
                  </p>
                )}
                {question.type === "TEXT" &&
                  (question.textAnswers?.length ? (
                    <ul className="space-y-2">
                      {question.textAnswers.map((answer, i) => (
                        <li key={i} className="rounded-lg bg-background p-2 text-sm border">
                          {answer}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <p className="text-sm text-muted-foreground">No written answers.</p>
                  ))}
              </div>
            ))
          )}
        </div>
        <DialogFooter>
          {survey.data?.status === "OPEN" && (
            <Button variant="outline" onClick={() => close.mutate()} disabled={close.isPending}>
              Close Survey
            </Button>
          )}
          <Button onClick={onClose}>Close</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
