import { useCallback, useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { formatDistanceToNow } from "date-fns";
import { toast } from "sonner";
import { GoogleTranslate } from "@/components/common/GoogleTranslate";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { useAuth } from "@/auth/AuthProvider";
import { notificationApi, referenceApi } from "@/api/endpoints";
import type { Notification, Role } from "@/api/types";
import { useNotificationStream } from "@/hooks/useNotificationStream";
import {
  Menu,
  X,
  MapPin,
  LayoutDashboard,
  Shield,
  Building2,
  Bell,
  LogOut,
  AlertTriangle,
  CheckCircle2,
  User,
  ClipboardList,
} from "lucide-react";
import { cn } from "@/lib/utils";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

/** Where each role's own area lives. */
export const HOME_BY_ROLE: Record<Role, string> = {
  CITIZEN: "/dashboard",
  AUTHORITY: "/authority",
  ADMIN: "/admin",
};

const PORTAL_LINK: Record<Role, { label: string; icon: typeof LayoutDashboard }> = {
  CITIZEN: { label: "Dashboard", icon: LayoutDashboard },
  AUTHORITY: { label: "Department Portal", icon: Building2 },
  ADMIN: { label: "Admin Portal", icon: Shield },
};

const ROLE_LABEL: Record<Role, string> = {
  CITIZEN: "Citizen",
  AUTHORITY: "Department official",
  ADMIN: "Admin",
};

function notificationLink(notification: Notification): string | null {
  if (notification.type === "SURVEY_INVITATION") return "/dashboard?tab=surveys";
  return notification.issueId ? `/issues/${notification.issueId}` : null;
}

export function Navbar() {
  const [isOpen, setIsOpen] = useState(false);
  const [googleOffset, setGoogleOffset] = useState(0);
  const location = useLocation();
  const { user, logout } = useAuth();
  const isAuthenticated = !!user;
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const notifications = useQuery({
    queryKey: ["notifications", "list"],
    queryFn: () => notificationApi.list(false, 0),
    enabled: isAuthenticated,
  });
  const unread = useQuery({
    queryKey: ["notifications", "unread"],
    queryFn: notificationApi.unreadCount,
    enabled: isAuthenticated,
  });
  const departments = useQuery({
    queryKey: ["departments"],
    queryFn: referenceApi.departments,
    enabled: !!user?.departmentId,
  });
  const departmentName = departments.data?.find((department) => department.id === user?.departmentId)?.name;

  const onLive = useCallback((notification: Notification) => {
    toast(notification.title ?? "New notification", { description: notification.body });
  }, []);
  useNotificationStream(isAuthenticated, onLive);

  const refresh = () => queryClient.invalidateQueries({ queryKey: ["notifications"] });
  const markRead = useMutation({ mutationFn: notificationApi.markRead, onSuccess: refresh });
  const markAllRead = useMutation({ mutationFn: notificationApi.markAllRead, onSuccess: refresh });

  const list = notifications.data?.content ?? [];
  const unreadCount = unread.data?.count ?? 0;

  useEffect(() => {
    // Monitor Google Translate banner adding 'top' to body
    const updateOffset = () => {
      const topVal = parseInt(document.body.style.top || "0", 10);
      setGoogleOffset(isNaN(topVal) ? 0 : topVal);
    };

    // Observer to watch body style attribute
    const observer = new MutationObserver(updateOffset);
    observer.observe(document.body, {
      attributes: true,
      attributeFilter: ["style"],
    });

    // Initial check
    updateOffset();

    return () => observer.disconnect();
  }, []);

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  const linkClass = (href: string) =>
    cn(
      "px-3 xl:px-4 py-2 rounded-lg text-sm font-medium whitespace-nowrap transition-all duration-200",
      location.pathname === href || (href !== "/" && location.pathname.startsWith(`${href}/`))
        ? "bg-primary/10 text-primary"
        : "text-muted-foreground hover:text-foreground hover:bg-muted",
    );

  const portal = user?.role ? { href: HOME_BY_ROLE[user.role], ...PORTAL_LINK[user.role] } : null;
  const canReport = !user || user.role === "CITIZEN";

  const openNotification = (notification: Notification) => {
    if (!notification.read) markRead.mutate(notification.id!);
    const target = notificationLink(notification);
    if (target) navigate(target);
  };

  return (
    <nav
      className="fixed left-0 right-0 z-50 bg-card/80 backdrop-blur-lg border-b border-border/50 transition-[top] duration-300 ease-in-out"
      style={{ top: `${googleOffset}px` }}
    >
      <div className="container mx-auto px-4 relative">
        <div className="flex items-center justify-between gap-2 h-16">
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2 z-20 shrink-0">
            <div className="w-10 h-10 rounded-xl hero-gradient flex items-center justify-center shadow-md">
              <MapPin className="w-5 h-5 text-primary-foreground" />
            </div>
            <span className="font-display font-bold text-xl text-foreground">
              Civ<span className="text-primary">Setu</span>
            </span>
          </Link>

          {/* Desktop Navigation - Centered */}
          <div className="hidden lg:flex flex-1 min-w-0 items-center justify-center gap-1 px-4">
            <Link to="/" className={linkClass("/")}>
              Home
            </Link>
            <Link to="/map" className={linkClass("/map")}>
              Issue Map
            </Link>
            {canReport && (
              <Link to="/report" className={linkClass("/report")}>
                Report Issue
              </Link>
            )}
            {portal && (
              <Link to={portal.href} className={linkClass(portal.href)}>
                {portal.label}
              </Link>
            )}
          </div>

          {/* Desktop Actions */}
          <div className="hidden lg:flex items-center gap-2 z-20 shrink-0">
            <div className="mr-2 hidden xl:block">
              <GoogleTranslate />
            </div>
            {!isAuthenticated ? (
              <>
                <Link to="/login">
                  <Button variant="outline" size="sm">
                    Sign In
                  </Button>
                </Link>
                <Link to="/report">
                  <Button variant="hero" size="sm">
                    Report Issue
                  </Button>
                </Link>
              </>
            ) : (
              <>
                {/* Rich User Profile Dropdown */}
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button
                      variant="ghost"
                      aria-label="Account menu"
                      className="relative h-10 w-auto flex items-center gap-2 px-2 hover:bg-muted/50"
                    >
                      <span className="text-sm font-semibold text-foreground hidden xl:inline-block">
                        {user?.name || "User"}
                      </span>
                      <div className="w-9 h-9 rounded-full bg-primary/10 flex items-center justify-center text-primary font-bold border border-primary/20">
                        {user?.name?.[0]?.toUpperCase() || "U"}
                      </div>
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent className="w-56" align="end">
                    <DropdownMenuLabel className="font-normal">
                      <div className="flex flex-col space-y-1">
                        <p className="text-sm font-medium leading-none">{user?.name}</p>
                        <p className="text-xs leading-none text-muted-foreground">{user?.email}</p>
                      </div>
                    </DropdownMenuLabel>
                    <DropdownMenuSeparator />
                    <div className="p-2">
                      {departmentName && (
                        <div className="mb-2">
                          <Badge
                            variant="outline"
                            className="w-full text-center justify-center border-primary/30 text-primary bg-primary/5"
                          >
                            {departmentName}
                          </Badge>
                        </div>
                      )}
                      <p className="text-xs text-muted-foreground text-center mb-1">
                        Role: {user?.role ? ROLE_LABEL[user.role] : ""}
                      </p>
                    </div>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem onClick={() => navigate("/profile")} className="cursor-pointer">
                      <User className="mr-2 h-4 w-4" />
                      <span>Profile</span>
                    </DropdownMenuItem>
                    <DropdownMenuItem
                      onClick={handleLogout}
                      className="text-destructive focus:text-destructive cursor-pointer"
                    >
                      <LogOut className="mr-2 h-4 w-4" />
                      <span>Sign out</span>
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>

                {/* Notification Sheet */}
                <Sheet>
                  <SheetTrigger asChild>
                    <Button
                      variant="ghost"
                      size="icon"
                      aria-label={`Notifications, ${unreadCount} unread`}
                      className="relative text-muted-foreground hover:text-foreground"
                    >
                      <Bell className="w-5 h-5" />
                      {unreadCount > 0 && (
                        <span className="absolute -top-1 -right-1 min-w-[18px] h-[18px] flex items-center justify-center bg-destructive text-[10px] font-bold text-white rounded-full border-2 border-card px-1 animate-in zoom-in">
                          {unreadCount > 9 ? "9+" : unreadCount}
                        </span>
                      )}
                    </Button>
                  </SheetTrigger>
                  <SheetContent>
                    <SheetHeader className="flex flex-row items-center justify-between space-y-0 pr-6">
                      <div>
                        <SheetTitle>Notifications</SheetTitle>
                        <SheetDescription>Real-time alerts and system updates.</SheetDescription>
                      </div>
                      {unreadCount > 0 && (
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-xs text-primary h-8 hover:bg-primary/10"
                          onClick={() => markAllRead.mutate()}
                        >
                          Mark all read
                        </Button>
                      )}
                    </SheetHeader>
                    <div className="mt-6 space-y-4 h-full max-h-[calc(100vh-140px)] overflow-y-auto pr-2">
                      {list.length === 0 ? (
                        <div className="text-center text-muted-foreground py-8">
                          <Bell className="w-12 h-12 mx-auto mb-3 opacity-20" />
                          <p>No new notifications</p>
                        </div>
                      ) : (
                        list.map((notif) => (
                          <button
                            type="button"
                            key={notif.id}
                            onClick={() => openNotification(notif)}
                            className={cn(
                              "block w-full text-left p-4 rounded-xl border relative overflow-hidden transition-all cursor-pointer",
                              notif.type === "SLA_BREACHED"
                                ? "bg-destructive/5 border-destructive/20"
                                : notif.read
                                  ? "bg-card border-border/50 opacity-80"
                                  : "bg-primary/5 border-primary/20",
                            )}
                          >
                            {!notif.read && (
                              <div className="absolute top-3 right-3 w-1.5 h-1.5 bg-primary rounded-full" />
                            )}

                            <div className="flex items-center gap-2 mb-1 relative z-10">
                              {notif.type === "SLA_BREACHED" ? (
                                <AlertTriangle className="w-4 h-4 text-destructive" />
                              ) : notif.type === "RESOLVED" ? (
                                <CheckCircle2 className="w-4 h-4 text-green-500" />
                              ) : notif.type === "SURVEY_INVITATION" ? (
                                <ClipboardList className="w-4 h-4 text-primary" />
                              ) : notif.type === "REPORT_RECEIVED" ? (
                                <div className="p-1 bg-green-100 rounded-full">
                                  <CheckCircle2 className="w-3 h-3 text-green-600" />
                                </div>
                              ) : (
                                <div className="p-1 bg-blue-100 rounded-full">
                                  <Bell className="w-3 h-3 text-blue-600" />
                                </div>
                              )}
                              <span className="font-semibold text-sm">{notif.title}</span>
                            </div>

                            <p className="text-sm text-muted-foreground relative z-10 mb-2">{notif.body}</p>

                            <div className="flex items-center justify-between relative z-10">
                              <span className="text-[10px] px-1.5 py-0.5 rounded bg-muted text-muted-foreground uppercase tracking-wider font-bold">
                                {notif.type?.replaceAll("_", " ") || "SYSTEM"}
                              </span>
                              <p className="text-[10px] text-muted-foreground/60 italic font-medium">
                                {notif.createdAt && formatDistanceToNow(new Date(notif.createdAt), { addSuffix: true })}
                              </p>
                            </div>
                          </button>
                        ))
                      )}
                    </div>
                  </SheetContent>
                </Sheet>

                {canReport && (
                  <Link to="/report">
                    <Button variant="hero" size="sm">
                      Report Issue
                    </Button>
                  </Link>
                )}
              </>
            )}
          </div>

          {/* Mobile Menu Button */}
          <button
            type="button"
            aria-label={isOpen ? "Close menu" : "Open menu"}
            aria-expanded={isOpen}
            onClick={() => setIsOpen(!isOpen)}
            className="lg:hidden p-2 rounded-lg hover:bg-muted transition-colors z-20"
          >
            {isOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
          </button>
        </div>

        {/* Mobile Menu */}
        {isOpen && (
          <div className="lg:hidden py-4 border-t border-border/50 animate-fade-in relative z-10 bg-background/95 backdrop-blur-sm">
            <div className="flex flex-col gap-2">
              <Link
                to="/"
                onClick={() => setIsOpen(false)}
                className="flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium text-muted-foreground hover:bg-muted"
              >
                Home
              </Link>
              <Link
                to="/map"
                onClick={() => setIsOpen(false)}
                className="flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium text-muted-foreground hover:bg-muted"
              >
                <MapPin className="w-4 h-4" />
                Issue Map
              </Link>

              {portal && (
                <Link
                  to={portal.href}
                  onClick={() => setIsOpen(false)}
                  className="flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium text-muted-foreground hover:bg-muted"
                >
                  <portal.icon className="w-4 h-4" />
                  {portal.label}
                </Link>
              )}

              {isAuthenticated && (
                <Link
                  to="/profile"
                  onClick={() => setIsOpen(false)}
                  className="flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium text-muted-foreground hover:bg-muted"
                >
                  <User className="w-4 h-4" />
                  Profile
                  {unreadCount > 0 && (
                    <span className="ml-auto rounded-full bg-destructive px-2 text-[10px] font-bold text-white">
                      {unreadCount} new
                    </span>
                  )}
                </Link>
              )}

              <hr className="my-2 border-border" />

              {!isAuthenticated ? (
                <>
                  <Link to="/login" onClick={() => setIsOpen(false)}>
                    <Button variant="outline" className="w-full">
                      Sign In
                    </Button>
                  </Link>
                  <Link to="/report" onClick={() => setIsOpen(false)}>
                    <Button variant="hero" className="w-full">
                      Report Issue
                    </Button>
                  </Link>
                </>
              ) : (
                <>
                  {canReport && (
                    <Link to="/report" onClick={() => setIsOpen(false)}>
                      <Button variant="hero" className="w-full">
                        Report Issue
                      </Button>
                    </Link>
                  )}
                  <Button
                    variant="ghost"
                    className="w-full justify-start"
                    onClick={() => {
                      handleLogout();
                      setIsOpen(false);
                    }}
                  >
                    Sign out
                  </Button>
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </nav>
  );
}
