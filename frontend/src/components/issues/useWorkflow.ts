import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { ApiError } from "@/api/client";
import type { IssueDetail } from "@/api/types";

/**
 * Runs one workflow action (plan, resolve, reject, return, duplicate) and refreshes every list and
 * view that shows the issue. A 409 STALE_ISSUE means someone else acted first: the issue is
 * reloaded and the person is told to check it again.
 */
export function useWorkflowAction<T>(issueId: string | undefined, action: (input: T) => Promise<IssueDetail>, done: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: action,
    onSuccess: (updated) => {
      queryClient.setQueryData(["issue", issueId], updated);
      queryClient.invalidateQueries({ queryKey: ["issue", issueId] });
      queryClient.invalidateQueries({ queryKey: ["issues"] });
      queryClient.invalidateQueries({ queryKey: ["analytics"] });
      toast.success(done);
    },
    onError: (error) => {
      if (error instanceof ApiError && error.code === "STALE_ISSUE") {
        queryClient.invalidateQueries({ queryKey: ["issue", issueId] });
        toast.error("Someone else updated this issue a moment ago. It has been reloaded; please check it and try again.");
      } else {
        toast.error(error instanceof ApiError ? error.message : "The action could not be saved.");
      }
    },
  });
}
