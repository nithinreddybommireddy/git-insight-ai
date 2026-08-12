import { useState, useEffect, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { githubApiEnhanced, aiApi, type OrganizationAnalytics } from "@/services/api";
import toast from "react-hot-toast";
import {
  Search,
  ArrowLeft,
  Loader2,
  AlertCircle,
  Star,
  GitFork,
  Boxes,
  Users,
  Activity,
  Languages,
  Sparkles,
  Building2,
  Globe,
  MapPin,
  Calendar,
  RefreshCw,
  TrendingUp,
  Archive,
  Clock,
  Share2,
  GitCommit,
  GitPullRequest,
  CircleDot,
} from "lucide-react";

function StatChip({
  icon: Icon,
  label,
  value,
  accent,
}: {
  icon: any;
  label: string;
  value: string;
  accent: string;
}) {
  return (
    <div className="flex items-center gap-2.5 rounded-xl border border-border/50 bg-muted/20 px-3 py-2.5">
      <div className={`w-8 h-8 rounded-lg bg-gradient-to-br ${accent} flex items-center justify-center shrink-0`}>
        <Icon className="w-4 h-4 text-white" />
      </div>
      <div className="min-w-0">
        <p className="text-[10px] text-muted-foreground leading-none mb-1">{label}</p>
        <p className="text-sm font-bold tabular-nums truncate">{value}</p>
      </div>
    </div>
  );
}

function ActivityMetric({
  icon: Icon,
  label,
  value30,
  value90,
  accent,
}: {
  icon: any;
  label: string;
  value30: number;
  value90: number;
  accent: string;
}) {
  return (
    <div className="rounded-xl border border-border/50 bg-muted/10 p-3.5">
      <div className="flex items-center gap-2 mb-3">
        <div className={`w-7 h-7 rounded-lg bg-gradient-to-br ${accent} flex items-center justify-center shrink-0`}>
          <Icon className="w-3.5 h-3.5 text-white" />
        </div>
        <span className="text-xs font-semibold">{label}</span>
      </div>
      <div className="grid grid-cols-2 gap-2 text-center">
        <div className="rounded-lg bg-muted/30 py-2">
          <p className="text-sm font-bold tabular-nums">{value30.toLocaleString()}</p>
          <p className="text-[10px] text-muted-foreground mt-0.5">Last 30d</p>
        </div>
        <div className="rounded-lg bg-muted/30 py-2">
          <p className="text-sm font-bold tabular-nums">{value90.toLocaleString()}</p>
          <p className="text-[10px] text-muted-foreground mt-0.5">Last 90d</p>
        </div>
      </div>
    </div>
  );
}

export function OrgAnalytics() {
  const { orgName } = useParams<{ orgName: string }>();
  const navigate = useNavigate();
  const org = orgName ?? "";
  const [inputValue, setInputValue] = useState(org);
  const [overview, setOverview] = useState<OrganizationAnalytics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [aiReview, setAiReview] = useState<string | null>(null);
  const [aiLoading, setAiLoading] = useState(false);

  const fetchOverview = useCallback(async (name: string) => {
    if (!name.trim()) return;
    setLoading(true);
    setError(null);
    setOverview(null);
    setAiReview(null);
    try {
      const res = await githubApiEnhanced.getOrganizationOverview(name.trim());
      if (res.success) {
        setOverview(res.data);
      } else {
        setError(res.message || "Failed to load organization analytics");
      }
    } catch (err: any) {
      setError(
        err.response?.data?.message || err.message || "Failed to load organization analytics"
      );
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (org) {
      fetchOverview(org);
      setInputValue(org);
    }
  }, [org, fetchOverview]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (inputValue.trim()) {
      navigate(`/org/${encodeURIComponent(inputValue.trim())}`);
    }
  };

  const handleAiReview = async () => {
    if (!overview) return;
    setAiLoading(true);
    try {
      const res = await aiApi.getOrganizationReview(overview.login);
      if (res.success) {
        setAiReview(res.data);
      } else {
        toast.error(res.message || "Failed to generate AI review");
      }
    } catch (err: any) {
      toast.error(err.message || "Failed to generate AI review");
    } finally {
      setAiLoading(false);
    }
  };

  const maxLang = overview?.languages.length
    ? Math.max(...overview.languages.map((l) => l.percentage), 1)
    : 1;

  return (
    <div className="min-h-screen pt-20 sm:pt-24">
      {/* Search header */}
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
              <Building2 className="w-5 h-5 ml-4 text-muted-foreground shrink-0" />
              <Input
                type="text"
                placeholder="Enter a GitHub organization name (e.g. facebook, vercel, google)..."
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                className="flex-1 border-0 bg-transparent focus-visible:ring-0 text-base"
              />
              <Button
                type="submit"
                variant="primary"
                size="lg"
                className="shrink-0 gap-2 rounded-xl"
                disabled={!inputValue.trim()}
              >
                <Search className="w-4 h-4" />
                Analyze
              </Button>
            </div>
          </form>
        </div>
      </section>

      <section className="px-4 pb-16">
        <div className="max-w-4xl mx-auto space-y-6">
          {/* Loading */}
          {loading && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="flex flex-col items-center justify-center py-24"
            >
              <Loader2 className="w-14 h-14 text-primary animate-spin" />
              <p className="mt-6 text-muted-foreground animate-pulse">
                Aggregating organization data...
              </p>
            </motion.div>
          )}

          {/* Error */}
          {error && !loading && (
            <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }}>
              <Card className="p-10 text-center">
                <div className="w-20 h-20 rounded-full bg-gradient-to-br from-red-500/10 to-orange-500/10 flex items-center justify-center mx-auto mb-5">
                  <AlertCircle className="w-10 h-10 text-red-400" />
                </div>
                <h3 className="text-xl font-semibold mb-2">Organization Not Found</h3>
                <p className="text-muted-foreground mb-6 max-w-md mx-auto">{error}</p>
                <Button variant="primary" onClick={() => fetchOverview(inputValue)} className="gap-2">
                  <RefreshCw className="w-4 h-4" />
                  Try Again
                </Button>
              </Card>
            </motion.div>
          )}

          {/* Overview */}
          {overview && !loading && (
            <>
              {/* Org header */}
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                className="rounded-2xl glass-strong p-6"
              >
                <div className="flex flex-col sm:flex-row items-start sm:items-center gap-5">
                  {overview.avatarUrl ? (
                    <img
                      src={overview.avatarUrl}
                      alt={overview.login}
                      className="w-20 h-20 rounded-2xl border border-border/60 object-cover shrink-0"
                    />
                  ) : (
                    <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-primary to-accent flex items-center justify-center shrink-0">
                      <Building2 className="w-9 h-9 text-white" />
                    </div>
                  )}
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2 mb-1">
                      <h1 className="text-2xl font-bold tracking-tight">
                        {overview.name || overview.login}
                      </h1>
                      {overview.name && (
                        <span className="text-sm text-muted-foreground">@{overview.login}</span>
                      )}
                    </div>
                    {overview.description && (
                      <p className="text-sm text-muted-foreground leading-relaxed mb-3">
                        {overview.description}
                      </p>
                    )}
                    <div className="flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-muted-foreground">
                      {overview.location && (
                        <span className="inline-flex items-center gap-1">
                          <MapPin className="w-3 h-3" /> {overview.location}
                        </span>
                      )}
                      {overview.blog && (
                        <a
                          href={overview.blog.startsWith("http") ? overview.blog : `https://${overview.blog}`}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex items-center gap-1 hover:text-foreground transition-colors"
                        >
                          <Globe className="w-3 h-3" /> {overview.blog.replace(/^https?:\/\//, "")}
                        </a>
                      )}
                      {overview.createdAt && (
                        <span className="inline-flex items-center gap-1">
                          <Calendar className="w-3 h-3" /> Since{" "}
                          {new Date(overview.createdAt).getFullYear()}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              </motion.div>

              {/* Stats */}
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.05 }}
                className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3"
              >
                <StatChip icon={Boxes} label="Public Repos" value={`${overview.publicRepos}`} accent="from-sky-500 to-blue-500" />
                <StatChip icon={Star} label="Stars" value={overview.totalStars.toLocaleString()} accent="from-amber-500 to-orange-500" />
                <StatChip icon={GitFork} label="Forks" value={overview.totalForks.toLocaleString()} accent="from-violet-500 to-purple-500" />
                <StatChip icon={TrendingUp} label="Avg Stars / Repo" value={`${overview.averageStars}`} accent="from-emerald-500 to-teal-500" />
                <StatChip icon={Activity} label="Active (90d)" value={`${overview.activeRepos}`} accent="from-cyan-500 to-sky-500" />
                <StatChip icon={Languages} label="Languages" value={`${overview.languagesCount}`} accent="from-fuchsia-500 to-pink-500" />
              </motion.div>

              {/* Repository health */}
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.07 }}
                className="grid grid-cols-2 sm:grid-cols-3 gap-3"
              >
                <StatChip icon={Archive} label="Archived" value={`${overview.archivedRepos}`} accent="from-slate-500 to-slate-600" />
                <StatChip icon={Clock} label="Inactive (90d)" value={`${overview.inactiveRepos}`} accent="from-orange-500 to-amber-500" />
                <StatChip icon={Share2} label="Fork Ratio" value={`${overview.forkRatio}%`} accent="from-indigo-500 to-violet-500" />
              </motion.div>

              {/* Languages + Contributors */}
              <motion.div
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0.1 }}
                className="grid grid-cols-1 lg:grid-cols-2 gap-6"
              >
                <Card>
                  <CardContent className="!py-6">
                    <div className="flex items-center gap-2.5 mb-5">
                      <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-fuchsia-500 to-purple-500 flex items-center justify-center">
                        <Languages className="w-5 h-5 text-white" />
                      </div>
                      <div>
                        <h3 className="text-sm font-semibold leading-tight">Language Stack</h3>
                        <p className="text-[11px] text-muted-foreground">Byte-weighted across public repos</p>
                      </div>
                    </div>
                    {overview.languages.length === 0 ? (
                      <p className="text-xs text-muted-foreground text-center py-6">No language data</p>
                    ) : (
                      <div className="space-y-3">
                        {overview.languages.slice(0, 8).map((l, idx) => (
                          <div key={l.language}>
                            <div className="flex items-center justify-between mb-1">
                              <span className="text-[11px] font-medium">{l.language}</span>
                              <span className="text-[11px] text-muted-foreground tabular-nums">
                                {l.percentage.toFixed(1)}%
                              </span>
                            </div>
                            <div className="h-2 rounded-full bg-muted/30 overflow-hidden">
                              <motion.div
                                className="h-full rounded-full bg-gradient-to-r from-fuchsia-500 to-purple-400"
                                initial={{ width: 0 }}
                                animate={{ width: `${(l.percentage / maxLang) * 100}%` }}
                                transition={{ duration: 0.8, delay: 0.1 + idx * 0.05, ease: "easeOut" }}
                              />
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </CardContent>
                </Card>

                <Card>
                  <CardContent className="!py-6">
                    <div className="flex items-center gap-2.5 mb-5">
                      <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-emerald-500 to-teal-500 flex items-center justify-center">
                        <Users className="w-5 h-5 text-white" />
                      </div>
                      <div>
                        <h3 className="text-sm font-semibold leading-tight">Top Contributors</h3>
                        <p className="text-[11px] text-muted-foreground">
                          {overview.activeContributors} active contributors · sampled public repos
                        </p>
                      </div>
                    </div>
                    {overview.topContributors.length === 0 ? (
                      <p className="text-xs text-muted-foreground text-center py-6">No contributor data</p>
                    ) : (
                      <div className="space-y-2.5">
                        {overview.topContributors.slice(0, 10).map((c, idx) => (
                          <div key={c.login} className="space-y-1">
                            <div className="flex items-center gap-3">
                              <span className="w-5 text-[11px] text-muted-foreground tabular-nums text-right">
                                {idx + 1}
                              </span>
                              {c.avatarUrl && (
                                <img
                                  src={c.avatarUrl}
                                  alt={c.login}
                                  className="w-7 h-7 rounded-full border border-border/60 object-cover"
                                />
                              )}
                              <span className="text-xs font-medium truncate flex-1">{c.login}</span>
                              <span className="text-[11px] text-muted-foreground tabular-nums">
                                {c.contributions} commits
                              </span>
                              {typeof c.contributionPercent === "number" && (
                                <span className="w-11 text-right text-[11px] font-semibold text-emerald-400/90 tabular-nums">
                                  {c.contributionPercent.toFixed(0)}%
                                </span>
                              )}
                            </div>
                            {typeof c.contributionPercent === "number" && (
                              <div className="ml-8 h-1 rounded-full bg-muted/30 overflow-hidden">
                                <div
                                  className="h-full rounded-full bg-gradient-to-r from-emerald-500 to-teal-400"
                                  style={{ width: `${Math.max(c.contributionPercent, 2)}%` }}
                                />
                              </div>
                            )}
                          </div>
                        ))}
                      </div>
                    )}
                  </CardContent>
                </Card>
              </motion.div>

              {/* Team Activity */}
              <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.13 }}>
                <Card>
                  <CardContent className="!py-6">
                    <div className="flex items-center gap-2.5 mb-5">
                      <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-cyan-500 to-blue-500 flex items-center justify-center">
                        <Activity className="w-5 h-5 text-white" />
                      </div>
                      <div>
                        <h3 className="text-sm font-semibold leading-tight">Team Activity</h3>
                        <p className="text-[11px] text-muted-foreground">Commits · PRs · Issues across the top sampled repos</p>
                      </div>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                      <ActivityMetric
                        icon={GitCommit}
                        label="Commits"
                        value30={overview.teamActivity.commits30d}
                        value90={overview.teamActivity.commits90d}
                        accent="from-emerald-500 to-teal-500"
                      />
                      <ActivityMetric
                        icon={GitPullRequest}
                        label="Pull Requests"
                        value30={overview.teamActivity.pullRequests30d}
                        value90={overview.teamActivity.pullRequests90d}
                        accent="from-sky-500 to-blue-500"
                      />
                      <ActivityMetric
                        icon={CircleDot}
                        label="Issues"
                        value30={overview.teamActivity.issues30d}
                        value90={overview.teamActivity.issues90d}
                        accent="from-fuchsia-500 to-purple-500"
                      />
                    </div>
                  </CardContent>
                </Card>
              </motion.div>

              {/* Top repos */}
              {overview.topRepos.length > 0 && (
                <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.15 }}>
                  <Card>
                    <CardContent className="!py-6">
                      <div className="flex items-center gap-2.5 mb-5">
                        <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-sky-500 to-blue-500 flex items-center justify-center">
                          <Boxes className="w-5 h-5 text-white" />
                        </div>
                        <div>
                          <h3 className="text-sm font-semibold leading-tight">Top Repositories</h3>
                          <p className="text-[11px] text-muted-foreground">By stars across {overview.totalRepos} analyzed repos</p>
                        </div>
                      </div>
                      <div className="space-y-2.5">
                        {overview.topRepos.map((r) => (
                          <div key={r.name} className="flex items-start gap-3 rounded-lg border border-border/50 bg-muted/10 px-3 py-2.5">
                            <div className="min-w-0 flex-1">
                              <p className="text-xs font-semibold truncate">{r.name}</p>
                              {r.description && (
                                <p className="text-[11px] text-muted-foreground truncate mt-0.5">{r.description}</p>
                              )}
                              <div className="flex items-center gap-3 mt-1.5 text-[10px] text-muted-foreground">
                                {r.language && (
                                  <span className="inline-flex items-center gap-1">
                                    <span className="w-1.5 h-1.5 rounded-full bg-primary" /> {r.language}
                                  </span>
                                )}
                                <span className="inline-flex items-center gap-1">
                                  <Star className="w-3 h-3 text-amber-400" /> {r.stars}
                                </span>
                                <span className="inline-flex items-center gap-1">
                                  <GitFork className="w-3 h-3" /> {r.forks}
                                </span>
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </CardContent>
                  </Card>
                </motion.div>
              )}

              {/* Team summary + AI review */}
              <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.2 }}>
                <Card>
                  <CardContent className="!py-6">
                    <div className="flex items-center justify-between mb-5">
                      <div className="flex items-center gap-2.5">
                        <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-amber-500 to-orange-500 flex items-center justify-center">
                          <Sparkles className="w-5 h-5 text-white" />
                        </div>
                        <div>
                          <h3 className="text-sm font-semibold leading-tight">Team Summary</h3>
                          <p className="text-[11px] text-muted-foreground">Deterministic org-level analysis</p>
                        </div>
                      </div>
                      <Button variant="outline" size="sm" onClick={handleAiReview} disabled={aiLoading} className="gap-1.5">
                        {aiLoading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Sparkles className="w-3.5 h-3.5" />}
                        AI Team Review
                      </Button>
                    </div>

                    {aiLoading ? (
                      <div className="flex flex-col items-center py-10">
                        <Loader2 className="w-7 h-7 text-primary animate-spin mb-3" />
                        <p className="text-xs text-muted-foreground animate-pulse">
                          Gemini is reviewing the organization...
                        </p>
                      </div>
                    ) : (
                      <div className="space-y-3">
                        <p className="text-xs text-muted-foreground leading-relaxed">{overview.summary}</p>
                        <div className="rounded-lg bg-amber-500/5 border border-amber-500/15 p-3">
                          <p className="text-[11px] text-amber-400/90 leading-relaxed">
                            <span className="font-semibold">Insight: </span>
                            {overview.insight}
                          </p>
                        </div>
                        {aiReview && (
                          <div className="rounded-lg bg-gradient-to-br from-fuchsia-500/5 to-purple-500/5 border border-fuchsia-500/15 p-3">
                            <p className="text-[11px] text-muted-foreground leading-relaxed whitespace-pre-wrap">{aiReview}</p>
                          </div>
                        )}
                      </div>
                    )}
                  </CardContent>
                </Card>
              </motion.div>
            </>
          )}
        </div>
      </section>
    </div>
  );
}
