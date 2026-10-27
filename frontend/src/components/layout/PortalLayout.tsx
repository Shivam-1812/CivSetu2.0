import { Link, Outlet, useLocation } from "react-router";
import { useQuery } from "@tanstack/react-query";
import {
  BarChart3,
  FileText,
  LayoutDashboard,
  MapPin,
  ScrollText,
  Settings2,
  Users,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { useAuth } from "@/auth/AuthProvider";
import { referenceApi } from "@/api/endpoints";

type Item = { icon: typeof LayoutDashboard; label: string; href: string };

const AUTHORITY_ITEMS: Item[] = [
  { icon: LayoutDashboard, label: "Dashboard", href: "/authority" },
  { icon: FileText, label: "Manage Issues", href: "/authority/issues" },
  { icon: MapPin, label: "Map & Surveys", href: "/authority/map" },
  { icon: BarChart3, label: "Analytics", href: "/authority/analytics" },
];

const ADMIN_ITEMS: Item[] = [
  { icon: LayoutDashboard, label: "Dashboard", href: "/admin" },
  { icon: FileText, label: "All Issues", href: "/admin/issues" },
  { icon: MapPin, label: "Map & Surveys", href: "/admin/map" },
  { icon: BarChart3, label: "Analytics", href: "/admin/analytics" },
  { icon: Users, label: "Users", href: "/admin/users" },
  { icon: Settings2, label: "Departments", href: "/admin/routing" },
  { icon: ScrollText, label: "Audit Log", href: "/admin/audit" },
];

/**
 * The "Municipal Control Center" shell from the original admin pages: a fixed sidebar beside a
 * header that names the department. Used by department officials (Authorities) and Admins.
 */
export function PortalLayout({ portal }: { portal: "authority" | "admin" }) {
  const location = useLocation();
  const { user } = useAuth();
  const departments = useQuery({
    queryKey: ["departments"],
    queryFn: referenceApi.departments,
    enabled: portal === "authority",
  });
  const departmentName = departments.data?.find((department) => department.id === user?.departmentId)?.name;
  const items = portal === "admin" ? ADMIN_ITEMS : AUTHORITY_ITEMS;


  const isActive = (href: string) =>
    location.pathname === href || (href !== `/${portal}` && location.pathname.startsWith(`${href}/`));

  return (
    <div className="min-h-[calc(100vh-4rem)] bg-background">
      <div className="flex">
        {/* Sidebar - Full Height minus navbar */}
        <aside className="hidden lg:flex w-72 flex-col fixed left-0 top-16 bottom-0 bg-card border-r border-border/50 z-30">
          <div className="p-6 border-b border-border/50">
            <div className="flex items-center gap-2 px-2">
              <div className="w-8 h-8 rounded-lg hero-gradient flex items-center justify-center shadow-md">
                <MapPin className="w-5 h-5 text-primary-foreground" />
              </div>
              <div className="flex flex-col">
                <span className="font-display font-bold text-lg text-foreground leading-none">
                  Civ<span className="text-primary">Setu</span>
                </span>
                <span className="text-[10px] text-muted-foreground font-medium uppercase tracking-wider mt-0.5">
                  {portal === "admin" ? "Admin Portal" : "Department Portal"}
                </span>
              </div>
            </div>
          </div>

          {/* Navigation */}
          <nav aria-label="Portal" className="flex-1 overflow-y-auto py-6 px-4 space-y-2">
            {items.map((item) => {
              const active = isActive(item.href);
              return (
                <Link
                  key={item.label}
                  to={item.href}
                  aria-current={active ? "page" : undefined}
                  className={cn(
                    "w-full flex items-center gap-4 px-5 py-3.5 rounded-xl text-base font-medium transition-all group",
                    active
                      ? "bg-primary/10 text-primary"
                      : "text-muted-foreground hover:bg-muted hover:text-foreground"
                  )}
                >
                  <item.icon
                    className={cn(
                      "w-6 h-6",
                      active ? "text-primary" : "text-muted-foreground group-hover:text-foreground"
                    )}
                  />
                  {item.label}
                </Link>
              );
            })}
          </nav>

          {/* Footer */}
          <div className="p-6 border-t border-border/50 flex flex-col items-center text-center opacity-50 hover:opacity-100 transition-opacity">
            <p className="text-xs text-muted-foreground">Municipal Control Center v1.0</p>
          </div>
        </aside>

        {/* Main Content Area */}
        <main className="flex-1 lg:ml-72 flex flex-col min-w-0">
          {/* Header */}
          <header className="h-20 border-b border-border/50 bg-card/50 backdrop-blur-sm flex items-center justify-between px-6 md:px-8 shrink-0">
            {/* Small screens: the sidebar becomes a scrolling row of links */}
            <nav aria-label="Portal sections" className="flex lg:hidden gap-1 overflow-x-auto">
              {items.map((item) => (
                <Link
                  key={item.label}
                  to={item.href}
                  className={cn(
                    "whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium",
                    isActive(item.href) ? "bg-primary/10 text-primary" : "text-muted-foreground hover:bg-muted"
                  )}
                >
                  {item.label}
                </Link>
              ))}
            </nav>
            <div className="hidden lg:block" />
            <div className="hidden sm:block text-sm font-medium shrink-0 ml-4">
              {portal === "admin" ? "City Administration" : `${departmentName ?? "Your"} Department`}
            </div>
          </header>

          <div className="flex-1 p-6 md:p-8">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
