import { describe, it, expect } from "vitest";
import {
  computeWeightedSkillMatchPercent,
  SkillCategory,
} from "@/lib/jobMatch";

describe("computeWeightedSkillMatchPercent", () => {
  it("counts mandatory skills double", () => {
    // Weights: Java 1 + Docker 1 + Redis 2 = 4; matched 2 → 50%.
    expect(
      computeWeightedSkillMatchPercent(
        ["Java", "Docker", "Redis"],
        ["Java", "Docker"],
        ["Redis"]
      )
    ).toBe(50);
  });

  it("a missing mandatory skill drags the percentage below the plain ratio", () => {
    // Plain ratio would be 75%; with mandatory Redis it is 60%.
    expect(
      computeWeightedSkillMatchPercent(
        ["Java", "Spring Boot", "React", "Redis"],
        ["Java", "Spring Boot", "React"],
        ["Redis"]
      )
    ).toBe(60);
  });

  it("full match is 100 even with mandatory skills", () => {
    expect(
      computeWeightedSkillMatchPercent(
        ["Java", "Redis"],
        ["Java", "Redis"],
        ["Redis"]
      )
    ).toBe(100);
  });

  it("no requirements yields 100", () => {
    expect(computeWeightedSkillMatchPercent([], [], [])).toBe(100);
  });

  it("no mandatory skills reduces to the plain ratio", () => {
    expect(
      computeWeightedSkillMatchPercent(["Java", "Docker"], ["Java"], [])
    ).toBe(50);
  });
});

describe("SkillCategory", () => {
  it("exposes the three JD categories", () => {
    expect(Object.keys(SkillCategory).sort()).toEqual([
      "MANDATORY",
      "PREFERRED",
      "REQUIRED",
    ]);
  });
});
