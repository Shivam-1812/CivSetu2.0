import { useState } from "react";
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
import { Label } from "@/components/ui/label";
import { Loader2, Upload, FileCheck, CheckCircle2 } from "lucide-react";
import { workflowApi } from "@/api/endpoints";
import type { IssueDetail } from "@/api/types";
import { useWorkflowAction } from "./useWorkflow";

interface ResolutionModalProps {
  issue: IssueDetail | null;
  isOpen: boolean;
  onClose: () => void;
}

export function ResolutionModal({ issue, isOpen, onClose }: ResolutionModalProps) {
  const [finalRemarks, setFinalRemarks] = useState("");
  const [files, setFiles] = useState<File[]>([]);
  const resolve = useWorkflowAction(
    issue?.id,
    () => workflowApi.resolve(issue!.id!, { version: issue!.version!, remarks: finalRemarks.trim() }, files),
    "Issue has been marked as resolved",
  );

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFiles(Array.from(e.target.files ?? []).slice(0, 5));
  };

  const handleSubmit = () => {
    resolve.mutate(undefined, {
      onSuccess: () => {
        setFinalRemarks("");
        setFiles([]);
        onClose();
      },
    });
  };

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <CheckCircle2 className="w-5 h-5 text-green-600" />
            Finalize Resolution
          </DialogTitle>
          <DialogDescription className="sr-only">Describe the fix and attach proof photos.</DialogDescription>
        </DialogHeader>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8 py-4">
          <div className="space-y-4">
            <div className="bg-green-50/50 p-6 rounded-xl border border-green-100 h-full">
              <h4 className="font-semibold text-green-900 mb-2">Resolution Checklist</h4>
              <ul className="space-y-3 text-sm text-green-800">
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-green-500" />
                  Verify all work is completed as per plan.
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-green-500" />
                  Ensure site is clean and safe.
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-green-500" />
                  Take clear photos of the result.
                </li>
                <li className="flex items-center gap-2">
                  <span className="w-1.5 h-1.5 rounded-full bg-green-500" />
                  Proof photos are shown publicly.
                </li>
              </ul>
            </div>
          </div>

          <div className="space-y-6">
            <div className="grid gap-2">
              <Label htmlFor="remarks">What was done</Label>
              <Textarea
                id="remarks"
                placeholder="Summarize the work done..."
                maxLength={2000}
                value={finalRemarks}
                onChange={(e) => setFinalRemarks(e.target.value)}
                className="min-h-[120px]"
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="proofs">Proof photos (required)</Label>
              <div className="border-2 border-dashed border-border rounded-lg p-6 flex flex-col items-center justify-center text-center cursor-pointer hover:bg-muted/50 transition-colors relative h-32 focus-within:ring-2 focus-within:ring-ring">
                <input
                  id="proofs"
                  type="file"
                  multiple
                  accept="image/jpeg,image/png,image/webp"
                  className="absolute inset-0 opacity-0 cursor-pointer"
                  onChange={handleFileChange}
                />
                <Upload className="w-8 h-8 text-muted-foreground mb-2" />
                <p className="text-sm text-muted-foreground">Upload photos of the fix (up to 5)</p>
                {files.length > 0 && (
                  <div className="mt-2 text-xs font-medium text-primary">{files.length} files selected</div>
                )}
              </div>
              {files.length > 0 && (
                <div className="flex flex-wrap gap-2 mt-2">
                  {files.map((file) => (
                    <div key={file.name} className="text-xs bg-muted px-2 py-1 rounded flex items-center gap-1">
                      <FileCheck className="w-3 h-3" />
                      <span className="truncate max-w-[150px]">{file.name}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={resolve.isPending}>
            Cancel
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={resolve.isPending || !finalRemarks.trim() || files.length === 0}
            className="bg-green-600 hover:bg-green-700 text-white min-w-[150px]"
          >
            {resolve.isPending ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : null}
            Mark Resolved
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
