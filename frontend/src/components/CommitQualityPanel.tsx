import { useState } from "react";
import { motion } from "framer-motion";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { aiApi, type CommitAnalytics } from "@/services/api";
import toast from "react-hot-toast";
import {
  GitCommitHorizontal,
  Sparkles,
  Loader2,
  Plus,
  Minus,
  CalendarRange,
  MessageSquareCode,
  Scale,
  TrendingUp,
  AlertCircle,
} from "lucide-react";

interface CommitQualityPanelProps {
  analytics: CommitAnalytics | null;
  loading?: boolean;
  username: string;
}

function scoreColor(score: number): string {
  if (score >= 70) return "from-emerald-500 to-teal-400";
  if (score >= 40) return "from-amber-500 to-orange-400";
  return "from-rose-500 to-red-400";
}

function scoreText(score: number): string {
  if (score >= 70) return "text-emerald-400";
  if (score >= 40) return "text-amber-400";
  return "text-rose-400";
}

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

export function CommitQualityPanel({ analytics, loading, username }: CommitQualityPanelProps) {
  const [aiReview, setAiReview] = useState<string | null>(null);
  const [aiLoading, setAiLoading] = useState(false);

  const handleAiReview = async () => {
    if (!username) return;
    setAiLoading(true);
    try {
      const res = await aiApi.getCodeQuality(username);
      if (res.success) {
        setAiReview(res.data.aiReview);
        if (res.data.analytics) {
          toast.success("AI code quality review generated");
        }
      } else {
        toast.error(res.message || "Failed to generate AI review");
      }
    } catch (err: any) {
      toast.error(err.message || "Failed to generate AI review");
    } finally {
      setAiLoading(false);
    }
  };

  const maxWeekly = analytics?.weeklyActivity?.length
    ? Math.max(...analytics.weeklyActivity.map((w) => w.commits), 1)
    : 1;

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.2 }}
      className="space-y-6"
    >
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Code Quality Score */}
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-8">
            <div className="flex items-center gap-2.5 mb-6">
              <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-fuchsia-500 to-purple-500 flex items-center justify-center">
                <GitCommitHorizontal className="w-5 h-5 text-white" />
              </div>
              <div>
                <h3 className="text-sm font-semibold leading-tight">Code Quality</h3>
                <p className="text-[11px] text-muted-foreground">Commit hygiene &amp; history</p>
              </div>
            </div>

            {loading ? (
              <div className="w-32 h-32 rounded-full bg-muted/30 animate-pulse" />
            ) : !analytics ? (
              <div className="text-center py-6">
                <AlertCircle className="w-8 h-8 text-muted-foreground/40 mx-auto mb-2" />
                <p className="text-xs text-muted-foreground">No commit data available</p>
              </div>
            ) : (
              <>
                <div className="relative w-32 h-32">
                  <svg viewBox="0 0 120 120" className="w-full h-full -rotate-90">
                    <circle cx="60" cy="60" r="52" fill="none" strokeWidth="10" className="stroke-muted/20" />
                    <motion.circle
                      cx="60"
                      cy="60"
                      r="52"
                      fill="none"
                      strokeWidth="10"
                      strokeLinecap="round"
                      strokeDasharray={2 * Math.PI * 52}
                      initial={{ strokeDashoffset: 2 * Math.PI * 52 }}
                      animate={{
                        strokeDashoffset: 2 * Math.PI * 52 * (1 - analytics.codeQualityScore / 100),
                      }}
                      transition={{ duration: 1.1, ease: "easeOut" }}
                      className={`stroke-[url(#cqGradient)]`}
                    />
                    <defs>
                      <linearGradient id="cqGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stopColor="#e879f9" />
                        <stop offset="100%" stopColor="#818cf8" />
                      </linearGradient>
                    </defs>
                  </svg>
                  <div className="absolute inset-0 flex flex-col items-center justify-center">
                    <span className={`text-3xl font-extrabold ${scoreText(analytics.codeQualityScore)}`}>
                      {analytics.codeQualityScore}
                    </span>
                    <span className="text-[10px] text-muted-foreground">/ 100</span>
                  </div>
                </div>

                {analytics.trend && (
                  <div className="flex items-center gap-1.5 mt-4 text-[11px] text-muted-foreground">
                    <TrendingUp
                      className={`w-3.5 h-3.5 ${
                        analytics.trend === "up"
                          ? "text-emerald-400"
                          : analytics.trend === "down"
                            ? "text-rose-400"
                            : "text-muted-foreground"
                      }`}
                    />
                    {analytics.trend === "up"
                      ? "Improving"
                      : analytics.trend === "down"
                        ? "Declining"
                        : "Stable"}
                  </div>
                )}
              </>
            )}
          </CardContent>
        </Card>

        {/* Stats */}
        <Card className="lg:col-span-2">
          <CardContent className="!py-6">
            <div className="flex items-center justify-between mb-5">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-sky-500 to-blue-500 flex items-center justify-center">
                  <GitCommitHorizontal className="w-5 h-5 text-white" />
                </div>
                <div>
                  <h3 className="text-sm font-semibold leading-tight">Commit Activity</h3>
                  <p className="text-[11px] text-muted-foreground">Real commit history across repositories</p>
                </div>
              </div>
              {analytics?.totalCommits !== undefined && analytics.totalCommits > 0 && (
                <span className="inline-flex items-center gap-1 text-[11px] px-2.5 py-1 rounded-full bg-sky-500/10 text-sky-400 border border-sky-500/20">
                  {analytics.reposAnalyzed} {analytics.reposAnalyzed === 1 ? "repo" : "repos"}
                </span>
              )}
            </div>

            {loading ? (
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                {[...Array(4)].map((_, i) => (
                  <div key={i} className="h-16 rounded-xl bg-muted/30 animate-pulse" />
                ))}
              </div>
            ) : !analytics ? (
              <div className="text-center py-8">
                <GitCommitHorizontal className="w-8 h-8 text-muted-foreground/40 mx-auto mb-2" />
                <p className="text-xs text-muted-foreground">No commit activity data available</p>
              </div>
            ) : (
              <>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                  <StatChip
                    icon={GitCommitHorizontal}
                    label="Total Commits"
                    value={analytics.totalCommits.toLocaleString()}
                    accent="from-sky-500 to-blue-500"
                  />
                  <StatChip
                    icon={CalendarRange}
                    label="Commits / Week"
                    value={`${analytics.commitsPerWeek.toFixed(1)}`}
                    accent="from-violet-500 to-purple-500"
                  />
                  <StatChip
                    icon={Plus}
                    label="Lines Added"
                    value={analytics.totalAdditions.toLocaleString()}
                    accent="from-emerald-500 to-teal-500"
                  />
                  <StatChip
                    icon={Minus}
                    label="Lines Removed"
                    value={analytics.totalDeletions.toLocaleString()}
                    accent="from-rose-500 to-red-500"
                  />
                </div>

                {/* Message quality + commit size bars */}
                <div className="mt-5 grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="text-[11px] font-medium flex items-center gap-1.5">
                        <MessageSquareCode className="w-3 h-3 text-muted-foreground" />
                        Commit Message Quality
                      </span>
                      <span className={`text-[11px] font-semibold tabular-nums ${scoreText(analytics.commitMessageQuality)}`}>
                        {analytics.commitMessageQuality}
                      </span>
                    </div>
                    <div className="h-2 rounded-full bg-muted/30 overflow-hidden">
                      <motion.div
                        className={`h-full rounded-full bg-gradient-to-r ${scoreColor(analytics.commitMessageQuality)}`}
                        initial={{ width: 0 }}
                        animate={{ width: `${analytics.commitMessageQuality}%` }}
                        transition={{ duration: 0.9, ease: "easeOut" }}
                      />
                    </div>
                    <p className="text-[10px] text-muted-foreground mt-1.5">
                      {analytics.conventionalCommitRate}% conventional · avg {analytics.averageMessageLength} chars
                    </p>
                  </div>
                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <span className="text-[11px] font-medium flex items-center gap-1.5">
                        <Scale className="w-3 h-3 text-muted-foreground" />
                        Commit Size Balance
                      </span>
                      <span className={`text-[11px] font-semibold tabular-nums ${scoreText(analytics.commitSizeScore)}`}>
                        {analytics.commitSizeScore}
                      </span>
                    </div>
                    <div className="h-2 rounded-full bg-muted/30 overflow-hidden">
                      <motion.div
                        className={`h-full rounded-full bg-gradient-to-r ${scoreColor(analytics.commitSizeScore)}`}
                        initial={{ width: 0 }}
                        animate={{ width: `${analytics.commitSizeScore}%` }}
                        transition={{ duration: 0.9, delay: 0.1, ease: "easeOut" }}
                      />
                    </div>
                    <p className="text-[10px] text-muted-foreground mt-1.5">
                      {analytics.topCommitTypes.length > 0
                        ? `Top types: ${analytics.topCommitTypes.slice(0, 4).join(", ")}`
                        : "No conventional commit prefixes detected"}
                    </p>
                  </div>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Weekly activity + explanation */}
      {analytics && analytics.totalCommits > 0 && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <Card>
            <CardContent className="!py-6">
              <div className="flex items-center justify-between mb-5">
                <div className="flex items-center gap-2.5">
                  <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-indigo-500 to-violet-500 flex items-center justify-center">
                    <CalendarRange className="w-5 h-5 text-white" />
                  </div>
                  <div>
                    <h3 className="text-sm font-semibold leading-tight">Weekly Activity</h3>
                    <p className="text-[11px] text-muted-foreground">Commits per week (last 12 weeks)</p>
                  </div>
                </div>
              </div>

              {analytics.weeklyActivity.length === 0 ? (
                <p className="text-xs text-muted-foreground text-center py-6">No weekly activity data</p>
              ) : (
                <div className="flex items-end gap-1.5 h-32">
                  {analytics.weeklyActivity.map((w, idx) => (
                    <div key={w.week} className="flex-1 flex flex-col items-center gap-1 min-w-0 group">
                      <motion.div
                        className={`w-full rounded-t-md bg-gradient-to-t from-indigo-500 to-violet-400 ${
                          w.commits === 0 ? "opacity-20" : ""
                        }`}
                        initial={{ height: 0 }}
                        animate={{ height: `${Math.max((w.commits / maxWeekly) * 100, 2)}%` }}
                        transition={{ duration: 0.7, delay: 0.1 + idx * 0.04, ease: "easeOut" }}
                      />
                      <span className="text-[8px] text-muted-foreground hidden sm:block truncate w-full text-center">
                        {w.week.split("-W")[1] ?? w.week}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardContent className="!py-6">
              <div className="flex items-center justify-between mb-5">
                <div className="flex items-center gap-2.5">
                  <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-amber-500 to-orange-500 flex items-center justify-center">
                    <Sparkles className="w-5 h-5 text-white" />
                  </div>
                  <div>
                    <h3 className="text-sm font-semibold leading-tight">Code Quality Insight</h3>
                    <p className="text-[11px] text-muted-foreground">Rule-based explanation</p>
                  </div>
                </div>
                <Button variant="outline" size="sm" onClick={handleAiReview} disabled={aiLoading} className="gap-1.5">
                  {aiLoading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Sparkles className="w-3.5 h-3.5" />}
                  AI Review
                </Button>
              </div>

              {aiLoading ? (
                <div className="flex flex-col items-center py-10">
                  <Loader2 className="w-7 h-7 text-primary animate-spin mb-3" />
                  <p className="text-xs text-muted-foreground animate-pulse">Gemini is reviewing commit history...</p>
                </div>
              ) : (
                <div className="space-y-3">
                  <p className="text-xs text-muted-foreground leading-relaxed">{analytics.explanation}</p>
                  <div className="rounded-lg bg-amber-500/5 border border-amber-500/15 p-3">
                    <p className="text-[11px] text-amber-400/90 leading-relaxed">
                      <span className="font-semibold">Improve: </span>
                      {analytics.improvementSuggestion}
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
        </div>
      )}
    </motion.div>
  );
}
