import { useState, useCallback, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Footer } from "@/components/Footer";
import { ReportExport } from "@/components/ReportExport";
import { reportsApi, type ScoreSnapshot } from "@/services/api";
import toast from "react-hot-toast";
import {
  FileText,
  Search,
  ArrowLeft,
  Loader2,
  TrendingUp,
  BarChart3,
  Clock,
  Users,
  Download,
  RefreshCw,
  LineChart,
  Star,
  Trophy,
} from "lucide-react";

export function ReportsPage() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [loading, setLoading] = useState(false);
  const [reportData, setReportData] = useState<any>(null);
  const [history, setHistory] = useState<ScoreSnapshot[]>([]);
  const [stats, setStats] = useState<any>(null);

  // Load stats on mount
  useEffect(() => {
    reportsApi.getStats().then((r) => {
      if (r.success) setStats(r.data);
    }).catch(() => {});
  }, []);

  const handleGenerate = useCallback(async () => {
    const u = username.trim();
    if (!u) {
      toast.error("Enter a GitHub username");
      return;
    }

    setLoading(true);
    try {
      const res = await reportsApi.generateReport(u);
      if (res.success) {
        setReportData(res.data);
        setHistory(res.data.history || []);
        toast.success(`Report generated for ${u}`);
      } else {
        toast.error(res.message || "Failed to generate report");
      }
    } catch (err: any) {
      toast.error(err.message || "Failed to generate report");
    } finally {
      setLoading(false);
    }
  }, [username]);

  const handleRefresh = async () => {
    if (!reportData) return;
    const u = reportData.score?.username || username;
    setUsername(u);
    await handleGenerate();
  };

  const chartWidth = 600;
  const chartHeight = 200;

  return (
    <div className="min-h-screen pt-20 pb-16">
      <div className="max-w-6xl mx-auto px-4">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-8"
        >
          <div className="flex items-center gap-2 mb-4">
            <Button variant="ghost" size="sm" onClick={() => navigate("/")} className="gap-1.5">
              <ArrowLeft className="w-4 h-4" />
              Back
            </Button>
          </div>

          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-blue-500 to-indigo-500 flex items-center justify-center shadow-lg shadow-blue-500/20">
              <FileText className="w-7 h-7 text-white" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">
                Developer <span className="gradient-text">Reports</span>
              </h1>
              <p className="text-sm text-muted-foreground">
                Generate PDF reports, track score history, and visualize trends
              </p>
            </div>
          </div>
        </motion.div>

        {/* Stats Section */}
        {stats && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="grid grid-cols-3 gap-4 mb-8"
          >
            <Card className="text-center py-4">
              <BarChart3 className="w-5 h-5 mx-auto mb-1.5 text-primary" />
              <p className="text-xl font-bold">{stats.totalSnapshots}</p>
              <p className="text-[10px] text-muted-foreground">Score Snapshots</p>
            </Card>
            <Card className="text-center py-4">
              <Users className="w-5 h-5 mx-auto mb-1.5 text-accent" />
              <p className="text-xl font-bold">{stats.uniqueUsers}</p>
              <p className="text-[10px] text-muted-foreground">Unique Developers</p>
            </Card>
            <Card className="text-center py-4">
              <Trophy className="w-5 h-5 mx-auto mb-1.5 text-amber-400" />
              <p className="text-xl font-bold">{stats.averageScore}</p>
              <p className="text-[10px] text-muted-foreground">Average Score</p>
            </Card>
          </motion.div>
        )}

        {/* Search */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="mb-8"
        >
          <Card>
            <CardContent className="!py-5">
              <div className="flex gap-3">
                <div className="flex-1 relative">
                  <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    type="text"
                    placeholder="Enter GitHub username to generate a report..."
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && handleGenerate()}
                    className="pl-9"
                  />
                </div>
                <Button
                  variant="primary"
                  onClick={handleGenerate}
                  disabled={loading || !username.trim()}
                  className="gap-2"
                >
                  {loading ? <Loader2 className="w-4 h-4 animate-spin" /> : <FileText className="w-4 h-4" />}
                  Generate Report
                </Button>
              </div>
            </CardContent>
          </Card>
        </motion.div>

        {/* Results */}
        {reportData && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="space-y-6"
          >
            {/* Header with export */}
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <LineChart className="w-5 h-5 text-primary" />
                <h2 className="text-lg font-semibold">
                  Report: <span className="gradient-text">{reportData.score?.username}</span>
                </h2>
                {reportData.score?.level && (
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-primary/10 text-primary font-medium">
                    {reportData.score.level}
                  </span>
                )}
              </div>
              <div className="flex items-center gap-2">
                <Button variant="ghost" size="sm" onClick={handleRefresh} className="gap-1.5" disabled={loading}>
                  <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
                  Refresh
                </Button>
                {reportData.profile && (
                  <ReportExport
                    profile={reportData.profile}
                    score={reportData.score}
                    repos={reportData.repos || []}
                    languages={[]}
                    username={reportData.score?.username}
                  />
                )}
              </div>
            </div>

            {/* Score Over Time Chart */}
            {history.length > 1 && (
              <Card>
                <CardContent className="!p-5">
                  <div className="flex items-center gap-2 mb-4">
                    <TrendingUp className="w-5 h-5 text-primary" />
                    <h3 className="font-semibold">Score History</h3>
                    <span className="text-[10px] text-muted-foreground ml-auto">
                      {history.length} snapshot{history.length !== 1 ? "s" : ""}
                    </span>
                  </div>

                  <div className="overflow-x-auto">
                    <svg viewBox={`0 0 ${chartWidth} ${chartHeight}`} className="w-full h-auto max-h-64">
                      {/* Grid lines */}
                      {[0, 25, 50, 75, 100].map((v) => (
                        <g key={v}>
                          <line
                            x1={60}
                            y1={chartHeight - (v / 100) * (chartHeight - 30) - 15}
                            x2={chartWidth - 10}
                            y2={chartHeight - (v / 100) * (chartHeight - 30) - 15}
                            stroke="currentColor"
                            className="text-muted/10"
                            strokeWidth={1}
                          />
                          <text
                            x={55}
                            y={chartHeight - (v / 100) * (chartHeight - 30) - 12}
                            textAnchor="end"
                            className="fill-muted-foreground"
                            fontSize={10}
                          >
                            {v}
                          </text>
                        </g>
                      ))}

                      {/* Score line */}
                      <polyline
                        fill="none"
                        stroke="url(#scoreGradient)"
                        strokeWidth="2.5"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        points={history.map((h, i) => {
                          const x = 60 + (i / Math.max(history.length - 1, 1)) * (chartWidth - 70);
                          const y = chartHeight - (h.overallScore / 100) * (chartHeight - 30) - 15;
                          return `${x},${y}`;
                        }).join(" ")}
                      />

                      {/* Data points */}
                      {history.map((h, i) => {
                        const x = 60 + (i / Math.max(history.length - 1, 1)) * (chartWidth - 70);
                        const y = chartHeight - (h.overallScore / 100) * (chartHeight - 30) - 15;
                        return (
                          <g key={h.id || i}>
                            <circle cx={x} cy={y} r={3} className="fill-primary" />
                            <text
                              x={x}
                              y={y - 10}
                              textAnchor="middle"
                              className="fill-muted-foreground"
                              fontSize={9}
                            >
                              {h.overallScore}
                            </text>
                          </g>
                        );
                      })}

                      {/* X-axis labels (dates) */}
                      {history.filter((_, i) => i % Math.max(1, Math.floor(history.length / 5)) === 0).map((h, i) => {
                        const idx = history.indexOf(h);
                        const x = 60 + (idx / Math.max(history.length - 1, 1)) * (chartWidth - 70);
                        return (
                          <text
                            key={h.id || i}
                            x={x}
                            y={chartHeight - 2}
                            textAnchor="middle"
                            className="fill-muted-foreground"
                            fontSize={8}
                          >
                            {new Date(h.createdAt).toLocaleDateString("en-US", { month: "short", day: "numeric" })}
                          </text>
                        );
                      })}

                      <defs>
                        <linearGradient id="scoreGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                          <stop offset="0%" stopColor="#818cf8" />
                          <stop offset="100%" stopColor="#22d3ee" />
                        </linearGradient>
                      </defs>
                    </svg>
                  </div>
                </CardContent>
              </Card>
            )}

            {/* Score History Table */}
            {history.length > 0 && (
              <Card>
                <CardContent className="!p-5">
                  <div className="flex items-center gap-2 mb-4">
                    <Clock className="w-5 h-5 text-primary" />
                    <h3 className="font-semibold">Score Snapshots</h3>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full text-xs">
                      <thead>
                        <tr className="text-muted-foreground border-b border-border/50">
                          <th className="text-left py-2 pr-3 font-medium">Date</th>
                          <th className="text-right px-2 py-2 font-medium">Score</th>
                          <th className="text-right px-2 py-2 font-medium">Recency</th>
                          <th className="text-right px-2 py-2 font-medium">Freq</th>
                          <th className="text-right px-2 py-2 font-medium">Health</th>
                          <th className="text-right px-2 py-2 font-medium">Quality</th>
                          <th className="text-right px-2 py-2 font-medium">Consist</th>
                          <th className="text-right px-2 py-2 font-medium">Lang</th>
                          <th className="text-right px-2 py-2 font-medium">Collab</th>
                          <th className="text-right px-2 py-2 font-medium">OSS</th>
                          <th className="text-right px-2 py-2 font-medium">Popular</th>
                          <th className="text-right pl-3 py-2 font-medium">Maint</th>
                        </tr>
                      </thead>
                      <tbody>
                        {history.map((h) => (
                          <tr key={h.id} className="border-b border-border/20 hover:bg-muted/20 transition-colors">
                            <td className="py-2 pr-3 whitespace-nowrap">
                              {new Date(h.createdAt).toLocaleDateString("en-US", {
                                month: "short",
                                day: "numeric",
                                hour: "2-digit",
                                minute: "2-digit",
                              })}
                            </td>
                            <td className="text-right px-2 py-2 font-semibold tabular-nums">{h.overallScore}</td>
                            <td className="text-right px-2 py-2 tabular-nums">{h.contributionRecency}</td>
                            <td className="text-right px-2 py-2 tabular-nums">{h.commitFrequency}</td>
                            <td className="text-right px-2 py-2 tabular-nums">{h.repositoryHealth}</td>
                            <td className="text-right px-2 py-2 tabular-nums">{h.repositoryQuality}</td>
                            <td className="text-right px-2 py-2 tabular-nums">{h.contributionConsistency}</td>
                            <td className="text-right px-2 py-2 tabular-nums">{h.languageDiversity}</td>
                            <td className="text-right px-2 py-2 tabular-nums">{h.collaboration}</td>
                            <td className="text-right px-2 py-2 tabular-nums">{h.openSourceImpact}</td>
                            <td className="text-right px-2 py-2 tabular-nums">{h.popularity}</td>
                            <td className="text-right pl-3 py-2 tabular-nums">{h.maintenance}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </CardContent>
              </Card>
            )}

            {/* Summary stats for this report */}
            <Card>
              <CardContent className="!p-5">
                <div className="flex items-center gap-2 mb-4">
                  <Star className="w-5 h-5 text-primary" />
                  <h3 className="font-semibold">Report Summary</h3>
                </div>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                  <div className="text-center p-3 glass rounded-lg">
                    <p className="text-lg font-bold tabular-nums">{reportData.score?.overallScore}</p>
                    <p className="text-[10px] text-muted-foreground">Current Score</p>
                  </div>
                  <div className="text-center p-3 glass rounded-lg">
                    <p className="text-lg font-bold tabular-nums">{reportData.score?.level}</p>
                    <p className="text-[10px] text-muted-foreground">Level</p>
                  </div>
                  <div className="text-center p-3 glass rounded-lg">
                    <p className="text-lg font-bold tabular-nums">{reportData.profile?.publicRepositories || 0}</p>
                    <p className="text-[10px] text-muted-foreground">Repositories</p>
                  </div>
                  <div className="text-center p-3 glass rounded-lg">
                    <p className="text-lg font-bold tabular-nums">{reportData.score?.totalStars || 0}</p>
                    <p className="text-[10px] text-muted-foreground">Total Stars</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          </motion.div>
        )}

        {/* Empty state */}
        {!reportData && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="text-center py-16"
          >
            <div className="w-24 h-24 rounded-3xl bg-gradient-to-br from-blue-500/10 to-indigo-500/10 flex items-center justify-center mx-auto mb-5">
              <FileText className="w-12 h-12 text-blue-400" />
            </div>
            <h3 className="text-xl font-semibold mb-2">Developer Reports</h3>
            <p className="text-sm text-muted-foreground max-w-lg mx-auto mb-8">
              Generate detailed reports for any GitHub developer. Track score history over time,
              visualize trends, and export PDF reports.
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 max-w-lg mx-auto">
              {[
                { icon: LineChart, title: "Score Trends", desc: "Track changes over time" },
                { icon: Download, title: "PDF Export", desc: "Printable report format" },
                { icon: Clock, title: "History", desc: "Detailed snapshot log" },
              ].map(({ icon: Icon, title, desc }) => (
                <Card key={title} className="text-left hover:scale-[1.02] transition-all">
                  <CardContent className="!p-4">
                    <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-blue-500/10 to-indigo-500/10 flex items-center justify-center mb-2">
                      <Icon className="w-4 h-4 text-blue-400" />
                    </div>
                    <h4 className="text-sm font-semibold mb-0.5">{title}</h4>
                    <p className="text-[11px] text-muted-foreground">{desc}</p>
                  </CardContent>
                </Card>
              ))}
            </div>
          </motion.div>
        )}
      </div>
      <Footer />
    </div>
  );
}
