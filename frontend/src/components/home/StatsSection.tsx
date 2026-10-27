import { useQuery } from "@tanstack/react-query";
import { TrendingUp, Building2, CheckCircle2, MapPin } from "lucide-react";
import { issueApi, referenceApi } from "@/api/endpoints";

/** Live public figures: how many issues were reported and fixed, and how many departments take part. */
function useLiveStats() {
  const count = (status?: Parameters<typeof issueApi.search>[0]["status"]) =>
    issueApi.search({ status }, 0, 1).then((page) => page.totalElements);
  const total = useQuery({ queryKey: ["stats", "total"], queryFn: () => count() });
  const resolved = useQuery({ queryKey: ["stats", "resolved"], queryFn: () => count(["RESOLVED"]) });
  const departments = useQuery({ queryKey: ["departments"], queryFn: referenceApi.departments });
  return { total: total.data, resolved: resolved.data, departments: departments.data?.length };
}

const number = new Intl.NumberFormat("en-IN");
const show = (value?: number) => (value === undefined ? "—" : number.format(value));

export function StatsSection() {
  const live = useLiveStats();
  const rate =
    live.total && live.resolved !== undefined ? `${Math.round((live.resolved / live.total) * 100)}%` : "—";

  const stats = [
    {
      icon: MapPin,
      value: show(live.total),
      label: "Issues Reported",
      description: "By citizens like you",
    },
    {
      icon: CheckCircle2,
      value: show(live.resolved),
      label: "Issues Resolved",
      description: "Real impact, real change",
    },
    {
      icon: TrendingUp,
      value: rate,
      label: "Resolution Rate",
      description: "Of all reports so far",
    },
    {
      icon: Building2,
      value: show(live.departments),
      label: "Departments",
      description: "Receiving reports directly",
    },
  ];

  return (
    <section className="py-20 hero-gradient relative overflow-hidden">
      {/* Decorative Elements */}
      <div className="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHZpZXdCb3g9IjAgMCA2MCA2MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48ZyBmaWxsPSJub25lIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiPjxnIGZpbGw9IiNmZmZmZmYiIGZpbGwtb3BhY2l0eT0iMC4xIj48cGF0aCBkPSJNMzYgMzRjMC0yLjIxLTEuNzktNC00LTRzLTQgMS43OS00IDQgMS43OSA0IDQgNCA0LTEuNzkgNC00eiIvPjwvZz48L2c+PC9zdmc+')] opacity-30" />

      <div className="container mx-auto px-4 relative z-10">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-8">
          {stats.map((stat) => (
            <div key={stat.label} className="text-center group">
              <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-primary-foreground/10 backdrop-blur-sm flex items-center justify-center group-hover:scale-110 transition-transform duration-300">
                <stat.icon className="w-8 h-8 text-primary-foreground" />
              </div>
              <div className="font-display text-4xl md:text-5xl font-bold text-primary-foreground mb-1 tabular-nums">
                {stat.value}
              </div>
              <div className="text-primary-foreground/90 font-semibold mb-1">{stat.label}</div>
              <div className="text-primary-foreground/60 text-sm">{stat.description}</div>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
