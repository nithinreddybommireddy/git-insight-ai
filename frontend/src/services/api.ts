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
};
