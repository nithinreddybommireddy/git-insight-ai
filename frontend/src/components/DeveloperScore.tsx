import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { Card, CardContent } from "@/components/ui/card";
import type { DeveloperScore as Score } from "@/services/api";
import {
  Star,
  GitFork,
  Code2,
  TrendingUp,
  Shield,
  Trophy,
  BookOpen,
} from "lucide-react";

interface DeveloperScoreProps {
  score: Score;
}

function getScoreColor(score: number): string {
  if (score >= 80) return "from-emerald-400 via-green-400 to-teal-400";
  if (score >= 60) return "from-cyan-400 via-blue-400 to-indigo-400";
  if (score >= 40) return "from-amber-400 via-yellow-400 to-orange-400";
  if (score >= 20) return "from-red-400 via-rose-400 to-pink-400";
  return "from-gray-400 to-slate-400";
}

function getLevelIcon(level: string) {
  switch (level) {
    case "Expert": return Trophy;
    case "Advanced": return TrendingUp;
    case "Intermediate": return Code2;
    case "Beginner": return BookOpen;
    default: return Code2;
  }
}

function Gauge({ value, size = 160 }: { value: number; size?: number }) {
  const [animatedValue, setAnimatedValue] = useState(0);
  const circumference = 2 * Math.PI * (size * 0.4);
  const strokeDashoffset = circumference - (animatedValue / 100) * circumference;

  useEffect(() => {
    const timer = setTimeout(() => setAnimatedValue(value), 300);
    return () => clearTimeout(timer);
  }, [value]);

  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="-rotate-90">
        <circle
          cx={size / 2}
          cy={size / 2}
          r={size * 0.4}
          fill="none"
          stroke="currentColor"
          strokeWidth={10}
          className="text-muted/20"
        />
        <motion.circle
          cx={size / 2}
          cy={size / 2}
          r={size * 0.4}
          fill="none"
          stroke={`url(#gauge-gradient)`}
          strokeWidth={10}
          strokeLinecap="round"
          strokeDasharray={circumference}
          initial={{ strokeDashoffset: circumference }}
          animate={{ strokeDashoffset }}
          transition={{ duration: 1.5, ease: "easeOut" }}
        />
        <defs>
          <linearGradient id="gauge-gradient" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor={value >= 80 ? "#34d399" : value >= 60 ? "#22d3ee" : value >= 40 ? "#fbbf24" : "#f87171"} />
            <stop offset="100%" stopColor={value >= 80 ? "#2dd4bf" : value >= 60 ? "#818cf8" : value >= 40 ? "#f97316" : "#e11d48"} />
          </linearGradient>
        </defs>
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <motion.span
          className="text-3xl font-bold tabular-nums"
          initial={{ opacity: 0, scale: 0 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ delay: 0.5, type: "spring" }}
        >
          {animatedValue}
        </motion.span>
        <span className="text-[10px] text-muted-foreground uppercase tracking-wider mt-0.5">Score</span>
      </div>
    </div>
  );
}

export function DeveloperScoreCard({ score }: DeveloperScoreProps) {
  const LevelIcon = getLevelIcon(score.level);

  const statCards = [
    {
      icon: Star,
      label: "Total Stars",
      value: score.totalStars.toLocaleString(),
      color: "from-amber-500 to-yellow-500",
    },
    {
      icon: GitFork,
      label: "Total Forks",
      value: score.totalForks.toLocaleString(),
      color: "from-cyan-500 to-blue-500",
    },
    {
      icon: BookOpen,
      label: "Repositories",
      value: score.totalRepositories.toLocaleString(),
      color: "from-violet-500 to-purple-500",
    },
    {
      icon: Code2,
      label: "Languages",
      value: score.languageCount.toLocaleString(),
      color: "from-emerald-500 to-teal-500",
    },
  ];

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: 0.1 }}
    >
      <Card>
        <CardContent>
          <div className="flex flex-col lg:flex-row gap-8 items-center">
            {/* Gauge */}
            <div className="text-center shrink-0">
              <Gauge value={score.overallScore} />
              <motion.div
                initial={{ opacity: 0, y: 5 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 1 }}
                className="mt-3 inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-gradient-to-r from-primary/10 to-accent/10 border border-primary/20"
              >
                <LevelIcon className="w-4 h-4 text-primary" />
                <span className="text-sm font-medium">{score.level}</span>
              </motion.div>
            </div>

            {/* Stats & details */}
            <div className="flex-1 w-full">
              <h3 className="text-lg font-semibold mb-4">Developer Score</h3>

              <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 mb-6">
                {statCards.map((stat) => (
                  <div
                    key={stat.label}
                    className="glass rounded-xl p-3 text-center hover:scale-[1.03] transition-all duration-200"
                  >
                    <div className={`w-8 h-8 rounded-lg bg-gradient-to-br ${stat.color} flex items-center justify-center mx-auto mb-1.5`}>
                      <stat.icon className="w-4 h-4 text-white" />
                    </div>
                    <p className="text-sm font-bold tabular-nums">{stat.value}</p>
                    <p className="text-[10px] text-muted-foreground">{stat.label}</p>
                  </div>
                ))}
              </div>

              {/* Sub-scores */}
              <div className="space-y-2.5">
                <ScoreBar label="Repository Health" value={score.avgHealthScore} color="from-violet-500 to-purple-500" />
                <ScoreBar label="Popularity" value={score.avgPopularityScore} color="from-amber-500 to-orange-500" />
                <ScoreBar label="Maintenance" value={score.avgMaintenanceScore} color="from-cyan-500 to-blue-500" />
              </div>

              {/* Languages */}
              {score.languages && score.languages.length > 0 && (
                <div className="mt-4 pt-4 border-t border-border">
                  <p className="text-xs text-muted-foreground mb-2">Languages</p>
                  <div className="flex flex-wrap gap-1.5">
                    {score.languages.map((lang) => (
                      <span
                        key={lang}
                        className="text-[11px] px-2 py-0.5 rounded-full bg-muted/50 text-muted-foreground"
                      >
                        {lang}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>
        </CardContent>
      </Card>
    </motion.div>
  );
}

function ScoreBar({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="flex items-center gap-3">
      <span className="text-xs text-muted-foreground w-28 shrink-0">{label}</span>
      <div className="flex-1 h-2 rounded-full bg-muted/30 overflow-hidden">
        <motion.div
          className={`h-full rounded-full bg-gradient-to-r ${color}`}
          initial={{ width: 0 }}
          animate={{ width: `${value}%` }}
          transition={{ duration: 1, delay: 0.5, ease: "easeOut" }}
        />
      </div>
      <span className="text-xs font-semibold tabular-nums w-8 text-right">{value}</span>
    </div>
  );
}
