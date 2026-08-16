import { describe, expect, it } from "vitest";
import { cn } from "./utils";

describe("cn", () => {
  it("joins class names and filters falsy values", () => {
    expect(cn("a", "b", null, undefined, false, 0, "")).toBe("a b");
  });

  it("merges conflicting Tailwind classes (tailwind-merge wins with the last)", () => {
    expect(cn("px-2 py-1", "px-4")).toBe("py-1 px-4");
  });

  it("handles conditional objects via clsx", () => {
    expect(cn("base", { active: true, hidden: false })).toBe("base active");
  });

  it("returns an empty string for no classes", () => {
    expect(cn()).toBe("");
    expect(cn(null, undefined)).toBe("");
  });
});
