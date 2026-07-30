import { useState, useEffect, useCallback } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import { ProfileCard } from "@/components/ProfileCard";
import { RepoList } from "@/components/RepoList";
import { DeveloperScoreCard } from "@/components/DeveloperScore";
import { Footer } from "@/components/Footer";
import {
  githubApi,
  type GitHubProfile,
  type Repository,
  type DeveloperScore,
  type ApiResponse,
} from "@/services/api";
import toast from "react-hot-toast";
import {
  Search,
  ArrowLeft,
  Loader2,
  AlertCircle,
  Clock,
  RefreshCw,
  TrendingUp,
  Lightbulb,
  ArrowRight,
  X,
  Trash2,
  ExternalLink,
} from "lucide-react";

const SUGGESTED_USERS = [
  "nithinreddybommireddy",
  "torvalds",
  "addyosmani",
  "sindresorhus",
  "gaearon",
  "tj",
];

const TIPS = [
  "GitHub usernames are case-sensitive!",
  "Try searching for popular open-source contributors",
  "Use the Compare feature to evaluate two developers side by side",
  "Developer Score and AI reviews are coming soon!",
];

function getRandomTip() {
  return TIPS[Math.floor(Math.random() * TIPS.length)];
}

function getRecentSearches(): string[] {
  try {
    const stored = localStorage.getItem("gitinsight-recent-searches");
    return stored ? JSON.parse(stored) : [];
  } catch {
    return [];
  }
}

function addRecentSearch(username: string) {
  const recents = getRecentSearches().filter((s) => s !== username);
  recents.unshift(username);
  localStorage.setItem("gitinsight-recent-searches", JSON.stringify(recents.slice(0, 8)));
}

function clearRecentSearches() {
  localStorage.removeItem("gitinsight-recent-searches");
}

export function SearchResults() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const query = searchParams.get("q") || "";
  const [inputValue, setInputValue] = useState(query);
  const [profile, setProfile] = useState<GitHubProfile | null>(null);
  const [repos, setRepos] = useState<Repository[]>([]);
  const [score, setScore] = useState<DeveloperScore | null>(null);
  const [loading, setLoading] = useState(false);
  const [reposLoading, setReposLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [recentSearches, setRecentSearches] = useState<string[]>(getRecentSearches);
  const [tip] = useState(getRandomTip);

  const fetchProfile = useCallback(async (username: string) => {
    if (!username.trim()) return;
    setLoading(true);
    setError(null);
    setProfile(null);

    try {
      const response: ApiResponse<GitHubProfile> = await githubApi.getProfile(username);
      if (response.success) {
        setProfile(response.data);
        addRecentSearch(username);
        setRecentSearches(getRecentSearches);

        // Fetch repos and score in parallel
        setReposLoading(true);
        Promise.allSettled([
          githubApi.getRepositories(username),
          githubApi.getDeveloperScore(username),
        ]).then(([reposResult, scoreResult]) => {
          if (reposResult.status === "fulfilled" && reposResult.value.success) {
            setRepos(reposResult.value.data);
          }
          if (scoreResult.status === "fulfilled" && scoreResult.value.success) {
            setScore(scoreResult.value.data);
          }
          setReposLoading(false);
        });
      } else {
        setError(response.message);
      }
    } catch (err: any) {
      const msg =
        err.response?.data?.message ||
        err.message ||
        "Failed to fetch profile. Please try again.";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (query) {
      fetchProfile(query);
    }
  }, [query, fetchProfile]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (inputValue.trim()) {
      setSearchParams({ q: inputValue.trim() });
    }
  };

  const handleClearRecents = () => {
    clearRecentSearches();
    setRecentSearches([]);
    toast.success("Recent searches cleared!");
  };

  return (
    <div className="min-h-screen pt-20 sm:pt-24">
      {/* Search Bar Section */}
      <section className="px-4 pb-6">
        <div className="max-w-4xl mx-auto">
          <div className="flex items-center gap-2 mb-4">
            <Button variant="ghost" size="sm" onClick={() => navigate("/")} className="gap-1.5">
              <ArrowLeft className="w-4 h-4" />
              Back
            </Button>
          </div>

          <form onSubmit={handleSearch} className="relative group">
            <div className="absolute -inset-1 bg-gradient-to-r from-primary via-accent to-primary rounded-2xl opacity-20 blur group-hover:opacity-40 transition-opacity duration-500" />
            <div className="relative flex items-center glass-strong rounded-2xl p-1.5">
              <Search className="w-5 h-5 ml-4 text-muted-foreground shrink-0" />
              <Input
                type="text"
                placeholder="Enter any GitHub username..."
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                className="flex-1 border-0 bg-transparent focus-visible:ring-0 text-base"
              />
              {inputValue && (
                <button
                  type="button"
                  onClick={() => setInputValue("")}
                  className="p-1.5 rounded-lg hover:bg-muted/50 text-muted-foreground transition-colors mr-1"
                >
                  <X className="w-4 h-4" />
                </button>
              )}
              <Button
                type="submit"
                variant="primary"
                size="lg"
                className="shrink-0 gap-2 rounded-xl"
                disabled={!inputValue.trim()}
              >
                <Search className="w-4 h-4" />
                Search
              </Button>
            </div>
          </form>

          {/* Suggested + Recent */}
          {!query && !profile && !loading && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className="mt-6 space-y-4"
            >
              {/* Recent Searches */}
              {recentSearches.length > 0 && (
                <div>
                  <div className="flex items-center justify-between mb-2.5">
                    <div className="flex items-center gap-2">
                      <Clock className="w-4 h-4 text-muted-foreground" />
                      <span className="text-sm text-muted-foreground font-medium">Recent</span>
                    </div>
                    <button
                      onClick={handleClearRecents}
                      className="flex items-center gap-1 text-xs text-muted-foreground hover:text-foreground transition-colors"
                    >
                      <Trash2 className="w-3 h-3" />
                      Clear
                    </button>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {recentSearches.slice(0, 6).map((name) => (
                      <button
                        key={name}
                        onClick={() => {
                          setInputValue(name);
                          setSearchParams({ q: name });
                        }}
                        className="group/btn inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl glass text-sm text-muted-foreground hover:text-foreground hover:scale-105 transition-all duration-200"
                      >
                        <Clock className="w-3 h-3 group-hover/btn:hidden" />
                        <Search className="w-3 h-3 hidden group-hover/btn:block" />
                        {name}
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* Suggested Developers */}
              <div>
                <div className="flex items-center gap-2 mb-2.5">
                  <TrendingUp className="w-4 h-4 text-muted-foreground" />
                  <span className="text-sm text-muted-foreground font-medium">Try these developers</span>
                </div>
                <div className="flex flex-wrap gap-2">
                  {SUGGESTED_USERS.map((name) => (
                    <button
                      key={name}
                      onClick={() => {
                        setInputValue(name);
                        setSearchParams({ q: name });
                      }}
                      className="group/btn inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl glass text-sm text-muted-foreground hover:text-foreground hover:scale-105 transition-all duration-200"
                    >
                      <ExternalLink className="w-3.5 h-3.5" />
                      {name}
                      <ArrowRight className="w-3 h-3 opacity-0 group-hover/btn:opacity-100 -ml-2 group-hover/btn:ml-0 transition-all duration-200" />
                    </button>
                  ))}
                </div>
              </div>

              {/* Tip */}
              <Card variant="glass" className="!p-4">
                <div className="flex items-start gap-3">
                  <div className="w-8 h-8 rounded-lg bg-amber-500/10 flex items-center justify-center shrink-0 mt-0.5">
                    <Lightbulb className="w-4 h-4 text-amber-400" />
                  </div>
                  <div>
                    <p className="text-sm font-medium text-foreground mb-0.5">Quick Tip</p>
                    <p className="text-xs text-muted-foreground">{tip}</p>
                  </div>
                </div>
              </Card>
            </motion.div>
          )}
        </div>
      </section>

      {/* Results Section */}
      <section className="px-4 pb-16">
        <div className="max-w-4xl mx-auto">
          {/* Loading State */}
          {loading && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="flex flex-col items-center justify-center py-24"
            >
              <div className="relative">
                <Loader2 className="w-14 h-14 text-primary animate-spin" />
                <div className="absolute inset-0 w-14 h-14 rounded-full bg-primary/20 animate-ping" />
              </div>
              <p className="mt-6 text-muted-foreground animate-pulse">
                Fetching GitHub profile...
              </p>
              <p className="mt-2 text-xs text-muted-foreground/60">
                Contacting GitHub API
              </p>
            </motion.div>
          )}

          {/* Error State */}
          {error && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
            >
              <Card className="p-10 text-center">
                <div className="w-20 h-20 rounded-full bg-gradient-to-br from-red-500/10 to-orange-500/10 flex items-center justify-center mx-auto mb-5">
                  <AlertCircle className="w-10 h-10 text-red-400" />
                </div>
                <h3 className="text-xl font-semibold mb-2">
                  {error.toLowerCase().includes("not found")
                    ? "Profile Not Found"
                    : "Something Went Wrong"}
                </h3>
                <p className="text-muted-foreground mb-6 max-w-md mx-auto">
                  {error}
                </p>

                <div className="flex flex-wrap justify-center gap-3 mb-8">
                  <Button
                    variant="primary"
                    onClick={() => fetchProfile(query)}
                    className="gap-2"
                  >
                    <RefreshCw className="w-4 h-4" />
                    Try Again
                  </Button>
                  <Button
                    variant="outline"
                    onClick={() => navigate("/")}
                    className="gap-2"
                  >
                    <ArrowLeft className="w-4 h-4" />
                    Back to Home
                  </Button>
                </div>

                <div className="border-t border-border pt-6">
                  <p className="text-sm text-muted-foreground mb-3">
                    Try searching for another developer
                  </p>
                  <div className="flex flex-wrap justify-center gap-2">
                    {SUGGESTED_USERS.slice(0, 4).map((name) => (
                      <button
                        key={name}
                        onClick={() => {
                          setInputValue(name);
                          setSearchParams({ q: name });
                        }}
                        className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl glass text-sm text-muted-foreground hover:text-foreground hover:scale-105 transition-all duration-200"
                      >                        <ExternalLink className="w-3.5 h-3.5" />
                        {name}
                      </button>
                    ))
                  }
                </div>
              </div>
              </Card>
            </motion.div>
          )}

          {/* Profile Result */}
          {profile && <ProfileCard profile={profile} />}

          {/* Developer Score */}
          {score && (
            <div className="mt-6">
              <DeveloperScoreCard score={score} />
            </div>
          )}

          {/* Repos Loading */}
          {reposLoading && profile && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="mt-6"
            >
              <div className="text-center py-8">
                <Loader2 className="w-6 h-6 text-primary animate-spin mx-auto mb-2" />
                <p className="text-xs text-muted-foreground">Analyzing repositories...</p>
              </div>
            </motion.div>
          )}

          {/* Repository List */}
          {repos.length > 0 && (
            <div className="mt-6">
              <RepoList repos={repos} />
            </div>
          )}
        </div>
      </section>

      <Footer />
    </div>
  );
}


