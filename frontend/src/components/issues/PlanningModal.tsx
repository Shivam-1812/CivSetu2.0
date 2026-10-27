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
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Loader2, Upload, AlertTriangle, FileText, Undo2 } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { workflowApi } from "@/api/endpoints";
import type { IssueDetail } from "@/api/types";
import { priorityConfig } from "@/lib/issues";
import { cn } from "@/lib/utils";
import { useWorkflowAction } from "./useWorkflow";

interface PlanningModalProps {
  issue: IssueDetail | null;
  isOpen: boolean;
  onClose: () => void;
  onReject: () => void;
  onReturn: () => void;
}

export function PlanningModal({ issue, isOpen, onClose, onReject, onReturn }: PlanningModalProps) {
  const [formData, setFormData] = useState({
    actionTaken: "",
    staffAllocated: "",
    resourcesUsed: "",
  });
  const [files, setFiles] = useState<File[]>([]);
  const plan = useWorkflowAction(
    issue?.id,
    () =>
      workflowApi.plan(
        issue!.id!,
        {
          version: issue!.version!,
          actionTaken: formData.actionTaken.trim(),
          staffAllocated: formData.staffAllocated.trim() || undefined,
          resourcesUsed: formData.resourcesUsed.trim() || undefined,
        },
        files,
      ),
    "Issue status updated to In Progress",
  );

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setFiles(Array.from(e.target.files ?? []).slice(0, 5));
  };

  const handleSubmit = () => {
    if (!formData.actionTaken.trim()) return;
    plan.mutate(undefined, {
      onSuccess: () => {
        setFormData({ actionTaken: "", staffAllocated: "", resourcesUsed: "" });
        setFiles([]);
        onClose();
      },
    });
  };

  const priority = priorityConfig[issue?.priority ?? "MEDIUM"];

  return (
    <Dialog open={isOpen} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex justify-between items-center">
            <span>Plan Resolution</span>
            <Badge variant="outline" className={cn("mr-8", priority.class)}>
              {priority.label} Priority
            </Badge>
          </DialogTitle>
          <DialogDescription className="sr-only">Describe how your department will fix this issue.</DialogDescription>
        </DialogHeader>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 py-4">
          <div className="space-y-4">
            <div className="bg-muted p-4 rounded-lg">
              <h4 className="font-semibold text-sm mb-2">{issue?.title}</h4>
              <p className="text-xs text-muted-foreground line-clamp-3">{issue?.description}</p>
              <div className="mt-2 text-xs flex gap-2">
                <span className="font-medium">Loc:</span> {issue?.address}
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="actionTaken">What will be done</Label>
              <Textarea
                id="actionTaken"
                placeholder="Detailed plan to resolve this issue..."
                maxLength={2000}
                value={formData.actionTaken}
                onChange={(e) => setFormData({ ...formData, actionTaken: e.target.value })}
                className="h-32"
              />
            </div>

            <div className="bg-red-50 p-4 rounded-lg border border-red-100 mt-4">
              <div className="flex items-start gap-3">
                <AlertTriangle className="w-5 h-5 text-red-600 mt-0.5" />
                <div>
                  <h5 className="text-sm font-semibold text-red-900">Valid Issue?</h5>
                  <p className="text-xs text-red-700 mt-1 mb-3">
                    If this issue is invalid you can reject it. If another department is responsible, send it
                    back and the system routes it again.
                  </p>
                  <div className="flex flex-wrap gap-2">
                    <Button variant="destructive" size="sm" onClick={onReject}>
                      Reject Issue
                    </Button>
                    <Button variant="outline" size="sm" onClick={onReturn} className="gap-1.5">
                      <Undo2 className="w-3.5 h-3.5" /> Not our department
                    </Button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div className="space-y-4">
            <div className="grid gap-2">
              <Label htmlFor="staff">Staff Allocated</Label>
              <Input
                id="staff"
                placeholder="e.g. Team A, 2 electricians"
                maxLength={300}
                value={formData.staffAllocated}
                onChange={(e) => setFormData({ ...formData, staffAllocated: e.target.value })}
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="resources">Resources/Budget</Label>
              <Input
                id="resources"
                placeholder="e.g. ₹5000, 2 trucks"
                maxLength={300}
                value={formData.resourcesUsed}
                onChange={(e) => setFormData({ ...formData, resourcesUsed: e.target.value })}
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="planDocs">Supporting Documents</Label>
              <div className="border-2 border-dashed border-border rounded-lg p-8 flex flex-col items-center justify-center text-center cursor-pointer hover:bg-muted/50 transition-colors relative h-32 focus-within:ring-2 focus-within:ring-ring">
                <input
                  id="planDocs"
                  type="file"
                  multiple
                  accept="image/jpeg,image/png,image/webp,application/pdf"
                  className="absolute inset-0 opacity-0 cursor-pointer"
                  onChange={handleFileChange}
                />
                <Upload className="w-6 h-6 text-muted-foreground mb-2" />
                <p className="text-sm text-muted-foreground">Upload budget, maps, or docs (images or PDF)</p>
                {files.length > 0 && (
                  <div className="mt-2 text-xs font-medium text-primary">{files.length} files attached</div>
                )}
              </div>

              {files.length > 0 && (
                <div className="flex flex-col gap-1 mt-1">
                  {files.map((f) => (
                    <div key={f.name} className="text-xs flex items-center gap-2 text-muted-foreground">
                      <FileText className="w-3 h-3" /> {f.name}
                    </div>
                  ))}
                </div>
              )}
              <p className="text-xs text-muted-foreground">Visible to the reporter and staff only.</p>
            </div>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onClose} disabled={plan.isPending}>
            Cancel
          </Button>
          <Button
            onClick={handleSubmit}
            disabled={!formData.actionTaken.trim() || plan.isPending}
            className="bg-blue-600 hover:bg-blue-700 text-white min-w-[140px]"
          >
            {plan.isPending ? <Loader2 className="w-4 h-4 animate-spin mr-2" /> : null}
            Allocate & Start
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
