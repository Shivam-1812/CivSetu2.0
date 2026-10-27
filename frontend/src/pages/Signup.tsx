import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { MapPin, Eye, EyeOff, ArrowRight, User, Loader2 } from "lucide-react";
import { useAuth } from "@/auth/AuthProvider";
import { useToast } from "@/hooks/use-toast";
import { ApiError } from "@/api/client";

/** Citizens sign up here. Department officials and admins get their accounts from an Admin. */
export function Signup() {
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string>();
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const { register } = useAuth();
  const navigate = useNavigate();
  const { toast } = useToast();

  const validateForm = (): string | null => {
    if (name.trim().length < 2) return "Please enter your full name";
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) return "Please enter a valid email address";
    if (phone && !/^[0-9]{10}$/.test(phone)) return "Phone number must be 10 digits";
    if (password.length < 8) return "Password must be at least 8 characters";
    if (password !== confirmPassword) return "Passwords do not match";
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const problem = validateForm();
    setFieldErrors({});
    if (problem) {
      setError(problem);
      return;
    }
    setError(undefined);
    setIsSubmitting(true);
    try {
      await register({ name: name.trim(), email: email.trim(), password, phone: phone ? `+91${phone}` : undefined });
      toast({ title: "Account Created!", description: "Welcome to CivSetu" });
      navigate("/dashboard", { replace: true });
    } catch (caught) {
      if (caught instanceof ApiError && caught.fieldErrors.length > 0) {
        setFieldErrors(Object.fromEntries(caught.fieldErrors.map((f) => [f.field, f.message])));
        setError("Please check the highlighted fields.");
      } else {
        setError(caught instanceof ApiError ? caught.message : "Could not create your account. Please try again.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const fieldError = (field: string) =>
    fieldErrors[field] && <p className="mt-1 text-xs text-destructive">{fieldErrors[field]}</p>;

  return (
    <div className="min-h-screen bg-background">
      <main className="pt-8 pb-16">
        <div className="container mx-auto px-4">
          <div className="max-w-md mx-auto">
            <div className="text-center mb-8">
              <div className="w-16 h-16 mx-auto mb-4 rounded-2xl hero-gradient flex items-center justify-center">
                <MapPin className="w-8 h-8 text-primary-foreground" />
              </div>
              <h1 className="font-display text-2xl font-bold mb-2">Create Account</h1>
              <p className="text-muted-foreground">Join CivSetu and help improve your community</p>
            </div>

            <div className="bg-card rounded-2xl border border-border/50 p-6">
              <div className="mb-6 flex items-center justify-center gap-2 rounded-md bg-muted p-2 text-sm font-medium">
                <User className="w-4 h-4" /> Citizen account
              </div>
              <p className="text-sm text-muted-foreground text-center mb-6">
                Report issues and track resolutions. Department officials receive their accounts from the city
                administration.
              </p>

              <form onSubmit={handleSubmit} className="space-y-4" noValidate>
                {error && (
                  <div role="alert" className="rounded-lg border border-destructive/20 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                    {error}
                  </div>
                )}
                <div>
                  <Label htmlFor="name">Full name</Label>
                  <Input
                    id="name"
                    type="text"
                    autoComplete="name"
                    placeholder="Enter your full name"
                    className="mt-1.5 h-12"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    required
                  />
                  {fieldError("name")}
                </div>

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
                  {fieldError("email")}
                </div>

                <div>
                  <Label htmlFor="phone">Phone Number (Optional)</Label>
                  <div className="flex gap-2 mt-1.5">
                    <Input value="+91" disabled aria-label="Country code" className="w-16 h-12 text-center" />
                    <Input
                      id="phone"
                      type="tel"
                      inputMode="numeric"
                      autoComplete="tel-national"
                      placeholder="9876543210"
                      className="flex-1 h-12"
                      value={phone}
                      onChange={(e) => setPhone(e.target.value.replace(/\D/g, "").slice(0, 10))}
                    />
                  </div>
                  {fieldError("phone")}
                </div>

                <div>
                  <Label htmlFor="password">Password</Label>
                  <div className="relative mt-1.5">
                    <Input
                      id="password"
                      type={showPassword ? "text" : "password"}
                      autoComplete="new-password"
                      placeholder="At least 8 characters"
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
                  {fieldError("password")}
                </div>

                <div>
                  <Label htmlFor="confirmPassword">Confirm password</Label>
                  <div className="relative mt-1.5">
                    <Input
                      id="confirmPassword"
                      type={showConfirmPassword ? "text" : "password"}
                      autoComplete="new-password"
                      placeholder="Confirm your password"
                      className="h-12 pr-10"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      required
                    />
                    <button
                      type="button"
                      aria-label={showConfirmPassword ? "Hide password" : "Show password"}
                      onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                    >
                      {showConfirmPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>

                <Button type="submit" variant="hero" size="lg" className="w-full gap-2" disabled={isSubmitting}>
                  {isSubmitting ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" />
                      Creating Account...
                    </>
                  ) : (
                    <>
                      Create Account
                      <ArrowRight className="w-4 h-4" />
                    </>
                  )}
                </Button>
              </form>
            </div>

            <p className="text-center mt-6 text-sm text-muted-foreground">
              Already have an account?{" "}
              <Link to="/login" className="text-primary font-medium hover:underline">
                Sign in
              </Link>
            </p>
          </div>
        </div>
      </main>
    </div>
  );
}
