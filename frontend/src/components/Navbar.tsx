import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useTheme } from "@/hooks/useTheme";
import { useAuth } from "@/hooks/useAuth";
import {
  Code2,
  Menu,
  X,
  Sun,
  Moon,
  ExternalLink,
  User,
  LogOut,
  LayoutDashboard,
  Users,
  LogIn,
  Brain,
} from "lucide-react";

export function Navbar() {
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);
  const navigate = useNavigate();
  const { theme, toggleTheme } = useTheme();
  const { user, isAuthenticated, logout } = useAuth();

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
            </Link>            <Link
              to="/compare"
              className="px-4 py-2 text-sm text-muted-foreground hover:text-foreground hover:bg-muted/50 rounded-xl transition-all duration-200"
            >
              Compare
            </Link>
            <Link
              to="/ai"
              className="px-4 py-2 text-sm text-muted-foreground hover:text-foreground hover:bg-muted/50 rounded-xl transition-all duration-200 inline-flex items-center gap-1.5"
            >
              <Brain className="w-3.5 h-3.5" />
              AI
            </Link>
            {isAuthenticated && (
              <Link
                to="/recruiter"
                className="px-4 py-2 text-sm text-muted-foreground hover:text-foreground hover:bg-muted/50 rounded-xl transition-all duration-200 inline-flex items-center gap-1.5"
              >
                <Users className="w-3.5 h-3.5" />
                Recruiter
              </Link>
            )}

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

            {/* Auth Section */}
            {isAuthenticated && user ? (
              <div className="flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => navigate("/dashboard")}
                  className="gap-2"
                >
                  <div className="w-6 h-6 rounded-full bg-gradient-to-br from-primary to-accent flex items-center justify-center text-[10px] font-bold text-white">
                    {user.name.charAt(0).toUpperCase()}
                  </div>
                  <span className="text-sm">{user.name.split(" ")[0]}</span>
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={logout}
                  className="gap-1.5 text-muted-foreground"
                >
                  <LogOut className="w-4 h-4" />
                </Button>
              </div>
            ) : (
              <div className="flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => navigate("/login")}
                  className="gap-1.5"
                >
                  <LogIn className="w-4 h-4" />
                  Sign In
                </Button>
                <Button
                  variant="primary"
                  size="sm"
                  onClick={() => navigate("/register")}
                >
                  Sign Up
                </Button>
              </div>
            )}

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
          "md:hidden glass-strong border-t border-border overflow-hidden transition-all duration-300 ease-in-out",
          mobileOpen ? "max-h-96" : "max-h-0"
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
          <Link
            to="/compare"
            className="block px-4 py-3 rounded-xl hover:bg-muted/50 text-sm text-foreground transition-colors"
            onClick={() => setMobileOpen(false)}
          >
            Compare
          </Link>
          <Link
            to="/ai"
            className="flex items-center gap-2 px-4 py-3 rounded-xl hover:bg-muted/50 text-sm text-foreground transition-colors"
            onClick={() => setMobileOpen(false)}
          >
            <Brain className="w-4 h-4" />
            AI Analysis
          </Link>
          {isAuthenticated ? (
            <>
              <Link
                to="/dashboard"
                className="flex items-center gap-2 px-4 py-3 rounded-xl hover:bg-muted/50 text-sm text-foreground transition-colors"
                onClick={() => setMobileOpen(false)}
              >
                <LayoutDashboard className="w-4 h-4" />
                Dashboard
              </Link>
              <Link
                to="/recruiter"
                className="flex items-center gap-2 px-4 py-3 rounded-xl hover:bg-muted/50 text-sm text-foreground transition-colors"
                onClick={() => setMobileOpen(false)}
              >
                <Users className="w-4 h-4" />
                Recruiter
              </Link>
              <button
                onClick={() => { logout(); setMobileOpen(false); }}
                className="flex items-center gap-2 px-4 py-3 rounded-xl hover:bg-muted/50 text-sm text-foreground transition-colors w-full text-left"
              >
                <LogOut className="w-4 h-4" />
                Logout
              </button>
            </>
          ) : (
            <>
              <Link
                to="/login"
                className="block px-4 py-3 rounded-xl hover:bg-muted/50 text-sm text-foreground transition-colors"
                onClick={() => setMobileOpen(false)}
              >
                Sign In
              </Link>
              <div className="pt-2">
                <Button
                  variant="primary"
                  size="md"
                  className="w-full gap-2"
                  onClick={() => {
                    setMobileOpen(false);
                    navigate("/register");
                  }}
                >
                  <User className="w-4 h-4" />
                  Sign Up
                </Button>
              </div>
            </>
          )}
        </div>
      </div>
    </nav>
  );
}
