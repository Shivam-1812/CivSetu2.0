import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Loader2, ClipboardList, CheckCircle2 } from "lucide-react";
import { toast } from "sonner";
import { ApiError } from "@/api/client";
import { surveyApi, type SurveyAnswer } from "@/api/endpoints";
import type { Survey } from "@/api/types";
import { formatDate } from "@/lib/format";
import { cn } from "@/lib/utils";

/** Citizens: surveys about areas where they reported an issue. */
export function SurveyList() {
  const surveys = useQuery({ queryKey: ["surveys", "mine"], queryFn: surveyApi.mine });
  const [selected, setSelected] = useState<string | null>(null);

  if (surveys.isPending) {
    return (
      <div className="flex justify-center py-12">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  if (!surveys.data || surveys.data.length === 0) {
    return (
      <div className="text-center py-12">
        <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-muted flex items-center justify-center">
          <ClipboardList className="w-8 h-8 text-muted-foreground" />
        </div>
        <h3 className="font-display font-semibold text-lg mb-2">No surveys right now</h3>
        <p className="text-muted-foreground">
          When officials survey an area where you reported an issue, it appears here.
        </p>
      </div>
    );
  }

  return (
    <div className="mb-8 space-y-4">
      <h3 className="text-xl font-display font-bold flex items-center gap-2">
        <ClipboardList className="w-5 h-5 text-primary" />
        Active Community Surveys
      </h3>
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {surveys.data.map((survey) => {
          const open = survey.status === "OPEN" && new Date(survey.closesAt!) > new Date();
          return (
            <Card key={survey.id} className="border-primary/20 bg-primary/5 flex flex-col">
              <CardHeader className="flex-1">
                <CardTitle className="text-lg">{survey.title}</CardTitle>
                <CardDescription className="line-clamp-2">{survey.description}</CardDescription>
                <p className="text-xs text-muted-foreground">
                  {survey.answeredByMe
                    ? "You answered this survey. Thank you!"
                    : open
                      ? `Open until ${formatDate(survey.closesAt)}`
                      : "Closed"}
                </p>
              </CardHeader>
              <CardFooter>
                {survey.answeredByMe ? (
                  <Button variant="outline" disabled className="w-full gap-2">
                    <CheckCircle2 className="w-4 h-4" /> Answered
                  </Button>
                ) : (
                  <Button onClick={() => setSelected(survey.id!)} disabled={!open} className="w-full">
                    Participate Now
                  </Button>
                )}
              </CardFooter>
            </Card>
          );
        })}
      </div>

      <SurveyDialog surveyId={selected} onClose={() => setSelected(null)} />
    </div>
  );
}

/** The questionnaire in a dialog; also used by the /surveys/:id link in notifications. */
export function SurveyDialog({ surveyId, onClose }: { surveyId: string | null; onClose: () => void }) {
  const survey = useQuery({
    queryKey: ["survey", surveyId],
    queryFn: () => surveyApi.get(surveyId!),
    enabled: !!surveyId,
  });

  return (
    <Dialog open={!!surveyId} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-w-lg max-h-[80vh] overflow-y-auto">
        {survey.data ? (
          <SurveyForm key={survey.data.id} survey={survey.data} onDone={onClose} />
        ) : (
          <>
            <DialogTitle className="sr-only">Survey</DialogTitle>
            <DialogDescription className="sr-only">Loading the survey questions.</DialogDescription>
            <div className="flex justify-center py-8">
              <Loader2 className="w-6 h-6 animate-spin text-primary" />
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

function SurveyForm({ survey, onDone }: { survey: Survey; onDone: () => void }) {
  const queryClient = useQueryClient();
  const [answers, setAnswers] = useState<Record<string, SurveyAnswer>>({});
  const set = (questionId: string, value: Partial<SurveyAnswer>) =>
    setAnswers((current) => ({ ...current, [questionId]: { ...current[questionId], questionId, ...value } }));

  const submit = useMutation({
    mutationFn: () => surveyApi.respond(survey.id!, Object.values(answers)),
    onSuccess: () => {
      toast.success("Survey submitted. Thank you for your feedback!");
      queryClient.invalidateQueries({ queryKey: ["surveys"] });
      queryClient.invalidateQueries({ queryKey: ["survey", survey.id] });
      onDone();
    },
    onError: (error) => toast.error(error instanceof ApiError ? error.message : "Failed to submit survey."),
  });

  const choice = (selected: boolean) =>
    cn(
      "flex cursor-pointer items-center justify-center rounded-lg border px-3 py-2 text-sm transition-colors",
      selected ? "border-primary bg-primary/10 text-primary font-semibold" : "border-input hover:bg-muted",
    );

  return (
    <>
      <DialogHeader>
        <DialogTitle>{survey.title}</DialogTitle>
        <DialogDescription>{survey.description || "Your answers help the department plan the work."}</DialogDescription>
      </DialogHeader>

      <div className="space-y-5 py-4">
        {survey.questions?.map((question) => (
          <fieldset key={question.id} className="space-y-2">
            <legend className="text-sm font-medium mb-2">
              {question.position}. {question.text}
            </legend>
            {question.type === "RATING" && (
              <div className="flex flex-wrap items-center gap-2">
                {[1, 2, 3, 4, 5].map((rating) => (
                  <label key={rating} className={choice(answers[question.id!]?.rating === rating)}>
                    <input
                      type="radio"
                      className="sr-only"
                      name={question.id}
                      checked={answers[question.id!]?.rating === rating}
                      onChange={() => set(question.id!, { rating })}
                    />
                    {rating}
                  </label>
                ))}
                <span className="text-xs text-muted-foreground">1 = very poor, 5 = very good</span>
              </div>
            )}
            {question.type === "YES_NO" && (
              <div className="flex gap-2">
                {[true, false].map((value) => (
                  <label key={String(value)} className={choice(answers[question.id!]?.yesNo === value)}>
                    <input
                      type="radio"
                      className="sr-only"
                      name={question.id}
                      checked={answers[question.id!]?.yesNo === value}
                      onChange={() => set(question.id!, { yesNo: value })}
                    />
                    {value ? "Yes" : "No"}
                  </label>
                ))}
              </div>
            )}
            {question.type === "TEXT" && (
              <>
                <Label htmlFor={`answer-${question.id}`} className="sr-only">
                  Your answer
                </Label>
                <Textarea
                  id={`answer-${question.id}`}
                  rows={3}
                  maxLength={1000}
                  placeholder="Your answer (optional)..."
                  value={answers[question.id!]?.text ?? ""}
                  onChange={(e) => set(question.id!, { text: e.target.value })}
                />
              </>
            )}
          </fieldset>
        ))}
      </div>

      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onDone}>
          Cancel
        </Button>
        <Button onClick={() => submit.mutate()} disabled={submit.isPending}>
          {submit.isPending && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
          Submit Response
        </Button>
      </div>
    </>
  );
}
