import { describe, it, expect } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import { SkillBadgeGroup, VISIBLE_SKILLS } from "@/components/SkillBadges";

const categories = {
  Java: "MANDATORY",
  Spring: "MANDATORY",
  SQL: "REQUIRED",
  Redis: "MANDATORY",
  Kafka: "MANDATORY",
  Docker: "PREFERRED",
  AWS: "MANDATORY",
  Kubernetes: "MANDATORY",
} as Record<string, string>;

describe("SkillBadgeGroup", () => {
  it("shows all mandatory skills even when the group is collapsed", () => {
    // 8 mandatory + 2 required, limit is 7 → mandatory must ALL be visible.
    render(
      <SkillBadgeGroup
        skills={["Java", "Spring", "SQL", "Redis", "Kafka", "Docker", "AWS", "Kubernetes", "Git", "Maven"]}
        matched={false}
        categories={categories}
      />
    );

    // All 6 mandatory skills in the list get the mandatory-missing title.
    expect(
      screen.getAllByTitle("Mandatory skill — missing (counts double against the match)").length
    ).toBe(6);
    for (const m of ["Java", "Spring", "Redis", "Kafka", "AWS", "Kubernetes"]) {
      expect(screen.getByText(m)).toBeInTheDocument();
    }
  });

  it("orders mandatory skills before others regardless of input order", () => {
    render(
      <SkillBadgeGroup
        skills={["Git", "AWS", "SQL", "Kafka"]}
        matched={false}
        categories={categories}
      />
    );
    const badges = screen.getAllByText(/^(Git|AWS|SQL|Kafka)$/);
    // AWS and Kafka are mandatory → they come first.
    expect(badges[0].textContent).toContain("AWS");
    expect(badges[1].textContent).toContain("Kafka");
  });

  it("caps non-mandatory skills with a '+N more' toggle that reveals the rest", () => {
    const skills = ["Go", "Rust", "C", "Ruby", "PHP", "Swift", "Kotlin", "Scala", "Perl"];
    render(<SkillBadgeGroup skills={skills} matched categories={undefined} />);

    expect(screen.getByText("Go")).toBeInTheDocument();
    expect(screen.queryByText("Scala")).not.toBeInTheDocument();
    expect(screen.queryByText("Perl")).not.toBeInTheDocument();

    const more = screen.getByText(`+${skills.length - VISIBLE_SKILLS} more`);
    fireEvent.click(more);

    expect(screen.getByText("Scala")).toBeInTheDocument();
    expect(screen.getByText("Perl")).toBeInTheDocument();
    expect(screen.getByText("Show less")).toBeInTheDocument();
  });

  it("marks mandatory badges distinctly (M marker + ring class)", () => {
    render(<SkillBadgeGroup skills={["Java", "SQL"]} matched categories={categories} />);
    const mand = screen.getByText("Java").closest("span");
    const plain = screen.getByText("SQL").closest("span");
    expect(mand?.className).toContain("ring-1");
    expect(mand?.textContent).toContain("M");
    expect(plain?.className).not.toContain("ring-1");
  });

  it("renders nothing for an empty list", () => {
    const { container } = render(<SkillBadgeGroup skills={[]} matched categories={categories} />);
    expect(container.firstChild).toBeNull();
  });
});
