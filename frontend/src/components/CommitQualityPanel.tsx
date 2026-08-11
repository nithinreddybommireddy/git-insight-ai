import { useState, useMemo } from "react";
import { motion } from "framer-motion";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import {
  aiApi,
  githubApiEnhanced,
  type CommitAnalytics,
  type CommitDiff,
  type CommitDiffReview,
  type CommitDiffFile,
} from "@/services/api";
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
  FileDiff,
  Download,
  CheckCircle2,
  AlertTriangle,
  Lightbulb,
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
  const [diffs, setDiffs] = useState<CommitDiff[] | null>(null);
  const [diffsLoading, setDiffsLoading] = useState(false);
  const [diffsError, setDiffsError] = useState<string | null>(null);
  const [selectedSha, setSelectedSha] = useState<string>("");
  const [diffReview, setDiffReview] = useState<CommitDiffReview | null>(null);
  const [reviewLoading, setReviewLoading] = useState(false);

  const selectedDiff = useMemo(
    () => diffs?.find((d) => d.sha === selectedSha) ?? diffs?.[0] ?? null,
    [diffs, selectedSha]
  );

  const handleLoadDiffs = async () => {
    if (!username) return;
    setDiffsLoading(true);
    setDiffsError(null);
    try {
      const res = await githubApiEnhanced.getCommitDiffs(username, 15);
      if (res.success) {
        setDiffs(res.data.commits);
        if (res.data.commits.length > 0) setSelectedSha(res.data.commits[0].sha);
        setDiffReview(null);
      } else {
        setDiffsError(res.message || "Failed to load commit diffs");
      }
    } catch (err: any) {
      setDiffsError(
        err.response?.data?.message || err.message || "Failed to load commit diffs"
      );
    } finally {
      setDiffsLoading(false);
    }
  };

  const handleDiffReview = async () => {
    if (!username || !selectedDiff) return;
    setReviewLoading(true);
    try {
      const res = await aiApi.getCommitDiffReview({ username, commits: [selectedDiff] });
      if (res.success) {
        setDiffReview(res.data);
        toast.success(
          res.data.aiEnabled
            ? "AI commit-diff review generated"
            : "Rule-based diff review generated (set GEMINI_API_KEY for AI)"
        );
      } else {
        toast.error(res.message || "Failed to generate commit-diff review");
      }
    } catch (err: any) {
      toast.error(
        err.response?.data?.message || err.message || "Failed to generate commit-diff review"
      );
    } finally {
      setReviewLoading(false);
    }
  };

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
      {/* Phase 6 — Commit-diff AI per-file review */}
      <Card>
        <CardContent className="!py-6">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-5">
            <div className="flex items-center gap-2.5">
              <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-emerald-500 to-teal-500 flex items-center justify-center">
                <FileDiff className="w-5 h-5 text-white" />
              </div>
              <div>
                <h3 className="text-sm font-semibold leading-tight">Commit Diff Review</h3>
                <p className="text-[11px] text-muted-foreground">Per-file AI code review of a recent commit's patch</p>
              </div>
            </div>
            {!diffs && (
              <Button
                variant="outline"
                size="sm"
                onClick={handleLoadDiffs}
                disabled={diffsLoading}
                className="gap-1.5"
              >
                {diffsLoading ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <Download className="w-3.5 h-3.5" />
                )}
                {diffsLoading ? "Loading diffs..." : "Load recent commit diffs"}
              </Button>
            )}
          </div>

          {diffsError && <p className="text-xs text-rose-400 mb-4">{diffsError}</p>}

          {diffs && diffs.length === 0 && (
            <p className="text-xs text-muted-foreground text-center py-6">
              No recent commit diffs available for this developer.
            </p>
          )}

          {diffs && diffs.length > 0 && (
            <div className="space-y-5">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <p className="text-[11px] font-medium text-muted-foreground mb-1.5">Select commit</p>
                  <select
                    value={selectedDiff?.sha ?? ""}
                    onChange={(e) => setSelectedSha(e.target.value)}
                    className="w-full rounded-lg border border-border/60 bg-muted/20 px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-primary"
                  >
                    {diffs.map((d) => (
                      <option key={d.sha} value={d.sha}>
                        {d.repoName} · {d.sha.slice(0, 7)} — {(d.message.split("\n")[0] || "untitled").slice(0, 56)}
                      </option>
                    ))}
                  </select>
                </div>
                <div>
                  <p className="text-[11px] font-medium text-muted-foreground mb-1.5">
                    Changed files ({selectedDiff?.changedFiles ?? 0})
                  </p>
                  <div className="flex flex-wrap gap-1.5 max-h-20 overflow-y-auto">
                    {selectedDiff?.files.map((f: CommitDiffFile) => (
                      <span
                        key={f.filename}
                        className="inline-flex items-center gap-1 rounded-md bg-muted/20 border border-border/50 px-2 py-1 text-[10px]"
                      >
                        <span className="text-muted-foreground max-w-40 truncate">{f.filename}</span>
                        <span className="text-emerald-400">+{f.additions}</span>
                        <span className="text-rose-400">−{f.deletions}</span>
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              <Button
                variant="primary"
                size="sm"
                onClick={handleDiffReview}
                disabled={reviewLoading || !selectedDiff}
                className="gap-1.5"
              >
                {reviewLoading ? (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                ) : (
                  <Sparkles className="w-3.5 h-3.5" />
                )}
                {reviewLoading ? "Gemini is reviewing the diff..." : "Run AI per-file review"}
              </Button>

              {reviewLoading && (
                <p className="text-xs text-muted-foreground animate-pulse">
                  Reading each file's patch and checking for issues, risks, and improvements...
                </p>
              )}

              {diffReview && !reviewLoading && (
                <div className="space-y-4">
                  {/* Overall verdict */}
                  <div className="rounded-xl border border-border/60 bg-muted/10 p-4 flex flex-col sm:flex-row items-start sm:items-center gap-4">
                    <div className="relative w-16 h-16 shrink-0">
                      <svg viewBox="0 0 60 60" className="w-full h-full -rotate-90">
                        <circle cx="30" cy="30" r="26" fill="none" strokeWidth="5" className="stroke-muted/20" />
                        <motion.circle
                          cx="30"
                          cy="30"
                          r="26"
                          fill="none"
                          strokeWidth="5"
                          strokeLinecap="round"
                          strokeDasharray={2 * Math.PI * 26}
                          initial={{ strokeDashoffset: 2 * Math.PI * 26 }}
                          animate={{
                            strokeDashoffset: 2 * Math.PI * 26 * (1 - diffReview.overallScore / 100),
                          }}
                          transition={{ duration: 1, ease: "easeOut" }}
                          className="stroke-[url(#cdrGradient)]"
                        />
                        <defs>
                          <linearGradient id="cdrGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                            <stop offset="0%" stopColor="#34d399" />
                            <stop offset="100%" stopColor="#2dd4bf" />
                          </linearGradient>
                        </defs>
                      </svg>
                      <div className="absolute inset-0 flex items-center justify-center">
                        <span className={`text-base font-extrabold ${scoreText(diffReview.overallScore)}`}>
                          {diffReview.overallScore}
                        </span>
                      </div>
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="text-sm font-semibold">Overall Verdict</span>
                        {!diffReview.aiEnabled && (
                          <span className="text-[10px] px-2 py-0.5 rounded-full bg-amber-500/10 text-amber-400 border border-amber-500/20">
                            Rule-based
                          </span>
                        )}
                        {diffReview.aiModel && (
                          <span className="text-[10px] px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                            {diffReview.aiModel}
                          </span>
                        )}
                      </div>
                      <p className="text-xs text-muted-foreground leading-relaxed">{diffReview.overallSummary}</p>
                    </div>
                  </div>

                  {/* Key issues + strengths + recommendations */}
                  {diffReview.keyIssues.length > 0 && (
                    <div className="rounded-lg bg-rose-500/5 border border-rose-500/15 p-3">
                      <p className="text-[11px] font-semibold text-rose-400 flex items-center gap-1.5 mb-1.5">
                        <AlertTriangle className="w-3.5 h-3.5" /> Key Issues
                      </p>
                      <ul className="space-y-1">
                        {diffReview.keyIssues.map((issue, i) => (
                          <li key={i} className="text-[11px] text-muted-foreground leading-relaxed list-disc list-inside">
                            {issue}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {diffReview.strengths.length > 0 && (
                    <div className="rounded-lg bg-emerald-500/5 border border-emerald-500/15 p-3">
                      <p className="text-[11px] font-semibold text-emerald-400 flex items-center gap-1.5 mb-1.5">
                        <CheckCircle2 className="w-3.5 h-3.5" /> Strengths
                      </p>
                      <ul className="space-y-1">
                        {diffReview.strengths.map((s, i) => (
                          <li key={i} className="text-[11px] text-muted-foreground leading-relaxed list-disc list-inside">
                            {s}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {diffReview.recommendations.length > 0 && (
                    <div className="rounded-lg bg-amber-500/5 border border-amber-500/15 p-3">
                      <p className="text-[11px] font-semibold text-amber-400 flex items-center gap-1.5 mb-1.5">
                        <Lightbulb className="w-3.5 h-3.5" /> Recommendations
                      </p>
                      <ul className="space-y-1">
                        {diffReview.recommendations.map((r, i) => (
                          <li key={i} className="text-[11px] text-muted-foreground leading-relaxed list-disc list-inside">
                            {r}
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {/* Per-file reviews */}
                  {diffReview.fileReviews.length > 0 && (
                    <div>
                      <p className="text-[11px] font-semibold text-muted-foreground mb-2">Per-file findings</p>
                      <div className="space-y-3">
                        {diffReview.fileReviews.map((fr) => (
                          <div key={fr.filename} className="rounded-xl border border-border/60 bg-muted/10 p-4">
                            <div className="flex items-center justify-between gap-3 mb-2">
                              <p className="text-xs font-semibold truncate">{fr.filename}</p>
                              <span className={`shrink-0 text-[11px] font-bold tabular-nums ${scoreText(fr.score)}`}>
                                {fr.score}/100
                              </span>
                            </div>
                            {fr.summary && (
                              <p className="text-[11px] text-muted-foreground leading-relaxed mb-2">{fr.summary}</p>
                            )}
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                              <div>
                                {fr.issues.length > 0 && (
                                  <ul className="space-y-1">
                                    {fr.issues.map((issue, i) => (
                                      <li
                                        key={i}
                                        className="text-[11px] text-rose-400/90 leading-relaxed flex gap-1.5"
                                      >
                                        <span className="mt-1 w-1 h-1 rounded-full bg-rose-400 shrink-0" />
                                        {issue}
                                      </li>
                                    ))}
                                  </ul>
                                )}
                              </div>
                              <div>
                                {fr.suggestions.length > 0 && (
                                  <ul className="space-y-1">
                                    {fr.suggestions.map((sug, i) => (
                                      <li
                                        key={i}
                                        className="text-[11px] text-amber-400/90 leading-relaxed flex gap-1.5"
                                      >
                                        <span className="mt-1 w-1 h-1 rounded-full bg-amber-400 shrink-0" />
                                        {sug}
                                      </li>
                                    ))}
                                  </ul>
                                )}
                              </div>
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </CardContent>
      </Card>
    </motion.div>
  );
}
