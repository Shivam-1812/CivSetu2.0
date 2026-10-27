import { Camera, MapPin, Users, Bell, BarChart3, Shield, Mic, Languages, Award } from "lucide-react";

const features = [
  {
    icon: Camera,
    title: "Multi-Modal Reporting",
    description: "Report issues using photos, videos, voice recordings, or text. Automatic GPS tagging for precise location.",
    color: "bg-primary/10 text-primary",
  },
  {
    icon: Users,
    title: "Community Verification",
    description: "Nearby citizens can confirm issues, add evidence, and boost priority through collective validation.",
    color: "bg-secondary/10 text-secondary",
  },
  {
    icon: MapPin,
    title: "Interactive Issue Map",
    description: "Real-time visualization of all civic issues with clustering, severity indicators, and hotspot detection.",
    color: "bg-info/10 text-info",
  },
  {
    icon: Bell,
    title: "Real-Time Notifications",
    description: "Stay updated with push notifications for status changes, resolutions, and nearby issue alerts.",
    color: "bg-warning/10 text-warning",
  },
  {
    icon: BarChart3,
    title: "AI-Powered Analytics",
    description: "Smart duplicate detection, automatic categorization, and predictive issue prioritization.",
    color: "bg-destructive/10 text-destructive",
  },
  {
    icon: Shield,
    title: "SLA Tracking",
    description: "Automatic escalation for overdue issues. Geo-tagged proof-of-work ensures genuine resolutions.",
    color: "bg-success/10 text-success",
  },
  {
    icon: Mic,
    title: "Voice Input Support",
    description: "Speak to report issues in your local language. Speech-to-text makes reporting accessible to all.",
    color: "bg-primary/10 text-primary",
  },
  {
    icon: Languages,
    title: "Multilingual Support",
    description: "Report and track issues in your preferred language with automatic translation support.",
    color: "bg-secondary/10 text-secondary",
  },
  {
    icon: Award,
    title: "Rewards & Gamification",
    description: "Earn points and badges for responsible reporting. Top contributors get special recognition.",
    color: "bg-accent/10 text-accent",
  },
];

export function FeaturesSection() {
  return (
    <section className="py-24 bg-muted/30">
      <div className="container mx-auto px-4">
        <div className="text-center max-w-3xl mx-auto mb-16">
          <span className="inline-block px-4 py-1 bg-primary/10 text-primary rounded-full text-sm font-medium mb-4">
            Features
          </span>
          <h2 className="font-display text-3xl md:text-4xl font-bold mb-4">
            Everything You Need to Make a Difference
          </h2>
          <p className="text-muted-foreground text-lg">
            CivSetu provides powerful tools for citizens and authorities to collaborate 
            on building better communities.
          </p>
        </div>

        <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-6">
          {features.map((feature, index) => (
            <div
              key={feature.title}
              className="group bg-card rounded-2xl p-6 border border-border/50 hover:border-primary/30 hover:shadow-card-hover transition-all duration-300"
              style={{ animationDelay: `${index * 100}ms` }}
            >
              <div className={`w-12 h-12 rounded-xl ${feature.color} flex items-center justify-center mb-4 group-hover:scale-110 transition-transform duration-300`}>
                <feature.icon className="w-6 h-6" />
              </div>
              <h3 className="font-display font-semibold text-lg mb-2">{feature.title}</h3>
              <p className="text-muted-foreground text-sm leading-relaxed">{feature.description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
