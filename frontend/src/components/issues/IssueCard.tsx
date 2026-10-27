import {
  MapPin,
  Clock,
  Building2,
  AlertTriangle,
  Mic,
  Image,
  ThumbsUp,
  ThumbsDown,
  MessageSquare,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { MediaImage } from "@/components/media";
import { cn } from "@/lib/utils";
import { formatRelative } from "@/lib/format";
import {
  categoryIcon,
  deadlineText,
  daysRemaining,
  isOpen,
  priorityConfig,
  slaConfig,
  statusConfig,
} from "@/lib/issues";
import { useVote } from "@/components/issues/useEngagement";
import type { IssueSummary } from "@/api/types";

interface IssueCardProps {
  issue: IssueSummary;
  onClick?: () => void;
  /** Opens the issue with the comments in view. */
  onComment?: () => void;
}

/** A feed card: cover photo, status, deadline and the community's likes and comments. */
export function IssueCard({ issue, onClick, onComment }: IssueCardProps) {
  const priority = priorityConfig[issue.priority ?? "MEDIUM"];
  const status = statusConfig[issue.status ?? "REPORTED"];
  const open = isOpen(issue.status);
  const days = daysRemaining(issue.slaDueAt);
  const vote = useVote(issue);

  return (
    <article
      onClick={onClick}
      aria-label={issue.title}
      className="group bg-card rounded-2xl border border-border/50 overflow-hidden hover:border-primary/30 hover:shadow-card-hover transition-all duration-300 cursor-pointer h-full flex flex-col"
    >
      {/* Media Preview Section */}
      <div className="aspect-video w-full bg-muted relative overflow-hidden flex-shrink-0">
        {issue.coverImageUrl ? (
          <MediaImage
            path={issue.coverImageUrl}
            alt=""
            className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
          />
        ) : (
          <div className="w-full h-full flex flex-col items-center justify-center gap-3 bg-muted/50 p-4 text-center">
            {issue.hasVoice ? (
              <>
                <Mic className="w-12 h-12 text-muted-foreground/50" />
                <span className="text-xs text-muted-foreground font-medium">
                  Voice Note Only
                </span>
              </>
            ) : (
              <span className="text-4xl">{categoryIcon(issue.categoryCode)}</span>
            )}
          </div>
        )}

        {/* Priority Badge */}
        <div className="absolute top-3 left-3 flex gap-2">
          <Badge className={cn("text-xs font-medium shadow-sm backdrop-blur-sm", priority.class)}>
            {issue.priority === "CRITICAL" && <AlertTriangle className="w-3 h-3 mr-1" />}
            {priority.label}
          </Badge>
        </div>

        {/* Media Type Indicators (Bottom Right of Image) */}
        <div className="absolute bottom-3 right-3 flex gap-1.5 pointer-events-none">
          {(issue.imageCount ?? 0) > 1 && (
            <div className="px-2 h-6 rounded-full bg-black/60 backdrop-blur-md flex items-center justify-center gap-1 text-white text-[10px] font-medium">
              <Image className="w-3.5 h-3.5" />
              <span>{issue.imageCount}</span>
            </div>
          )}
          {issue.hasVoice && (
            <div
              className="w-6 h-6 rounded-full bg-black/60 backdrop-blur-md flex items-center justify-center text-white"
              title="Voice Note"
            >
              <Mic className="w-3.5 h-3.5" />
            </div>
          )}
        </div>
      </div>

      <div className="p-5 flex-1 flex flex-col">
        {/* Category & Status */}
        <div className="flex items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2 min-w-0">
            <span className="text-lg">{categoryIcon(issue.categoryCode)}</span>
            <span className="text-sm font-medium text-muted-foreground truncate">
              {issue.categoryLabel}
            </span>
          </div>
          <Badge className={cn("text-xs shrink-0", status.class)}>{status.label}</Badge>
        </div>

        {/* SLA Status Bar (open issues only) */}
        {open && issue.slaState && days !== undefined && (
          <div className="flex items-center gap-2 mb-3">
            <Badge
              variant="outline"
              className={cn("text-[10px] font-semibold border", slaConfig[issue.slaState].class)}
            >
              <Clock className="w-3 h-3 mr-1" />
              {deadlineText(issue.slaDueAt)}
            </Badge>
            {issue.slaState === "BREACHED" && (
              <span className="text-[10px] font-bold text-red-500 animate-pulse">
                SLA BREACHED
              </span>
            )}
          </div>
        )}

        {/* Title & Description */}
        <h3 className="font-display font-semibold text-lg mb-2 line-clamp-2 group-hover:text-primary transition-colors flex-shrink-0">
          {/* The keyboard way to open the card; a click anywhere on the card does the same */}
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onClick?.();
            }}
            className="text-left rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            {issue.title}
          </button>
        </h3>
        <p className="text-muted-foreground text-sm line-clamp-3 mb-4 flex-1">
          {issue.description}
        </p>

        {/* Meta Info */}
        <div className="grid grid-cols-2 gap-y-2 text-xs text-muted-foreground mb-4 flex-shrink-0">
          <div className="flex items-center gap-1.5 min-w-0">
            <MapPin className="w-3.5 h-3.5 shrink-0" />
            <span className="truncate">{issue.address}</span>
          </div>
          <div className="flex items-center gap-1.5">
            <Clock className="w-3.5 h-3.5" />
            <span>{formatRelative(issue.createdAt)}</span>
          </div>
          <div className="flex items-center gap-1.5 col-span-2 min-w-0">
            <Building2 className="w-3.5 h-3.5 shrink-0" />
            <span className="truncate">{issue.departmentName}</span>
          </div>
        </div>

        {/* Engagement Footer: like, dislike and comment like a social feed */}
        <div className="mt-auto pt-3 border-t border-border/50 flex items-center gap-1 text-xs text-muted-foreground">
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              vote.toggle("AGREE");
            }}
            disabled={vote.pending}
            aria-pressed={vote.myVote === "AGREE"}
            aria-label={`Agree, ${vote.agree}`}
            title={vote.hint ?? "I see this problem too"}
            className={cn(
              "flex items-center gap-1 rounded-full px-2 py-1 transition-colors hover:bg-muted",
              vote.myVote === "AGREE" && "text-green-600",
            )}
          >
            <ThumbsUp className={cn("w-3.5 h-3.5", vote.myVote === "AGREE" && "fill-current")} />
            <span>{vote.agree}</span>
          </button>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              vote.toggle("DISAGREE");
            }}
            disabled={vote.pending}
            aria-pressed={vote.myVote === "DISAGREE"}
            aria-label={`Disagree, ${vote.disagree}`}
            title={vote.hint ?? "I don't think this is a problem"}
            className={cn(
              "flex items-center gap-1 rounded-full px-2 py-1 transition-colors hover:bg-muted",
              vote.myVote === "DISAGREE" && "text-red-500",
            )}
          >
            <ThumbsDown className={cn("w-3.5 h-3.5", vote.myVote === "DISAGREE" && "fill-current")} />
            <span>{vote.disagree}</span>
          </button>
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              (onComment ?? onClick)?.();
            }}
            aria-label={`Comments, ${issue.commentCount ?? 0}`}
            className="flex items-center gap-1 ml-auto rounded-full px-2 py-1 transition-colors hover:bg-muted"
          >
            <MessageSquare className="w-3.5 h-3.5" />
            <span>{issue.commentCount ?? 0}</span>
          </button>
        </div>
      </div>
    </article>
  );
}
