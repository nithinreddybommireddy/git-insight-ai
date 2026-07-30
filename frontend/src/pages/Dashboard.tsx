import { useState, useEffect } from "react";
import { useNavigate, Link } from "react-router-dom";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Footer } from "@/components/Footer";
import { useAuth } from "@/hooks/useAuth";
import toast from "react-hot-toast";
import {
  Search,
  ArrowRight,
  Clock,
  TrendingUp,
  LogOut,
  Settings,
  BarChart3,
  BookOpen,
} from "lucide-react";

export function Dashboard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState("");
  const [recentSearches, setRecentSearches] = useState<string[]>([]);

  useEffect(() => {
    try {
      const stored = localStorage.getItem("gitinsight-recent-searches");
      setRecentSearches(stored ? JSON.parse(stored) : []);
    } catch {
      // ignore
    }
  }, []);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/search?q=${encodeURIComponent(searchQuery.trim())}`);
    }
  };

  const quickActions = [
    {
      icon: Search,
      title: "Analyze Profile",
      description: "Enter any GitHub username to get insights",
      action: () => navigate("/search"),
      gradient: "from-violet-500 to-purple-500",
    },
    {
      icon: TrendingUp,
      title: "Compare Developers",
      description: "Compare two GitHub profiles side by side",
      action: () => toast.success("Compare feature coming soon!", { icon: "🚧" }),
      gradient: "from-cyan-500 to-blue-500",
    },
    {
      icon: BarChart3,
      title: "View Reports",
      description: "Download or view generated analytics reports",
      action: () => toast.success("Reports coming soon!", { icon: "🚧" }),
      gradient: "from-amber-500 to-orange-500",
    },
    {
      icon: BookOpen,
      title: "My History",
      description: "View your recent profile analyses",
      action: () => toast.success("History coming soon!", { icon: "🚧" }),
      gradient: "from-emerald-500 to-teal-500",
    },
  ];

  if (!user) return null;

  return (
    <div className="min-h-screen pt-20 pb-16">
      <div className="max-w-6xl mx-auto px-4">
        {/* Welcome Header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-8"
        >
          <div className="flex items-center justify-between flex-wrap gap-4">
            <div className="flex items-center gap-4">
              <div className="w-14 h-14 rounded-full bg-gradient-to-br from-primary to-accent flex items-center justify-center text-white text-xl font-bold">
                {user.name.charAt(0).toUpperCase()}
              </div>
              <div>
                <h1 className="text-2xl font-bold">Welcome, {user.name.split(" ")[0]}!</h1>
                <p className="text-sm text-muted-foreground">{user.email}</p>
              </div>
            </div>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" className="gap-2" asChild>
                <Link to="/settings">
                  <Settings className="w-4 h-4" />
                  Settings
                </Link>
              </Button>
              <Button variant="ghost" size="sm" className="gap-2 text-muted-foreground" onClick={logout}>
                <LogOut className="w-4 h-4" />
                Logout
              </Button>
            </div>
          </div>
        </motion.div>

        {/* Search Bar */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="mb-10"
        >
          <Card>
            <CardContent className="!py-6">
              <form onSubmit={handleSearch} className="relative group">
                <div className="absolute -inset-1 bg-gradient-to-r from-primary via-accent to-primary rounded-2xl opacity-20 blur group-hover:opacity-40 transition-opacity duration-500" />
                <div className="relative flex items-center glass-strong rounded-2xl p-1.5">
                  <Search className="w-5 h-5 ml-4 text-muted-foreground shrink-0" />
                  <Input
                    type="text"
                    placeholder="Enter any GitHub username to analyze..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="flex-1 border-0 bg-transparent focus-visible:ring-0 text-base"
                  />
                  <Button type="submit" variant="primary" size="lg" className="shrink-0 gap-2 rounded-xl" disabled={!searchQuery.trim()}>
                    Analyze
                    <ArrowRight className="w-4 h-4" />
                  </Button>
                </div>
              </form>
            </CardContent>
          </Card>
        </motion.div>

        {/* Quick Actions */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="mb-8"
        >
          <h2 className="text-lg font-semibold mb-4">Quick Actions</h2>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {quickActions.map((action) => (
              <button
                key={action.title}
                onClick={action.action}
                className="text-left group"
              >
                <Card className="h-full hover:scale-[1.02] hover:-translate-y-0.5 transition-all duration-200 cursor-pointer">
                  <CardContent className="!p-5">
                    <div className={`w-10 h-10 rounded-lg bg-gradient-to-br ${action.gradient} flex items-center justify-center mb-3 group-hover:scale-110 transition-transform duration-200`}>
                      <action.icon className="w-5 h-5 text-white" />
                    </div>
                    <h3 className="font-medium text-sm mb-1">{action.title}</h3>
                    <p className="text-xs text-muted-foreground">{action.description}</p>
                  </CardContent>
                </Card>
              </button>
            ))}
          </div>
        </motion.div>

        {/* Recent Searches */}
        {recentSearches.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.3 }}
          >
            <h2 className="text-lg font-semibold mb-4">Recent Searches</h2>
            <div className="flex flex-wrap gap-2">
              {recentSearches.slice(0, 8).map((name) => (
                <button
                  key={name}
                  onClick={() => navigate(`/search?q=${name}`)}
                  className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl glass text-sm text-muted-foreground hover:text-foreground hover:scale-105 transition-all duration-200"
                >
                  <Clock className="w-3 h-3" />
                  {name}
                </button>
              ))}
            </div>
          </motion.div>
        )}
      </div>
      <Footer />
    </div>
  );
}
