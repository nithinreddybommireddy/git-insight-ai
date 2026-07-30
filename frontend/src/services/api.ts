import axios from "axios";

const api = axios.create({
  baseURL: "/api",
  headers: {
    "Content-Type": "application/json",
  },
});

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
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export const githubApi = {
  getProfile: async (username: string): Promise<ApiResponse<GitHubProfile>> => {
    const { data } = await api.get<ApiResponse<GitHubProfile>>(
      `/github/profile/${username}`
    );
    return data;
  },

  getRepositories: async (username: string): Promise<ApiResponse<Repository[]>> => {
    const { data } = await api.get<ApiResponse<Repository[]>>(
      `/github/${username}/repos`
    );
    return data;
  },

  getDeveloperScore: async (username: string): Promise<ApiResponse<DeveloperScore>> => {
    const { data } = await api.get<ApiResponse<DeveloperScore>>(
      `/github/${username}/score`
    );
    return data;
  },
};
