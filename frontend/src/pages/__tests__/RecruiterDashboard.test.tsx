import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { RecruiterDashboard } from "@/pages/RecruiterDashboard";
import { AuthContext, type AuthContextType } from "@/hooks/useAuth";
import type { ApiResponse, JobMatchJobStatus } from "@/services/api";

vi.mock("@/services/api", () => ({
  recruiterApi: {
    listSavedCandidates: vi.fn().mockResolvedValue({ success: true, data: [] }),
    getStats: vi.fn().mockResolvedValue({ success: true, data: { savedCandidates: 0, totalNotes: 0 } }),
    matchByJobDescriptionAsync: vi.fn(),
    getJobMatchStatus: vi.fn(),
  },
  githubApi: {
    getProfile: vi.fn(),
    getDeveloperScore: vi.fn().mockResolvedValue({ success: false }),
  },
  githubApiEnhanced: {
    getLanguageBreakdown: vi.fn().mockResolvedValue({ success: false }),
  },
}));

import { recruiterApi } from "@/services/api";

function status(
  overrides: Partial<JobMatchJobStatus> = {}
): ApiResponse<JobMatchJobStatus> {
  return {
    success: true,
    message: "ok",
    data: {
      jobId: 7,
      status: "RUNNING",
      jobTitle: null,
      total: 4,
      processed: 0,
      failed: 0,
      progressPercent: 0,
      aiEnabled: false,
      createdAt: "2026-10-04T07:06:08.912835",
      startedAt: null,
      completedAt: null,
      errorMessage: null,
      result: null,
      ...overrides,
    },
  };
}

function renderDashboard() {
  const auth = {
    user: { id: 1, name: "Rec", email: "r@t.io", role: "RECRUITER" },
    loading: false,
    isAuthenticated: true,
  } as unknown as AuthContextType;
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter>
        <RecruiterDashboard />
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

/**
 * Advance the fake clock inside act(). Timer callbacks (the polling loop's
 * setTimeout) fire during the advance; the awaited promise lets the loop's
 * microtask continuations (status response → setState → next setTimeout)
 * flush before we return, so the DOM and mock call counts are stable here.
 */
async function flush(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

const runButton = () => screen.getByRole("button", { name: /run job match|scoring candidates/i });

/**
 * Pick a JD file and click Run. Uses fireEvent (synchronous) rather than
 * userEvent: userEvent's internal async waits deadlock under fake timers,
 * while polling requires them. Each step is followed by flush(0) so the
 * async handleRunMatch chain (POST → first status GET → first poll settle)
 * runs to its first scheduled setTimeout before the test asserts.
 */
async function startRun() {
  const input = screen.getByLabelText(/job description/i);
  const jd = new File(["Senior backend engineer — Java, Spring, Kafka"], "jd.txt", {
    type: "text/plain",
  });
  Object.defineProperty(input, "files", { value: [jd], configurable: true });
  fireEvent.change(input);

  fireEvent.click(runButton());
  await flush(0); // POST resolves → first status poll fires and settles
}

describe("RecruiterDashboard job-match polling", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Fake only what the polling loop needs. Leaving rAF/performance/etc.
    // real keeps framer-motion and React's scheduler out of the fake clock.
    vi.useFakeTimers({
      toFake: ["setTimeout", "clearTimeout", "setInterval", "clearInterval", "Date"],
    });
  });
  afterEach(() => {
    vi.clearAllTimers(); // never run pending polls outside act()
    vi.useRealTimers();
  });

  it("polls sequentially with ~2s gaps and stops immediately after COMPLETED", async () => {
    vi.mocked(recruiterApi.matchByJobDescriptionAsync).mockResolvedValue(
      status({ jobId: 7, status: "QUEUED" })
    );
    const getStatus = vi.mocked(recruiterApi.getJobMatchStatus);
    getStatus
      .mockResolvedValueOnce(status({ processed: 0, progressPercent: 0 })) // RUNNING
      .mockResolvedValueOnce(status({ processed: 2, progressPercent: 50 })) // RUNNING
      // Regression guard: an extra timer tick must NOT cause an extra request.
      .mockResolvedValue(status({ status: "COMPLETED", progressPercent: 100, result: {
        jobTitle: "Backend Engineer",
        requiredSkills: ["Java"],
        source: "file",
        total: 4,
        processed: 4,
        failed: 0,
        results: [
          { username: "octocat", name: "Octo Cat", avatarUrl: null, bio: null, developerScore: 80, level: "Expert", matchScore: 90, skillMatchPercent: 75, matchedSkills: ["Java"], missingSkills: [], languages: ["Java"], topRepos: [] },
        ],
        aiEnabled: false,
        aiModel: null,
        aiExplanations: [],
      } }));

    renderDashboard();
    await act(async () => {}); // initial load effects
    await startRun();

    // First poll fired immediately after the POST and settled.
    expect(getStatus).toHaveBeenCalledTimes(1);
    expect(screen.getByText(/0\/4 candidates scored/)).toBeInTheDocument();

    await flush(2100); // ~1 interval → exactly 1 new request
    expect(getStatus).toHaveBeenCalledTimes(2);
    await flush(2100); // next interval → exactly 1 new request (terminal)
    expect(getStatus).toHaveBeenCalledTimes(3);

    // Terminal state: results render, button re-enabled.
    expect(screen.getByText("Backend Engineer")).toBeInTheDocument();
    expect(runButton()).toBeEnabled();

    // No further polls after terminal state, no matter how much time passes.
    await flush(10000);
    expect(getStatus).toHaveBeenCalledTimes(3);
  });

  it("only ever has one in-flight status request (no overlap on slow responses)", async () => {
    vi.mocked(recruiterApi.matchByJobDescriptionAsync).mockResolvedValue(
      status({ jobId: 7, status: "QUEUED" })
    );
    let resolveSlow: ((v: ApiResponse<JobMatchJobStatus>) => void) | undefined;
    const getStatus = vi.mocked(recruiterApi.getJobMatchStatus);
    getStatus.mockImplementationOnce(
      () =>
        new Promise<ApiResponse<JobMatchJobStatus>>((res) => {
          resolveSlow = res;
        })
    );
    getStatus.mockResolvedValue(status({ processed: 1, progressPercent: 25 }));

    renderDashboard();
    await act(async () => {}); // initial load effects
    await startRun();

    expect(getStatus).toHaveBeenCalledTimes(1); // exactly one in flight
    await flush(5000); // several intervals pass while the request hangs
    expect(getStatus).toHaveBeenCalledTimes(1); // still no second request

    await act(async () => {
      resolveSlow!(status({ processed: 1, progressPercent: 25 }));
    });
    await flush(0); // response settles → next poll is scheduled
    expect(getStatus).toHaveBeenCalledTimes(1);
    await flush(2100); // next poll only after settle
    expect(getStatus).toHaveBeenCalledTimes(2);
  });

  it("does not mark the job failed when the polling deadline elapses", async () => {
    vi.mocked(recruiterApi.matchByJobDescriptionAsync).mockResolvedValue(
      status({ jobId: 7, status: "QUEUED" })
    );
    vi.mocked(recruiterApi.getJobMatchStatus).mockResolvedValue(
      status({ status: "QUEUED", startedAt: null })
    );

    renderDashboard();
    await act(async () => {}); // initial load effects
    await startRun();

    // Advance past the 15-minute deadline.
    await flush(15 * 60 * 1000 + 5000);

    expect(screen.getByText(/taking longer than expected/i)).toBeInTheDocument();
    // Not a false failure:
    expect(screen.queryByText(/job match failed/i)).not.toBeInTheDocument();
    expect(runButton()).toBeEnabled();
    // Polling stopped at the deadline.
    const calls = vi.mocked(recruiterApi.getJobMatchStatus).mock.calls.length;
    await flush(10000);
    expect(vi.mocked(recruiterApi.getJobMatchStatus).mock.calls.length).toBe(calls);
  });

  it("keeps polling through transient network errors and stops on FAILED", async () => {
    vi.mocked(recruiterApi.matchByJobDescriptionAsync).mockResolvedValue(
      status({ jobId: 7, status: "QUEUED" })
    );
    const getStatus = vi.mocked(recruiterApi.getJobMatchStatus);
    getStatus.mockRejectedValueOnce(new Error("network blip"));
    getStatus.mockResolvedValueOnce(status({ status: "FAILED", errorMessage: "GitHub API down" }));

    renderDashboard();
    await act(async () => {}); // initial load effects
    await startRun();

    expect(getStatus).toHaveBeenCalledTimes(1); // first poll threw → retry scheduled
    await flush(2100); // retry → FAILED
    expect(getStatus).toHaveBeenCalledTimes(2);
    expect(screen.getByText(/GitHub API down/i)).toBeInTheDocument();

    const calls = getStatus.mock.calls.length;
    await flush(8000);
    expect(getStatus.mock.calls.length).toBe(calls); // stopped after terminal
  });

  it("guards against double submission: a second click during a run is ignored", async () => {
    vi.mocked(recruiterApi.matchByJobDescriptionAsync).mockResolvedValue(
      status({ jobId: 7, status: "QUEUED" })
    );
    vi.mocked(recruiterApi.getJobMatchStatus).mockResolvedValue(status({ processed: 0 }));

    renderDashboard();
    await act(async () => {}); // initial load effects
    await startRun();

    // Disabled button + handler guard — a second submission never happens.
    expect(runButton()).toBeDisabled();
    fireEvent.click(runButton()); // no-op: disabled and/or matchLoading guard
    await flush(0);
    expect(vi.mocked(recruiterApi.matchByJobDescriptionAsync)).toHaveBeenCalledTimes(1);
  });
});
