import { Link } from "react-router";
import { Button } from "@/components/ui/button";
import {
  MapPin,
  Camera,
  Users,
  ArrowRight,
  CheckCircle2,
  Smartphone,
  Download,
} from "lucide-react";
import { useState } from "react";
import { useToast } from "@/hooks/use-toast";

export function HeroSection() {
  const [isDownloading, setIsDownloading] = useState(false);
  const { toast } = useToast();

  // The Android app is planned for a later release; there is no download yet
  const handleDownloadAPK = () => {
    setIsDownloading(true);
    toast({
      title: "Android app coming soon",
      description: "Meanwhile, CivSetu works in your phone's browser.",
    });
    setIsDownloading(false);
  };

  return (
    <section className="relative min-h-screen flex items-center overflow-hidden">
      {/* Background */}
      <div className="absolute inset-0 hero-gradient opacity-5" />
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,_var(--tw-gradient-stops))] from-primary/10 via-transparent to-transparent" />

      {/* Floating Elements */}
      <div className="absolute top-1/4 right-1/4 w-64 h-64 bg-secondary/20 rounded-full blur-3xl animate-pulse-slow" />
      <div
        className="absolute bottom-1/4 left-1/4 w-96 h-96 bg-primary/10 rounded-full blur-3xl animate-pulse-slow"
        style={{ animationDelay: "2s" }}
      />

      <div className="container mx-auto px-4 pt-24 pb-16 relative z-10">
        <div className="grid lg:grid-cols-2 gap-12 items-center">
          {/* Content */}
          <div className="space-y-8">
            <div className="inline-flex items-center gap-2 px-4 py-2 bg-primary/10 rounded-full border border-primary/20">
              <span className="w-2 h-2 bg-success rounded-full animate-pulse" />
              <span className="text-sm font-medium text-primary">
                Now Live in 50+ Cities
              </span>
            </div>

            <h1 className="font-display text-4xl md:text-5xl lg:text-6xl font-extrabold leading-tight">
              Your Voice for a{" "}
              <span className="gradient-text">Better City</span>
            </h1>

            <p className="text-lg text-muted-foreground max-w-xl leading-relaxed">
              Report civic issues like potholes, water leaks, and broken
              streetlights. Track resolutions in real-time and help build a
              cleaner, safer community for everyone.
            </p>

            <div className="flex flex-col sm:flex-row gap-4">
              <Link to="/report">
                <Button
                  variant="hero"
                  size="xl"
                  className="w-full sm:w-auto gap-3"
                >
                  <Camera className="w-5 h-5" />
                  Report an Issue
                  <ArrowRight className="w-5 h-5" />
                </Button>
              </Link>
              <Link to="/map">
                <Button
                  variant="outline"
                  size="xl"
                  className="w-full sm:w-auto gap-3"
                >
                  <MapPin className="w-5 h-5" />
                  View Issue Map
                </Button>
              </Link>
            </div>

            {/* Android App Download Button */}
            <div className="pt-2">
              <Button
                onClick={handleDownloadAPK}
                disabled={isDownloading}
                size="lg"
                className="w-full sm:w-auto gap-2 bg-gradient-to-r from-primary to-blue-600 hover:from-primary/90 hover:to-blue-700 text-white border-0 shadow-lg hover:shadow-xl transition-all duration-300 transform hover:scale-105"
              >
                <Smartphone className="w-4 h-4" />
                {isDownloading ? (
                  <>
                    <Download className="w-4 h-4 animate-bounce" />
                    Downloading...
                  </>
                ) : (
                  <>
                    Download Android App
                    <Download className="w-4 h-4" />
                  </>
                )}
              </Button>
            </div>
          </div>

          {/* Visual */}
          <div className="relative hidden lg:block">
            <div className="relative z-10">
              {/* Phone Mockup */}
              <div className="relative mx-auto w-72 h-[580px] bg-foreground rounded-[3rem] p-3 shadow-2xl rotate-3 hover:rotate-0 transition-transform duration-500">
                <div className="w-full h-full bg-card rounded-[2.5rem] overflow-hidden relative">
                  {/* Phone Notch */}
                  <div className="absolute top-0 left-1/2 -translate-x-1/2 w-24 h-6 bg-foreground rounded-b-2xl z-10" />

                  {/* App Content */}
                  <div className="p-6 pt-10 space-y-4">
                    <div className="text-center">
                      <div className="w-14 h-14 mx-auto rounded-2xl hero-gradient flex items-center justify-center mb-3">
                        <MapPin className="w-7 h-7 text-primary-foreground" />
                      </div>
                      <h3 className="font-display font-bold text-lg">
                        Report Issue
                      </h3>
                    </div>

                    {/* Fake Map */}
                    <div className="h-40 bg-muted rounded-2xl relative overflow-hidden">
                      <div className="absolute inset-0 bg-[linear-gradient(45deg,transparent_25%,hsl(var(--muted))_25%,hsl(var(--muted))_50%,transparent_50%,transparent_75%,hsl(var(--muted))_75%)] bg-[length:20px_20px] opacity-30" />
                      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2">
                        <div className="w-8 h-8 bg-destructive rounded-full flex items-center justify-center animate-pulse-slow">
                          <MapPin className="w-4 h-4 text-destructive-foreground" />
                        </div>
                        <div className="absolute inset-0 w-8 h-8 bg-destructive/30 rounded-full animate-pulse-ring" />
                      </div>
                    </div>

                    {/* Fake Form */}
                    <div className="space-y-3">
                      <div className="h-10 bg-muted rounded-xl" />
                      <div className="h-20 bg-muted rounded-xl" />
                      <div className="h-12 hero-gradient rounded-xl" />
                    </div>
                  </div>
                </div>
              </div>

              {/* Floating Cards */}
              <div
                className="absolute -left-8 top-20 glass-card rounded-2xl p-4 shadow-xl animate-float"
                style={{ animationDelay: "0s" }}
              >
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-warning/20 flex items-center justify-center">
                    <div className="w-5 h-5 rounded-full bg-warning" />
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">
                      Pothole Reported
                    </p>
                    <p className="font-semibold text-sm">MG Road, Sector 5</p>
                  </div>
                </div>
              </div>

              <div
                className="absolute -right-4 top-1/2 glass-card rounded-2xl p-4 shadow-xl animate-float"
                style={{ animationDelay: "2s" }}
              >
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-success/20 flex items-center justify-center">
                    <CheckCircle2 className="w-5 h-5 text-success" />
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">
                      Issue Resolved
                    </p>
                    <p className="font-semibold text-sm">Street Light Fixed</p>
                  </div>
                </div>
              </div>

              <div
                className="absolute -left-4 bottom-20 glass-card rounded-2xl p-4 shadow-xl animate-float"
                style={{ animationDelay: "4s" }}
              >
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-full bg-info/20 flex items-center justify-center">
                    <Users className="w-5 h-5 text-info" />
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground">
                      Community Verified
                    </p>
                    <p className="font-semibold text-sm">12 confirmations</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
