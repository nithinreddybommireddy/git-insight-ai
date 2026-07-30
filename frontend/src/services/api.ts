import axios from "axios";

const api = axios.create({
  baseURL: "/api",
  headers: {
    "Content-Type": "application/json",
  },
});

// Add JWT interceptor
api.interceptors.request.use((config) => {
  const token = localStorage.getItem("gitinsight-token");
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

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

export interface AuthData {
  token: string;
  refreshToken: string;
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

export interface DeveloperScore {
  username: string;
  overallScore: number;
  totalStars: number;
  totalForks: number;
  totalRepositories: number;
  languageCount: number;
  languages: string[];
  avgHealthScore: number;
  avgPopularityScore: number;
  avgMaintenanceScore: number;
  level: string;
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

  refresh: async (refreshToken: string): Promise<ApiResponse<AuthData>> => {
    const { data } = await api.post<ApiResponse<AuthData>>("/auth/refresh", { refreshToken });
    return data;
  },

  me: async (token?: string): Promise<ApiResponse<User>> => {
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const { data } = await api.get<ApiResponse<User>>("/auth/me", { headers });
    return data;
  },

  githubOAuth: (): string => {
    return `/api/auth/oauth/github`;
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
