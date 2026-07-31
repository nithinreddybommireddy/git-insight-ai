import { useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import {
  githubApi,
  type CompareResult,
  type CompareUserData,
  type DeveloperScore,
  type MetricScore,
  type DeveloperInsights,
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
  TrendingUp,
  GitCommit,
  HeartPulse,
  ShieldCheck,
  EqualApproximately,
  Code2,
  Globe,
  Wrench,
  Brain,
  Lightbulb,
  ChevronDown,
  Sparkles,
} from "lucide-react";
import toast from "react-hot-toast";

// ── Variants ──

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.05 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 15 },
  visible: { opacity: 1, y: 0 },
};

// ── Helpers ──

function getScoreColor(score: number): string {
  if (score >= 80) return "from-emerald-500 to-green-500";
  if (score >= 65) return "from-cyan-500 to-blue-500";
  if (score >= 50) return "from-violet-500 to-purple-500";
  if (score >= 35) return "from-amber-500 to-yellow-500";
  if (score >= 20) return "from-orange-500 to-red-500";
  return "from-gray-500 to-slate-500";
}

function getLevelBadge(level: string): string {
  if (level.includes("Elite")) return "bg-gradient-to-r from-emerald-500 to-teal-500";
  if (level.includes("Expert")) return "bg-gradient-to-r from-cyan-500 to-blue-500";
  if (level.includes("Advanced")) return "bg-gradient-to-r from-violet-500 to-purple-500";
  if (level.includes("Proficient")) return "bg-gradient-to-r from-blue-500 to-indigo-500";
  if (level.includes("Intermediate")) return "bg-gradient-to-r from-amber-500 to-orange-500";
  if (level.includes("Beginner")) return "bg-gradient-to-r from-rose-500 to-pink-500";
  return "bg-gradient-to-r from-gray-500 to-slate-500";
}

function getTrendIcon(trend: string) {
  switch (trend) {
    case "up": return "\u25B2";
    case "down": return "\u25BC";
    default: return "\u25C6";
  }
}

function getTrendColor(trend: string): string {
  switch (trend) {
    case "up": return "text-emerald-400";
    case "down": return "text-red-400";
    default: return "text-muted-foreground";
  }
}

// ── Circular Score Gauge ──

function ScoreGauge({ value, size = 80 }: { value: number; size?: number }) {
  const circumference = 2 * Math.PI * (size * 0.38);
  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={size * 0.38} fill="none" stroke="currentColor" strokeWidth={6} className="text-muted/15" />
        <motion.circle
          cx={size / 2} cy={size / 2} r={size * 0.38} fill="none"
          stroke={`url(#sg-${value})`} strokeWidth={6} strokeLinecap="round"
          strokeDasharray={circumference}
          initial={{ strokeDashoffset: circumference }}
          animate={{ strokeDashoffset: circumference - (value / 100) * circumference }}
          transition={{ duration: 1.2, ease: "easeOut" }}
        />
        <defs>
          <linearGradient id={`sg-${value}`} x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor={value >= 80 ? "#34d399" : value >= 65 ? "#22d3ee" : value >= 50 ? "#a78bfa" : value >= 35 ? "#fbbf24" : "#f87171"} />
            <stop offset="100%" stopColor={value >= 80 ? "#2dd4bf" : value >= 65 ? "#818cf8" : value >= 50 ? "#c084fc" : value >= 35 ? "#f97316" : "#e11d48"} />
          </linearGradient>
        </defs>
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">
        <span className="text-lg font-bold tabular-nums">{value}</span>
      </div>
    </div>
  );
}

// ── Metric Score Card ──

const METRIC_ICONS: Record<string, any> = {
  "activity": TrendingUp,
  "git-commit": GitCommit,
  "heart-pulse": HeartPulse,
  "shield-check": ShieldCheck,
  "equal-approximately": EqualApproximately,
  "code-2": Code2,
  "users": Users,
  "globe": Globe,
  "trending-up": TrendingUp,
  "wrench": Wrench,
};

function MetricCard({
  score,
  user1Value,
  user2Value,
  user1Name,
  user2Name,
}: {
  score: MetricScore;
  user1Value: number;
  user2Value: number;
  user1Name: string;
  user2Name: string;
}) {
  const [showSuggestion, setShowSuggestion] = useState(false);
  const Icon = METRIC_ICONS[score.icon] || BarChart3;
  const isWinner = user1Value >= user2Value;

  return (
    <motion.div variants={itemVariants}>
      <Card className="h-full overflow-hidden hover:shadow-lg transition-all duration-300 group">
        <CardContent className="!p-5">
          {/* Header */}
          <div className="flex items-start justify-between mb-3">
            <div className="flex items-center gap-2.5">
              <div className={`w-9 h-9 rounded-lg bg-gradient-to-br ${getScoreColor(score.score)} flex items-center justify-center shadow-lg`}>
                <Icon className="w-4 h-4 text-white" />
              </div>
              <div>
                <h4 className="text-sm font-semibold">{score.label}</h4>
                <p className="text-[10px] text-muted-foreground">{score.description}</p>
              </div>
            </div>
            <span className={`text-[10px] font-bold ${getTrendColor(score.trend)}`}>
              {getTrendIcon(score.trend)}
            </span>
          </div>

          {/* Two scores side by side */}
          <div className="flex items-center gap-4 mb-3">
            <div className="flex-1 text-center">
              <p className="text-[10px] text-muted-foreground truncate">{user1Name}</p>
              <p className={`text-lg font-bold tabular-nums ${isWinner ? "text-primary" : "text-muted-foreground"}`}>{user1Value}</p>
            </div>
            <div className="text-[10px] text-muted-foreground font-bold">VS</div>
            <div className="flex-1 text-center">
              <p className="text-[10px] text-muted-foreground truncate">{user2Name}</p>
              <p className={`text-lg font-bold tabular-nums ${!isWinner ? "text-cyan-400" : "text-muted-foreground"}`}>{user2Value}</p>
            </div>
          </div>

          {/* Comparison bar */}
          <div className="flex h-2 rounded-full bg-muted/20 overflow-hidden mb-3">
            <motion.div
              className="h-full rounded-l-full bg-gradient-to-r from-primary to-accent"
              initial={{ width: 0 }}
              animate={{ width: `${(user1Value / Math.max(user1Value + user2Value, 1)) * 100}%` }}
              transition={{ duration: 0.8, ease: "easeOut" }}
            />
            <motion.div
              className="h-full rounded-r-full bg-gradient-to-r from-cyan-500 to-blue-500"
              initial={{ width: 0 }}
              animate={{ width: `${(user2Value / Math.max(user1Value + user2Value, 1)) * 100}%` }}
              transition={{ duration: 0.8, ease: "easeOut", delay: 0.2 }}
            />
          </div>

          {/* Explanation */}
          <p className="text-[11px] text-muted-foreground leading-relaxed mb-2">{score.explanation}</p>

          {/* Weight indicator */}
          <div className="flex items-center gap-1.5 mb-1">
            <div className="flex-1 h-1 rounded-full bg-muted/10 overflow-hidden">
              <div className="h-full rounded-full bg-primary/30" style={{ width: `${score.weight * 10}%` }} />
            </div>
            <span className="text-[9px] text-muted-foreground">{score.weight}% weight</span>
          </div>

          {/* Improvement suggestion toggle */}
          <button
            onClick={() => setShowSuggestion(!showSuggestion)}
            className="flex items-center gap-1 text-[10px] text-muted-foreground hover:text-primary transition-colors mt-1"
          >
            <Lightbulb className="w-3 h-3" />
            Improvement tip
            <ChevronDown className={`w-3 h-3 transition-transform ${showSuggestion ? "rotate-180" : ""}`} />
          </button>
          {showSuggestion && (
            <motion.p
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: "auto" }}
              className="text-[10px] text-amber-400/80 mt-1 leading-relaxed"
            >
              {score.improvementSuggestion}
            </motion.p>
          )}
        </CardContent>
      </Card>
    </motion.div>
  );
}

// ── Profile Column ──

function UserColumn({ data, side }: { data: CompareUserData; side: "left" | "right" }) {
  const { profile, repos } = data;
  if (!profile) return null;

  const totalStars = repos.reduce((s, r) => s + r.stars, 0);
  const totalForks = repos.reduce((s, r) => s + r.forks, 0);

  return (
    <motion.div variants={itemVariants} className={`flex-1 ${side === "left" ? "lg:pr-3" : "lg:pl-3"}`}>
      {data.score && (
        <motion.div initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} className="text-center mb-4">
          <span className={`inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold text-white ${getLevelBadge(data.score.level)}`}>
            <Trophy className="w-3 h-3" />
            {data.score.level}
          </span>
        </motion.div>
      )}

      <Card className="overflow-hidden hover:shadow-lg transition-all duration-300">
        <div className={`h-24 bg-gradient-to-br ${data.score ? getScoreColor(data.score.overallScore) : "from-muted to-muted/50"}`} />
        <CardContent className="relative !pt-0">
          <div className="flex justify-center -mt-12 mb-3">
            <div className="w-24 h-24 rounded-2xl border-4 border-background overflow-hidden shadow-xl">
              <img src={profile.avatarUrl} alt={profile.name || profile.username} className="w-full h-full object-cover" />
            </div>
          </div>

          <div className="text-center mb-4">
            <h3 className="text-lg font-bold">{profile.name || profile.username}</h3>
            {profile.name && <p className="text-xs text-muted-foreground">@{profile.username}</p>}
            {profile.bio && <p className="text-xs text-muted-foreground mt-1.5 line-clamp-2 max-w-xs mx-auto">{profile.bio}</p>}
            {profile.location && <p className="text-xs text-muted-foreground mt-1">{'\uD83D\uDCCD'} {profile.location}</p>}
          </div>

          {data.score && (
            <div className="flex justify-center mb-4">
              <ScoreGauge value={data.score.overallScore} size={90} />
            </div>
          )}

          <div className="grid grid-cols-2 gap-2 mb-3">
            <StatBox icon={Star} value={totalStars.toLocaleString()} label="Stars" />
            <StatBox icon={GitFork} value={totalForks.toLocaleString()} label="Forks" />
            <StatBox icon={BookOpen} value={profile.publicRepositories.toLocaleString()} label="Repos" />
            <StatBox icon={Users} value={profile.followers.toLocaleString()} label="Followers" />
          </div>

          {data.score && (
            <div className="space-y-1.5 pt-3 border-t border-border/50">
              <MetricMini label="Recency" value={data.score.contributionRecency} />
              <MetricMini label="Freq" value={data.score.commitFrequency} />
              <MetricMini label="Health" value={data.score.repositoryHealth} />
              <MetricMini label="Quality" value={data.score.repositoryQuality} />
              <MetricMini label="Consist" value={data.score.contributionConsistency} />
              <MetricMini label="Lang" value={data.score.languageDiversity} />
              <MetricMini label="Collab" value={data.score.collaboration} />
              <MetricMini label="OSS" value={data.score.openSourceImpact} />
              <MetricMini label="Popular" value={data.score.popularity} />
              <MetricMini label="Maint" value={data.score.maintenance} />
            </div>
          )}

          <div className="mt-3">
            <Button variant="ghost" size="sm" className="w-full gap-1.5 text-xs" asChild>
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

function MetricMini({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-[9px] text-muted-foreground w-10 shrink-0">{label}</span>
      <div className="flex-1 h-1.5 rounded-full bg-muted/20 overflow-hidden">
        <motion.div
          className={`h-full rounded-full bg-gradient-to-r ${value >= 60 ? "from-emerald-500 to-teal-500" : value >= 35 ? "from-amber-500 to-orange-500" : "from-rose-500 to-pink-500"}`}
          initial={{ width: 0 }}
          animate={{ width: `${value}%` }}
          transition={{ duration: 1, ease: "easeOut" }}
        />
      </div>
      <span className="text-[9px] font-semibold tabular-nums w-5 text-right">{value}</span>
    </div>
  );
}

// ── AI Insights Panel ──

function InsightsPanel({ insights }: { insights: DeveloperInsights | null }) {
  if (!insights) return null;

  return (
    <motion.div variants={itemVariants}>
      <Card className="overflow-hidden border-primary/20">
        <div className="h-1.5 bg-gradient-to-r from-primary via-accent to-primary" />
        <CardContent className="!p-6">
          <div className="flex items-center gap-2.5 mb-5">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary to-accent flex items-center justify-center">
              <Brain className="w-5 h-5 text-white" />
            </div>
            <div>
              <h3 className="text-base font-semibold">AI Developer Analysis</h3>
              <p className="text-[11px] text-muted-foreground">Powered by GitInsight Scoring Engine</p>
            </div>
          </div>

          <div className="space-y-4">
            <InsightBlock icon={Sparkles} label="Overall Assessment" text={insights.overallAssessment} />
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <InsightBlock icon={Trophy} label="Strongest Skill" text={insights.strongestSkill} isHighlight />
              <InsightBlock icon={AlertCircle} label="Weakest Area" text={insights.weakestArea} color="amber" />
            </div>
            <InsightBlock icon={Users} label="Collaboration Analysis" text={insights.collaborationAnalysis} />
            <InsightBlock icon={Globe} label="Open Source Impact" text={insights.openSourceImpact} />
            <InsightBlock icon={Code2} label="Technology Expertise" text={insights.technologyExpertise} />
            <InsightBlock icon={TrendingUp} label="Activity Trend" text={insights.activityTrend} />
            <InsightBlock icon={ShieldCheck} label="Repository Quality" text={insights.repositoryQualityObs} />
            <div className="rounded-xl bg-gradient-to-r from-amber-500/10 to-orange-500/10 border border-amber-500/20 p-4">
              <div className="flex items-start gap-3">
                <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-amber-500 to-orange-500 flex items-center justify-center shrink-0 mt-0.5">
                  <Lightbulb className="w-4 h-4 text-white" />
                </div>
                <div>
                  <p className="text-xs font-semibold text-amber-400 mb-1">Recommendations</p>
                  <p className="text-xs text-amber-300/80 leading-relaxed">{insights.recommendations}</p>
                </div>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </motion.div>
  );
}

function InsightBlock({
  icon: Icon,
  label,
  text,
  color,
  isHighlight,
}: {
  icon: any;
  label: string;
  text: string;
  color?: string;
  isHighlight?: boolean;
}) {
  const borderColor = color === "amber"
    ? "border-amber-500/20"
    : isHighlight
    ? "border-emerald-500/20"
    : "border-border/50";

  const bgColor = color === "amber"
    ? "from-amber-500/5 to-orange-500/5"
    : isHighlight
    ? "from-emerald-500/5 to-teal-500/5"
    : "from-muted/5 to-muted/10";

  return (
    <div className={`rounded-xl bg-gradient-to-br ${bgColor} border ${borderColor} p-3.5`}>
      <div className="flex items-start gap-2.5">
        <Icon className={`w-4 h-4 ${color === "amber" ? "text-amber-400" : isHighlight ? "text-emerald-400" : "text-primary"} mt-0.5 shrink-0`} />
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground mb-1">{label}</p>
          <p className="text-xs leading-relaxed">{text}</p>
        </div>
      </div>
    </div>
  );
}

// ── Main ComparePage ──

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
      if (!compareResult.user1.profile) toast.error(`Could not find user: ${u1}`);
      if (!compareResult.user2.profile) toast.error(`Could not find user: ${u2}`);
      if (compareResult.user1.profile && compareResult.user2.profile) toast.success("Comparison ready!");
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

  const metricsConfig: { key: keyof DeveloperScore; label: string }[] = [
    { key: "contributionRecency", label: "Contribution Recency" },
    { key: "commitFrequency", label: "Commit Frequency" },
    { key: "repositoryHealth", label: "Repository Health" },
    { key: "repositoryQuality", label: "Repository Quality" },
    { key: "contributionConsistency", label: "Consistency" },
    { key: "languageDiversity", label: "Language Diversity" },
    { key: "collaboration", label: "Collaboration" },
    { key: "openSourceImpact", label: "Open Source Impact" },
    { key: "popularity", label: "Popularity" },
    { key: "maintenance", label: "Maintenance" },
  ];

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
              Side-by-side analysis with 10 performance metrics, AI-powered insights, and actionable recommendations.
            </p>
          </motion.div>

          {/* Input form */}
          <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.1 }}>
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
                    <Button variant="ghost" size="sm" onClick={handleSwap} className="rounded-full w-10 h-10 p-0" title="Swap users">
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

                  <Button variant="primary" onClick={handleCompare} disabled={loading || !user1.trim() || !user2.trim()} className="gap-2 shrink-0 h-10">
                    {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Zap className="w-4 h-4" />}
                    Compare
                  </Button>
                </div>
              </CardContent>
            </Card>
          </motion.div>
        </div>
      </section>

      {/* Results */}
      <section className="px-4 pb-20">
        <div className="max-w-6xl mx-auto">
          {loading && (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="flex flex-col items-center justify-center py-24">
              <div className="relative">
                <Loader2 className="w-14 h-14 text-primary animate-spin" />
                <div className="absolute inset-0 w-14 h-14 rounded-full bg-primary/20 animate-ping" />
              </div>
              <p className="mt-6 text-muted-foreground animate-pulse">Fetching & analyzing both profiles...</p>
              <p className="mt-2 text-xs text-muted-foreground/60">Calculating 10 performance metrics</p>
            </motion.div>
          )}

          {error && (
            <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} className="max-w-md mx-auto text-center">
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

          {result && (
            <motion.div variants={containerVariants} initial="hidden" animate="visible">
              {/* Winner */}
              {winner !== "tie" && result.user1.score && result.user2.score && (
                <motion.div variants={itemVariants} className="text-center mb-6">
                  <div className="inline-flex items-center gap-3 px-6 py-3 rounded-2xl glass-strong">
                    <Trophy className="w-6 h-6 text-amber-400" />
                    <span className="text-sm font-medium">
                      <span className="font-bold">
                        {winner === "left" ? (result.user1.profile?.name || result.user1.username) : (result.user2.profile?.name || result.user2.username)}
                      </span>
                      {" wins "}
                      <span className="font-bold">{winner === "left" ? result.user1.score.overallScore : result.user2.score.overallScore}</span>
                      {" vs "}{winner === "left" ? result.user2.score.overallScore : result.user1.score.overallScore}
                    </span>
                  </div>
                </motion.div>
              )}

              {/* Profile columns */}
              <div className="flex flex-col lg:flex-row gap-6 mb-10">
                <UserColumn data={result.user1} side="left" />
                <div className="flex items-center justify-center lg:flex-col">
                  <div className="w-12 h-12 rounded-full glass-strong flex items-center justify-center">
                    <span className="text-sm font-bold gradient-text">VS</span>
                  </div>
                </div>
                <UserColumn data={result.user2} side="right" />
              </div>

              {/* 10 Metric Cards */}
              {result.user1.score && result.user2.score && (
                <motion.div variants={itemVariants} className="mb-10">
                  <div className="flex items-center gap-2.5 mb-5">
                    <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-violet-500 to-purple-500 flex items-center justify-center">
                      <BarChart3 className="w-5 h-5 text-white" />
                    </div>
                    <div>
                      <h2 className="text-lg font-bold">10-Point Metric Comparison</h2>
                      <p className="text-xs text-muted-foreground">Each metric scored 0-100 with weight, explanation, and improvement tip</p>
                    </div>
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-4">
                    {metricsConfig.map(({ key }) => {
                      const detailKey = (key + "Details") as keyof DeveloperScore;
                      const v1 = result.user1.score![key] as unknown as number;
                      const v2 = result.user2.score![key] as unknown as number;
                      const detail = result.user1.score![detailKey] as MetricScore | null;
                      if (!detail) return null;
                      return (
                        <MetricCard
                          key={key}
                          score={detail}
                          user1Value={v1}
                          user2Value={v2}
                          user1Name={result.user1.profile?.name || result.user1.username}
                          user2Name={result.user2.profile?.name || result.user2.username}
                        />
                      );
                    })}
                  </div>
                </motion.div>
              )}

              {/* AI Insights */}
              {result.user1.score?.insights && result.user2.score?.insights && (
                <motion.div variants={itemVariants} className="mb-10">
                  <div className="flex items-center gap-2.5 mb-5">
                    <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-amber-500 to-orange-500 flex items-center justify-center">
                      <Brain className="w-5 h-5 text-white" />
                    </div>
                    <div>
                      <h2 className="text-lg font-bold">AI-Powered Insights</h2>
                      <p className="text-xs text-muted-foreground">Detailed analysis for each developer</p>
                    </div>
                  </div>
                  <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    <InsightsPanel insights={result.user1.score.insights} />
                    <InsightsPanel insights={result.user2.score.insights} />
                  </div>
                </motion.div>
              )}

              <motion.div variants={itemVariants} className="text-center mt-6">
                <Button variant="outline" onClick={() => { setResult(null); setError(null); }} className="gap-2">
                  <X className="w-4 h-4" />
                  Compare Different Users
                </Button>
              </motion.div>
            </motion.div>
          )}

          {/* Empty state */}
          {!result && !loading && !error && (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="text-center py-16">
              <div className="w-24 h-24 rounded-3xl bg-gradient-to-br from-violet-500/10 to-purple-500/10 flex items-center justify-center mx-auto mb-5">
                <BarChart3 className="w-12 h-12 text-violet-400" />
              </div>
              <h3 className="text-xl font-semibold mb-2">Ready to Compare</h3>
              <p className="text-sm text-muted-foreground max-w-md mx-auto mb-8">
                Enter two GitHub usernames to get a deep 10-metric analysis with AI-powered insights.
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
                    <span className="font-medium">{a}</span> vs <span className="font-medium">{b}</span>
                  </button>
                ))}
              </div>
            </motion.div>
          )}
        </div>
      </section>

    </div>
  );
}
