import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";

import { ApiError } from "@/api/client";
import { engagementApi } from "@/api/endpoints";
import type { IssueSummary, VoteTally } from "@/api/types";
import { useAuth } from "@/auth/AuthProvider";

type VoteValue = NonNullable<VoteTally["myVote"]>;

interface Tally {
  agree: number;
  disagree: number;
  myVote?: VoteValue;
}

/**
 * Agree/disagree on an issue, as on a social feed. The counts update at once and are then
 * replaced by the server's tally. Only citizens vote, and never on their own report (the API
 * enforces both; the hint explains it before anyone tries).
 */
export function useVote(issue: Pick<IssueSummary, "id" | "agreeCount" | "disagreeCount" | "myVote">) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const server: Tally = { agree: issue.agreeCount ?? 0, disagree: issue.disagreeCount ?? 0, myVote: issue.myVote };
  const [local, setLocal] = useState<{ base: Tally; tally: Tally } | null>(null);
  // A fresher server value (e.g. after a refetch) replaces the local one
  const sameBase = local && JSON.stringify(local.base) === JSON.stringify(server);
  const tally = sameBase ? local.tally : server;

  const mutation = useMutation({
    mutationFn: (value: VoteValue | null) =>
      value ? engagementApi.vote(issue.id!, value) : engagementApi.removeVote(issue.id!),
    onSuccess: (result) => {
      setLocal({
        base: server,
        tally: { agree: result.agree ?? 0, disagree: result.disagree ?? 0, myVote: result.myVote },
      });
      queryClient.setQueryData(["issue", issue.id, "votes"], result);
      queryClient.invalidateQueries({ queryKey: ["issue", issue.id] });
    },
    onError: (error) => {
      setLocal(null);
      toast.error(error instanceof ApiError ? error.message : "Could not save your vote. Please try again.");
    },
  });

  const hint = !user
    ? "Sign in as a citizen to vote"
    : user.role !== "CITIZEN"
      ? "Only citizens can vote"
      : undefined;

  const toggle = (value: VoteValue) => {
    if (hint) {
      toast.info(hint);
      return;
    }
    const next = tally.myVote === value ? null : value;
    const counts = { ...tally };
    if (tally.myVote === "AGREE") counts.agree -= 1;
    if (tally.myVote === "DISAGREE") counts.disagree -= 1;
    if (next === "AGREE") counts.agree += 1;
    if (next === "DISAGREE") counts.disagree += 1;
    setLocal({ base: server, tally: { ...counts, myVote: next ?? undefined } });
    mutation.mutate(next);
  };

  return { ...tally, toggle, pending: mutation.isPending, hint };
}
