import { useState, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { aiApi } from "@/services/api";
import toast from "react-hot-toast";
import {
  Brain,
  Search,
  ArrowLeft,
  Loader2,
  Sparkles,
  FileText,
  Code2,
  Map,
  Target,
  Users,
  ExternalLink,
} from "lucide-react";

type AITab = "summary" | "skills" | "roadmap" | "interview";

const TAB_CONFIG: { id: AITab; label: string; icon: any; description: string }[] = [
  { id: "summary", label: "Summary", icon: FileText, description: "Comprehensive developer assessment" },
  { id: "skills", label: "Skills", icon: Code2, description: "Technology skill detection & analysis" },
  { id: "roadmap", label: "Roadmap", icon: Map, description: "Personalized career development plan" },
  { id: "interview", label: "Interview", icon: Target, description: "Interview readiness assessment" },
];

function TabButton({
  active,
  onClick,
  icon: Icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: any;
  label: string;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm transition-all duration-200 ${
        active
          ? "bg-primary/10 text-primary font-medium shadow-sm"
          : "text-muted-foreground hover:text-foreground hover:bg-muted/50"
      }`}
    >
      <Icon className="w-4 h-4" />
      {label}
    </button>
  );
}

function MarkdownRenderer({ text }: { text: string }) {
  if (!text) return null;

  const lines = text.split("\n").filter((l) => l.trim());
  return (
    <div className="space-y-3">
      {lines.map((line, i) => {
        // Bold headings
        if (line.startsWith("**") && line.endsWith("**")) {
          return (
            <h3 key={i} className="text-sm font-semibold text-primary pt-2">
              {line.replace(/\*\*/g, "")}
            </h3>
          );
        }
        // Star items
        if (line.startsWith("- **")) {
          const match = line.match(/- \*\*(.+?)\*\*\s*[:.]?\s*(.*)/);
          if (match) {
            return (
              <div key={i} className="flex items-start gap-2 text-sm">
                <span className="w-1.5 h-1.5 rounded-full bg-primary/60 mt-1.5 shrink-0" />
                <div>
                  <span className="font-semibold">{match[1]}</span>
                  {match[2] && <span className="text-muted-foreground">: {match[2]}</span>}
                </div>
              </div>
            );
          }
        }
        if (line.startsWith("- ")) {
          return (
            <div key={i} className="flex items-start gap-2 text-sm">
              <span className="w-1.5 h-1.5 rounded-full bg-primary/40 mt-1.5 shrink-0" />
              <span className="text-muted-foreground">{line.replace("- ", "")}</span>
            </div>
          );
        }
        // Numbered items
        if (/^\d+\./.test(line)) {
          return (
            <div key={i} className="flex items-start gap-3 text-sm ml-2">
              <span className="text-primary font-semibold shrink-0">
                {line.match(/^\d+/)?.[0]}.
              </span>
              <span className="text-muted-foreground">
                {line.replace(/^\d+\.\s*/, "")}
              </span>
            </div>
          );
        }
        // Regular paragraphs
        return (
          <p key={i} className="text-sm text-muted-foreground leading-relaxed">
            {line}
          </p>
        );
      })}
    </div>
  );
}

export function AIAnalysis() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [activeTab, setActiveTab] = useState<AITab>("summary");
  const [results, setResults] = useState<Record<AITab, string>>({} as Record<AITab, string>);
  const [loading, setLoading] = useState<Record<AITab, boolean>>({} as Record<AITab, boolean>);
  const [searched, setSearched] = useState(false);
  const [aiEnabled, setAiEnabled] = useState<boolean | null>(null);

  const handleAnalyze = useCallback(async () => {
    const u = username.trim();
    if (!u) {
      toast.error("Enter a GitHub username");
      return;
    }

    setSearched(true);
    setResults({} as Record<AITab, string>);

    // Check AI status first
    try {
      const statusRes = await aiApi.getStatus();
      setAiEnabled(statusRes.success ? statusRes.data.enabled : false);
    } catch {
      setAiEnabled(false);
    }

    // Load all tabs
    const tabs: AITab[] = ["summary", "skills", "roadmap", "interview"];

    for (const tab of tabs) {
      setLoading((prev) => ({ ...prev, [tab]: true }));

      try {
        let res;
        switch (tab) {
          case "summary":
            res = await aiApi.getSummary(u);
            break;
          case "skills":
            res = await aiApi.getSkillAnalysis(u);
            break;
          case "roadmap":
            res = await aiApi.getCareerRoadmap(u);
            break;
          case "interview":
            res = await aiApi.getInterviewReadiness(u);
            break;
        }
        if (res?.success) {
          setResults((prev) => ({ ...prev, [tab]: res.data }));
        } else {
          setResults((prev) => ({
            ...prev,
            [tab]: res?.data || "No data available for this analysis.",
          }));
        }
      } catch {
        setResults((prev) => ({
          ...prev,
          [tab]: (prev as Record<string, string>)[tab] || "Failed to generate. Please try again.",
        }));
      } finally {
        setLoading((prev) => ({ ...prev, [tab]: false }));
      }
    }

    toast.success("AI analysis complete!");
  }, [username]);

  return (
    <div className="min-h-screen pt-20 pb-16">
      <div className="max-w-5xl mx-auto px-4">
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
            <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-amber-500 to-orange-500 flex items-center justify-center shadow-lg shadow-amber-500/20">
              <Brain className="w-7 h-7 text-white" />
            </div>
            <div>
              <h1 className="text-2xl font-bold">
                AI <span className="gradient-text">Analysis</span>
              </h1>
              <p className="text-sm text-muted-foreground">
                AI-powered developer summaries, skill detection,{" "}
                <span className="text-primary">career roadmap</span>, and interview readiness
              </p>
            </div>
            {aiEnabled === false && (
              <div className="ml-auto px-3 py-1.5 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-400 text-[11px] flex items-center gap-1.5">
                <Sparkles className="w-3 h-3" />
                Fallback mode
              </div>
            )}
          </div>
        </motion.div>

        {/* Search Input */}
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
                    placeholder="Enter GitHub username for AI analysis..."
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    onKeyDown={(e) => e.key === "Enter" && handleAnalyze()}
                    className="pl-9"
                  />
                </div>
                <Button
                  variant="primary"
                  onClick={handleAnalyze}
                  disabled={!username.trim()}
                  className="gap-2"
                >
                  {loading.summary || loading.skills || loading.roadmap || loading.interview ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <Sparkles className="w-4 h-4" />
                  )}
                  Analyze
                </Button>
              </div>
            </CardContent>
          </Card>
        </motion.div>

        {/* Tab Navigation */}
        {searched && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            className="flex flex-wrap gap-2 mb-6"
          >
            {TAB_CONFIG.map((tab) => (
              <TabButton
                key={tab.id}
                active={activeTab === tab.id}
                onClick={() => setActiveTab(tab.id)}
                icon={tab.icon}
                label={tab.label}
              />
            ))}
            {username && (
              <div className="ml-auto flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="sm"
                  className="gap-1.5 text-xs"
                  onClick={() => navigate(`/compare?user1=${username}&user2=torvalds`)}
                >
                  <Users className="w-3 h-3" />
                  Compare
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  className="gap-1.5 text-xs"
                  onClick={() => navigate(`/search?q=${username}`)}
                >
                  <ExternalLink className="w-3 h-3" />
                  View Profile
                </Button>
              </div>
            )}
          </motion.div>
        )}

        {/* Results */}
        {searched && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
          >
            {TAB_CONFIG.map((tab) => {
              if (tab.id !== activeTab) return null;
              const isLoading = loading[tab.id];
              const content = results[tab.id];

              return (
                <Card key={tab.id} className="overflow-hidden">
                  {!aiEnabled && !isLoading && (
                    <div className="px-6 py-2 bg-gradient-to-r from-amber-500/5 to-orange-500/5 border-b border-amber-500/10">
                      <p className="text-[11px] text-amber-400/80 flex items-center gap-1.5">
                        <Sparkles className="w-3 h-3" />
                        Running in fallback mode — set GEMINI_API_KEY for AI-powered responses
                      </p>
                    </div>
                  )}
                  <CardContent className="!p-6">
                    <div className="flex items-center gap-2.5 mb-5">
                      <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-amber-500 to-orange-500 flex items-center justify-center">
                        <tab.icon className="w-4 h-4 text-white" />
                      </div>
                      <div>
                        <h3 className="text-base font-semibold">{tab.label}</h3>
                        <p className="text-xs text-muted-foreground">{tab.description}</p>
                      </div>
                    </div>

                    {isLoading ? (
                      <div className="flex flex-col items-center py-12">
                        <Loader2 className="w-8 h-8 text-primary animate-spin mb-3" />
                        <p className="text-sm text-muted-foreground animate-pulse">
                          AI is analyzing {username}...
                        </p>
                      </div>
                    ) : content ? (
                      <div className="prose prose-sm dark:prose-invert max-w-none">
                        <div className="bg-muted/10 rounded-xl p-5 border border-border/30">
                          <MarkdownRenderer text={content} />
                        </div>
                      </div>
                    ) : (
                      <div className="text-center py-8">
                        <FileText className="w-10 h-10 text-muted-foreground/50 mx-auto mb-3" />
                        <p className="text-sm text-muted-foreground">
                          No result available
                        </p>
                      </div>
                    )}
                  </CardContent>
                </Card>
              );
            })}

            {/* Quick actions */}
            <div className="flex flex-wrap gap-3 mt-6 justify-center">
              {[
                { label: "AI Summary", tab: "summary" as AITab },
                { label: "Skills Analysis", tab: "skills" as AITab },
                { label: "Career Roadmap", tab: "roadmap" as AITab },
                { label: "Interview Prep", tab: "interview" as AITab },
              ].map(({ label, tab }) => (
                <Button
                  key={tab}
                  variant="outline"
                  size="sm"
                  onClick={() => setActiveTab(tab)}
                  className={`gap-1.5 ${activeTab === tab ? "ring-1 ring-primary" : ""}`}
                >
                  {label}
                </Button>
              ))}
            </div>
          </motion.div>
        )}

        {/* Empty state */}
        {!searched && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="text-center py-16"
          >
            <div className="w-24 h-24 rounded-3xl bg-gradient-to-br from-amber-500/10 to-orange-500/10 flex items-center justify-center mx-auto mb-5">
              <Sparkles className="w-12 h-12 text-amber-400" />
            </div>
            <h3 className="text-xl font-semibold mb-2">AI-Powered Developer Analysis</h3>
            <p className="text-sm text-muted-foreground max-w-lg mx-auto mb-8">
              Enter a GitHub username to get an AI-generated developer summary, skill analysis,
              personalized career roadmap, and interview readiness assessment.
            </p>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 max-w-lg mx-auto">
              {[
                {
                  icon: FileText,
                  title: "Developer Summary",
                  desc: "Comprehensive assessment based on GitHub metrics",
                },
                {
                  icon: Code2,
                  title: "Skill Detection",
                  desc: "Identify strengths, weaknesses, and tech stack",
                },
                {
                  icon: Map,
                  title: "Career Roadmap",
                  desc: "Personalized 3/6/12-month growth plan",
                },
                {
                  icon: Target,
                  title: "Interview Readiness",
                  desc: "Role fit analysis and preparation tips",
                },
              ].map(({ icon: Icon, title, desc }) => (
                <Card key={title} className="text-left hover:scale-[1.02] transition-all">
                  <CardContent className="!p-4">
                    <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-amber-500/10 to-orange-500/10 flex items-center justify-center mb-2">
                      <Icon className="w-4 h-4 text-amber-400" />
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
    </div>
  );
}
