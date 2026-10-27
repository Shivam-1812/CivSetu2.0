import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { X, FileText, Settings, CheckCircle } from "lucide-react";
import { MediaCarousel } from "@/components/common/MediaCarousel";
import { cn } from "@/lib/utils";
import { formatDateTime } from "@/lib/format";
import type { Media } from "@/api/types";

export type DetailKind = "plan" | "resolution" | "rejection";

interface DetailViewerModalProps {
  isOpen: boolean;
  onClose: () => void;
  type: DetailKind;
  data: {
    description?: string; // Action Taken / Remarks / Reason
    staff?: string;
    resources?: string;
    proofs: Media[];
    timestamp?: string;
  };
}

const THEME: Record<DetailKind, { title: string; header: string; iconBox: string; icon: string; heading: string; mediaLabel: string }> = {
  plan: {
    title: "Official Response Plan",
    header: "border-blue-100 bg-blue-50/30",
    iconBox: "bg-blue-100",
    icon: "text-blue-600",
    heading: "text-blue-900",
    mediaLabel: "Plan document",
  },
  resolution: {
    title: "Resolution Verified",
    header: "border-green-100 bg-green-50/30",
    iconBox: "bg-green-100",
    icon: "text-green-600",
    heading: "text-green-900",
    mediaLabel: "Photo showing the fix",
  },
  rejection: {
    title: "Issue Rejected",
    header: "border-red-100 bg-red-50/30",
    iconBox: "bg-red-100",
    icon: "text-red-600",
    heading: "text-red-900",
    mediaLabel: "Rejection evidence",
  },
};

export function DetailViewerModal({ isOpen, onClose, type, data }: DetailViewerModalProps) {
  const theme = THEME[type];
  const Icon = type === "plan" ? Settings : type === "resolution" ? CheckCircle : X;

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-5xl h-[85vh] p-0 gap-0 overflow-hidden flex flex-col md:flex-row bg-card border-none shadow-2xl">
        {/* LEFT: Media Carousel */}
        <div className="w-full md:w-[60%] h-[40vh] md:h-full bg-black relative">
          <Button
            variant="ghost"
            size="icon"
            aria-label="Close"
            className="absolute top-4 right-4 text-white hover:bg-white/10 md:hidden z-20"
            onClick={onClose}
          >
            <X className="w-6 h-6" />
          </Button>
          <MediaCarousel media={data.proofs} label={theme.mediaLabel} />
        </div>

        {/* RIGHT: Details Text */}
        <div className="w-full md:w-[40%] flex flex-col h-full bg-card relative min-h-0">
          <div className={cn("p-6 border-b", theme.header)}>
            <div className="flex items-center gap-3 mb-2">
              <div className={cn("p-2 rounded-full", theme.iconBox)}>
                <Icon className={cn("w-5 h-5", theme.icon)} />
              </div>
              <div>
                <DialogTitle className={cn("text-lg font-bold", theme.heading)}>{theme.title}</DialogTitle>
                <DialogDescription className="text-xs text-muted-foreground">
                  {data.timestamp ? formatDateTime(data.timestamp) : "Official response"}
                </DialogDescription>
              </div>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto p-6">
            <div className="space-y-6">
              {data.description && (
                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-2">
                    Description / Remarks
                  </h3>
                  <p className="text-sm leading-relaxed text-foreground bg-muted/30 p-4 rounded-lg border border-border">
                    {data.description}
                  </p>
                </div>
              )}

              {(data.staff || data.resources) && (
                <div className="grid grid-cols-2 gap-4">
                  {data.staff && (
                    <div>
                      <h3 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-2">
                        Staff Allocated
                      </h3>
                      <p className="text-sm font-medium">{data.staff}</p>
                    </div>
                  )}
                  {data.resources && (
                    <div>
                      <h3 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-2">
                        Resources Used
                      </h3>
                      <p className="text-sm font-medium">{data.resources}</p>
                    </div>
                  )}
                </div>
              )}

              <div>
                <h3 className="text-xs font-semibold uppercase tracking-widest text-muted-foreground mb-3">
                  Evidence Files
                </h3>
                <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
                  {data.proofs.length > 0 ? (
                    <Badge variant="outline" className="gap-1.5 py-1">
                      <FileText className="w-3 h-3" />
                      {data.proofs.length} Files attached (View on Left)
                    </Badge>
                  ) : (
                    <span className="italic">No proofs attached</span>
                  )}
                </div>
              </div>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
