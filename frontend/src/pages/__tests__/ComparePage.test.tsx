import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { ComparePage } from "@/pages/ComparePage";
import type { CompareResult, DeveloperScore, MetricScore } from "@/services/api";

vi.mock("@/services/api", () => ({
  githubApi: {
    compare: vi.fn(),
  },
}));

import { githubApi } from "@/services/api";

function score(overrides: Partial<DeveloperScore>): DeveloperScore {
  const detail = (n: number, explanation: string): MetricScore => ({
    score: n,
    weight: 10,
    label: "Metric",
    description: "desc",
    explanation,
    improvementSuggestion: "tip",
    trend: "up",
    icon: "activity",
  });
  return {
    username: "x",
    overallScore: overrides.overallScore ?? 50,
    level: overrides.level ?? "Proficient",
    contributionRecency: overrides.contributionRecency ?? 50,
    commitFrequency: overrides.commitFrequency ?? 50,
    repositoryHealth: overrides.repositoryHealth ?? 50,
    repositoryQuality: overrides.repositoryQuality ?? 50,
    contributionConsistency: overrides.contributionConsistency ?? 50,
    languageDiversity: overrides.languageDiversity ?? 50,
    collaboration: overrides.collaboration ?? 50,
    openSourceImpact: overrides.openSourceImpact ?? 50,
    popularity: overrides.popularity ?? 50,
    maintenance: overrides.maintenance ?? 50,
    contributionRecencyDetails:
      overrides.contributionRecencyDetails ?? detail(50, "default"),
    commitFrequencyDetails: detail(50, "default"),
    repositoryHealthDetails: detail(50, "default"),
    repositoryQualityDetails: detail(50, "default"),
    contributionConsistencyDetails: detail(50, "default"),
    languageDiversityDetails: detail(50, "default"),
    collaborationDetails: detail(50, "default"),
    openSourceImpactDetails: detail(50, "default"),
    popularityDetails: detail(50, "default"),
    maintenanceDetails: detail(50, "default"),
    insights: null,
    weightedLanguages: [],
  } as unknown as DeveloperScore;
}

const u1Score = score({
  overallScore: 72,
  contributionRecency: 90,
  level: "Expert",
  contributionRecencyDetails: {
    score: 90,
    weight: 15,
    label: "Contribution Recency",
    description: "d",
    explanation: "User1 recency explanation",
    improvementSuggestion: "User1 recency tip",
    trend: "up",
    icon: "activity",
  },
});

const u2Score = score({
  overallScore: 55,
  contributionRecency: 30,
  level: "Intermediate",
  contributionRecencyDetails: {
    score: 30,
    weight: 15,
    label: "Contribution Recency",
    description: "d",
    explanation: "User2 recency explanation",
    improvementSuggestion: "User2 recency tip",
    trend: "down",
    icon: "activity",
  },
});

function renderCompare() {
  return render(
    <MemoryRouter>
      <ComparePage />
    </MemoryRouter>
  );
}

describe("ComparePage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders both users' independent metric values", async () => {
    const result: CompareResult = {
      user1: {
        username: "alice",
        profile: {
          username: "alice",
          name: "Alice",
          avatarUrl: "a.png",
          publicRepositories: 10,
          followers: 5,
          profileUrl: "u/alice",
        } as any,
        repos: [],
        score: u1Score,
      },
      user2: {
        username: "bob",
        profile: {
          username: "bob",
          name: "Bob",
          avatarUrl: "b.png",
          publicRepositories: 3,
          followers: 2,
          profileUrl: "u/bob",
        } as any,
        repos: [],
        score: u2Score,
      },
    };
    vi.mocked(githubApi.compare).mockResolvedValue(result);

    renderCompare();
    await userEvent.type(screen.getAllByPlaceholderText("e.g. torvalds")[0], "alice");
    await userEvent.type(screen.getByPlaceholderText("e.g. addyosmani"), "bob");
    await userEvent.click(screen.getByRole("button", { name: /compare/i }));

    await waitFor(() =>
      expect(githubApi.compare).toHaveBeenCalledWith("alice", "bob")
    );

    // Winner banner + user1 metric card both show 72; user2 shows 55.
    await waitFor(() => expect(screen.getAllByText("72").length).toBeGreaterThan(0));
    expect(screen.getAllByText("55").length).toBeGreaterThan(0);

    // The metric card shows each user's own value (90 vs 30, not 90 vs 90).
    await waitFor(() => {
      expect(screen.getAllByText("90").length).toBeGreaterThan(0);
      expect(screen.getAllByText("30").length).toBeGreaterThan(0);
    });
  });

  it("attributes the metric explanation to the user it describes", async () => {
    const result: CompareResult = {
      user1: {
        username: "alice",
        profile: { username: "alice", name: "Alice", avatarUrl: "a.png", publicRepositories: 10, followers: 5, profileUrl: "u/alice" } as any,
        repos: [],
        score: u1Score,
      },
      user2: {
        username: "bob",
        profile: { username: "bob", name: "Bob", avatarUrl: "b.png", publicRepositories: 3, followers: 2, profileUrl: "u/bob" } as any,
        repos: [],
        score: u2Score,
      },
    };
    vi.mocked(githubApi.compare).mockResolvedValue(result);

    renderCompare();
    await userEvent.type(screen.getAllByPlaceholderText("e.g. torvalds")[0], "alice");
    await userEvent.type(screen.getByPlaceholderText("e.g. addyosmani"), "bob");
    await userEvent.click(screen.getByRole("button", { name: /compare/i }));

    // Alice (user1) leads recency 90 vs 30 → her explanation is shown,
    // explicitly attributed to her (unique to the leading-user card).
    await waitFor(() =>
      expect(screen.getByText(/User1 recency explanation/)).toBeInTheDocument()
    );
    expect(screen.getByText(/User1 recency explanation/)).toBeInTheDocument();
    // The losing user's explanation must not be shown in the shared card.
    expect(screen.queryByText(/User2 recency explanation/)).not.toBeInTheDocument();
  });

  it("still renders when one user's data fails — per-user error only", async () => {
    const result: CompareResult = {
      user1: {
        username: "alice",
        profile: { username: "alice", name: "Alice", avatarUrl: "a.png", publicRepositories: 10, followers: 5, profileUrl: "u/alice" } as any,
        repos: [],
        score: u1Score,
      },
      user2: {
        username: "ghost",
        profile: null,
        repos: [],
        score: null,
        error: "Profile not found: ghost",
      },
    };
    vi.mocked(githubApi.compare).mockResolvedValue(result);

    renderCompare();
    await userEvent.type(screen.getAllByPlaceholderText("e.g. torvalds")[0], "alice");
    await userEvent.type(screen.getByPlaceholderText("e.g. addyosmani"), "ghost");
    await userEvent.click(screen.getByRole("button", { name: /compare/i }));

    // Alice's column still renders fully — the failed user does not blank the page.
    await waitFor(() => expect(screen.getByText("Expert")).toBeInTheDocument());
    expect(screen.getAllByText("90").length).toBeGreaterThan(0);
  });
});
