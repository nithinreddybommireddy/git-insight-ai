import { motion } from "framer-motion";
import type { LanguageBreakdown } from "@/services/api";
import { Code2 } from "lucide-react";

// ── Language color mapping ──

const LANG_COLORS: Record<string, string> = {
  TypeScript: "#3178C6",
  JavaScript: "#F7DF1E",
  Python: "#3776AB",
  Java: "#B07219",
  Go: "#00ADD8",
  Rust: "#DEA584",
  "C++": "#00599C",
  C: "#555555",
  "C#": "#178600",
  Ruby: "#CC342D",
  PHP: "#4F5D95",
  Swift: "#F05138",
  Kotlin: "#A97BFF",
  Scala: "#DC322F",
  Dart: "#00B4AB",
  Lua: "#000080",
  Haskell: "#5E5086",
  Shell: "#89E051",
  PowerShell: "#012456",
  HTML: "#E34F26",
  CSS: "#1572B6",
  SCSS: "#C6538C",
  Vue: "#4FC08D",
  Svelte: "#FF3E00",
  Elm: "#60B5CC",
  Elixir: "#4E2A59",
  Erlang: "#B83998",
  Clojure: "#DB5855",
  ObjectiveC: "#438EFF",
};

function getLangColor(lang: string): string {
  return LANG_COLORS[lang] || `hsl(${Math.abs(lang.split("").reduce((a, c) => a + c.charCodeAt(0), 0) % 360)}, 70%, 55%)`;
}

// ── Props ──

interface SkillsMatrixProps {
  languages: LanguageBreakdown[];
  variant?: "card" | "inline";
}

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.05 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, x: -10 },
  visible: { opacity: 1, x: 0 },
};

// ── Component ──

export function SkillsMatrix({ languages, variant = "card" }: SkillsMatrixProps) {
  if (!languages || languages.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-6 text-muted-foreground">
        <Code2 className="w-8 h-8 mb-2 opacity-50" />
        <p className="text-xs">No language data available</p>
      </div>
    );
  }

  const sorted = [...languages].sort((a, b) => b.percentage - a.percentage);
  const top = sorted.slice(0, 6);

  if (variant === "inline") {
    return (
      <div className="flex flex-wrap items-center gap-1.5">
        {top.map((lang) => (
          <span
            key={lang.language}
            className="text-[10px] px-2 py-0.5 rounded-full flex items-center gap-1"
            style={{
              backgroundColor: `${getLangColor(lang.language)}20`,
              color: getLangColor(lang.language),
            }}
          >
            <span
              className="w-1.5 h-1.5 rounded-full"
              style={{ backgroundColor: getLangColor(lang.language) }}
            />
            {lang.language}
          </span>
        ))}
        {sorted.length > 6 && (
          <span className="text-[10px] text-muted-foreground">+{sorted.length - 6} more</span>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2">
        <Code2 className="w-4 h-4 text-muted-foreground" />
        <h3 className="text-sm font-semibold">Skills Matrix</h3>
      </div>

      <motion.div
        variants={containerVariants}
        initial="hidden"
        animate="visible"
        className="space-y-2"
      >
        {top.map((lang) => (
          <motion.div
            key={lang.language}
            variants={itemVariants}
            className="group"
          >
            <div className="flex items-center gap-2.5">
              <span
                className="w-2.5 h-2.5 rounded-sm shrink-0"
                style={{ backgroundColor: getLangColor(lang.language) }}
              />
              <span className="text-xs font-medium w-24 shrink-0">{lang.language}</span>
              <div className="flex-1 h-2 rounded-full bg-muted/20 overflow-hidden">
                <motion.div
                  className="h-full rounded-full"
                  style={{
                    background: `linear-gradient(90deg, ${getLangColor(lang.language)}, ${getLangColor(lang.language)}88)`,
                  }}
                  initial={{ width: 0 }}
                  animate={{ width: `${Math.min(lang.percentage, 100)}%` }}
                  transition={{ duration: 0.8, ease: "easeOut" }}
                />
              </div>
              <span className="text-[10px] font-semibold tabular-nums w-12 text-right text-muted-foreground">
                {lang.percentage}%
              </span>
            </div>
          </motion.div>
        ))}
      </motion.div>

      {sorted.length > 6 && (
        <details className="group">
          <summary className="text-[10px] text-muted-foreground cursor-pointer hover:text-primary transition-colors list-none flex items-center gap-1">
            <span>Show {sorted.length - 6} more languages</span>
            <motion.span
              className="inline-block"
              animate={{ rotate: 0 }}
            >
              ▾
            </motion.span>
          </summary>
          <div className="mt-2 space-y-2">
            {sorted.slice(6).map((lang) => (
              <div key={lang.language} className="flex items-center gap-2.5">
                <span
                  className="w-2 h-2 rounded-sm shrink-0"
                  style={{ backgroundColor: getLangColor(lang.language) }}
                />
                <span className="text-[11px] font-medium w-24 shrink-0">{lang.language}</span>
                <div className="flex-1 h-1.5 rounded-full bg-muted/20 overflow-hidden">
                  <motion.div
                    className="h-full rounded-full"
                    style={{ backgroundColor: getLangColor(lang.language) }}
                    initial={{ width: 0 }}
                    animate={{ width: `${Math.min(lang.percentage, 100)}%` }}
                    transition={{ duration: 0.5, ease: "easeOut" }}
                  />
                </div>
                <span className="text-[10px] tabular-nums w-12 text-right text-muted-foreground">
                  {lang.percentage}%
                </span>
              </div>
            ))}
          </div>
        </details>
      )}

      {/* Quick stats */}
      <div className="flex items-center gap-3 pt-2 text-[10px] text-muted-foreground border-t border-border/30">
        <span>{sorted.length} languages</span>
        <span>·</span>
        <span>Top: {sorted[0]?.language} ({sorted[0]?.percentage}%)</span>
      </div>
    </div>
  );
}
