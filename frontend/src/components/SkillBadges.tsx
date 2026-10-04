import { useState } from "react";
import { CheckCircle2, XCircle } from "lucide-react";

// ══════════════════════════════════════════════════════════════════
//  Skill badge display for job-match candidates (RecruiterDashboard).
//
//  Presentation only — the matched/missing/category data comes verbatim
//  from the backend's JobMatchResponse (skillCategories), so JD extraction
//  and mandatory/preferred classification are untouched. No skill names
//  are hardcoded here: everything is driven by the backend's response.
//
//  Rules:
//   • MANDATORY skills are listed first in both groups and can never be
//     hidden by the initial cap (they always render, even when collapsed).
//   • Everything else is capped at VISIBLE_SKILLS with a clickable
//     "+N more" / "Show less" toggle — no skill silently disappears.
//   • Mandatory badges are visually distinct (ring + "M" marker).
// ══════════════════════════════════════════════════════════════════

export const VISIBLE_SKILLS = 7;

export function SkillBadgeGroup({
  skills,
  matched,
  categories,
}: {
  skills: string[];
  matched: boolean;
  categories?: Record<string, string>;
}) {
  const [showAll, setShowAll] = useState(false);
  if (skills.length === 0) return null;

  const isMandatory = (s: string) => categories?.[s] === "MANDATORY";
  // Mandatory first (stable within each group); everything else keeps its
  // backend order so the JD's own sequence stays readable.
  const ordered = [...skills].sort((a, b) => Number(isMandatory(b)) - Number(isMandatory(a)));

  const mandatory = ordered.filter(isMandatory);
  // When collapsed: all mandatory + the top VISIBLE_SKILLS overall.
  const visible =
    showAll || mandatory.length >= VISIBLE_SKILLS
      ? ordered
      : ordered.slice(0, Math.max(VISIBLE_SKILLS, mandatory.length));
  const hiddenCount = ordered.length - visible.length;

  return (
    <>
      {visible.map((s) => {
        const mand = isMandatory(s);
        const pref = categories?.[s] === "PREFERRED";
        const title = matched
          ? mand
            ? "Mandatory skill — proven by repository evidence"
            : pref
              ? "Preferred skill — proven by repository evidence"
              : "Required skill — proven by repository evidence"
          : mand
            ? "Mandatory skill — missing (counts double against the match)"
            : pref
              ? "Preferred skill — missing"
              : "Required skill — missing";
        return (
          <span
            key={s}
            title={title}
            className={`text-[10px] px-2 py-0.5 rounded-full flex items-center gap-1 ${
              matched
                ? mand
                  ? "bg-emerald-500/15 text-emerald-300 ring-1 ring-amber-400/40"
                  : "bg-emerald-500/10 text-emerald-400"
                : mand
                  ? "bg-red-500/10 text-red-300 ring-1 ring-amber-400/40"
                  : "bg-muted/30 text-muted-foreground"
            }`}
          >
            {matched ? (
              <CheckCircle2 className={`w-2.5 h-2.5 ${mand ? "text-emerald-300" : ""}`} />
            ) : (
              <XCircle className={`w-2.5 h-2.5 ${mand ? "text-red-300" : "text-red-400/70"}`} />
            )}
            {s}
            {mand && (
              <span className="text-[8px] font-bold text-amber-400 uppercase">M</span>
            )}
          </span>
        );
      })}
      {hiddenCount > 0 && (
        <button
          type="button"
          onClick={() => setShowAll(true)}
          className="text-[10px] px-2 py-0.5 rounded-full bg-primary/10 text-primary hover:bg-primary/20 transition-colors font-medium"
          title={`Show ${hiddenCount} more skill${hiddenCount === 1 ? "" : "s"}`}
        >
          +{hiddenCount} more
        </button>
      )}
      {showAll && hiddenCount === 0 && ordered.length > VISIBLE_SKILLS && (
        <button
          type="button"
          onClick={() => setShowAll(false)}
          className="text-[10px] px-2 py-0.5 rounded-full bg-muted/30 text-muted-foreground hover:bg-muted/50 transition-colors font-medium"
        >
          Show less
        </button>
      )}
    </>
  );
}
