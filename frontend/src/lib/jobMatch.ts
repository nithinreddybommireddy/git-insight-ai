/**
 * Frontend mirror of the backend mandatory-weighted skill-match formula
 * (auth-service JobMatcherService.computeWeightedSkillMatchPercent):
 * skills the job description marks MANDATORY count double.
 *
 * Kept in sync with the backend so any UI-side recompute (e.g. tooltips,
 * what-if scenarios) matches the server-calculated skillMatchPercent.
 */
export const MANDATORY_SKILL_WEIGHT = 2.0;

export function computeWeightedSkillMatchPercent(
  required: string[],
  matched: string[],
  mandatory: string[]
): number {
  if (!required || required.length === 0) return 100;
  const mandatorySet = new Set(mandatory ?? []);
  let totalWeight = 0;
  let matchedWeight = 0;
  for (const skill of required) {
    const w = mandatorySet.has(skill) ? MANDATORY_SKILL_WEIGHT : 1.0;
    totalWeight += w;
    if (matched.includes(skill)) matchedWeight += w;
  }
  if (totalWeight === 0) return 0;
  return Math.round((matchedWeight * 100.0) / totalWeight);
}

export const SkillCategory = {
  REQUIRED: "REQUIRED",
  PREFERRED: "PREFERRED",
  MANDATORY: "MANDATORY",
} as const;

export type SkillCategoryValue =
  (typeof SkillCategory)[keyof typeof SkillCategory];
