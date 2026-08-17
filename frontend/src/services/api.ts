import axios from "axios";

// API base: set VITE_API_BASE at build time when the backend is NOT same-origin
// (e.g. a gateway at https://api.example.com). Defaults to same-origin /api,
// which the dev proxy (vite.config.ts) and the Docker nginx gateway both serve.
const API_BASE = (import.meta.env.VITE_API_BASE as string | undefined ?? "").replace(/\/$/, "");

const api = axios.create({
  baseURL: `${API_BASE}/api`,
  headers: {
    "Content-Type": "application/json",
  },
  // Session tokens ride in HttpOnly cookies (set by login/register/OAuth),
  // so every request must send them. Nothing is stored in localStorage.
  withCredentials: true,
});

// Session change pub/sub: the silent-refresh interceptor below tells
// AuthProvider when a refresh succeeded (new user) or the session died.
type SessionListener = (user: User | null) => void;
let sessionListeners: SessionListener[] = [];

export function onSessionChange(listener: SessionListener): () => void {
  sessionListeners.push(listener);
  return () => {
    sessionListeners = sessionListeners.filter((l) => l !== listener);
  };
}

function notifySessionChange(user: User | null) {
  for (const listener of sessionListeners) {
    listener(user);
  }
}

// ── Silent access-token refresh (single-flight) ──
// When any request comes back 401 (expired access token), one refresh call is
// kicked off and every concurrent 401 waits on the same promise, then retries
// the original request. If the refresh fails, the session is dropped so the
// UI can route the user back to /login.
let refreshPromise: Promise<boolean> | null = null;

async function refreshSession(): Promise<boolean> {
  try {
    const { data } = await api.post<ApiResponse<AuthData>>("/auth/refresh", {});
    if (data.success) {
      notifySessionChange(data.data.user);
      return true;
    }
  } catch {
    // fall through — session is gone
  }
  notifySessionChange(null);
  return false;
}

// Surface a friendly message when the GitHub API is temporarily unavailable
// (HTTP 429) so pages render guidance instead of a raw status text. End users
// should never see backend/API details (quota, tokens, rate-limit config).
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    // 429: friendly rate-limit copy for the GitHub analysis surface.
    if (error?.response?.status === 429 && error.response.data && typeof error.response.data === "object") {
      error.response.data.message =
        "GitHub is temporarily busy. Please wait a minute and try again.";
    }

    // 401: try a silent refresh once, then replay the original request.
    const status = error?.response?.status;
    const url: string = error?.config?.url ?? "";
    const isAuthEndpoint =
      url.includes("/auth/login") || url.includes("/auth/register") ||
      url.includes("/auth/refresh") || url.includes("/auth/logout");
    if (status === 401 && !isAuthEndpoint && !(error.config as { _retry?: boolean } | undefined)?._retry) {
      const config = error.config as { _retry?: boolean };
      config._retry = true;
      refreshPromise ??= refreshSession();
      const ok = await refreshPromise;
      refreshPromise = null;
      if (ok) {
        return api(error.config);
      }
    }
    return Promise.reject(error);
  }
);

// ==================== Auth Types ====================

export interface User {
  id: number;
  email: string;
  name: string;
  avatarUrl: string | null;
  role: string;
  githubUsername: string | null;
  createdAt: string;
}

// Sessions ride exclusively in HttpOnly cookies — the login/register/refresh
// JSON bodies never contain tokens, so these fields are optional for API
// clients that might send a token explicitly to /auth/refresh.
export interface AuthData {
  token?: string;
  refreshToken?: string;
  user: User;
}

// ==================== GitHub Types ====================

export interface GitHubProfile {
  githubId: number;
  username: string;
  name: string | null;
  avatarUrl: string;
  profileUrl: string;
  bio: string | null;
  company: string | null;
  location: string | null;
  website: string | null;
  email: string | null;
  twitterUsername: string | null;
  hireable: boolean | null;
  publicRepositories: number;
  publicGists: number;
  followers: number;
  following: number;
  createdAt: string;
  updatedAt: string;
}

export interface Repository {
  githubId: number;
  name: string;
  fullName: string;
  description: string | null;
  htmlUrl: string;
  homepage: string | null;
  language: string | null;
  fork: boolean;
  defaultBranch: string;
  stars: number;
  forks: number;
  openIssues: number;
  watchers: number;
  size: number;
  topics: string[];
  hasLicense: boolean;
  createdAt: string;
  updatedAt: string;
  pushedAt: string;
  archived: boolean;
  disabled: boolean;
  healthScore: number;
  documentationScore: number;
  maintenanceScore: number;
  popularityScore: number;
  activityScore: number;
}

export interface MetricScore {
  score: number;
  weight: number;
  label: string;
  description: string;
  explanation: string;
  improvementSuggestion: string;
  trend: "up" | "down" | "stable";
  icon: string;
}

export interface DeveloperInsights {
  overallAssessment: string;
  strongestSkill: string;
  weakestArea: string;
  collaborationAnalysis: string;
  openSourceImpact: string;
  technologyExpertise: string;
  activityTrend: string;
  repositoryQualityObs: string;
  recommendations: string;
}

export interface DeveloperScore {
  username: string;
  overallScore: number;
  level: string;

  // 10 metrics
  contributionRecency: number;
  commitFrequency: number;
  repositoryHealth: number;
  repositoryQuality: number;
  contributionConsistency: number;
  languageDiversity: number;
  collaboration: number;
  openSourceImpact: number;
  popularity: number;
  maintenance: number;

  // Detailed breakdowns
  contributionRecencyDetails: MetricScore | null;
  commitFrequencyDetails: MetricScore | null;
  repositoryHealthDetails: MetricScore | null;
  repositoryQualityDetails: MetricScore | null;
  contributionConsistencyDetails: MetricScore | null;
  languageDiversityDetails: MetricScore | null;
  collaborationDetails: MetricScore | null;
  openSourceImpactDetails: MetricScore | null;
  popularityDetails: MetricScore | null;
  maintenanceDetails: MetricScore | null;

  // AI insights
  insights: DeveloperInsights | null;

  // Legacy fields
  totalStars: number;
  totalForks: number;
  totalRepositories: number;
  languageCount: number;
  languages: string[];
  avgHealthScore: number;
  avgPopularityScore: number;
  avgMaintenanceScore: number;
  contributionRecencyScore: number;
  commitFrequencyScore: number;
  consistencyScore: number;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

// ==================== Auth API ====================

export const authApi = {
  register: async (name: string, email: string, password: string): Promise<ApiResponse<AuthData>> => {
    const { data } = await api.post<ApiResponse<AuthData>>("/auth/register", { name, email, password });
    return data;
  },

  login: async (email: string, password: string): Promise<ApiResponse<AuthData>> => {
    const { data } = await api.post<ApiResponse<AuthData>>("/auth/login", { email, password });
    return data;
  },

  /**
   * Refresh via the HttpOnly refresh cookie (browser flow). API clients may
   * still pass a token explicitly; the backend prefers the cookie.
   */
  refresh: async (refreshToken?: string): Promise<ApiResponse<AuthData>> => {
    const { data } = await api.post<ApiResponse<AuthData>>(
      "/auth/refresh",
      refreshToken ? { refreshToken } : {}
    );
    return data;
  },

  /** Current user from the HttpOnly session cookie. */
  me: async (): Promise<ApiResponse<User>> => {
    const { data } = await api.get<ApiResponse<User>>("/auth/me");
    return data;
  },

  /** Clear the HttpOnly session cookies server-side. */
  logout: async (): Promise<ApiResponse<void>> => {
    const { data } = await api.post<ApiResponse<void>>("/auth/logout");
    return data;
  },

  githubOAuth: (): string => {
    // Must point at the backend origin when it differs from the frontend's
    // (VITE_API_BASE) — the OAuth entry is a full-page navigation, not an XHR,
    // so the axios baseURL is not applied here.
    return `${API_BASE}/api/auth/oauth/github`;
  },
};

// ==================== GitHub API ====================

export const githubApi = {
  getProfile: async (username: string): Promise<ApiResponse<GitHubProfile>> => {
    const { data } = await api.get<ApiResponse<GitHubProfile>>(`/github/profile/${username}`);
    return data;
  },

  getRepositories: async (username: string): Promise<ApiResponse<Repository[]>> => {
    const { data } = await api.get<ApiResponse<Repository[]>>(`/github/${username}/repos`);
    return data;
  },

  getDeveloperScore: async (username: string): Promise<ApiResponse<DeveloperScore>> => {
    const { data } = await api.get<ApiResponse<DeveloperScore>>(`/github/${username}/score`);
    return data;
  },

  compare: async (user1: string, user2: string): Promise<CompareResult> => {
    const [profile1, profile2, repos1, repos2, score1, score2] = await Promise.all([
      githubApi.getProfile(user1),
      githubApi.getProfile(user2),
      githubApi.getRepositories(user1),
      githubApi.getRepositories(user2),
      githubApi.getDeveloperScore(user1),
      githubApi.getDeveloperScore(user2),
    ]);

    return {
      user1: {
        username: user1,
        profile: profile1.success ? profile1.data : null,
        repos: repos1.success ? repos1.data : [],
        score: score1.success ? score1.data : null,
      },
      user2: {
        username: user2,
        profile: profile2.success ? profile2.data : null,
        repos: repos2.success ? repos2.data : [],
        score: score2.success ? score2.data : null,
      },
    };
  },
};

export interface CompareUserData {
  username: string;
  profile: GitHubProfile | null;
  repos: Repository[];
  score: DeveloperScore | null;
}

export interface CompareResult {
  user1: CompareUserData;
  user2: CompareUserData;
}

// ==================== Recruiter Types ====================

export interface SavedCandidate {
  id: number;
  candidateUsername: string;
  candidateName: string | null;
  candidateAvatarUrl: string | null;
  candidateGithubId: number | null;
  candidateScore: number | null;
  candidateLevel: string | null;
  candidateLanguages: string | null;
  bookmarked: boolean;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface RecruiterNote {
  id: number;
  candidateUsername: string;
  title: string | null;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface RecruiterStats {
  savedCandidates: number;
  totalNotes: number;
}

export interface JobMatchCandidate {
  username: string;
  name: string | null;
  avatarUrl: string | null;
  bio: string | null;
  developerScore: number;
  level: string;
  matchScore: number;
  skillMatchPercent: number;
  matchedSkills: string[];
  missingSkills: string[];
  languages: string[];
  topRepos: string[];
}

export interface JobMatchResponse {
  jobTitle: string;
  requiredSkills: string[];
  source: "file" | "saved";
  total: number;
  processed: number;
  failed: number;
  results: JobMatchCandidate[];
  aiEnabled: boolean;
  aiModel: string | null;
  aiExplanations: JobMatchAiExplanation[];
}

export interface JobMatchAiExplanation {
  username: string;
  aiRank: number;
  fitLabel: string;
  explanation: string;
  strengths: string[];
  gaps: string[];
  recommendation: string;
}

export interface GitHubOrg {
  login: string;
  avatarUrl: string;
  description: string | null;
}

export interface GitHubPR {
  number: number;
  title: string;
  state: string;
  createdAt: string;
  mergedAt: string | null;
  repoName: string;
  comments: number;
}

export interface GitHubIssue {
  number: number;
  title: string;
  state: string;
  createdAt: string;
  closedAt: string | null;
  repoName: string;
  labels: string[];
}

export interface GitHubCommit {
  sha: string;
  message: string;
  date: string;
  repoName: string;
}

export interface LanguageBreakdown {
  language: string;
  percentage: number;
  repos: number;
}

export interface GitHubContributor {
  login: string;
  contributions: number;
  avatarUrl: string | null;
  /** Present on org overview responses — share of sampled contributions (%). */
  contributionPercent?: number;
}

export interface ContributionStats {
  /** PushEvents in the user's recent 100-event feed — NOT total commits. */
  recentPushEvents: number;
  /** PRs returned by the latest-30 search — a sample, not a lifetime total. */
  sampledPullRequests: number;
  /** Issues returned by the latest-30 search — a sample, not a lifetime total. */
  sampledIssues: number;
  reposContributedTo: number;
  orgCount: number;
  samplingNote: string;
}

export interface RateLimitResource {
  limit: number;
  used: number;
  remaining: number;
  resetEpoch: number;
  resetDate: string;
}

export interface RateLimitStatus {
  authenticated: boolean;
  hint: string;
  headers: Record<string, string>;
  core: RateLimitResource;
  search: RateLimitResource;
  graphql: RateLimitResource;
}

export interface CommitWeeklyActivity {
  week: string;
  commits: number;
}

export interface CommitRepoStat {
  repoName: string;
  totalCommits: number;
  additions: number;
  deletions: number;
  messageQuality: number;
}

export interface CommitAnalytics {
  username: string;
  totalCommits: number;
  totalAdditions: number;
  totalDeletions: number;
  commitsPerWeek: number;
  reposAnalyzed: number;
  codeQualityScore: number;
  commitMessageQuality: number;
  conventionalCommitRate: number;
  averageMessageLength: number;
  commitSizeScore: number;
  topCommitTypes: string[];
  weeklyActivity: CommitWeeklyActivity[];
  repoBreakdown: CommitRepoStat[];
  explanation: string;
  improvementSuggestion: string;
  trend: "up" | "stable" | "down";
}

// Phase 6 — Commit-diff AI review types

export interface CommitDiffFile {
  filename: string;
  status: "added" | "modified" | "removed" | "renamed" | "copied" | string;
  previousFilename: string | null;
  additions: number;
  deletions: number;
  changes: number;
  patch: string | null;
}

export interface CommitDiff {
  sha: string;
  message: string;
  date: string;
  repoName: string;
  additions: number;
  deletions: number;
  changedFiles: number;
  files: CommitDiffFile[];
}

export interface CommitDiffList {
  username: string;
  totalCommits: number;
  commits: CommitDiff[];
}

export interface CommitDiffFileReview {
  filename: string;
  score: number;
  summary: string;
  issues: string[];
  suggestions: string[];
}

export interface CommitDiffReview {
  aiEnabled: boolean;
  aiModel: string | null;
  overallScore: number;
  overallSummary: string;
  keyIssues: string[];
  strengths: string[];
  recommendations: string[];
  fileReviews: CommitDiffFileReview[];
}

// Organization / team-level analytics types

export interface OrgLanguageStat {
  language: string;
  percentage: number;
  repos: number;
}

export interface OrgRepoStat {
  name: string;
  description: string | null;
  language: string | null;
  stars: number;
  forks: number;
  pushedAt: string | null;
}

export interface OrgTeamActivity {
  commits30d: number;
  commits90d: number;
  pullRequests30d: number;
  pullRequests90d: number;
  issues30d: number;
  issues90d: number;
}

export interface OrganizationAnalytics {
  login: string;
  name: string | null;
  description: string | null;
  avatarUrl: string;
  blog: string | null;
  location: string | null;
  publicRepos: number;
  followers: number;
  createdAt: string;
  totalRepos: number;
  totalStars: number;
  totalForks: number;
  averageStars: number;
  languagesCount: number;
  activeRepos: number;
  archivedRepos: number;
  inactiveRepos: number;
  forkRatio: number;
  activeContributors: number;
  teamActivity: OrgTeamActivity;
  languages: OrgLanguageStat[];
  topRepos: OrgRepoStat[];
  topContributors: GitHubContributor[];
  summary: string;
  insight: string;
}

// ==================== Recruiter API ====================

export const recruiterApi = {
  saveCandidate: async (candidate: {
    username: string;
    name?: string;
    avatarUrl?: string;
    githubId?: number;
    score?: number;
    level?: string;
    languages?: string;
  }): Promise<ApiResponse<SavedCandidate>> => {
    const { data } = await api.post<ApiResponse<SavedCandidate>>("/recruiter/candidates/save", candidate);
    return data;
  },

  unsaveCandidate: async (username: string): Promise<ApiResponse<void>> => {
    const { data } = await api.delete<ApiResponse<void>>(`/recruiter/candidates/${username}`);
    return data;
  },

  listSavedCandidates: async (): Promise<ApiResponse<SavedCandidate[]>> => {
    const { data } = await api.get<ApiResponse<SavedCandidate[]>>("/recruiter/candidates");
    return data;
  },

  listBookmarked: async (): Promise<ApiResponse<SavedCandidate[]>> => {
    const { data } = await api.get<ApiResponse<SavedCandidate[]>>("/recruiter/candidates/bookmarked");
    return data;
  },

  toggleBookmark: async (username: string, bookmarked: boolean): Promise<ApiResponse<SavedCandidate>> => {
    const { data } = await api.put<ApiResponse<SavedCandidate>>(`/recruiter/candidates/${username}/bookmark`, { bookmarked });
    return data;
  },

  addNote: async (username: string, content: string, title?: string): Promise<ApiResponse<RecruiterNote>> => {
    const { data } = await api.post<ApiResponse<RecruiterNote>>(`/recruiter/candidates/${username}/notes`, { content, title });
    return data;
  },

  getNotes: async (username: string): Promise<ApiResponse<RecruiterNote[]>> => {
    const { data } = await api.get<ApiResponse<RecruiterNote[]>>(`/recruiter/candidates/${username}/notes`);
    return data;
  },

  deleteNote: async (noteId: number): Promise<ApiResponse<void>> => {
    const { data } = await api.delete<ApiResponse<void>>(`/recruiter/notes/${noteId}`);
    return data;
  },

  getStats: async (): Promise<ApiResponse<RecruiterStats>> => {
    const { data } = await api.get<ApiResponse<RecruiterStats>>("/recruiter/stats");
    return data;
  },

  /**
   * Upload a job description file (.txt/.md/.pdf) and optionally a CSV/TXT of
   * GitHub usernames to run a fresh candidate search ranked by job fit.
   * Without a usernames file the recruiter's saved candidates are used.
   */
  matchByJobDescription: async (file: File, usernamesFile?: File | null, ai?: boolean): Promise<ApiResponse<JobMatchResponse>> => {
    const form = new FormData();
    form.append("file", file);
    if (usernamesFile) form.append("usernames", usernamesFile);
    if (ai) form.append("ai", "true");
    // axios (v1) clears the default JSON content-type for FormData so the
    // browser sets the correct multipart boundary automatically.
    const { data } = await api.post<ApiResponse<JobMatchResponse>>("/recruiter/match", form);
    return data;
  },
};

// ==================== Enhanced GitHub API ====================

export const githubApiEnhanced = {
  getOrganizations: async (username: string): Promise<ApiResponse<GitHubOrg[]>> => {
    const { data } = await api.get<ApiResponse<GitHubOrg[]>>(`/github/${username}/organizations`);
    return data;
  },

  getPullRequests: async (username: string): Promise<ApiResponse<GitHubPR[]>> => {
    const { data } = await api.get<ApiResponse<GitHubPR[]>>(`/github/${username}/pull-requests`);
    return data;
  },

  getIssues: async (username: string): Promise<ApiResponse<GitHubIssue[]>> => {
    const { data } = await api.get<ApiResponse<GitHubIssue[]>>(`/github/${username}/issues`);
    return data;
  },

  getCommits: async (username: string): Promise<ApiResponse<GitHubCommit[]>> => {
    const { data } = await api.get<ApiResponse<GitHubCommit[]>>(`/github/${username}/commits`);
    return data;
  },

  getLanguageBreakdown: async (username: string): Promise<ApiResponse<LanguageBreakdown[]>> => {
    const { data } = await api.get<ApiResponse<LanguageBreakdown[]>>(`/github/${username}/languages`);
    return data;
  },

  getWeightedLanguages: async (username: string): Promise<ApiResponse<LanguageBreakdown[]>> => {
    const { data } = await api.get<ApiResponse<LanguageBreakdown[]>>(`/github/${username}/languages/weighted`);
    return data;
  },

  getContributors: async (username: string): Promise<ApiResponse<GitHubContributor[]>> => {
    const { data } = await api.get<ApiResponse<GitHubContributor[]>>(`/github/${username}/contributors`);
    return data;
  },

  getContributionStats: async (username: string): Promise<ApiResponse<ContributionStats>> => {
    const { data } = await api.get<ApiResponse<ContributionStats>>(`/github/${username}/contribution-stats`);
    return data;
  },

  getFullInsights: async (username: string): Promise<ApiResponse<DeveloperScore>> => {
    const { data } = await api.get<ApiResponse<DeveloperScore>>(`/github/${username}/insights`);
    return data;
  },

  getRateLimit: async (): Promise<ApiResponse<RateLimitStatus>> => {
    const { data } = await api.get<ApiResponse<RateLimitStatus>>("/github/rate-limit");
    return data;
  },

  getCommitAnalytics: async (username: string): Promise<ApiResponse<CommitAnalytics>> => {
    const { data } = await api.get<ApiResponse<CommitAnalytics>>(`/github/${username}/commits/analytics`);
    return data;
  },

  getCommitDiffs: async (username: string, limit = 15): Promise<ApiResponse<CommitDiffList>> => {
    const { data } = await api.get<ApiResponse<CommitDiffList>>(`/github/${username}/commits/diffs?limit=${limit}`);
    return data;
  },

  getOrganizationOverview: async (org: string): Promise<ApiResponse<OrganizationAnalytics>> => {
    const { data } = await api.get<ApiResponse<OrganizationAnalytics>>(`/github/org/${encodeURIComponent(org)}/overview`);
    return data;
  },
};

// ==================== Gemini AI API ====================

export const aiApi = {
  getStatus: async (): Promise<ApiResponse<{ enabled: boolean; provider: string; model: string }>> => {
    const { data } = await api.get<ApiResponse<{ enabled: boolean; provider: string; model: string }>>("/ai/status");
    return data;
  },

  getSummary: async (username: string): Promise<ApiResponse<string>> => {
    const { data } = await api.get<ApiResponse<string>>(`/ai/summary/${username}`);
    return data;
  },

  getSkillAnalysis: async (username: string): Promise<ApiResponse<string>> => {
    const { data } = await api.get<ApiResponse<string>>(`/ai/skills/${username}`);
    return data;
  },

  getCareerRoadmap: async (username: string): Promise<ApiResponse<string>> => {
    const { data } = await api.get<ApiResponse<string>>(`/ai/roadmap/${username}`);
    return data;
  },

  getInterviewReadiness: async (username: string): Promise<ApiResponse<string>> => {
    const { data } = await api.get<ApiResponse<string>>(`/ai/interview/${username}`);
    return data;
  },

  getRepositoryReview: async (username: string, repoName: string): Promise<ApiResponse<string>> => {
    const { data } = await api.get<ApiResponse<string>>(`/ai/review/${username}/${encodeURIComponent(repoName)}`);
    return data;
  },

  getAComparison: async (user1: string, user2: string): Promise<ApiResponse<string>> => {
    const { data } = await api.get<ApiResponse<string>>(`/ai/compare/${user1}/${user2}`);
    return data;
  },

  getEnhancedInsights: async (username: string): Promise<ApiResponse<{ score: DeveloperScore; aiInsight: string }>> => {
    const { data } = await api.get<ApiResponse<{ score: DeveloperScore; aiInsight: string }>>(`/ai/insights/${username}`);
    return data;
  },

  getCodeQuality: async (username: string): Promise<
    ApiResponse<{ analytics: CommitAnalytics; aiReview: string }>
  > => {
    const { data } = await api.get<ApiResponse<{ analytics: CommitAnalytics; aiReview: string }>>(
      `/ai/code-quality/${username}`
    );
    return data;
  },

  getCommitDiffReview: async (request: {
    username: string;
    commits: CommitDiff[];
  }): Promise<ApiResponse<CommitDiffReview>> => {
    const { data } = await api.post<ApiResponse<CommitDiffReview>>("/ai/commit-diff-review", request);
    return data;
  },

  getOrganizationReview: async (org: string): Promise<ApiResponse<string>> => {
    const { data } = await api.get<ApiResponse<string>>(`/ai/org/${encodeURIComponent(org)}`);
    return data;
  },
};

// ==================== Score History Types ====================

export interface ScoreHistoryPage {
  content: ScoreSnapshot[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ScoreSnapshot {
  id: number;
  username: string;
  displayName: string | null;
  overallScore: number;
  level: string;
  contributionRecency: number;
  commitFrequency: number;
  repositoryHealth: number;
  repositoryQuality: number;
  contributionConsistency: number;
  languageDiversity: number;
  collaboration: number;
  openSourceImpact: number;
  popularity: number;
  maintenance: number;
  totalStars: number;
  totalForks: number;
  totalRepositories: number;
  languageCount: number;
  languages: string | null;
  createdAt: string;
}

// ==================== Reports API ====================

export const reportsApi = {
  recordScore: async (username: string): Promise<ApiResponse<ScoreSnapshot>> => {
    const { data } = await api.post<ApiResponse<ScoreSnapshot>>(`/reports/record/${username}`);
    return data;
  },

  getHistory: async (username: string): Promise<ApiResponse<ScoreSnapshot[]>> => {
    const { data } = await api.get<ApiResponse<ScoreSnapshot[]>>(`/reports/history/${username}`);
    return data;
  },

  getLatest: async (username: string): Promise<ApiResponse<ScoreSnapshot>> => {
    const { data } = await api.get<ApiResponse<ScoreSnapshot>>(`/reports/latest/${username}`);
    return data;
  },

  /** Paginated (default page 0, 50 per page) — the backend never returns the full table. */
  getAllHistory: async (page = 0, size = 50): Promise<ApiResponse<ScoreHistoryPage>> => {
    const { data } = await api.get<ApiResponse<ScoreHistoryPage>>(`/reports/all?page=${page}&size=${size}`);
    return data;
  },

  getStats: async (): Promise<ApiResponse<{ totalSnapshots: number; uniqueUsers: number; averageScore: number }>> => {
    const { data } = await api.get<ApiResponse<{ totalSnapshots: number; uniqueUsers: number; averageScore: number }>>(`/reports/stats`);
    return data;
  },

  /** POST (not GET): generating writes a history snapshot; a mutating GET is a CSRF vector. */
  generateReport: async (username: string): Promise<ApiResponse<{
    score: DeveloperScore;
    profile: GitHubProfile;
    repos: Repository[];
    history: ScoreSnapshot[];
    recorded: ScoreSnapshot;
  }>> => {
    const { data } = await api.post<ApiResponse<{
      score: DeveloperScore;
      profile: GitHubProfile;
      repos: Repository[];
      history: ScoreSnapshot[];
      recorded: ScoreSnapshot;
    }>>(`/reports/generate/${username}`);
    return data;
  },
};
