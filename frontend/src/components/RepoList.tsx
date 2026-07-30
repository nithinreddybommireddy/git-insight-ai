import { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import type { Repository } from "@/services/api";
import {
  Star,
  GitFork,
  AlertCircle,
  BookOpen,
  ExternalLink,
  ChevronDown,
  ChevronUp,
  Shield,
  FileText,
} from "lucide-react";

interface RepoListProps {
  repos: Repository[];
  loading?: boolean;
}

function getScoreColor(score: number): string {
  if (score >= 80) return "from-emerald-500 to-green-500";
  if (score >= 60) return "from-cyan-500 to-blue-500";
  if (score >= 40) return "from-amber-500 to-yellow-500";
  return "from-red-500 to-rose-500";
}

function getLanguageColor(lang: string | null): string {
  const colors: Record<string, string> = {
    TypeScript: "bg-blue-500",
    JavaScript: "bg-yellow-400",
    Python: "bg-blue-400",
    Java: "bg-orange-500",
    Go: "bg-cyan-500",
    Rust: "bg-orange-600",
    "C++": "bg-pink-500",
    C: "bg-gray-500",
    Ruby: "bg-red-500",
    PHP: "bg-indigo-400",
    Swift: "bg-orange-400",
    Kotlin: "bg-purple-500",
    Dart: "bg-teal-400",
    HTML: "bg-orange-500",
    CSS: "bg-purple-400",
    Vue: "bg-emerald-500",
    Shell: "bg-green-600",
    Dockerfile: "bg-blue-500",
  };
  return lang ? colors[lang] || "bg-muted-foreground" : "bg-muted-foreground";
}

function ScoreBadge({ score, label }: { score: number; label: string; icon?: any }) {
  return (
    <div className="flex items-center gap-2">
      <div className={`w-1.5 h-1.5 rounded-full bg-gradient-to-br ${getScoreColor(score)}`} />
      <div className="flex-1">
        <div className="flex items-center justify-between gap-2">
          <span className="text-[11px] text-muted-foreground uppercase tracking-wider">{label}</span>
          <span className="text-xs font-semibold tabular-nums">{score}</span>
        </div>
        <div className="mt-1 h-1.5 rounded-full bg-muted/50 overflow-hidden">
          <div
            className={`h-full rounded-full bg-gradient-to-r ${getScoreColor(score)} transition-all duration-700`}
            style={{ width: `${score}%` }}
          />
        </div>
      </div>
    </div>
  );
}

export function RepoList({ repos, loading }: RepoListProps) {
  const [expandedRepo, setExpandedRepo] = useState<string | null>(null);
  const [showAll, setShowAll] = useState(false);

  const displayedRepos = showAll ? repos : repos.slice(0, 6);

  if (loading) {
    return (
      <Card>
        <CardContent>
          <div className="space-y-4 animate-pulse">
            {[...Array(3)].map((_, i) => (
              <div key={i} className="h-24 rounded-xl bg-muted/30" />
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  if (!repos.length) return null;

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.2 }}
    >
      <Card>
        <CardContent>
          <div className="flex items-center justify-between mb-6">
            <div className="flex items-center gap-2.5">
              <div className="w-10 h-10 rounded-lg bg-gradient-to-br from-violet-500 to-purple-500 flex items-center justify-center">
                <BookOpen className="w-5 h-5 text-white" />
              </div>
              <div>
                <h3 className="text-lg font-semibold">Repositories</h3>
                <p className="text-xs text-muted-foreground">{repos.length} repositories analyzed</p>
              </div>
            </div>
          </div>

          <div className="grid gap-3">
            {displayedRepos.map((repo, index) => (
              <motion.div
                key={repo.name}
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: index * 0.05 }}
              >
                <div
                  className="group rounded-xl border border-border/50 hover:border-primary/30 bg-card/50 hover:bg-card/80 transition-all duration-200 cursor-pointer"
                  onClick={() => setExpandedRepo(expandedRepo === repo.name ? null : repo.name)}
                >
                  <div className="p-4">
                    <div className="flex items-start justify-between gap-4">
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2 mb-1">
                          {repo.fork && (
                            <span className="text-[10px] px-1.5 py-0.5 rounded bg-muted/50 text-muted-foreground uppercase font-medium">Fork</span>
                          )}
                          <h4 className="font-medium text-sm truncate group-hover:text-primary transition-colors">
                            {repo.name}
                          </h4>
                        </div>
                        {repo.description && (
                          <p className="text-xs text-muted-foreground line-clamp-2 mt-1">
                            {repo.description}
                          </p>
                        )}
                        <div className="flex flex-wrap items-center gap-3 mt-3">
                          {repo.language && (
                            <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
                              <span className={`w-2.5 h-2.5 rounded-full ${getLanguageColor(repo.language)}`} />
                              {repo.language}
                            </span>
                          )}
                          <span className="flex items-center gap-1 text-xs text-muted-foreground">
                            <Star className="w-3 h-3" />
                            {repo.stars}
                          </span>
                          <span className="flex items-center gap-1 text-xs text-muted-foreground">
                            <GitFork className="w-3 h-3" />
                            {repo.forks}
                          </span>
                          {repo.openIssues > 0 && (
                            <span className="flex items-center gap-1 text-xs text-muted-foreground">
                              <AlertCircle className="w-3 h-3" />
                              {repo.openIssues} issues
                            </span>
                          )}
                          <div className={`ml-auto flex items-center gap-1.5 px-2 py-0.5 rounded-full text-[10px] font-medium bg-gradient-to-r ${getScoreColor(repo.healthScore)} text-white`}>
                            <Shield className="w-3 h-3" />
                            {repo.healthScore}
                          </div>
                        </div>
                      </div>
                      <div className="shrink-0 text-muted-foreground group-hover:text-foreground transition-colors">
                        {expandedRepo === repo.name ? (
                          <ChevronUp className="w-4 h-4" />
                        ) : (
                          <ChevronDown className="w-4 h-4" />
                        )}
                      </div>
                    </div>
                  </div>

                  <AnimatePresence>
                    {expandedRepo === repo.name && (
                      <motion.div
                        initial={{ height: 0, opacity: 0 }}
                        animate={{ height: "auto", opacity: 1 }}
                        exit={{ height: 0, opacity: 0 }}
                        transition={{ duration: 0.2 }}
                        className="overflow-hidden"
                      >
                        <div className="px-4 pb-4 pt-0 border-t border-border/50">
                          <div className="pt-3 grid grid-cols-2 sm:grid-cols-4 gap-3">
                            <ScoreBadge score={repo.healthScore} label="Overall" />
                            <ScoreBadge score={repo.popularityScore} label="Popularity" />
                            <ScoreBadge score={repo.maintenanceScore} label="Maintenance" />
                            <ScoreBadge score={repo.documentationScore} label="Documentation" />
                          </div>
                          <div className="flex items-center gap-2 mt-3 pt-3 border-t border-border/30">
                            <Button
                              variant="ghost"
                              size="sm"
                              asChild
                              className="gap-1.5 text-xs h-8"
                              onClick={(e) => e.stopPropagation()}
                            >
                              <a href={repo.htmlUrl} target="_blank" rel="noopener noreferrer">
                                <ExternalLink className="w-3 h-3" />
                                View on GitHub
                              </a>
                            </Button>
                            {repo.hasLicense && (
                              <span className="text-[11px] text-muted-foreground flex items-center gap-1">
                                <FileText className="w-3 h-3" />
                                Licensed
                              </span>
                            )}
                            {repo.topics && repo.topics.length > 0 && (
                              <div className="flex flex-wrap gap-1 ml-auto">
                                {repo.topics.slice(0, 3).map(topic => (
                                  <span key={topic} className="text-[10px] px-1.5 py-0.5 rounded-full bg-primary/5 text-primary">
                                    {topic}
                                  </span>
                                ))}
                              </div>
                            )}
                          </div>
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              </motion.div>
            ))}
          </div>

          {repos.length > 6 && (
            <div className="mt-4 text-center">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setShowAll(!showAll)}
                className="gap-2 text-xs text-muted-foreground"
              >
                {showAll ? (
                  <>Show Less <ChevronUp className="w-3 h-3" /></>
                ) : (
                  <>View All {repos.length} Repositories <ChevronDown className="w-3 h-3" /></>
                )}
              </Button>
            </div>
          )}
        </CardContent>
      </Card>
    </motion.div>
  );
}
