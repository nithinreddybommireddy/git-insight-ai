import { motion } from "framer-motion";
import { Card, CardContent } from "@/components/ui/card";
import type { DeveloperInsights } from "@/services/api";
import {
  Brain,
  Sparkles,
  Trophy,
  AlertCircle,
  Users,
  Globe,
  Code2,
  TrendingUp,
  ShieldCheck,
  Lightbulb,
  ChevronDown,
} from "lucide-react";
import { useState } from "react";

// ── Props ──

interface AISummaryPanelProps {
  insights: DeveloperInsights | null;
  username: string;
  variant?: "full" | "compact";
}

// ── Insight Block ──

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
  const borderColor =
    color === "amber"
      ? "border-amber-500/20"
      : isHighlight
      ? "border-emerald-500/20"
      : "border-border/50";

  const bgColor =
    color === "amber"
      ? "from-amber-500/5 to-orange-500/5"
      : isHighlight
      ? "from-emerald-500/5 to-teal-500/5"
      : "from-muted/5 to-muted/10";

  return (
    <div
      className={`rounded-xl bg-gradient-to-br ${bgColor} border ${borderColor} p-3.5`}
    >
      <div className="flex items-start gap-2.5">
        <Icon
          className={`w-4 h-4 ${
            color === "amber"
              ? "text-amber-400"
              : isHighlight
              ? "text-emerald-400"
              : "text-primary"
          } mt-0.5 shrink-0`}
        />
        <div>
          <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground mb-1">
            {label}
          </p>
          <p className="text-xs leading-relaxed">{text}</p>
        </div>
      </div>
    </div>
  );
}

// ── Component ──

export function AISummaryPanel({ insights, username, variant = "full" }: AISummaryPanelProps) {
  const [expanded, setExpanded] = useState(false);

  if (!insights) {
    return (
      <Card className="overflow-hidden border-primary/10">
        <CardContent className="!p-6">
          <div className="flex flex-col items-center justify-center py-4 text-center">
            <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-primary/10 to-accent/10 flex items-center justify-center mb-3">
              <Brain className="w-6 h-6 text-primary/60" />
            </div>
            <p className="text-sm font-medium text-muted-foreground mb-1">
              AI insights not available
            </p>
            <p className="text-xs text-muted-foreground/60">
              Data for @{username} may be limited
            </p>
          </div>
        </CardContent>
      </Card>
    );
  }

  if (variant === "compact") {
    return (
      <Card
        className="overflow-hidden border-primary/10 cursor-pointer hover:scale-[1.01] transition-all duration-200"
        onClick={() => setExpanded(!expanded)}
      >
        <div className="h-1 bg-gradient-to-r from-primary via-accent to-primary" />
        <CardContent className="!p-4">
          <div className="flex items-center gap-2.5 mb-3">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary to-accent flex items-center justify-center">
              <Brain className="w-4 h-4 text-white" />
            </div>
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <h4 className="text-sm font-semibold">AI Insights</h4>
                <ChevronDown
                  className={`w-3.5 h-3.5 text-muted-foreground transition-transform duration-200 ${
                    expanded ? "rotate-180" : ""
                  }`}
                />
              </div>
              <p className="text-[10px] text-muted-foreground">
                @{username} — AI Summary
              </p>
            </div>
            <div className="flex items-center gap-1.5 px-2 py-1 rounded-full bg-emerald-500/10 text-emerald-400 text-[9px] font-medium">
              <Sparkles className="w-3 h-3" />
              Powered
            </div>
          </div>

          {expanded && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: "auto" }}
              className="space-y-2.5 pt-2"
            >
              <InsightBlock
                icon={Sparkles}
                label="Overall Assessment"
                text={insights.overallAssessment}
              />
              {insights.strongestSkill && (
                <InsightBlock
                  icon={Trophy}
                  label="Strongest Skill"
                  text={insights.strongestSkill}
                  isHighlight
                />
              )}
              {insights.weakestArea && (
                <InsightBlock
                  icon={AlertCircle}
                  label="Weakest Area"
                  text={insights.weakestArea}
                  color="amber"
                />
              )}
            </motion.div>
          )}
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="overflow-hidden border-primary/20">
      <div className="h-1.5 bg-gradient-to-r from-primary via-accent to-primary" />
      <CardContent className="!p-6">
        <div className="flex items-center gap-2.5 mb-5">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary to-accent flex items-center justify-center">
            <Brain className="w-5 h-5 text-white" />
          </div>
          <div>
            <h3 className="text-base font-semibold">AI Developer Analysis</h3>
            <p className="text-[11px] text-muted-foreground">
              Powered by GitInsight Scoring Engine
            </p>
          </div>
        </div>

        <div className="space-y-4">
          <InsightBlock
            icon={Sparkles}
            label="Overall Assessment"
            text={insights.overallAssessment}
          />
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <InsightBlock
              icon={Trophy}
              label="Strongest Skill"
              text={insights.strongestSkill}
              isHighlight
            />
            <InsightBlock
              icon={AlertCircle}
              label="Weakest Area"
              text={insights.weakestArea}
              color="amber"
            />
          </div>
          {insights.collaborationAnalysis && (
            <InsightBlock
              icon={Users}
              label="Collaboration Analysis"
              text={insights.collaborationAnalysis}
            />
          )}
          {insights.openSourceImpact && (
            <InsightBlock
              icon={Globe}
              label="Open Source Impact"
              text={insights.openSourceImpact}
            />
          )}
          {insights.technologyExpertise && (
            <InsightBlock
              icon={Code2}
              label="Technology Expertise"
              text={insights.technologyExpertise}
            />
          )}
          {insights.activityTrend && (
            <InsightBlock
              icon={TrendingUp}
              label="Activity Trend"
              text={insights.activityTrend}
            />
          )}
          {insights.repositoryQualityObs && (
            <InsightBlock
              icon={ShieldCheck}
              label="Repository Quality"
              text={insights.repositoryQualityObs}
            />
          )}
          {insights.recommendations && (
            <div className="rounded-xl bg-gradient-to-r from-amber-500/10 to-orange-500/10 border border-amber-500/20 p-4">
              <div className="flex items-start gap-3">
                <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-amber-500 to-orange-500 flex items-center justify-center shrink-0 mt-0.5">
                  <Lightbulb className="w-4 h-4 text-white" />
                </div>
                <div>
                  <p className="text-xs font-semibold text-amber-400 mb-1">
                    Recommendations
                  </p>
                  <p className="text-xs text-amber-300/80 leading-relaxed">
                    {insights.recommendations}
                  </p>
                </div>
              </div>
            </div>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
