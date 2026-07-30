import { useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { Footer } from "@/components/Footer";
import {
  githubApi,
  type CompareResult,
  type CompareUserData,
} from "@/services/api";
import {
  Search,
  ArrowLeft,
  Loader2,
  AlertCircle,
  Star,
  GitFork,
  BookOpen,
  Trophy,
  Users,
  BarChart3,
  Zap,
  ExternalLink,
  ArrowRight,
  X,
  RefreshCw,
} from "lucide-react";
import toast from "react-hot-toast";

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.08 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 15 },
  visible: { opacity: 1, y: 0 },
};

function getScoreColor(score: number): string {
  if (score >= 80) return "from-emerald-500 to-green-500";
  if (score >= 60) return "from-cyan-500 to-blue-500";
  if (score >= 40) return "from-amber-500 to-yellow-500";
  if (score >= 20) return "from-red-500 to-rose-500";
  return "from-gray-500 to-slate-500";
}

function getLevelBadge(level: string): string {
  switch (level) {
    case "Expert": return "bg-gradient-to-r from-emerald-500 to-teal-500";
    case "Advanced": return "bg-gradient-to-r from-cyan-500 to-blue-500";
    case "Intermediate": return "bg-gradient-to-r from-amber-500 to-orange-500";
    case "Beginner": return "bg-gradient-to-r from-rose-500 to-pink-500";
    default: return "bg-gradient-to-r from-gray-500 to-slate-500";
  }
}

function ScoreMiniGauge({ value, size = 80 }: { value: number; size?: number }) {
  const circumference = 2 * Math.PI * (size * 0.38);

  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={size * 0.38}
          fill="none"
          stroke="currentColor"
          strokeWidth={8}
          className="text-muted/15"
        />
        <motion.circle
          cx={size / 2}
          cy={size / 2}
          r={size * 0.38}
          fill="none"
          stroke={`url(#gauge-${value})`}
          strokeWidth={8}
          strokeLinecap="round"
          strokeDasharray={circumference}
          initial={{ strokeDashoffset: circumference }}
          animate={{ strokeDashoffset: circumference - (value / 100) * circumference }}
          transition={{ duration: 1.5, ease: "easeOut" }}
        />
        <defs>
          <linearGradient id={`gauge-${value}`} x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor={value >= 80 ? "#34d399" : value >= 60 ? "#22d3ee" : value >= 40 ? "#fbbf24" : "#f87171"} />
            <stop offset="100%" stopColor={value >= 80 ? "#2dd4bf" : value >= 60 ? "#818cf8" : value >= 40 ? "#f97316" : "#e11d48"} />
          </linearGradient>
        </defs>
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">
        <span className="text-lg font-bold tabular-nums">{value}</span>
      </div>
    </div>
  );
}

function ComparisonBar({ label, value1, value2 }: { label: string; value1: number; value2: number }) {
  const total = value1 + value2 || 1;
  const pct1 = (value1 / total) * 100;
  const pct2 = (value2 / total) * 100;

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between">
        <span className="text-[11px] font-medium text-muted-foreground uppercase tracking-wider">{label}</span>
        <div className="flex items-center gap-3 text-xs tabular-nums">
          <span className={value1 >= value2 ? "text-primary font-bold" : "text-muted-foreground"}>{value1}</span>
          <span className="text-muted-foreground/40">vs</span>
          <span className={value2 >= value1 ? "text-primary font-bold" : "text-muted-foreground"}>{value2}</span>
        </div>
      </div>
      <div className="flex h-2 rounded-full bg-muted/20 overflow-hidden">
        <motion.div
          className="h-full rounded-l-full bg-gradient-to-r from-primary to-accent"
          initial={{ width: 0 }}
          animate={{ width: `${pct1}%` }}
          transition={{ duration: 0.8, ease: "easeOut" }}
        />
        <motion.div
          className="h-full rounded-r-full bg-gradient-to-r from-cyan-500 to-blue-500"
          initial={{ width: 0 }}
          animate={{ width: `${pct2}%` }}
          transition={{ duration: 0.8, ease: "easeOut", delay: 0.2 }}
        />
      </div>
    </div>
  );
}

function UserColumn({ data, side }: { data: CompareUserData; side: "left" | "right" }) {
  const { profile, score, repos } = data;
  const isLeft = side === "left";

  if (!profile) return null;

  const starredLangs = repos
    .filter(r => r.language)
    .reduce((acc: Record<string, number>, r) => {
      acc[r.language!] = (acc[r.language!] || 0) + 1;
      return acc;
    }, {});

  const topLangs = Object.entries(starredLangs)
    .sort(([, a], [, b]) => b - a)
    .slice(0, 4);

  const totalStars = repos.reduce((s, r) => s + r.stars, 0);
  const totalForks = repos.reduce((s, r) => s + r.forks, 0);

  return (
    <motion.div
      variants={itemVariants}
      className={`flex-1 ${isLeft ? "lg:pr-3" : "lg:pl-3"}`}
    >
      {/* Winner badge */}
      {score && (
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          className="text-center mb-4"
        >
          <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold text-white ${getLevelBadge(score.level)}`}>
            <Trophy className="w-3 h-3" />
            {score.level}
          </span>
        </motion.div>
      )}

      {/* Profile card */}
      <Card className="overflow-hidden hover:shadow-lg transition-all duration-300">
        <div className={`h-24 bg-gradient-to-br ${score ? getScoreColor(score.overallScore) : "from-muted to-muted/50"}`} />
        <CardContent className="relative !pt-0">
          <div className="flex justify-center -mt-12 mb-3">
            <div className="w-24 h-24 rounded-2xl border-4 border-background overflow-hidden shadow-xl">
              <img
                src={profile.avatarUrl}
                alt={profile.name || profile.username}
                className="w-full h-full object-cover"
              />
            </div>
          </div>

          <div className="text-center mb-4">
            <h3 className="text-lg font-bold">{profile.name || profile.username}</h3>
            {profile.name && (
              <p className="text-xs text-muted-foreground">@{profile.username}</p>
            )}
            {profile.bio && (
              <p className="text-xs text-muted-foreground mt-1.5 line-clamp-2 max-w-xs mx-auto">{profile.bio}</p>
            )}
            {profile.location && (
              <p className="text-xs text-muted-foreground mt-1">📍 {profile.location}</p>
            )}
          </div>

          {/* Mini score gauge */}
          {score && (
            <div className="flex justify-center mb-4">
              <ScoreMiniGauge value={score.overallScore} />
            </div>
          )}

          {/* Quick stats */}
          <div className="grid grid-cols-2 gap-2 mb-3">
            <StatBox icon={Star} value={totalStars.toLocaleString()} label="Stars" />
            <StatBox icon={GitFork} value={totalForks.toLocaleString()} label="Forks" />
            <StatBox icon={BookOpen} value={profile.publicRepositories.toLocaleString()} label="Repos" />
            <StatBox icon={Users} value={profile.followers.toLocaleString()} label="Followers" />
          </div>

          {/* Score breakdown */}
          {score && (
            <div className="space-y-2 pt-3 border-t border-border/50">
              <ScoreBarMini label="Health" value={score.avgHealthScore} />
              <ScoreBarMini label="Recency" value={score.contributionRecencyScore} />
              <ScoreBarMini label="Commits" value={score.commitFrequencyScore} />
              <ScoreBarMini label="Consist." value={score.consistencyScore} />
            </div>
          )}

          {/* Top languages */}
          {topLangs.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mt-3 pt-3 border-t border-border/50">
              {topLangs.map(([lang]) => (
                <span key={lang} className="text-[10px] px-2 py-0.5 rounded-full bg-muted/50 text-muted-foreground">
                  {lang}
                </span>
              ))}
            </div>
          )}

          {/* View profile link */}
          <div className="mt-3">
            <Button
              variant="ghost"
              size="sm"
              className="w-full gap-1.5 text-xs"
              asChild
            >
              <a href={profile.profileUrl} target="_blank" rel="noopener noreferrer">
                <ExternalLink className="w-3 h-3" />
                View GitHub Profile
              </a>
            </Button>
          </div>
        </CardContent>
      </Card>
    </motion.div>
  );
}

function StatBox({ icon: Icon, value, label }: { icon: any; value: string; label: string }) {
  return (
    <div className="glass rounded-lg p-2.5 text-center">
      <Icon className="w-3.5 h-3.5 mx-auto mb-0.5 text-muted-foreground" />
      <p className="text-xs font-bold tabular-nums">{value}</p>
      <p className="text-[9px] text-muted-foreground uppercase tracking-wider">{label}</p>
    </div>
  );
}

function ScoreBarMini({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-[10px] text-muted-foreground w-12 shrink-0">{label}</span>
      <div className="flex-1 h-1.5 rounded-full bg-muted/20 overflow-hidden">
        <motion.div
          className={`h-full rounded-full bg-gradient-to-r ${value >= 60 ? "from-emerald-500 to-teal-500" : value >= 40 ? "from-amber-500 to-orange-500" : "from-rose-500 to-pink-500"}`}
          initial={{ width: 0 }}
          animate={{ width: `${value}%` }}
          transition={{ duration: 1, ease: "easeOut" }}
        />
      </div>
      <span className="text-[10px] font-semibold tabular-nums w-6 text-right">{value}</span>
    </div>
  );
}

export function ComparePage() {
  const navigate = useNavigate();
  const [user1, setUser1] = useState("");
  const [user2, setUser2] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<CompareResult | null>(null);

  const handleCompare = useCallback(async () => {
    const u1 = user1.trim();
    const u2 = user2.trim();
    if (!u1 || !u2) {
      toast.error("Please enter both usernames");
      return;
    }

    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const compareResult = await githubApi.compare(u1, u2);
      setResult(compareResult);

      if (!compareResult.user1.profile) {
        toast.error(`Could not find user: ${u1}`);
      }
      if (!compareResult.user2.profile) {
        toast.error(`Could not find user: ${u2}`);
      }
      if (compareResult.user1.profile && compareResult.user2.profile) {
        toast.success("Comparison ready!");
      }
    } catch (err: any) {
      setError(err.message || "Failed to compare. Please try again.");
    } finally {
      setLoading(false);
    }
  }, [user1, user2]);

  const handleSwap = () => {
    setUser1(user2);
    setUser2(user1);
    setResult(null);
    setError(null);
  };

  const getWinner = (): "left" | "right" | "tie" => {
    if (!result?.user1.score || !result?.user2.score) return "tie";
    if (result.user1.score.overallScore > result.user2.score.overallScore) return "left";
    if (result.user2.score.overallScore > result.user1.score.overallScore) return "right";
    return "tie";
  };

  const winner = getWinner();

  return (
    <div className="min-h-screen pt-20 sm:pt-24">
      {/* Header */}
      <section className="px-4 pb-6">
        <div className="max-w-6xl mx-auto">
          <div className="flex items-center gap-2 mb-4">
            <Button variant="ghost" size="sm" onClick={() => navigate("/")} className="gap-1.5">
              <ArrowLeft className="w-4 h-4" />
              Back
            </Button>
          </div>

          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="text-center mb-6"
          >
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-violet-500 to-purple-500 flex items-center justify-center mx-auto mb-3 shadow-lg shadow-violet-500/20">
              <BarChart3 className="w-7 h-7 text-white" />
            </div>
            <h1 className="text-3xl sm:text-4xl font-bold mb-2">
              Compare <span className="gradient-text">Developers</span>
            </h1>
            <p className="text-sm text-muted-foreground max-w-lg mx-auto">
              Enter two GitHub usernames to compare their profiles, scores, and repositories side by side.
            </p>
          </motion.div>

          {/* Dual input form */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
          >
            <Card>
              <CardContent className="!py-5">
                <div className="flex flex-col sm:flex-row items-end gap-3">
                  <div className="flex-1 w-full">
                    <label className="text-xs text-muted-foreground mb-1.5 block font-medium">Developer 1</label>
                    <div className="relative">
                      <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                      <Input
                        type="text"
                        placeholder="e.g. torvalds"
                        value={user1}
                        onChange={(e) => setUser1(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && handleCompare()}
                        className="pl-9"
                      />
                    </div>
                  </div>

                  <div className="flex items-center gap-2 px-2 py-1">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={handleSwap}
                      className="rounded-full w-10 h-10 p-0"
                      title="Swap users"
                    >
                      <ArrowRight className="w-4 h-4 rotate-90 sm:rotate-0" />
                    </Button>
                  </div>

                  <div className="flex-1 w-full">
                    <label className="text-xs text-muted-foreground mb-1.5 block font-medium">Developer 2</label>
                    <div className="relative">
                      <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                      <Input
                        type="text"
                        placeholder="e.g. addyosmani"
                        value={user2}
                        onChange={(e) => setUser2(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && handleCompare()}
                        className="pl-9"
                      />
                    </div>
                  </div>

                  <Button
                    variant="primary"
                    onClick={handleCompare}
                    disabled={loading || !user1.trim() || !user2.trim()}
                    className="gap-2 shrink-0 h-10"
                  >
                    {loading ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <Zap className="w-4 h-4" />
                    )}
                    Compare
                  </Button>
                </div>
              </CardContent>
            </Card>
          </motion.div>
        </div>
      </section>

      {/* Results section */}
      <section className="px-4 pb-16">
        <div className="max-w-6xl mx-auto">
          {/* Loading */}
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
              <p className="mt-6 text-muted-foreground animate-pulse">Fetching both profiles...</p>
              <p className="mt-2 text-xs text-muted-foreground/60">Gathering data from GitHub API</p>
            </motion.div>
          )}

          {/* Error */}
          {error && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              className="max-w-md mx-auto text-center"
            >
              <Card className="p-8">
                <div className="w-16 h-16 rounded-full bg-gradient-to-br from-red-500/10 to-orange-500/10 flex items-center justify-center mx-auto mb-4">
                  <AlertCircle className="w-8 h-8 text-red-400" />
                </div>
                <h3 className="text-lg font-semibold mb-2">Comparison Failed</h3>
                <p className="text-sm text-muted-foreground mb-4">{error}</p>
                <Button variant="outline" size="sm" onClick={handleCompare} className="gap-2">
                  <RefreshCw className="w-4 h-4" />
                  Try Again
                </Button>
              </Card>
            </motion.div>
          )}

          {/* Comparison results */}
          {result && (
            <motion.div
              variants={containerVariants}
              initial="hidden"
              animate="visible"
            >
              {/* Winner announcement */}
              {winner !== "tie" && result.user1.score && result.user2.score && (
                <motion.div
                  variants={itemVariants}
                  className="text-center mb-6"
                >
                  <div className="inline-flex items-center gap-3 px-6 py-3 rounded-2xl glass-strong">
                    <Trophy className="w-6 h-6 text-amber-400" />
                    <span className="text-sm font-medium">
                      <span className="font-bold">
                        {winner === "left" ? result.user1.profile?.name || result.user1.username : result.user2.profile?.name || result.user2.username}
                      </span>
                      {" "}wins with{" "}
                      <span className="font-bold">
                        {winner === "left" ? result.user1.score.overallScore : result.user2.score.overallScore}
                      </span>{" "}
                      vs{" "}
                      {winner === "left" ? result.user2.score.overallScore : result.user1.score.overallScore}
                    </span>
                  </div>
                </motion.div>
              )}

              {/* Side by side comparison */}
              <div className="flex flex-col lg:flex-row gap-6 mb-8">
                <UserColumn data={result.user1} side="left" />
                {/* VS divider */}
                <div className="flex items-center justify-center lg:flex-col">
                  <div className="w-12 h-12 rounded-full glass-strong flex items-center justify-center">
                    <span className="text-sm font-bold gradient-text">VS</span>
                  </div>
                </div>
                <UserColumn data={result.user2} side="right" />
              </div>

              {/* Detailed comparison bars */}
              {result.user1.score && result.user2.score && (
                <motion.div variants={itemVariants}>
                  <Card>
                    <CardContent className="!py-6">
                      <div className="flex items-center gap-2 mb-5">
                        <BarChart3 className="w-5 h-5 text-muted-foreground" />
                        <h3 className="text-base font-semibold">Score Comparison</h3>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div className="space-y-3">
                          <ComparisonBar
                            label="Overall Score"
                            value1={result.user1.score.overallScore}
                            value2={result.user2.score.overallScore}
                          />
                          <ComparisonBar
                            label="Repository Health"
                            value1={result.user1.score.avgHealthScore}
                            value2={result.user2.score.avgHealthScore}
                          />
                          <ComparisonBar
                            label="Contribution Recency"
                            value1={result.user1.score.contributionRecencyScore}
                            value2={result.user2.score.contributionRecencyScore}
                          />
                          <ComparisonBar
                            label="Popularity"
                            value1={result.user1.score.avgPopularityScore}
                            value2={result.user2.score.avgPopularityScore}
                          />
                        </div>
                        <div className="space-y-3">
                          <ComparisonBar
                            label="Total Stars"
                            value1={result.user1.score.totalStars}
                            value2={result.user2.score.totalStars}
                          />
                          <ComparisonBar
                            label="Commit Frequency"
                            value1={result.user1.score.commitFrequencyScore}
                            value2={result.user2.score.commitFrequencyScore}
                          />
                          <ComparisonBar
                            label="Consistency"
                            value1={result.user1.score.consistencyScore}
                            value2={result.user2.score.consistencyScore}
                          />
                          <ComparisonBar
                            label="Maintenance"
                            value1={result.user1.score.avgMaintenanceScore}
                            value2={result.user2.score.avgMaintenanceScore}
                          />
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                </motion.div>
              )}

              {/* New comparison button */}
              <motion.div variants={itemVariants} className="text-center mt-6">
                <Button
                  variant="outline"
                  onClick={() => { setResult(null); setError(null); }}
                  className="gap-2"
                >
                  <X className="w-4 h-4" />
                  Compare Different Users
                </Button>
              </motion.div>
            </motion.div>
          )}

          {/* Empty state */}
          {!result && !loading && !error && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="text-center py-16"
            >
              <div className="w-24 h-24 rounded-3xl bg-gradient-to-br from-violet-500/10 to-purple-500/10 flex items-center justify-center mx-auto mb-5">
                <BarChart3 className="w-12 h-12 text-violet-400" />
              </div>
              <h3 className="text-xl font-semibold mb-2">Ready to Compare</h3>
              <p className="text-sm text-muted-foreground max-w-md mx-auto mb-8">
                Enter two GitHub usernames above and click Compare to see a detailed side-by-side analysis.
              </p>
              <div className="flex flex-wrap justify-center gap-4">
                {[
                  ["torvalds", "addyosmani"],
                  ["gaearon", "sindresorhus"],
                  ["tj", "nithinreddybommireddy"],
                ].map(([a, b]) => (
                  <button
                    key={`${a}-${b}`}
                    onClick={() => { setUser1(a); setUser2(b); }}
                    className="text-xs px-3 py-2 rounded-xl glass text-muted-foreground hover:text-foreground hover:scale-105 transition-all duration-200"
                  >
                    <span className="font-medium">{a}</span>
                    {" vs "}
                    <span className="font-medium">{b}</span>
                  </button>
                ))}
              </div>
            </motion.div>
          )}
        </div>
      </section>

      <Footer />
    </div>
  );
}
