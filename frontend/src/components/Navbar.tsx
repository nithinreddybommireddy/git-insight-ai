import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useTheme } from "@/hooks/useTheme";
import {
  Search,
  Code2,
  Menu,
  X,
  Sun,
  Moon,
  ExternalLink,
} from "lucide-react";

export function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 20);
    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  return (
    <nav
      className={cn(
        "fixed top-0 left-0 right-0 z-50 transition-all duration-500",
        scrolled
          ? "glass-strong shadow-lg shadow-black/10 dark:shadow-black/30"
          : "bg-transparent"
      )}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16 sm:h-20">
          {/* Logo */}
          <Link to="/" className="flex items-center gap-2.5 group">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-primary to-accent flex items-center justify-center transition-all duration-300 group-hover:scale-110 group-hover:shadow-lg group-hover:shadow-primary/30">
              <Code2 className="w-5 h-5 text-white" />
            </div>
            <span className="text-lg font-bold">
              <span className="gradient-text">GitInsight</span>
              <span className="text-foreground"> AI</span>
            </span>
          </Link>

          {/* Desktop Nav */}
          <div className="hidden md:flex items-center gap-1">
            <Link
              to="/"
              className="px-4 py-2 text-sm text-muted-foreground hover:text-foreground hover:bg-muted/50 rounded-xl transition-all duration-200"
            >
              Home
            </Link>
            <Link
              to="/search"
              className="px-4 py-2 text-sm text-muted-foreground hover:text-foreground hover:bg-muted/50 rounded-xl transition-all duration-200"
            >
              Search
            </Link>

            {/* Theme Toggle */}
            <button
              onClick={toggleTheme}
              className="p-2.5 rounded-xl hover:bg-muted/50 text-muted-foreground hover:text-foreground transition-all duration-200 group relative"
              aria-label="Toggle theme"
            >
              {theme === "dark" ? (
                <Sun className="w-4 h-4 group-hover:rotate-90 transition-transform duration-500" />
              ) : (
                <Moon className="w-4 h-4 group-hover:-rotate-12 transition-transform duration-500" />
              )}
            </button>

            {/* GitHub Star */}
            <a
              href="https://github.com/nithinreddybommireddy/git-insight-ai"
              target="_blank"
              rel="noopener noreferrer"
              className="p-2.5 rounded-xl hover:bg-muted/50 text-muted-foreground hover:text-foreground transition-all duration-200"
              aria-label="GitHub repository"
            >
              <ExternalLink className="w-4 h-4" />
            </a>

            <div className="w-px h-6 bg-border mx-2" />

            <Button
              variant="primary"
              size="sm"
              onClick={() => navigate("/search")}
              className="gap-2"
            >
              <Search className="w-4 h-4" />
              Search Profile
            </Button>
          </div>

          {/* Mobile: Theme + GitHub + Menu */}
          <div className="flex md:hidden items-center gap-1">
            <button
              onClick={toggleTheme}
              className="p-2 rounded-lg hover:bg-muted/50 text-muted-foreground transition-colors"
              aria-label="Toggle theme"
            >
              {theme === "dark" ? (
                <Sun className="w-5 h-5" />
              ) : (
                <Moon className="w-5 h-5" />
              )}
            </button>
            <button
              onClick={() => setMobileOpen(!mobileOpen)}
              className="p-2 rounded-lg hover:bg-muted/50 text-muted-foreground transition-colors"
              aria-label="Toggle menu"
            >
              {mobileOpen ? (
                <X className="w-5 h-5" />
              ) : (
                <Menu className="w-5 h-5" />
              )}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Menu */}
      <div
        className={cn(
          "md:hidden glass-strong border-t border-border overflow-hidden transition-all duration-400 ease-in-out",
          mobileOpen ? "max-h-80" : "max-h-0"
        )}
      >
        <div className="px-4 py-4 space-y-2">
          <Link
            to="/"
            className="block px-4 py-3 rounded-xl hover:bg-muted/50 text-sm text-foreground transition-colors"
            onClick={() => setMobileOpen(false)}
          >
            Home
          </Link>
          <Link
            to="/search"
            className="block px-4 py-3 rounded-xl hover:bg-muted/50 text-sm text-foreground transition-colors"
            onClick={() => setMobileOpen(false)}
          >
            Search Profiles
          </Link>
          <div className="pt-2">
            <Button
              variant="primary"
              size="md"
              className="w-full gap-2"
              onClick={() => {
                setMobileOpen(false);
                navigate("/search");
              }}
            >
              <Search className="w-4 h-4" />
              Search Profile
            </Button>
          </div>
        </div>
      </div>
    </nav>
  );
}
