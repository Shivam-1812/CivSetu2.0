import { Link, useNavigate, useParams } from "react-router";
import { ArrowLeft } from "lucide-react";
import { IssueDetailView } from "@/components/issues/IssueDetailModal";

/** One issue on its own page (linked from notifications and dashboards). */
export function IssuePage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  return (
    <main className="container mx-auto px-4 py-8">
      <button
        type="button"
        onClick={() => (window.history.length > 1 ? navigate(-1) : navigate("/map"))}
        className="mb-4 inline-flex items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="w-4 h-4" /> Back
      </button>
      {id ? (
        <IssueDetailView key={id} issueId={id} layout="page" />
      ) : (
        <Link to="/map" className="text-primary underline">
          Browse issues
        </Link>
      )}
    </main>
  );
}
