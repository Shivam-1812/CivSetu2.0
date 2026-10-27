import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2, Upload, FileWarning, Undo2, Copy } from "lucide-react";
import { issueApi, workflowApi } from "@/api/endpoints";
import type { IssueDetail } from "@/api/types";
import { cn } from "@/lib/utils";
import { useWorkflowAction } from "./useWorkflow";

interface WorkflowModalProps {
  issue: IssueDetail | null;
  isOpen: boolean;
  onClose: () => void;
}

export function RejectionModal({ issue, isOpen, onClose }: WorkflowModalProps) {
  const [reason, setReason] = useState("");
  const [files, setFiles] = useState<File[]>([]);
  const reject = useWorkflowAction(
    issue?.id,
    () => workflowApi.reject(issue!.id!, { version: issue!.version!, reason: reason.trim() }, files),
    "Issue has been rejected",
  );

  const handleSubmit = () => {
    if (!reason.trim()) return;
    reject.mutate(undefined, {
      onSuccess: () => {
        setReason("");
        setFiles([]);
        onClose();
      },
    });
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[500px] border-l-4 border-l-red-500">
        <DialogHeader>
          <DialogTitle className="text-red-600 flex items-center gap-2">
            <FileWarning className="w-5 h-5" />
            Reject Issue
          </DialogTitle>
          <DialogDescription className="sr-only">Close this issue without action and say why.</DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          <div className="bg-red-50 p-3 rounded text-xs text-red-800">
            You are about to reject the issue <strong>"{issue?.title}"</strong>. This will notify the citizen,
            and the reason is shown publicly.
          </div>

          <div className="grid gap-2">
            <Label htmlFor="reason">
              Reason for Rejection <span className="text-red-500">*</span>
            </Label>
            <Textarea
              id="reason"
              placeholder="e.g. Invalid location, already fixed, outside the city's responsibility..."
              maxLength={2000}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              rows={4}
            />
          </div>

          <div className="grid gap-2">
            <Label htmlFor="evidence">Proof (Optional)</Label>
            <div className="border-2 border-dashed border-border rounded-lg p-4 flex flex-col items-center justify-center text-center cursor-pointer hover:bg-muted/50 transition-colors relative focus-within:ring-2 focus-within:ring-ring">
              <input
                id="evidence"
                type="file"
                multiple
                accept="image/jpeg,image/png,image/webp"
                className="absolute inset-0 opacity-0 cursor-pointer"
                onChange={(e) => setFiles(Array.from(e.target.files ?? []).slice(0, 5))}
              />
              <Upload className="w-6 h-6 text-muted-foreground mb-1" />
              <p className="text-xs text-muted-foreground">Attach photos (reporter and staff only)</p>
              {files.length > 0 && (
                <div className="mt-2 text-xs font-medium text-primary">{files.length} files selected</div>
              )}
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="ghost" onClick={onClose} disabled={reject.isPending}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={!reason.trim() || reject.isPending} variant="destructive">
            {reject.isPending ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : null}
            Confirm Rejection
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Sends a misrouted issue back; the system routes it to another department. */
export function ReturnModal({ issue, isOpen, onClose }: WorkflowModalProps) {
  const [reason, setReason] = useState("");
  const send = useWorkflowAction(
    issue?.id,
    () => workflowApi.returnIssue(issue!.id!, { version: issue!.version!, reason: reason.trim() }),
    "Issue sent back for re-routing",
  );

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[500px] border-l-4 border-l-amber-500">
        <DialogHeader>
          <DialogTitle className="text-amber-700 flex items-center gap-2">
            <Undo2 className="w-5 h-5" />
            Not Our Department
          </DialogTitle>
          <DialogDescription>
            The system will route <strong>"{issue?.title}"</strong> to another department. The deadline does not
            change.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-2 py-2">
          <Label htmlFor="returnReason">
            Why it is not your department's responsibility <span className="text-red-500">*</span>
          </Label>
          <Textarea
            id="returnReason"
            rows={4}
            maxLength={2000}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="e.g. This is a water pipeline, handled by Water Supply."
          />
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose} disabled={send.isPending}>
            Cancel
          </Button>
          <Button
            disabled={!reason.trim() || send.isPending}
            onClick={() => send.mutate(undefined, { onSuccess: () => { setReason(""); onClose(); } })}
          >
            {send.isPending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
            Send Back
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Closes an issue as a duplicate of an earlier report. */
export function DuplicateModal({ issue, isOpen, onClose }: WorkflowModalProps) {
  const [original, setOriginal] = useState("");
  const [remarks, setRemarks] = useState("");
  const similar = useQuery({
    queryKey: ["issue", issue?.id, "similar"],
    queryFn: () => issueApi.similarTo(issue!.id!),
    enabled: isOpen && !!issue?.id,
  });
  const mark = useWorkflowAction(
    issue?.id,
    () =>
      workflowApi.markDuplicate(issue!.id!, {
        version: issue!.version!,
        duplicateOfId: original,
        remarks: remarks.trim() || undefined,
      }),
    "Issue marked as duplicate",
  );

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Copy className="w-5 h-5 text-primary" />
            Mark as Duplicate
          </DialogTitle>
          <DialogDescription>Choose the earlier report of the same problem.</DialogDescription>
        </DialogHeader>
        <div className="space-y-4 py-2">
          {similar.isPending ? (
            <Loader2 className="w-5 h-5 animate-spin text-primary" />
          ) : similar.data && similar.data.length > 0 ? (
            <div className="space-y-2" role="radiogroup" aria-label="Original report">
              {similar.data.map((match) => (
                <button
                  type="button"
                  role="radio"
                  aria-checked={original === match.issue?.id}
                  key={match.issue?.id}
                  onClick={() => setOriginal(match.issue!.id!)}
                  className={cn(
                    "w-full text-left rounded-lg border p-3 text-sm transition-colors",
                    original === match.issue?.id ? "border-primary bg-primary/5" : "hover:bg-muted",
                  )}
                >
                  <p className="font-medium">{match.issue?.title}</p>
                  <p className="text-xs text-muted-foreground">
                    {match.distanceMeters} m away · {match.issue?.address}
                  </p>
                </button>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">No similar open issue found nearby.</p>
          )}
          <div className="grid gap-2">
            <Label htmlFor="originalId">Or paste the original issue's id</Label>
            <Input id="originalId" value={original} onChange={(e) => setOriginal(e.target.value.trim())} />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="duplicateNote">Note (optional)</Label>
            <Textarea id="duplicateNote" rows={2} maxLength={2000} value={remarks} onChange={(e) => setRemarks(e.target.value)} />
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose} disabled={mark.isPending}>
            Cancel
          </Button>
          <Button
            disabled={!original || mark.isPending}
            onClick={() => mark.mutate(undefined, { onSuccess: () => { setOriginal(""); setRemarks(""); onClose(); } })}
          >
            {mark.isPending && <Loader2 className="w-4 h-4 animate-spin mr-2" />}
            Mark Duplicate
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
