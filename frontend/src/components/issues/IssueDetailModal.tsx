import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link } from "react-router";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Input } from "@/components/ui/input";
import {
  ThumbsUp,
  ThumbsDown,
  MessageSquare,
  Send,
  MapPin,
  X,
  ChevronRight,
  Settings,
  CheckCircle,
  ExternalLink,
  Building2,
  Loader2,
  EyeOff,
  Sparkles,
} from "lucide-react";
import { toast } from "sonner";
import { useAuth } from "@/auth/AuthProvider";
import { ApiError } from "@/api/client";
import { engagementApi, issueApi } from "@/api/endpoints";
import type { HistoryEntry, IssueDetail } from "@/api/types";
import { cn } from "@/lib/utils";
import { formatDate, formatDateTime, formatRelative } from "@/lib/format";
import { deadlineText, isOpen, priorityConfig, slaConfig, statusConfig } from "@/lib/issues";
import { MediaCarousel } from "@/components/common/MediaCarousel";
import { DetailViewerModal, type DetailKind } from "./DetailViewerModal";
import { IssueTimeline } from "./IssueTimeline";
import { useVote } from "./useEngagement";
import { IssueActions } from "@/pages/issue/IssueActions";

interface IssueDetailModalProps {
  issueId: string | null;
  isOpen: boolean;
  onClose: () => void;
  onViewOnMap?: (issue: IssueDetail) => void;
}

/** The issue popup used on the map, dashboards and management pages. */
export function IssueDetailModal({ issueId, isOpen, onClose, onViewOnMap }: IssueDetailModalProps) {
  return (
    <Dialog open={isOpen && !!issueId} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-5xl h-[90vh] p-0 gap-0 overflow-hidden flex flex-col md:flex-row bg-card">
        <DialogTitle className="sr-only">Issue details</DialogTitle>
        <DialogDescription className="sr-only">Photos, status, community votes and comments for this issue.</DialogDescription>
        {issueId && <IssueDetailView issueId={issueId} onClose={onClose} onViewOnMap={onViewOnMap} />}
      </DialogContent>
    </Dialog>
  );
}

/** The latest step of each kind, for the plan / resolution / rejection buttons. */
function latest(history: HistoryEntry[] | undefined, action: HistoryEntry["action"]) {
  return history?.filter((step) => step.action === action).at(-1);
}

export function IssueDetailView({
  issueId,
  onClose,
  onViewOnMap,
  layout = "modal",
}: {
  issueId: string;
  onClose?: () => void;
  onViewOnMap?: (issue: IssueDetail) => void;
  layout?: "modal" | "page";
}) {
  const issue = useQuery({ queryKey: ["issue", issueId], queryFn: () => issueApi.get(issueId) });
  const history = useQuery({ queryKey: ["issue", issueId, "history"], queryFn: () => issueApi.history(issueId) });
  const [viewing, setViewing] = useState<DetailKind | null>(null);

  if (issue.isPending) {
    return (
      <div className="flex-1 flex items-center justify-center min-h-[300px]">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }
  if (issue.error) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-3 p-8 text-center min-h-[300px]">
        <p className="font-semibold">This issue could not be loaded.</p>
        <p className="text-sm text-muted-foreground">
          {issue.error instanceof ApiError ? issue.error.message : "Please try again."}
        </p>
      </div>
    );
  }

  const data = issue.data;
  const plan = latest(history.data, "PLANNED");
  const resolution = latest(history.data, "RESOLVED");
  const rejection = latest(history.data, "REJECTED");
  const viewed = viewing === "plan" ? plan : viewing === "resolution" ? resolution : viewing === "rejection" ? rejection : undefined;
  const status = statusConfig[data.status ?? "REPORTED"];
  const reportMedia = data.media ?? [];

  return (
    <div
      className={cn(
        "flex flex-col md:flex-row w-full",
        layout === "modal" ? "h-full" : "bg-card rounded-2xl border border-border/50 overflow-hidden md:min-h-[640px]",
      )}
    >
      {/* LEFT: Media Section */}
      <div className="w-full md:w-[60%] h-[40vh] md:h-auto bg-black/95 relative flex items-center justify-center">
        {onClose && (
          <Button
            variant="ghost"
            size="icon"
            aria-label="Close"
            className="absolute top-4 right-4 text-white hover:bg-white/10 md:hidden z-20"
            onClick={onClose}
          >
            <X className="w-6 h-6" />
          </Button>
        )}
        <MediaCarousel media={reportMedia} label="Photo of the issue" />
      </div>

      {/* RIGHT: Details & Engagement */}
      <div className="w-full md:w-[40%] flex flex-col md:h-full bg-card min-h-0">
        {/* Header (right padding keeps the badges clear of the dialog close button) */}
        <div className={cn("p-6 border-b border-border/50", layout === "modal" && "pr-12")}>
          <div className="flex items-center gap-3 mb-4">
            <Avatar className="w-10 h-10 border border-border">
              <AvatarFallback>{data.reporter?.name?.charAt(0).toUpperCase() ?? "C"}</AvatarFallback>
            </Avatar>
            <div className="min-w-0">
              <p className="font-semibold text-sm truncate">
                {data.reportedByMe ? "Reported by you" : data.reporter?.name ? `Reported by ${data.reporter.name}` : "Reported by a citizen"}
              </p>
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <MapPin className="w-3 h-3 shrink-0" />
                <span className="truncate max-w-[150px]">{data.address}</span>
              </div>
            </div>
            <div className="ml-auto flex flex-col items-end gap-1.5">
              <div className="flex gap-2">
                <Badge variant="outline" className={cn("capitalize", priorityConfig[data.priority ?? "MEDIUM"].class)}>
                  {priorityConfig[data.priority ?? "MEDIUM"].label}
                </Badge>
                <Badge className={cn("capitalize", status.class)}>{status.label}</Badge>
              </div>
              {/* SLA Info */}
              {isOpen(data.status) && data.slaState && (
                <div className="flex flex-col items-end">
                  <span className={cn("text-[10px] font-bold px-1.5 py-0.5 rounded border", slaConfig[data.slaState].class)}>
                    {deadlineText(data.slaDueAt)}
                  </span>
                  <span className="text-[9px] text-muted-foreground">Target: {formatDate(data.slaDueAt)}</span>
                </div>
              )}
              {onViewOnMap ? (
                <button
                  onClick={() => onViewOnMap(data)}
                  className="text-primary hover:underline text-[10px] font-medium flex items-center gap-1"
                >
                  View on Map <ChevronRight className="w-3 h-3" />
                </button>
              ) : (
                layout === "modal" && (
                  <Link
                    to={`/issues/${data.id}`}
                    className="text-primary hover:underline text-[10px] font-medium flex items-center gap-1"
                  >
                    Open full page <ChevronRight className="w-3 h-3" />
                  </Link>
                )
              )}
            </div>
          </div>

          <h2 className="text-xl font-bold font-display mb-2">{data.title}</h2>
          <p className="flex items-center gap-1.5 text-xs text-muted-foreground mb-2">
            <Building2 className="w-3.5 h-3.5" />
            <span>
              {data.categoryLabel} · handled by <strong className="text-foreground">{data.departmentName}</strong> ·{" "}
              {formatRelative(data.createdAt)}
            </span>
          </p>
          <div className="max-h-[100px] overflow-y-auto">
            <p className="text-sm text-muted-foreground leading-relaxed whitespace-pre-line">{data.description}</p>
          </div>
        </div>

        {/* Responses, actions, timeline and comments */}
        <div className="flex-1 overflow-y-auto p-6 min-h-0">
          <div className="space-y-4">
            {data.priorityReason && (
              <div className="flex gap-3 rounded-xl border border-border/50 bg-muted/30 p-3 text-xs">
                <Sparkles className="w-4 h-4 text-primary shrink-0 mt-0.5" />
                <div>
                  <p className="font-semibold uppercase tracking-wide text-muted-foreground">
                    Why this priority ({data.prioritySource === "AI" ? "AI" : "rules"})
                  </p>
                  <p className="mt-0.5">{data.priorityReason}</p>
                </div>
              </div>
            )}

            {plan && (
              <ResponseButton kind="plan" onClick={() => setViewing("plan")} />
            )}
            {resolution && data.status === "RESOLVED" && (
              <ResponseButton kind="resolution" onClick={() => setViewing("resolution")} />
            )}
            {rejection && data.status === "REJECTED" && (
              <ResponseButton kind="rejection" onClick={() => setViewing("rejection")} />
            )}

            <IssueActions issue={data} />

            <VoteBar issue={data} />

            <IssueTimeline issueId={issueId} />

            <Comments issue={data} />
          </div>
        </div>

        {/* Footer Input */}
        <CommentInput issue={data} />
      </div>

      {viewing && viewed && (
        <DetailViewerModal
          isOpen
          onClose={() => setViewing(null)}
          type={viewing}
          data={{
            description: viewing === "plan" ? viewed.actionTaken : viewed.remarks,
            staff: viewed.staffAllocated,
            resources: viewed.resourcesUsed,
            proofs: viewed.media ?? [],
            timestamp: viewed.createdAt,
          }}
        />
      )}
    </div>
  );
}

const RESPONSE_STYLES: Record<DetailKind, { title: string; hint: string; box: string; icon: string; text: string; sub: string; link: string }> = {
  plan: {
    title: "Official Response Plan",
    hint: "View action plan, staff & proofs",
    box: "bg-blue-50 hover:bg-blue-100/80 border-blue-200",
    icon: "bg-blue-100 group-hover:bg-blue-200 text-blue-700",
    text: "text-blue-900",
    sub: "text-blue-700",
    link: "text-blue-400 group-hover:text-blue-600",
  },
  resolution: {
    title: "Resolution Verified",
    hint: "View final remarks & evidence",
    box: "bg-green-50 hover:bg-green-100/80 border-green-200",
    icon: "bg-green-100 group-hover:bg-green-200 text-green-700",
    text: "text-green-900",
    sub: "text-green-700",
    link: "text-green-400 group-hover:text-green-600",
  },
  rejection: {
    title: "Issue Rejected",
    hint: "View rejection reason & evidence",
    box: "bg-red-50 hover:bg-red-100/80 border-red-200",
    icon: "bg-red-100 group-hover:bg-red-200 text-red-700",
    text: "text-red-900",
    sub: "text-red-700",
    link: "text-red-400 group-hover:text-red-600",
  },
};

function ResponseButton({ kind, onClick }: { kind: DetailKind; onClick: () => void }) {
  const style = RESPONSE_STYLES[kind];
  const Icon = kind === "plan" ? Settings : kind === "resolution" ? CheckCircle : X;
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "group w-full text-left cursor-pointer border p-4 rounded-xl transition-all duration-200 shadow-sm hover:shadow-md flex items-center justify-between",
        style.box,
      )}
    >
      <div className="flex items-center gap-3">
        <div className={cn("p-2 rounded-full transition-colors", style.icon)}>
          <Icon className="w-5 h-5" />
        </div>
        <div>
          <h3 className={cn("font-bold text-sm", style.text)}>{style.title}</h3>
          <p className={cn("text-xs mt-0.5", style.sub)}>{style.hint}</p>
        </div>
      </div>
      <ExternalLink className={cn("w-4 h-4 transition-colors", style.link)} />
    </button>
  );
}

function VoteBar({ issue }: { issue: IssueDetail }) {
  const vote = useVote({
    id: issue.id,
    agreeCount: issue.votes?.agree,
    disagreeCount: issue.votes?.disagree,
    myVote: issue.votes?.myVote,
  });
  const ownReport = issue.reportedByMe;
  return (
    <div className="flex items-center justify-between p-4 bg-muted/30 rounded-xl my-6">
      <div className="flex gap-6">
        <button
          type="button"
          onClick={() => (ownReport ? toast.info("You cannot vote on your own report.") : vote.toggle("AGREE"))}
          disabled={vote.pending}
          aria-pressed={vote.myVote === "AGREE"}
          aria-label={`I see this problem too, ${vote.agree}`}
          className={cn(
            "flex flex-col items-center gap-1 transition-colors",
            vote.myVote === "AGREE" ? "text-green-500" : "text-muted-foreground hover:text-foreground",
          )}
        >
          <ThumbsUp className={cn("w-5 h-5", vote.myVote === "AGREE" && "fill-current")} />
          <span className="text-xs font-bold">{vote.agree}</span>
        </button>
        <button
          type="button"
          onClick={() => (ownReport ? toast.info("You cannot vote on your own report.") : vote.toggle("DISAGREE"))}
          disabled={vote.pending}
          aria-pressed={vote.myVote === "DISAGREE"}
          aria-label={`Not a problem, ${vote.disagree}`}
          className={cn(
            "flex flex-col items-center gap-1 transition-colors",
            vote.myVote === "DISAGREE" ? "text-red-500" : "text-muted-foreground hover:text-foreground",
          )}
        >
          <ThumbsDown className={cn("w-5 h-5", vote.myVote === "DISAGREE" && "fill-current")} />
          <span className="text-xs font-bold">{vote.disagree}</span>
        </button>
      </div>
      <div className="h-8 w-[1px] bg-border" />
      <div className="flex flex-col items-center gap-1 text-muted-foreground">
        <MessageSquare className="w-5 h-5" />
        <span className="text-xs font-bold">{issue.commentCount ?? 0}</span>
      </div>
    </div>
  );
}

function Comments({ issue }: { issue: IssueDetail }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const comments = useQuery({
    queryKey: ["issue", issue.id, "comments"],
    queryFn: () => engagementApi.comments(issue.id!),
  });
  const [hiding, setHiding] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const moderate = useMutation({
    mutationFn: ({ id, hidden }: { id: string; hidden: boolean }) =>
      engagementApi.moderate(id, hidden, hidden ? reason : undefined),
    onSuccess: () => {
      setHiding(null);
      setReason("");
      queryClient.invalidateQueries({ queryKey: ["issue", issue.id, "comments"] });
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "Could not update the comment."),
  });
  const isAdmin = user?.role === "ADMIN";
  const list = comments.data?.content ?? [];

  return (
    <div>
      <h3 className="font-semibold text-sm mb-4">Comments</h3>
      {comments.isPending ? (
        <Loader2 className="w-5 h-5 animate-spin text-primary" />
      ) : list.length > 0 ? (
        <div className="space-y-4">
          {list.map((comment) => (
            <div key={comment.id} className="flex gap-3 text-sm animate-in fade-in slide-in-from-bottom-2">
              <Avatar className="w-8 h-8">
                <AvatarFallback>{comment.authorName?.charAt(0).toUpperCase() || "A"}</AvatarFallback>
              </Avatar>
              <div className="bg-muted/50 p-3 rounded-lg rounded-tl-none flex-1 min-w-0">
                <div className="flex items-center justify-between gap-2 mb-1">
                  <p className="font-semibold text-xs">
                    {comment.authorName}
                    {comment.authorRole && comment.authorRole !== "CITIZEN" && (
                      <span className="ml-1.5 rounded bg-primary/10 px-1.5 py-0.5 text-[10px] font-semibold text-primary">
                        {comment.authorRole === "AUTHORITY" ? "Official" : "Admin"}
                      </span>
                    )}
                  </p>
                  <span className="text-[10px] text-muted-foreground">{formatDateTime(comment.createdAt)}</span>
                </div>
                {comment.hidden ? (
                  <p className="italic text-muted-foreground flex items-center gap-1.5">
                    <EyeOff className="w-3.5 h-3.5" /> Hidden by a moderator: {comment.hiddenReason}
                  </p>
                ) : (
                  <p className="text-muted-foreground break-words">{comment.body}</p>
                )}
                {isAdmin && (
                  <div className="mt-2">
                    {comment.hidden ? (
                      <button
                        type="button"
                        className="text-[11px] font-medium text-primary hover:underline"
                        onClick={() => moderate.mutate({ id: comment.id!, hidden: false })}
                      >
                        Show again
                      </button>
                    ) : hiding === comment.id ? (
                      <div className="flex gap-2">
                        <Input
                          aria-label="Reason for hiding (shown publicly)"
                          placeholder="Reason (shown publicly)"
                          value={reason}
                          onChange={(e) => setReason(e.target.value)}
                          className="h-8 text-xs"
                        />
                        <Button
                          size="sm"
                          variant="destructive"
                          disabled={!reason.trim() || moderate.isPending}
                          onClick={() => moderate.mutate({ id: comment.id!, hidden: true })}
                        >
                          Hide
                        </Button>
                      </div>
                    ) : (
                      <button
                        type="button"
                        className="text-[11px] font-medium text-destructive hover:underline"
                        onClick={() => setHiding(comment.id!)}
                      >
                        Hide comment
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="text-center py-10 text-muted-foreground">
          <p>No comments yet. Be the first to verify!</p>
        </div>
      )}
    </div>
  );
}

function CommentInput({ issue }: { issue: IssueDetail }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [text, setText] = useState("");
  const send = useMutation({
    mutationFn: () => engagementApi.comment(issue.id!, text.trim()),
    onSuccess: () => {
      setText("");
      queryClient.invalidateQueries({ queryKey: ["issue", issue.id] });
      queryClient.invalidateQueries({ queryKey: ["issues"] });
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "Could not post your comment."),
  });

  if (!user) {
    return (
      <div className="p-4 border-t border-border/50 bg-card text-center text-sm text-muted-foreground">
        <Link to="/login" className="text-primary font-medium hover:underline">
          Sign in
        </Link>{" "}
        to comment and verify this issue.
      </div>
    );
  }

  const submit = () => {
    if (text.trim()) send.mutate();
  };

  return (
    <div className="p-4 border-t border-border/50 bg-card">
      <div className="flex gap-2 relative">
        <Input
          aria-label="Add a comment"
          placeholder="Add a verification comment..."
          value={text}
          maxLength={1000}
          onChange={(e) => setText(e.target.value)}
          className="pr-10"
          onKeyDown={(e) => e.key === "Enter" && submit()}
        />
        <Button
          size="icon"
          variant="ghost"
          aria-label="Send comment"
          className="absolute right-1 top-1/2 -translate-y-1/2 h-8 w-8 text-primary hover:bg-primary/10"
          onClick={submit}
          disabled={!text.trim() || send.isPending}
        >
          {send.isPending ? <Loader2 className="w-4 h-4 animate-spin" /> : <Send className="w-4 h-4" />}
        </Button>
      </div>
      <p className="text-[10px] text-muted-foreground mt-2 text-center">
        Comments are strictly moderated. Keep it civil.
      </p>
    </div>
  );
}
