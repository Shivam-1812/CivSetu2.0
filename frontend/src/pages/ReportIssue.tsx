import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Footer } from "@/components/layout/Footer";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Upload, X, CheckCircle2, Loader2, AlertTriangle, Sparkles, Building2 } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { VoiceRecorder } from "@/components/VoiceRecorder";
import { LocationPicker } from "@/components/maps";
import { ApiError } from "@/api/client";
import { issueApi, referenceApi } from "@/api/endpoints";
import type { IssueDetail } from "@/api/types";
import { useDebounced } from "@/hooks/useDebounced";
import { categoryIcon, priorityConfig } from "@/lib/issues";
import { cn } from "@/lib/utils";

const MAX_PHOTOS = 5;
const MAX_BYTES = 10 * 1024 * 1024;
const PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"];

const emptyForm = { title: "", description: "", category: "", address: "" };

export function ReportIssue() {
  const { toast } = useToast();
  const [submitted, setSubmitted] = useState<IssueDetail | null>(null);
  const [imageFiles, setImageFiles] = useState<File[]>([]);
  const [voiceFile, setVoiceFile] = useState<Blob | null>(null);
  const [formData, setFormData] = useState(emptyForm);
  const [position, setPosition] = useState<[number, number] | null>(null);

  const categories = useQuery({ queryKey: ["categories"], queryFn: referenceApi.categories, staleTime: 5 * 60_000 });

  // Previews are object URLs, released whenever the selection changes
  const imagePreviews = useMemo(() => imageFiles.map((file) => URL.createObjectURL(file)), [imageFiles]);
  useEffect(() => () => imagePreviews.forEach((url) => URL.revokeObjectURL(url)), [imagePreviews]);

  // Is this already reported nearby? Checked once the details settle
  const duplicateKey = useDebounced(
    position && formData.category && formData.title.trim().length >= 5
      ? JSON.stringify({ latitude: position[0], longitude: position[1], category: formData.category, title: formData.title.trim() })
      : "",
    600,
  );
  const similar = useQuery({
    queryKey: ["similar", duplicateKey],
    queryFn: () => issueApi.similarAt(JSON.parse(duplicateKey)),
    enabled: !!duplicateKey,
  });

  const submit = useMutation({
    mutationFn: () =>
      issueApi.report(
        {
          title: formData.title.trim(),
          description: formData.description.trim(),
          category: formData.category,
          latitude: position![0],
          longitude: position![1],
          address: formData.address.trim(),
        },
        imageFiles,
        voiceFile,
      ),
    onSuccess: (issue) => {
      setSubmitted(issue);
      toast({ title: "Issue Reported Successfully!", description: "It is being routed to the right department." });
    },
    onError: (error) => {
      toast({
        title: "Submission Failed",
        description: error instanceof ApiError ? error.message : "Please try again later.",
        variant: "destructive",
      });
    },
  });

  const fieldError = (field: string) =>
    submit.error instanceof ApiError ? submit.error.fieldMessage(field) : undefined;

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    e.target.value = "";
    const accepted = files.filter((file) => PHOTO_TYPES.includes(file.type) && file.size <= MAX_BYTES);
    if (accepted.length < files.length) {
      toast({
        title: "Some files were skipped",
        description: "Photos must be JPEG, PNG or WebP and at most 10 MB each.",
        variant: "destructive",
      });
    }
    setImageFiles((prev) => [...prev, ...accepted].slice(0, MAX_PHOTOS));
  };

  const removeImage = (index: number) => {
    setImageFiles((prev) => prev.filter((_, i) => i !== index));
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!formData.title.trim() || !formData.category || !formData.address.trim() || !formData.description.trim()) {
      toast({
        title: "Missing Information",
        description: "Please fill in all required fields.",
        variant: "destructive",
      });
      return;
    }
    if (!position) {
      toast({
        title: "Location needed",
        description: "Tap the map where the problem is, or use your location.",
        variant: "destructive",
      });
      return;
    }
    submit.mutate();
  };

  const reset = () => {
    setSubmitted(null);
    setFormData(emptyForm);
    setPosition(null);
    setImageFiles([]);
    setVoiceFile(null);
    submit.reset();
  };

  if (submitted) {
    return <ReportSuccess issue={submitted} onReportAnother={reset} />;
  }

  return (
    <div className="min-h-screen bg-background">
      <main className="pt-8 pb-16">
        <div className="container mx-auto px-4">
          <div className="max-w-2xl mx-auto">
            {/* Header */}
            <div className="text-center mb-8">
              <span className="inline-block px-4 py-1 bg-primary/10 text-primary rounded-full text-sm font-medium mb-4">
                Report Issue
              </span>
              <h1 className="font-display text-3xl md:text-4xl font-bold mb-4">Report a Civic Issue</h1>
              <p className="text-muted-foreground">
                Help improve your community by reporting problems you encounter.
              </p>
            </div>

            {/* Form */}
            <form onSubmit={handleSubmit} className="space-y-6" noValidate>
              {/* Issue Details */}
              <div className="bg-card rounded-2xl border border-border/50 p-6 space-y-6">
                <div>
                  <Label htmlFor="title" className="text-base font-semibold mb-2 block">
                    Issue Title <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id="title"
                    placeholder="Brief description of the issue"
                    maxLength={150}
                    value={formData.title}
                    onChange={(e) => setFormData((prev) => ({ ...prev, title: e.target.value }))}
                    aria-invalid={!!fieldError("title")}
                    className="h-12"
                  />
                  {fieldError("title") && <p className="mt-1 text-xs text-destructive">{fieldError("title")}</p>}
                </div>

                <div className="grid sm:grid-cols-2 gap-4">
                  <div>
                    <Label htmlFor="category" className="text-base font-semibold mb-2 block">
                      Category <span className="text-destructive">*</span>
                    </Label>
                    <Select
                      value={formData.category}
                      onValueChange={(value) => setFormData((prev) => ({ ...prev, category: value }))}
                    >
                      <SelectTrigger id="category" className="h-12" aria-label="Category">
                        <SelectValue placeholder="Select category" />
                      </SelectTrigger>
                      <SelectContent>
                        {categories.data
                          ?.filter((category) => category.active !== false)
                          .map((category) => (
                            <SelectItem key={category.code} value={category.code!}>
                              <span className="flex items-center gap-2">
                                <span>{categoryIcon(category.code)}</span>
                                <span>{category.label}</span>
                              </span>
                            </SelectItem>
                          ))}
                      </SelectContent>
                    </Select>
                    <p className="mt-1 text-xs text-muted-foreground">
                      A hint only: the system picks the department and priority.
                    </p>
                  </div>
                </div>

                <div>
                  <Label htmlFor="description" className="text-base font-semibold mb-2 block">
                    Description <span className="text-destructive">*</span>
                  </Label>
                  <Textarea
                    id="description"
                    placeholder="Provide more details about the issue..."
                    maxLength={2000}
                    value={formData.description}
                    onChange={(e) => setFormData((prev) => ({ ...prev, description: e.target.value }))}
                    aria-invalid={!!fieldError("description")}
                    rows={4}
                  />
                  {fieldError("description") && (
                    <p className="mt-1 text-xs text-destructive">{fieldError("description")}</p>
                  )}
                </div>
              </div>

              {/* Media Upload */}
              <div className="bg-card rounded-2xl border border-border/50 p-6">
                <Label htmlFor="photos" className="text-base font-semibold mb-4 block">
                  Photos (optional)
                </Label>
                <div className="grid grid-cols-3 sm:grid-cols-5 gap-3">
                  {imagePreviews.map((img, index) => (
                    <div key={img} className="relative aspect-square rounded-xl overflow-hidden group">
                      <img src={img} alt={`Selected photo ${index + 1}`} className="w-full h-full object-cover" />
                      <button
                        type="button"
                        aria-label={`Remove photo ${index + 1}`}
                        onClick={() => removeImage(index)}
                        className="absolute top-1 right-1 w-6 h-6 bg-destructive rounded-full flex items-center justify-center opacity-80 group-hover:opacity-100 transition-opacity"
                      >
                        <X className="w-4 h-4 text-destructive-foreground" />
                      </button>
                    </div>
                  ))}
                  {imageFiles.length < MAX_PHOTOS && (
                    <label className="aspect-square rounded-xl border-2 border-dashed border-border hover:border-primary/50 flex flex-col items-center justify-center cursor-pointer transition-colors focus-within:ring-2 focus-within:ring-ring">
                      <Upload className="w-6 h-6 text-muted-foreground mb-1" />
                      <span className="text-xs text-muted-foreground">Upload</span>
                      <input
                        id="photos"
                        type="file"
                        accept={PHOTO_TYPES.join(",")}
                        multiple
                        onChange={handleImageUpload}
                        className="sr-only"
                      />
                    </label>
                  )}
                </div>
                <p className="text-xs text-muted-foreground mt-3">
                  Upload up to 5 photos (JPEG, PNG or WebP) - max 10MB each. Location data is removed from photos.
                </p>
              </div>

              {/* Voice Input */}
              <div className="bg-card rounded-2xl border border-border/50 p-6">
                <Label className="text-base font-semibold mb-4 block">Voice Description (Optional)</Label>
                <VoiceRecorder
                  onRecordingComplete={(blob) => {
                    setVoiceFile(blob);
                    toast({
                      title: "Voice Recorded",
                      description: "Your voice description has been captured",
                    });
                  }}
                  onTranscriptUpdate={(text) => {
                    setFormData((prev) => ({ ...prev, description: text }));
                  }}
                />
                {voiceFile && (
                  <div className="text-sm text-success mt-3 flex items-center gap-2">
                    <CheckCircle2 className="w-4 h-4" />
                    Voice recording ready to submit
                    <button
                      type="button"
                      onClick={() => setVoiceFile(null)}
                      className="ml-auto text-xs text-muted-foreground underline"
                    >
                      Remove
                    </button>
                  </div>
                )}
              </div>

              {/* Location */}
              <div className="bg-card rounded-2xl border border-border/50 p-6 space-y-4">
                <div>
                  <Label htmlFor="address" className="text-base font-semibold mb-2 block">
                    Address / Landmark <span className="text-destructive">*</span>
                  </Label>
                  <Input
                    id="address"
                    placeholder="Enter landmark (e.g. Near Central Park)"
                    maxLength={300}
                    value={formData.address}
                    onChange={(e) => setFormData((prev) => ({ ...prev, address: e.target.value }))}
                    className="h-12"
                  />
                </div>
                <LocationPicker value={position} onChange={setPosition} />
              </div>

              {/* Possible duplicates */}
              {similar.data && similar.data.length > 0 && (
                <div className="bg-warning/10 border border-warning/30 rounded-2xl p-5">
                  <div className="flex items-start gap-3">
                    <AlertTriangle className="w-5 h-5 text-warning shrink-0 mt-0.5" />
                    <div className="space-y-2">
                      <p className="font-semibold text-sm">This may already be reported nearby</p>
                      <p className="text-xs text-muted-foreground">
                        If it is the same problem, agree with the existing report instead; it raises its priority faster.
                      </p>
                      <ul className="space-y-1">
                        {similar.data.map((match) => (
                          <li key={match.issue?.id} className="text-sm">
                            <Link
                              to={`/issues/${match.issue?.id}`}
                              target="_blank"
                              className="font-medium text-primary hover:underline"
                            >
                              {match.issue?.title}
                            </Link>{" "}
                            <span className="text-xs text-muted-foreground">({match.distanceMeters} m away)</span>
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>
                </div>
              )}

              {/* Submit */}
              <Button type="submit" variant="hero" size="xl" className="w-full" disabled={submit.isPending}>
                {submit.isPending ? (
                  <>
                    <Loader2 className="w-5 h-5 animate-spin" />
                    Submitting...
                  </>
                ) : (
                  <>Submit Issue Report</>
                )}
              </Button>
            </form>
          </div>
        </div>
      </main>

      <Footer />
    </div>
  );
}

/** After submitting: waits for the automatic routing and shows the department and priority it chose. */
function ReportSuccess({ issue, onReportAnother }: { issue: IssueDetail; onReportAnother: () => void }) {
  const [startedAt] = useState(() => Date.now());
  const live = useQuery({
    queryKey: ["issue", issue.id],
    queryFn: () => issueApi.get(issue.id!),
    initialData: issue,
    // Routing usually takes a few seconds; stop asking after a minute
    refetchInterval: (query) =>
      query.state.data?.status === "REPORTED" && Date.now() - startedAt < 60_000 ? 2000 : false,
  });
  const data = live.data;
  const routed = data.status !== "REPORTED";
  const priority = priorityConfig[data.priority ?? "MEDIUM"];

  return (
    <div className="min-h-[calc(100vh-4rem)] bg-background flex items-center justify-center p-4">
      <div className="bg-card w-full max-w-md p-8 rounded-2xl shadow-xl text-center border border-border/50">
        <div className="w-16 h-16 bg-success/10 rounded-full flex items-center justify-center mx-auto mb-6">
          <CheckCircle2 className="w-8 h-8 text-success" />
        </div>
        <h2 className="font-display text-2xl font-bold mb-2">Issue Reported!</h2>
        <p className="text-muted-foreground mb-6">
          Thank you for helping improve our city. Your report has been submitted.
        </p>

        <div className="bg-muted/50 rounded-xl p-4 mb-8 text-left space-y-3">
          {routed ? (
            <>
              <div className="flex justify-between items-center gap-3">
                <span className="text-sm font-medium flex items-center gap-1.5">
                  <Sparkles className="w-4 h-4 text-primary" />
                  {data.prioritySource === "AI" ? "AI Priority Assigned" : "Priority Assigned"}
                </span>
                <span className={cn("px-2 py-0.5 rounded-full text-xs font-bold", priority.class)}>
                  {priority.label.toUpperCase()}
                </span>
              </div>
              <p className="text-sm flex items-center gap-1.5">
                <Building2 className="w-4 h-4 text-muted-foreground" />
                Routed to <strong>{data.departmentName}</strong>
              </p>
              {data.priorityReason && <p className="text-xs text-muted-foreground">{data.priorityReason}</p>}
            </>
          ) : (
            <p className="text-sm text-muted-foreground flex items-center gap-2">
              <Loader2 className="w-4 h-4 animate-spin text-primary" />
              Finding the right department and priority…
            </p>
          )}
        </div>

        <div className="space-y-3">
          <Link to={`/issues/${data.id}`} className="block">
            <Button variant="hero" className="w-full">
              View Your Issue
            </Button>
          </Link>
          <Button variant="outline" className="w-full" onClick={onReportAnother}>
            Report Another Issue
          </Button>
          <Link to="/dashboard" className="block">
            <Button variant="ghost" className="w-full">
              Go to Dashboard
            </Button>
          </Link>
        </div>
      </div>
    </div>
  );
}
