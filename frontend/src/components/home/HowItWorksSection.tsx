import { Camera, MapPinned, CheckCircle, Users, Clock, ThumbsUp } from "lucide-react";

const steps = [
  {
    number: "01",
    icon: Camera,
    title: "Report the Issue",
    description: "Capture a photo or video, describe the problem using text or voice, and let GPS tag the exact location.",
  },
  {
    number: "02", 
    icon: MapPinned,
    title: "Issue Goes Live",
    description: "Your report appears on the public map instantly. Nearby citizens can view and verify the issue.",
  },
  {
    number: "03",
    icon: Users,
    title: "Community Verification",
    description: "Other citizens confirm the issue, add supporting evidence, and boost its priority through collective validation.",
  },
  {
    number: "04",
    icon: Clock,
    title: "Authority Assignment",
    description: "The issue is automatically routed to the relevant department with SLA timers for resolution.",
  },
  {
    number: "05",
    icon: CheckCircle,
    title: "Resolution & Proof",
    description: "Field staff resolves the issue and uploads geo-tagged proof-of-work for validation.",
  },
  {
    number: "06",
    icon: ThumbsUp,
    title: "Citizen Feedback",
    description: "You rate the resolution quality and provide feedback to ensure accountability and continuous improvement.",
  },
];

export function HowItWorksSection() {
  return (
    <section className="py-24 relative overflow-hidden">
      {/* Background */}
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_bottom_left,_var(--tw-gradient-stops))] from-secondary/5 via-transparent to-transparent" />

      <div className="container mx-auto px-4 relative z-10">
        <div className="text-center max-w-3xl mx-auto mb-16">
          <span className="inline-block px-4 py-1 bg-secondary/10 text-secondary rounded-full text-sm font-medium mb-4">
            How It Works
          </span>
          <h2 className="font-display text-3xl md:text-4xl font-bold mb-4">
            From Report to Resolution in 6 Simple Steps
          </h2>
          <p className="text-muted-foreground text-lg">
            Our streamlined process ensures every civic issue gets the attention it deserves.
          </p>
        </div>

        <div className="relative">
          {/* Connection Line */}
          <div className="hidden lg:block absolute top-1/2 left-0 right-0 h-0.5 bg-gradient-to-r from-primary via-secondary to-primary opacity-20" />

          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-8">
            {steps.map((step, index) => (
              <div
                key={step.number}
                className="relative group"
              >
                {/* Card */}
                <div className="bg-card rounded-2xl p-6 border border-border/50 hover:border-primary/30 hover:shadow-xl transition-all duration-300 h-full">
                  {/* Number Badge */}
                  <div className="absolute -top-4 left-6 px-3 py-1 hero-gradient rounded-full text-primary-foreground text-sm font-bold shadow-lg">
                    Step {step.number}
                  </div>

                  <div className="pt-4">
                    <div className="w-14 h-14 rounded-2xl bg-muted flex items-center justify-center mb-4 group-hover:bg-primary/10 transition-colors duration-300">
                      <step.icon className="w-7 h-7 text-primary" />
                    </div>

                    <h3 className="font-display font-semibold text-lg mb-2">{step.title}</h3>
                    <p className="text-muted-foreground text-sm leading-relaxed">{step.description}</p>
                  </div>
                </div>

                {/* Arrow for desktop */}
                {index < steps.length - 1 && (index + 1) % 3 !== 0 && (
                  <div className="hidden lg:block absolute top-1/2 -right-4 w-8 h-8 text-primary/30">
                    <svg viewBox="0 0 24 24" fill="currentColor">
                      <path d="M13.293 6.293L7.586 12l5.707 5.707 1.414-1.414L10.414 12l4.293-4.293z" transform="rotate(180 12 12)" />
                    </svg>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
