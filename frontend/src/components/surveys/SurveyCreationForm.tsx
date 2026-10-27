import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Loader2, Plus, X } from "lucide-react";
import { toast } from "sonner";
import { ApiError } from "@/api/client";
import { surveyApi, type NewSurvey } from "@/api/endpoints";
import type { IssueCluster, QuestionType, Survey } from "@/api/types";

const QUESTION_TYPES: Record<QuestionType, string> = {
  RATING: "Rating 1-5",
  YES_NO: "Yes / No",
  TEXT: "Free text",
};

interface SurveyCreationFormProps {
  cluster: IssueCluster;
  radius: number;
  onCancel: () => void;
  onSuccess: (survey: Survey) => void;
}

/** A survey for the citizens who reported issues inside a cluster. */
export function SurveyCreationForm({ cluster, radius, onCancel, onSuccess }: SurveyCreationFormProps) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [closesOn, setClosesOn] = useState(() => new Date(Date.now() + 7 * 864e5).toISOString().slice(0, 10));
  const [questions, setQuestions] = useState<NewSurvey["questions"]>([{ text: "", type: "RATING" }]);

  const create = useMutation({
    mutationFn: () =>
      surveyApi.create({
        title: title.trim(),
        description: description.trim() || undefined,
        centerLatitude: cluster.centerLatitude!,
        centerLongitude: cluster.centerLongitude!,
        radiusMeters: radius,
        // End of the chosen day, India time
        closesAt: new Date(`${closesOn}T23:59:00+05:30`).toISOString(),
        questions: questions.filter((question) => question.text.trim()).map((q) => ({ ...q, text: q.text.trim() })),
      }),
    onSuccess,
    onError: (error) => {
      const detail =
        error instanceof ApiError && error.fieldErrors.length
          ? error.fieldErrors.map((field) => field.message).join("; ")
          : error instanceof ApiError
            ? error.message
            : "Failed to create survey";
      toast.error(detail);
    },
  });

  const updateQuestion = (index: number, change: Partial<NewSurvey["questions"][number]>) =>
    setQuestions((current) => current.map((question, i) => (i === index ? { ...question, ...change } : question)));

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !questions.some((question) => question.text.trim())) {
      toast.error("Please provide a title and at least one question.");
      return;
    }
    create.mutate();
  };

  return (
    <div className="bg-card border border-border/50 rounded-xl p-6 shadow-sm mt-4 animate-in fade-in slide-in-from-top-4">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h3 className="text-lg font-semibold">Create Community Survey</h3>
          <p className="text-sm text-muted-foreground">
            Targeting {cluster.reporterCount} citizens who reported {cluster.issueCount} issues in this cluster
          </p>
        </div>
        <Button variant="ghost" size="icon" aria-label="Cancel survey" onClick={onCancel}>
          <X className="w-4 h-4" />
        </Button>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="grid gap-4 md:grid-cols-[1fr_12rem]">
          <div className="space-y-2">
            <Label htmlFor="surveyTitle">Survey Title</Label>
            <Input
              id="surveyTitle"
              placeholder="e.g., Waste Management Feedback"
              maxLength={150}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="closesOn">Open until</Label>
            <Input id="closesOn" type="date" value={closesOn} onChange={(e) => setClosesOn(e.target.value)} />
          </div>
        </div>

        <div className="space-y-2">
          <Label htmlFor="surveyDescription">Description (Optional)</Label>
          <Textarea
            id="surveyDescription"
            placeholder="Explain why you are conducting this survey..."
            maxLength={2000}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>

        <div className="space-y-3 pt-2">
          <div className="flex items-center justify-between">
            <Label>Questions (up to 10)</Label>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={questions.length >= 10}
              onClick={() => setQuestions((current) => [...current, { text: "", type: "YES_NO" }])}
              className="h-8 text-xs"
            >
              <Plus className="w-3 h-3 mr-1" /> Add Question
            </Button>
          </div>

          {questions.map((question, idx) => (
            <div key={idx} className="flex gap-2">
              <Input
                aria-label={`Question ${idx + 1}`}
                placeholder={`Question ${idx + 1}`}
                maxLength={300}
                value={question.text}
                onChange={(e) => updateQuestion(idx, { text: e.target.value })}
              />
              <select
                aria-label={`Answer type for question ${idx + 1}`}
                value={question.type}
                onChange={(e) => updateQuestion(idx, { type: e.target.value as QuestionType })}
                className="h-10 rounded-md border border-input bg-background px-3 text-sm"
              >
                {Object.entries(QUESTION_TYPES).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
              {questions.length > 1 && (
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  aria-label={`Remove question ${idx + 1}`}
                  onClick={() => setQuestions((current) => current.filter((_, i) => i !== idx))}
                >
                  <X className="w-4 h-4 text-muted-foreground" />
                </Button>
              )}
            </div>
          ))}
        </div>

        <div className="flex justify-end gap-3 pt-4">
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          <Button type="submit" disabled={create.isPending}>
            {create.isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
            Create & Distribute
          </Button>
        </div>
      </form>
    </div>
  );
}
