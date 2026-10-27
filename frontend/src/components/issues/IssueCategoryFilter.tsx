import { useQuery } from "@tanstack/react-query";
import { referenceApi } from "@/api/endpoints";
import { categoryIcon } from "@/lib/issues";
import { cn } from "@/lib/utils";

interface IssueCategoryFilterProps {
  /** A category code, or "all". */
  selected: string;
  onChange: (category: string) => void;
}

/** Category chips, loaded from the categories the Admin has set up. */
export function IssueCategoryFilter({ selected, onChange }: IssueCategoryFilterProps) {
  const categories = useQuery({ queryKey: ["categories"], queryFn: referenceApi.categories, staleTime: 5 * 60_000 });
  const chips = [
    { id: "all", label: "All Issues", icon: "📋" },
    ...(categories.data ?? [])
      .filter((category) => category.active !== false)
      .map((category) => ({ id: category.code!, label: category.label!, icon: categoryIcon(category.code) })),
  ];

  return (
    <div className="flex flex-wrap gap-2">
      {chips.map((category) => (
        <button
          key={category.id}
          type="button"
          aria-pressed={selected === category.id}
          onClick={() => onChange(category.id)}
          className={cn(
            "inline-flex items-center gap-2 px-4 py-2 rounded-full text-sm font-medium transition-all duration-200",
            selected === category.id
              ? "bg-primary text-primary-foreground shadow-md"
              : "bg-muted text-muted-foreground hover:bg-muted/80"
          )}
        >
          <span>{category.icon}</span>
          <span>{category.label}</span>
        </button>
      ))}
    </div>
  );
}
