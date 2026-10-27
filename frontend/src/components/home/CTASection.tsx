import { Link } from "react-router";
import { Button } from "@/components/ui/button";
import { ArrowRight, Camera, Building2 } from "lucide-react";

export function CTASection() {
  return (
    <section className="py-24 relative overflow-hidden">
      {/* Background */}
      <div className="absolute inset-0 bg-gradient-to-br from-muted/50 to-background" />
      <div className="absolute top-0 right-0 w-1/2 h-1/2 bg-primary/5 rounded-full blur-3xl" />
      <div className="absolute bottom-0 left-0 w-1/3 h-1/3 bg-secondary/5 rounded-full blur-3xl" />

      <div className="container mx-auto px-4 relative z-10">
        <div className="grid lg:grid-cols-2 gap-8">
          {/* Citizen CTA */}
          <div className="bg-card rounded-3xl p-8 md:p-12 border border-border/50 hover:border-primary/30 hover:shadow-xl transition-all duration-300">
            <div className="w-16 h-16 rounded-2xl hero-gradient flex items-center justify-center mb-6">
              <Camera className="w-8 h-8 text-primary-foreground" />
            </div>
            <h3 className="font-display text-2xl md:text-3xl font-bold mb-4">
              For Citizens
            </h3>
            <p className="text-muted-foreground mb-6 leading-relaxed">
              Be the change you want to see. Report civic issues in your
              neighborhood and track their resolution. Your voice matters.
            </p>
            <ul className="space-y-3 mb-8">
              <li className="flex items-center gap-3 text-sm">
                <div className="w-5 h-5 rounded-full bg-success/20 flex items-center justify-center">
                  <div className="w-2 h-2 rounded-full bg-success" />
                </div>
                Report issues in seconds
              </li>
              <li className="flex items-center gap-3 text-sm">
                <div className="w-5 h-5 rounded-full bg-success/20 flex items-center justify-center">
                  <div className="w-2 h-2 rounded-full bg-success" />
                </div>
                Track resolution in real-time
              </li>
              <li className="flex items-center gap-3 text-sm">
                <div className="w-5 h-5 rounded-full bg-success/20 flex items-center justify-center">
                  <div className="w-2 h-2 rounded-full bg-success" />
                </div>
                Earn rewards for participation
              </li>
            </ul>
            <Link to="/report">
              <Button
                variant="hero"
                size="lg"
                className="w-full sm:w-auto gap-2"
              >
                Report an Issue
                <ArrowRight className="w-5 h-5" />
              </Button>
            </Link>
          </div>

          {/* Authority CTA */}
          <div className="bg-foreground rounded-3xl p-8 md:p-12 text-background hover:shadow-xl transition-all duration-300">
            <div className="w-16 h-16 rounded-2xl bg-secondary flex items-center justify-center mb-6">
              <Building2 className="w-8 h-8 text-secondary-foreground" />
            </div>
            <h3 className="font-display text-2xl md:text-3xl font-bold mb-4">
              For Authorities
            </h3>
            <p className="text-background/70 mb-6 leading-relaxed">
              Streamline civic issue management with our powerful dashboard.
              Prioritize, assign, and resolve issues efficiently.
            </p>
            <ul className="space-y-3 mb-8">
              <li className="flex items-center gap-3 text-sm text-background/80">
                <div className="w-5 h-5 rounded-full bg-secondary/30 flex items-center justify-center">
                  <div className="w-2 h-2 rounded-full bg-secondary" />
                </div>
                Real-time issue dashboard
              </li>
              <li className="flex items-center gap-3 text-sm text-background/80">
                <div className="w-5 h-5 rounded-full bg-secondary/30 flex items-center justify-center">
                  <div className="w-2 h-2 rounded-full bg-secondary" />
                </div>
                Automated routing & SLA tracking
              </li>
              <li className="flex items-center gap-3 text-sm text-background/80">
                <div className="w-5 h-5 rounded-full bg-secondary/30 flex items-center justify-center">
                  <div className="w-2 h-2 rounded-full bg-secondary" />
                </div>
                Analytics & performance insights
              </li>
            </ul>
            <Link to="/login">
              <Button
                variant="secondary"
                size="lg"
                className="w-full sm:w-auto gap-2"
              >
                Access Admin Portal
                <ArrowRight className="w-5 h-5" />
              </Button>
            </Link>
          </div>
        </div>
      </div>
    </section>
  );
}
