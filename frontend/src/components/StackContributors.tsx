import { motion } from "framer-motion";
import { Card, CardContent } from "@/components/ui/card";
import type { LanguageBreakdown, GitHubContributor } from "@/services/api";
import { Code2, Users, BarChart3, GitCommitHorizontal } from "lucide-react";

const LANG_COLORS: Record<string, string> = {
  JavaScript: "from-yellow-400 to-amber-500",
  TypeScript: "from-blue-400 to-indigo-500",
  Python: "from-emerald-400 to-teal-500",
  Java: "from-orange-400 to-red-500",
  Go: "from-cyan-400 to-sky-500",
  Rust: "from-orange-500 to-amber-600",
  C: "from-slate-400 to-slate-600",
  "C++": "from-pink-400 to-rose-500",
  "C#": "from-violet-400 to-purple-500",
  Ruby: "from-red-400 to-rose-600",
  PHP: "from-indigo-400 to-blue-600",
  Swift: "from-orange-400 to-red-500",
  Kotlin: "from-purple-400 to-fuchsia-500",
  Shell: "from-lime-400 to-green-500",
  HTML: "from-orange-400 to-red-500",
  CSS: "from-blue-400 to-indigo-500",
  Vue: "from-emerald-400 to-green-500",
  Dart: "from-cyan-400 to-teal-500",
  Scala: "from-red-400 to-rose-500",
  Haskell: "from-purple-400 to-violet-600",
};

function langGradient(language: string): string {
  return LANG_COLORS[language] ?? "from-primary to-accent";
}

function initials(login: string): string {
  return login.slice(0, 2).toUpperCase();
}

interface StackContributorsProps {
  languages: LanguageBreakdown[];
  contributors: GitHubContributor[];
  loading?: boolean;
}

export function StackContributors({ languages, contributors, loading }: StackContributorsProps) {
  const topLanguages = languages.slice(0, 8);
  const maxPercentage = topLanguages.reduce((max, l) => Math.max(max, l.percentage), 0);

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.15 }}
      className="grid grid-cols-1 lg:grid-cols-2 gap-6"
    >
      {/* Byte-weighted language breakdown */}
      <Card>
        <CardContent>
          <div className="flex items-center justify-between mb-5">
            <div className="flex items-center gap-2.5">
              <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-emerald-500 to-teal-500 flex items-center justify-center">
                <Code2 className="w-5 h-5 text-white" />
              </div>
              <div>
                <h3 className="text-sm font-semibold leading-tight">Language Stack</h3>
                <p className="text-[11px] text-muted-foreground">Byte-weighted by code volume</p>
              </div>
            </div>
            {topLanguages.length > 0 && (
              <span className="inline-flex items-center gap-1 text-[11px] px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                <BarChart3 className="w-3 h-3" />
                {topLanguages.length} {topLanguages.length === 1 ? "language" : "languages"}
              </span>
            )}
          </div>

          {loading ? (
            <div className="space-y-3 py-2">
              {[...Array(5)].map((_, i) => (
                <div key={i} className="flex items-center gap-3">
                  <div className="w-20 h-3 rounded-full bg-muted/40 animate-pulse" />
                  <div className="flex-1 h-2 rounded-full bg-muted/30 animate-pulse" />
                  <div className="w-8 h-3 rounded-full bg-muted/40 animate-pulse" />
                </div>
              ))}
            </div>
          ) : topLanguages.length === 0 ? (
            <div className="text-center py-8">
              <Code2 className="w-8 h-8 text-muted-foreground/40 mx-auto mb-2" />
              <p className="text-xs text-muted-foreground">No language data available</p>
            </div>
          ) : (
            <div className="space-y-3">
              {topLanguages.map((lang, idx) => (
                <div key={lang.language} className="group">
                  <div className="flex items-center justify-between mb-1">
                    <span className="text-xs font-medium flex items-center gap-2">
                      <span
                        className={`w-2 h-2 rounded-full bg-gradient-to-br ${langGradient(lang.language)}`}
                      />
                      {lang.language}
                    </span>
                    <span className="text-xs font-semibold tabular-nums text-muted-foreground">
                      {lang.percentage.toFixed(1)}%
                    </span>
                  </div>
                  <div className="h-2 rounded-full bg-muted/30 overflow-hidden">
                    <motion.div
                      className={`h-full rounded-full bg-gradient-to-r ${langGradient(lang.language)}`}
                      initial={{ width: 0 }}
                      animate={{ width: `${(lang.percentage / Math.max(maxPercentage, 1)) * 100}%` }}
                      transition={{ duration: 0.9, delay: 0.2 + idx * 0.08, ease: "easeOut" }}
                    />
                  </div>
                </div>
              ))}
              {languages.length > topLanguages.length && (
                <p className="text-[11px] text-muted-foreground pt-1.5">
                  +{languages.length - topLanguages.length} more languages
                </p>
              )}
            </div>
          )}
        </CardContent>
      </Card>

      {/* Top contributors */}
      <Card>
        <CardContent>
          <div className="flex items-center justify-between mb-5">
            <div className="flex items-center gap-2.5">
              <div className="w-9 h-9 rounded-lg bg-gradient-to-br from-cyan-500 to-blue-500 flex items-center justify-center">
                <Users className="w-5 h-5 text-white" />
              </div>
              <div>
                <h3 className="text-sm font-semibold leading-tight">Top Contributors</h3>
                <p className="text-[11px] text-muted-foreground">People building in these repos</p>
              </div>
            </div>
            {contributors.length > 0 && (
              <span className="inline-flex items-center gap-1 text-[11px] px-2.5 py-1 rounded-full bg-cyan-500/10 text-cyan-400 border border-cyan-500/20">
                <GitCommitHorizontal className="w-3 h-3" />
                {contributors.length} total
              </span>
            )}
          </div>

          {loading ? (
            <div className="space-y-3 py-2">
              {[...Array(5)].map((_, i) => (
                <div key={i} className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full bg-muted/40 animate-pulse" />
                  <div className="flex-1">
                    <div className="w-24 h-3 rounded-full bg-muted/40 animate-pulse mb-1.5" />
                    <div className="w-16 h-2 rounded-full bg-muted/30 animate-pulse" />
                  </div>
                </div>
              ))}
            </div>
          ) : contributors.length === 0 ? (
            <div className="text-center py-8">
              <Users className="w-8 h-8 text-muted-foreground/40 mx-auto mb-2" />
              <p className="text-xs text-muted-foreground">No contributor data available</p>
            </div>
          ) : (
            <div className="space-y-2.5">
              {contributors.slice(0, 8).map((contributor, idx) => (
                <motion.div
                  key={contributor.login}
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.15 + idx * 0.07 }}
                  className="flex items-center gap-3 group"
                >
                  {contributor.avatarUrl ? (
                    <img
                      src={contributor.avatarUrl}
                      alt={contributor.login}
                      className="w-8 h-8 rounded-full object-cover ring-1 ring-border"
                      loading="lazy"
                    />
                  ) : (
                    <div className="w-8 h-8 rounded-full bg-gradient-to-br from-cyan-500 to-blue-600 flex items-center justify-center text-[10px] font-bold text-white ring-1 ring-border">
                      {initials(contributor.login)}
                    </div>
                  )}
                  <div className="flex-1 min-w-0">
                    <p className="text-xs font-medium truncate">{contributor.login}</p>
                    <p className="text-[10px] text-muted-foreground">
                      {contributor.contributions.toLocaleString()} contributions
                    </p>
                  </div>
                  <div className="flex items-center gap-1 text-[10px] text-muted-foreground tabular-nums">
                    <GitCommitHorizontal className="w-3 h-3 opacity-60" />
                    #{idx + 1}
                  </div>
                </motion.div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </motion.div>
  );
}
