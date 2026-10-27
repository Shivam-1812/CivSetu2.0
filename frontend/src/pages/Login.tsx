import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { MapPin, Eye, EyeOff, ArrowRight, User, Building2, Loader2 } from "lucide-react";
import { useAuth } from "@/auth/AuthProvider";
import { useToast } from "@/hooks/use-toast";
import { ApiError } from "@/api/client";
import { HOME_BY_ROLE } from "@/components/layout/Navbar";

export function Login() {
  const [showPassword, setShowPassword] = useState(false);
  const [audience, setAudience] = useState<"citizen" | "official">("citizen");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string>();

  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const { toast } = useToast();
  const from = (location.state as { from?: string } | null)?.from;

  const handleEmailLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setError("Please fill in all fields");
      return;
    }
    setIsSubmitting(true);
    setError(undefined);
    try {
      const user = await login(email.trim(), password);
      toast({ title: "Welcome back!", description: "Successfully logged in" });
      navigate(from ?? (user.role ? HOME_BY_ROLE[user.role] : "/"), { replace: true });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Could not sign in. Please try again.");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-background">
      <main className="pt-8 pb-16">
        <div className="container mx-auto px-4">
          <div className="max-w-md mx-auto">
            <div className="text-center mb-8">
              <div className="w-16 h-16 mx-auto mb-4 rounded-2xl hero-gradient flex items-center justify-center">
                <MapPin className="w-8 h-8 text-primary-foreground" />
              </div>
              <h1 className="font-display text-2xl font-bold mb-2">Welcome Back</h1>
              <p className="text-muted-foreground">Sign in to track your civic contributions</p>
            </div>

            <div className="bg-card rounded-2xl border border-border/50 p-6">
              <Tabs value={audience} onValueChange={(v) => setAudience(v as "citizen" | "official")} className="mb-6">
                <TabsList className="w-full">
                  <TabsTrigger value="citizen" className="flex-1 gap-2">
                    <User className="w-4 h-4" />
                    Citizen
                  </TabsTrigger>
                  <TabsTrigger value="official" className="flex-1 gap-2">
                    <Building2 className="w-4 h-4" />
                    Official
                  </TabsTrigger>
                </TabsList>
                <TabsContent value="citizen" className="mt-4">
                  <p className="text-sm text-muted-foreground text-center">
                    Report issues and follow them until they are fixed
                  </p>
                </TabsContent>
                <TabsContent value="official" className="mt-4">
                  <p className="text-sm text-muted-foreground text-center">
                    Department officials and admins sign in with the account created for them
                  </p>
                </TabsContent>
              </Tabs>

              <form onSubmit={handleEmailLogin} className="space-y-4" noValidate>
                {error && (
                  <div role="alert" className="rounded-lg border border-destructive/20 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                    {error}
                  </div>
                )}
                <div>
                  <Label htmlFor="email">Email</Label>
                  <Input
                    id="email"
                    type="email"
                    autoComplete="email"
                    placeholder="you@example.com"
                    className="mt-1.5 h-12"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                  />
                </div>
                <div>
                  <Label htmlFor="password">Password</Label>
                  <div className="relative mt-1.5">
                    <Input
                      id="password"
                      type={showPassword ? "text" : "password"}
                      autoComplete="current-password"
                      placeholder="••••••••"
                      className="h-12 pr-10"
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      required
                    />
                    <button
                      type="button"
                      aria-label={showPassword ? "Hide password" : "Show password"}
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
                <Button type="submit" variant="hero" size="lg" className="w-full gap-2" disabled={isSubmitting}>
                  {isSubmitting ? <Loader2 className="w-4 h-4 animate-spin" /> : <ArrowRight className="w-4 h-4" />}
                  Sign In
                </Button>
              </form>

              <p className="text-center mt-6 text-sm text-muted-foreground">
                Don't have an account?{" "}
                <Link to="/signup" className="text-primary font-medium hover:underline">
                  Sign up for free
                </Link>
              </p>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}
