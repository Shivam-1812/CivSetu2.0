import { Link } from "react-router";
import { MapPin, Mail, Phone, Facebook, Twitter, Instagram, Linkedin } from "lucide-react";

export function Footer() {
  return (
    <footer className="bg-foreground text-background">
      <div className="container mx-auto px-4 py-16">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-12">
          {/* Brand */}
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <div className="w-10 h-10 rounded-xl hero-gradient flex items-center justify-center">
                <MapPin className="w-5 h-5 text-primary-foreground" />
              </div>
              <span className="font-display font-bold text-xl">
                Civ<span className="text-secondary">Setu</span>
              </span>
            </div>
            <p className="text-background/70 text-sm leading-relaxed">
              Empowering citizens to build better communities through transparent, 
              accountable civic issue reporting and resolution.
            </p>
            <div className="flex items-center gap-4">
              <a href="#" className="text-background/60 hover:text-secondary transition-colors">
                <Facebook className="w-5 h-5" />
              </a>
              <a href="#" className="text-background/60 hover:text-secondary transition-colors">
                <Twitter className="w-5 h-5" />
              </a>
              <a href="#" className="text-background/60 hover:text-secondary transition-colors">
                <Instagram className="w-5 h-5" />
              </a>
              <a href="#" className="text-background/60 hover:text-secondary transition-colors">
                <Linkedin className="w-5 h-5" />
              </a>
            </div>
          </div>

          {/* Quick Links */}
          <div>
            <h4 className="font-display font-semibold text-lg mb-4">Quick Links</h4>
            <ul className="space-y-3">
              <li><Link to="/map" className="text-background/70 hover:text-secondary transition-colors text-sm">Issue Map</Link></li>
              <li><Link to="/report" className="text-background/70 hover:text-secondary transition-colors text-sm">Report Issue</Link></li>
              <li><Link to="/dashboard" className="text-background/70 hover:text-secondary transition-colors text-sm">My Dashboard</Link></li>
              <li><Link to="/admin" className="text-background/70 hover:text-secondary transition-colors text-sm">Admin Portal</Link></li>
            </ul>
          </div>

          {/* Issue Categories */}
          <div>
            <h4 className="font-display font-semibold text-lg mb-4">Issue Categories</h4>
            <ul className="space-y-3">
              <li><span className="text-background/70 text-sm">Potholes & Roads</span></li>
              <li><span className="text-background/70 text-sm">Water & Drainage</span></li>
              <li><span className="text-background/70 text-sm">Street Lights</span></li>
              <li><span className="text-background/70 text-sm">Garbage & Sanitation</span></li>
              <li><span className="text-background/70 text-sm">Public Infrastructure</span></li>
            </ul>
          </div>

          {/* Contact */}
          <div>
            <h4 className="font-display font-semibold text-lg mb-4">Contact Us</h4>
            <ul className="space-y-3">
              <li className="flex items-center gap-3 text-background/70 text-sm">
                <Mail className="w-4 h-4 text-secondary" />
                support@civsetu.gov.in
              </li>
              <li className="flex items-center gap-3 text-background/70 text-sm">
                <Phone className="w-4 h-4 text-secondary" />
                1800-XXX-XXXX (Toll Free)
              </li>
              <li className="flex items-start gap-3 text-background/70 text-sm">
                <MapPin className="w-4 h-4 text-secondary mt-0.5" />
                Municipal Corporation Office,<br />
                City Center, State - 000000
              </li>
            </ul>
          </div>
        </div>

        <hr className="my-8 border-background/10" />

        <div className="flex flex-col md:flex-row items-center justify-between gap-4 text-sm text-background/60">
          <p>© 2025 CivSetu Created By Team DevX. A Government of India Initiative.</p>
          <div className="flex items-center gap-6">
            <Link to="/privacy" className="hover:text-secondary transition-colors">Privacy Policy</Link>
            <Link to="/terms" className="hover:text-secondary transition-colors">Terms of Service</Link>
            <Link to="/accessibility" className="hover:text-secondary transition-colors">Accessibility</Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
