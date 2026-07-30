import { useState, useEffect, useCallback } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Footer } from "@/components/Footer";
import { AISummaryPanel } from "@/components/AISummaryPanel";
import { SkillsMatrix } from "@/components/SkillsMatrix";
import { ReportExport } from "@/components/ReportExport";
import {
  githubApi,
  githubApiEnhanced,
  recruiterApi,
  type DeveloperScore,
  type GitHubProfile,
  type Repository,
  type GitHubPR,
  type GitHubIssue,
  type LanguageBreakdown,
  type ContributionStats,
} from "@/services/api";
import toast from "react-hot-toast";
import {
  ArrowLeft,
  Loader2,
  Star,
  GitFork,
  BookOpen,
  Users,
  TrendingUp,
  BarChart3,
  Shield,
  ExternalLink,
  GitPullRequest,
  AlertCircle,
  MapPin,
  Link2,
  UserPlus,
  Trophy,
  Brain,
  GitCommit,
} from "lucide-react";

// ── Score Gauge Mini ──

function ScoreGaugeMini({ value, size = 56 }: { value: number; size?: number }) {
  const circumference = 2 * Math.PI * (size * 0.38);
  return (
    <div
      className="relative inline-flex items-center justify-center"
      style={{ width: size, height: size }}
    >
      <svg width={size} height={size} className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={size * 0.38}
          fill="none"
          stroke="currentColor"
          strokeWidth={5}
          className="text-muted/15"
        />
        <motion.circle
          cx={size / 2}
          cy={size / 2}
          r={size * 0.38}
          fill="none"
          stroke={value >= 80 ? "#34d399" : value >= 65 ? "#22d3ee" : value >= 50 ? "#a78bfa" : value >= 35 ? "#fbbf24" : "#f87171"}
          strokeWidth={5}
          strokeLinecap="round"
          strokeDasharray={circumference}
          initial={{ strokeDashoffset: circumference }}
          animate={{ strokeDashoffset: circumference - (value / 100) * circumference }}
          transition={{ duration: 1, ease: "easeOut" }}
        />
      </svg>
      <div className="absolute inset-0 flex items-center justify-center">
        <span className="text-xs font-bold tabular-nums">{value}</span>
      </div>
    </div>
  );
}

// ── Stat Row ──

function StatRow({ icon: Icon, label, value }: { icon: any; label: string; value: string | number }) {
  return (
    <div className="flex items-center gap-2 text-sm">
      <Icon className="w-4 h-4 text-muted-foreground shrink-0" />
      <span className="text-muted-foreground min-w-[80px] text-xs">{label}</span>
      <span className="font-medium text-xs">{value}</span>
    </div>
  );
}

// ── Metric Mini Bar ──

function MetricBar({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-[10px] text-muted-foreground w-20 shrink-0">{label}</span>
      <div className="flex-1 h-1.5 rounded-full bg-muted/20 overflow-hidden">
        <motion.div
          className={`h-full rounded-full bg-gradient-to-r ${
            value >= 60 ? "from-emerald-500 to-teal-500" : value >= 35 ? "from-amber-500 to-orange-500" : "from-rose-500 to-pink-500"
          }`}
          initial={{ width: 0 }}
          animate={{ width: `${value}%` }}
          transition={{ duration: 0.8, ease: "easeOut" }}
        />
      </div>
      <span className="text-[10px] font-semibold tabular-nums w-5 text-right">{value}</span>
    </div>
  );
}

// ── Tab Button ──

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={`px-4 py-2 text-sm rounded-xl transition-all duration-200 ${
        active
          ? "bg-primary/10 text-primary font-medium"
          : "text-muted-foreground hover:text-foreground hover:bg-muted/50"
      }`}
    >
      {children}
    </button>
  );
}

// ── Main Component ──

export function CandidateDetails() {
  const { username } = useParams<{ username: string }>();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState<"overview" | "repos" | "activity" | "insights">("overview");
  const [profile, setProfile] = useState<GitHubProfile | null>(null);
  const [repos, setRepos] = useState<Repository[]>([]);
  const [score, setScore] = useState<DeveloperScore | null>(null);
  const [prs, setPrs] = useState<GitHubPR[]>([]);
  const [issues, setIssues] = useState<GitHubIssue[]>([]);
  const [languages, setLanguages] = useState<LanguageBreakdown[]>([]);
  const [contribStats, setContribStats] = useState<ContributionStats | null>(null);
  const [saving, setSaving] = useState(false);

  const loadData = useCallback(async () => {
    if (!username) return;
    setLoading(true);

    try {
      const [profileRes, reposRes, scoreRes] = await Promise.all([
        githubApi.getProfile(username),
        githubApi.getRepositories(username),
        githubApi.getDeveloperScore(username),
      ]);

      if (profileRes.success) setProfile(profileRes.data);
      if (reposRes.success) setRepos(reposRes.data);
      if (scoreRes.success) setScore(scoreRes.data);

      // Load async data in background
      Promise.allSettled([
        githubApiEnhanced.getPullRequests(username).then((r) => r.success && setPrs(r.data)),
        githubApiEnhanced.getIssues(username).then((r) => r.success && setIssues(r.data)),
        githubApiEnhanced.getLanguageBreakdown(username).then((r) => r.success && setLanguages(r.data)),
        githubApiEnhanced.getContributionStats(username).then((r) => r.success && setContribStats(r.data)),
      ]);

      if (!profileRes.success) toast.error(`Could not find user: ${username}`);
    } catch (err: any) {
      toast.error(err.message || "Failed to load candidate data");
    } finally {
      setLoading(false);
    }
  }, [username]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleSaveCandidate = async () => {
    if (!profile) return;
    setSaving(true);
    try {
      await recruiterApi.saveCandidate({
        username: profile.username,
        name: profile.name || undefined,
        avatarUrl: profile.avatarUrl,
        githubId: profile.githubId,
        score: score?.overallScore,
        level: score?.level,
      });
      toast.success(`${profile.name || profile.username} saved as candidate!`);
    } catch {
      toast.error("Failed to save candidate");
    } finally {
      setSaving(false);
    }
  };

  if (!username) return null;

  if (loading) {
    return (
      <div className="min-h-screen pt-20 pb-16 flex items-center justify-center">
        <div className="text-center">
          <div className="relative inline-block">
            <Loader2 className="w-12 h-12 text-primary animate-spin" />
            <div className="absolute inset-0 rounded-full bg-primary/20 animate-ping" />
          </div>
          <p className="mt-4 text-muted-foreground">Loading {username}...</p>
        </div>
      </div>
    );
  }

  const totalStars = repos.reduce((s, r) => s + r.stars, 0);
  const totalForks = repos.reduce((s, r) => s + r.forks, 0);
  const topRepos = repos
    .filter((r) => !r.fork && !r.archived)
    .sort((a, b) => b.stars - a.stars)
    .slice(0, 15);
  const mergedPRs = prs.filter((p) => p.mergedAt != null);
  const openIssues = issues.filter((i) => i.state === "open");

  const metricsList = score
    ? [
        { key: "contributionRecency", label: "Contribution Recency", value: score.contributionRecency },
        { key: "commitFrequency", label: "Commit Frequency", value: score.commitFrequency },
        { key: "repositoryHealth", label: "Repository Health", value: score.repositoryHealth },
        { key: "repositoryQuality", label: "Repository Quality", value: score.repositoryQuality },
        { key: "contributionConsistency", label: "Consistency", value: score.contributionConsistency },
        { key: "languageDiversity", label: "Language Diversity", value: score.languageDiversity },
        { key: "collaboration", label: "Collaboration", value: score.collaboration },
        { key: "openSourceImpact", label: "Open Source Impact", value: score.openSourceImpact },
        { key: "popularity", label: "Popularity", value: score.popularity },
        { key: "maintenance", label: "Maintenance", value: score.maintenance },
      ]
    : [];

  return (
    <div className="min-h-screen pt-20 pb-16">
      <div className="max-w-6xl mx-auto px-4">
        {/* Back + Actions */}
        <motion.div
          initial={{ opacity: 0, y: -5 }}
          animate={{ opacity: 1, y: 0 }}
          className="flex items-center justify-between mb-6"
        >
          <Button variant="ghost" size="sm" onClick={() => navigate("/recruiter")} className="gap-1.5">
            <ArrowLeft className="w-4 h-4" />
            Back to Recruiter
          </Button>
          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={handleSaveCandidate}
              disabled={saving}
              className="gap-1.5"
            >
              <UserPlus className="w-4 h-4" />
              Save Candidate
            </Button>
            <ReportExport
              profile={profile}
              score={score}
              repos={repos}
              languages={languages}
              username={username}
            />
          </div>
        </motion.div>

        {/* Profile Header */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-8"
        >
          <Card className="overflow-hidden">
            <div className={`h-28 bg-gradient-to-br ${
              score ? (score.overallScore >= 65 ? "from-emerald-500 to-teal-500" : score.overallScore >= 35 ? "from-amber-500 to-orange-500" : "from-rose-500 to-pink-500") : "from-primary to-accent"
            }`} />
            <CardContent className="relative !pt-0">
              <div className="flex flex-col sm:flex-row items-start gap-5">
                <div className="-mt-14 shrink-0">
                  <div className="w-28 h-28 rounded-2xl border-4 border-background overflow-hidden shadow-xl">
                    {profile?.avatarUrl ? (
                      <img src={profile.avatarUrl} alt="" className="w-full h-full object-cover" />
                    ) : (
                      <div className="w-full h-full bg-muted flex items-center justify-center text-2xl font-bold">
                        {username.charAt(0).toUpperCase()}
                      </div>
                    )}
                  </div>
                </div>

                <div className="flex-1 min-w-0 pt-3 sm:pt-0">
                  <div className="flex items-start justify-between flex-wrap gap-4">
                    <div>
                      <h1 className="text-2xl font-bold">{profile?.name || username}</h1>
                      {profile?.name && <p className="text-sm text-muted-foreground">@{username}</p>}
                      {profile?.bio && <p className="text-sm text-muted-foreground mt-1 max-w-lg">{profile.bio}</p>}
                    </div>
                    {score && (
                      <div className="flex items-center gap-3">
                        <div className="text-center">
                          <ScoreGaugeMini value={score.overallScore} size={64} />
                        </div>
                        <div>
                          <div className={`px-2.5 py-1 rounded-full text-xs font-semibold text-white bg-gradient-to-r ${
                            score.level.includes("Elite") ? "from-emerald-500 to-teal-500" :
                            score.level.includes("Expert") ? "from-cyan-500 to-blue-500" :
                            score.level.includes("Advanced") ? "from-violet-500 to-purple-500" :
                            score.level.includes("Proficient") ? "from-blue-500 to-indigo-500" :
                            score.level.includes("Intermediate") ? "from-amber-500 to-orange-500" :
                            "from-gray-500 to-slate-500"
                          }`}>
                            <Trophy className="w-3 h-3 inline mr-1" />
                            {score.level}
                          </div>
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="flex flex-wrap items-center gap-x-4 gap-y-1.5 mt-3">
                    {profile?.company && <StatRow icon={Users} label="Company" value={profile.company} />}
                    {profile?.location && <StatRow icon={MapPin} label="Location" value={profile.location} />}
                    {profile?.website && (
                      <a href={profile.website} target="_blank" rel="noopener noreferrer" className="flex items-center gap-2">
                        <Link2 className="w-4 h-4 text-muted-foreground" />
                        <span className="text-xs text-primary hover:underline">{new URL(profile.website).hostname}</span>
                      </a>
                    )}
                    {profile?.email && <StatRow icon={AlertCircle} label="Email" value={profile.email} />}
                    {profile?.hireable && (
                      <span className="px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 text-[10px] font-medium">
                        Available for hire
                      </span>
                    )}
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>
        </motion.div>

        {/* Stats Cards */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-6 gap-3 mb-8"
        >
          <StatCard icon={BookOpen} value={profile?.publicRepositories || 0} label="Public Repos" />
          <StatCard icon={Star} value={totalStars} label="Total Stars" />
          <StatCard icon={GitFork} value={totalForks} label="Total Forks" />
          <StatCard icon={Users} value={profile?.followers || 0} label="Followers" />
          <StatCard icon={GitPullRequest} value={mergedPRs.length} label="Merged PRs" />
          <StatCard icon={AlertCircle} value={openIssues.length} label="Open Issues" />
        </motion.div>

        {/* Tabs */}
        <div className="flex gap-2 mb-6 overflow-x-auto pb-2">
          <TabButton active={tab === "overview"} onClick={() => setTab("overview")}>
            <BarChart3 className="w-4 h-4 inline mr-1.5" />
            Overview
          </TabButton>
          <TabButton active={tab === "repos"} onClick={() => setTab("repos")}>
            <BookOpen className="w-4 h-4 inline mr-1.5" />
            Repositories
          </TabButton>
          <TabButton active={tab === "activity"} onClick={() => setTab("activity")}>
            <GitCommit className="w-4 h-4 inline mr-1.5" />
            Activity
          </TabButton>
          <TabButton active={tab === "insights"} onClick={() => setTab("insights")}>
            <Brain className="w-4 h-4 inline mr-1.5" />
            AI Insights
          </TabButton>
        </div>

        {/* Tab Content */}
        {tab === "overview" && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            key="overview"
            className="grid grid-cols-1 lg:grid-cols-3 gap-6"
          >
            {/* Score Breakdown */}
            <Card className="lg:col-span-2">
              <CardContent className="!p-5">
                <div className="flex items-center gap-2 mb-4">
                  <BarChart3 className="w-5 h-5 text-primary" />
                  <h3 className="font-semibold">10-Point Metric Breakdown</h3>
                </div>
                <div className="space-y-2">
                  {metricsList.map((m) => (
                    <MetricBar key={m.key} label={m.label} value={m.value} />
                  ))}
                </div>
                {score?.insights?.recommendations && (
                  <div className="mt-4 pt-4 border-t border-border/50">
                    <div className="flex items-start gap-2">
                      <TrendingUp className="w-4 h-4 text-amber-400 mt-0.5 shrink-0" />
                      <p className="text-[11px] text-amber-400/80 leading-relaxed">{score.insights.recommendations}</p>
                    </div>
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Skills Matrix */}
            <Card>
              <CardContent className="!p-5">
                <SkillsMatrix languages={languages} />
              </CardContent>
            </Card>

            {/* Contribution Stats */}
            {contribStats && (
              <Card className="lg:col-span-3">
                <CardContent className="!p-5">
                  <div className="flex items-center gap-2 mb-4">
                    <TrendingUp className="w-5 h-5 text-primary" />
                    <h3 className="font-semibold">Contribution Activity</h3>
                  </div>
                  <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-4">
                    <ContribStatBox label="Commits" value={contribStats.totalCommits} icon={GitCommit} />
                    <ContribStatBox label="Pull Requests" value={contribStats.totalPRs} icon={GitPullRequest} />
                    <ContribStatBox label="Issues" value={contribStats.totalIssues} icon={AlertCircle} />
                    <ContribStatBox label="Repos" value={contribStats.reposContributedTo} icon={BookOpen} />
                    <ContribStatBox label="Organizations" value={contribStats.orgCount} icon={Users} />
                  </div>
                </CardContent>
              </Card>
            )}
          </motion.div>
        )}

        {tab === "repos" && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            key="repos"
            className="space-y-3"
          >
            {topRepos.length === 0 ? (
              <Card className="p-10 text-center">
                <BookOpen className="w-10 h-10 text-muted-foreground/50 mx-auto mb-3" />
                <p className="text-sm text-muted-foreground">No original repositories found</p>
              </Card>
            ) : (
              topRepos.map((repo, i) => (
                <motion.div
                  key={repo.name}
                  initial={{ opacity: 0, y: 5 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: i * 0.03 }}
                >
                  <Card className="hover:scale-[1.002] transition-all duration-200">
                    <CardContent className="!p-4">
                      <div className="flex items-start justify-between gap-4">
                        <div className="min-w-0 flex-1">
                          <div className="flex items-center gap-2 mb-1">
                            <a
                              href={repo.htmlUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className="text-sm font-semibold text-primary hover:underline"
                            >
                              {repo.name}
                            </a>
                            {repo.healthScore >= 70 && (
                              <Shield className="w-3.5 h-3.5 text-emerald-400" />
                            )}
                          </div>
                          {repo.description && (
                            <p className="text-xs text-muted-foreground line-clamp-1 mb-2">{repo.description}</p>
                          )}
                          <div className="flex flex-wrap items-center gap-3 text-[10px] text-muted-foreground">
                            {repo.language && (
                              <span className="flex items-center gap-1">
                                <span className="w-2 h-2 rounded-full bg-primary/70" />
                                {repo.language}
                              </span>
                            )}
                            <span>⭐ {repo.stars}</span>
                            <span>🍴 {repo.forks}</span>
                            <span>📋 {repo.openIssues} issues</span>
                            {repo.topics && repo.topics.length > 0 && (
                              <span className="flex items-center gap-1">
                                🏷️ {repo.topics.slice(0, 3).join(", ")}
                                {repo.topics.length > 3 && ` +${repo.topics.length - 3}`}
                              </span>
                            )}
                          </div>
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          <a
                            href={repo.htmlUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="p-1.5 rounded-lg hover:bg-muted/50 text-muted-foreground transition-colors"
                          >
                            <ExternalLink className="w-3.5 h-3.5" />
                          </a>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                </motion.div>
              ))
            )}
          </motion.div>
        )}

        {tab === "activity" && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            key="activity"
            className="grid grid-cols-1 lg:grid-cols-2 gap-6"
          >
            {/* Pull Requests */}
            <Card>
              <CardContent className="!p-5">
                <div className="flex items-center gap-2 mb-4">
                  <GitPullRequest className="w-5 h-5 text-primary" />
                  <h3 className="font-semibold">Pull Requests</h3>
                  <span className="text-[10px] text-muted-foreground ml-auto">{prs.length} total</span>
                </div>
                {prs.length === 0 ? (
                  <p className="text-xs text-muted-foreground text-center py-6">No PR data available</p>
                ) : (
                  <div className="space-y-2 max-h-[400px] overflow-y-auto">
                    {prs.slice(0, 20).map((pr) => (
                      <div key={`${pr.repoName}#${pr.number}`} className="flex items-start gap-2.5 p-2 rounded-lg hover:bg-muted/30 transition-colors">
                        <div className={`w-2 h-2 rounded-full mt-1.5 ${
                          pr.mergedAt ? "bg-purple-400" : pr.state === "open" ? "bg-green-400" : "bg-red-400"
                        }`} />
                        <div className="min-w-0 flex-1">
                          <p className="text-xs font-medium truncate">{pr.title}</p>
                          <p className="text-[10px] text-muted-foreground">
                            {pr.repoName} · <span className={
                              pr.mergedAt ? "text-purple-400" : pr.state === "open" ? "text-green-400" : "text-red-400"
                            }>{pr.mergedAt ? "Merged" : pr.state}</span>
                          </p>
                        </div>
                        <span className="text-[10px] text-muted-foreground shrink-0">{pr.comments} 💬</span>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>

            {/* Issues */}
            <Card>
              <CardContent className="!p-5">
                <div className="flex items-center gap-2 mb-4">
                  <AlertCircle className="w-5 h-5 text-primary" />
                  <h3 className="font-semibold">Issues</h3>
                  <span className="text-[10px] text-muted-foreground ml-auto">{issues.length} total</span>
                </div>
                {issues.length === 0 ? (
                  <p className="text-xs text-muted-foreground text-center py-6">No issue data available</p>
                ) : (
                  <div className="space-y-2 max-h-[400px] overflow-y-auto">
                    {issues.slice(0, 20).map((issue) => (
                      <div key={`${issue.repoName}#${issue.number}`} className="flex items-start gap-2.5 p-2 rounded-lg hover:bg-muted/30 transition-colors">
                        <div className={`w-2 h-2 rounded-full mt-1.5 ${
                          issue.state === "open" ? "bg-green-400" : "bg-gray-400"
                        }`} />
                        <div className="min-w-0 flex-1">
                          <p className="text-xs font-medium truncate">{issue.title}</p>
                          <p className="text-[10px] text-muted-foreground">
                            {issue.repoName} · <span className={
                              issue.state === "open" ? "text-green-400" : "text-gray-400"
                            }>{issue.state}</span>
                          </p>
                          {issue.labels.length > 0 && (
                            <div className="flex flex-wrap gap-1 mt-1">
                              {issue.labels.slice(0, 3).map((label) => (
                                <span key={label} className="text-[9px] px-1.5 py-0.5 rounded-full bg-muted/50 text-muted-foreground">
                                  {label}
                                </span>
                              ))}
                            </div>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          </motion.div>
        )}

        {tab === "insights" && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            key="insights"
            className="max-w-2xl mx-auto"
          >
            <AISummaryPanel insights={score?.insights || null} username={username} variant="full" />
            {languages.length > 0 && (
              <div className="mt-6">
                <Card>
                  <CardContent className="!p-5">
                    <SkillsMatrix languages={languages} />
                  </CardContent>
                </Card>
              </div>
            )}
            <div className="mt-6 text-center">
              <Button variant="outline" size="sm" onClick={() => navigate(`/compare?user1=${username}&user2=addyosmani`)} className="gap-1.5">
                <BarChart3 className="w-4 h-4" />
                Compare @{username} with another developer
              </Button>
            </div>
          </motion.div>
        )}
      </div>
      <Footer />
    </div>
  );
}

// ── Helper sub-components ──

function StatCard({ icon: Icon, value, label }: { icon: any; value: number; label: string }) {
  return (
    <Card>
      <CardContent className="!p-3 text-center">
        <Icon className="w-4 h-4 mx-auto mb-1 text-muted-foreground" />
        <p className="text-lg font-bold tabular-nums">{value.toLocaleString()}</p>
        <p className="text-[9px] text-muted-foreground uppercase tracking-wider">{label}</p>
      </CardContent>
    </Card>
  );
}

function ContribStatBox({ icon: Icon, value, label }: { icon: any; value: number; label: string }) {
  return (
    <div className="glass rounded-lg p-3 text-center">
      <Icon className="w-4 h-4 mx-auto mb-1 text-primary" />
      <p className="text-lg font-bold tabular-nums">{value.toLocaleString()}</p>
      <p className="text-[9px] text-muted-foreground">{label}</p>
    </div>
  );
}
