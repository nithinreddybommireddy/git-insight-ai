import { Link } from "react-router-dom";
import {
  Heart,
  ExternalLink,
  Star,
  ArrowRight,
  Search,
  Brain,
  BarChart3,
  Users,
  LayoutDashboard,
  LogIn,
  FileText,
  GitCompareArrows,
} from "lucide-react";
import { useAuth } from "@/hooks/useAuth";

const productLinks = [
  { label: "Home", to: "/" },
  { label: "Search Profiles", to: "/search", icon: Search },
  { label: "Compare Developers", to: "/compare", icon: GitCompareArrows },
  { label: "AI Analysis", to: "/ai", icon: Brain },
  { label: "Reports & Score History", to: "/reports", icon: BarChart3 },
];

const resourceLinks = [
  { label: "GitHub Repository", href: "https://github.com/nithinreddybommireddy/git-insight-ai", icon: ExternalLink },
  { label: "Documentation", href: "https://github.com/nithinreddybommireddy/git-insight-ai/blob/main/README.md", icon: FileText },
];

export function Footer() {
  const { isAuthenticated } = useAuth();

  const accountLinks = isAuthenticated
    ? [
        { label: "Dashboard", to: "/dashboard", icon: LayoutDashboard },
        { label: "Recruiter Hub", to: "/recruiter", icon: Users },
      ]
    : [
        { label: "Sign In", to: "/login", icon: LogIn },
        { label: "Create Account", to: "/register", icon: Users },
      ];

  return (
    <footer className="border-t border-border bg-background/50 mt-16">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-10">
          {/* Brand */}
          <div className="lg:col-span-1 space-y-4">
            <Link to="/" className="flex items-center gap-2.5 group w-fit">
              <img
                src="/icon.svg"
                alt="GitInsight AI"
                className="w-9 h-9 rounded-xl ring-1 ring-primary/20 transition-all duration-300 group-hover:scale-110 group-hover:shadow-lg group-hover:shadow-primary/30"
              />
              <span className="text-lg font-bold tracking-tight">
                <span className="text-[#1c2f6b] dark:text-[#a8c4e0]">GitInsight</span>
                <span className="text-[#0a84ff]">-AI</span>
              </span>
            </Link>
            <p className="text-sm text-muted-foreground leading-relaxed max-w-xs">
              Data-driven developer scoring, commit &amp; code-quality analysis,
              and AI-powered insights — straight from real GitHub activity.
            </p>
            <a
              href="https://github.com/nithinreddybommireddy/git-insight-ai"
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              <Star className="w-4 h-4" />
              Star on GitHub
              <ExternalLink className="w-3.5 h-3.5" />
            </a>
          </div>

          {/* Product */}
          <div>
            <h3 className="text-sm font-semibold uppercase tracking-wider text-foreground mb-4">
              Product
            </h3>
            <ul className="space-y-3">
              {productLinks.map(({ label, to, icon: Icon }) => (
                <li key={to}>
                  <Link
                    to={to}
                    className="group inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-primary transition-colors"
                  >
                    {Icon && <Icon className="w-4 h-4 opacity-70 group-hover:opacity-100" />}
                    {label}
                    <ArrowRight className="w-3.5 h-3.5 opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-200" />
                  </Link>
                </li>
              ))}
            </ul>
          </div>

          {/* Account */}
          <div>
            <h3 className="text-sm font-semibold uppercase tracking-wider text-foreground mb-4">
              Account
            </h3>
            <ul className="space-y-3">
              {accountLinks.map(({ label, to, icon: Icon }) => (
                <li key={to}>
                  <Link
                    to={to}
                    className="group inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-primary transition-colors"
                  >
                    {Icon && <Icon className="w-4 h-4 opacity-70 group-hover:opacity-100" />}
                    {label}
                    <ArrowRight className="w-3.5 h-3.5 opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-200" />
                  </Link>
                </li>
              ))}
              {isAuthenticated && (
                <li>
                  <Link
                    to="/search"
                    className="group inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-primary transition-colors"
                  >
                    <Search className="w-4 h-4 opacity-70 group-hover:opacity-100" />
                    Find Developers
                    <ArrowRight className="w-3.5 h-3.5 opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-200" />
                  </Link>
                </li>
              )}
            </ul>
          </div>

          {/* Resources */}
          <div>
            <h3 className="text-sm font-semibold uppercase tracking-wider text-foreground mb-4">
              Resources
            </h3>
            <ul className="space-y-3">
              {resourceLinks.map(({ label, href, icon: Icon }) => (
                <li key={href}>
                  <a
                    href={href}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="group inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-primary transition-colors"
                  >
                    {Icon && <Icon className="w-4 h-4 opacity-70 group-hover:opacity-100" />}
                    {label}
                    <ExternalLink className="w-3.5 h-3.5 opacity-0 -translate-x-1 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-200" />
                  </a>
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Bottom bar */}
        <div className="mt-12 pt-6 border-t border-border flex flex-col md:flex-row items-center justify-between gap-4">
          <p className="text-sm text-muted-foreground">
            © {new Date().getFullYear()} GitInsight AI. All rights reserved.
          </p>
          <p className="text-sm text-muted-foreground flex items-center gap-1">
            Made with <Heart className="w-3.5 h-3.5 text-red-500 fill-red-500" /> by
            Nithin Reddy
          </p>
        </div>
      </div>
    </footer>
  );
}
