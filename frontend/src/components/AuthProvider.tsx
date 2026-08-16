import { useState, useEffect, useCallback, type ReactNode } from "react";
import { AuthContext } from "@/hooks/useAuth";
import { authApi, type User } from "@/services/api";
import toast from "react-hot-toast";

function getStoredToken(): string | null {
  return localStorage.getItem("gitinsight-token");
}

function getStoredRefreshToken(): string | null {
  return localStorage.getItem("gitinsight-refresh-token");
}

function storeTokens(token: string, refreshToken: string) {
  localStorage.setItem("gitinsight-token", token);
  localStorage.setItem("gitinsight-refresh-token", refreshToken);
}

function clearTokens() {
  localStorage.removeItem("gitinsight-token");
  localStorage.removeItem("gitinsight-refresh-token");
  localStorage.removeItem("gitinsight-user");
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(getStoredToken());
  const [loading, setLoading] = useState(true);

  const fetchUser = useCallback(async (authToken: string) => {
    try {
      const response = await authApi.me(authToken);
      if (response.success) {
        setUser(response.data);
        localStorage.setItem("gitinsight-user", JSON.stringify(response.data));
      } else {
        clearTokens();
        setToken(null);
        setUser(null);
      }
    } catch {
      // Token expired, try refresh
      const refreshToken = getStoredRefreshToken();
      if (refreshToken) {
        try {
          const refreshResponse = await authApi.refresh(refreshToken);
          if (refreshResponse.success) {
            storeTokens(refreshResponse.data.token, refreshResponse.data.refreshToken);
            setToken(refreshResponse.data.token);
            setUser(refreshResponse.data.user);
            localStorage.setItem("gitinsight-user", JSON.stringify(refreshResponse.data.user));
          } else {
            clearTokens();
            setToken(null);
            setUser(null);
          }
        } catch {
          clearTokens();
          setToken(null);
          setUser(null);
        }
      } else {
        clearTokens();
        setToken(null);
        setUser(null);
      }
    }
  }, []);

  useEffect(() => {
    const stored = localStorage.getItem("gitinsight-user");
    if (token && stored) {
      try {
        setUser(JSON.parse(stored));
      } catch {
        // ignore
      }
      fetchUser(token).finally(() => setLoading(false));
    } else {
      setLoading(false);
    }
  }, [token, fetchUser]);

  const login = async (email: string, password: string) => {
    const response = await authApi.login(email, password);
    if (response.success) {
      storeTokens(response.data.token, response.data.refreshToken);
      setToken(response.data.token);
      setUser(response.data.user);
      localStorage.setItem("gitinsight-user", JSON.stringify(response.data.user));
      toast.success("Welcome back!");
    } else {
      throw new Error(response.message);
    }
  };

  const register = async (name: string, email: string, password: string) => {
    const response = await authApi.register(name, email, password);
    if (response.success) {
      storeTokens(response.data.token, response.data.refreshToken);
      setToken(response.data.token);
      setUser(response.data.user);
      localStorage.setItem("gitinsight-user", JSON.stringify(response.data.user));
      toast.success("Account created!");
    } else {
      throw new Error(response.message);
    }
  };

  const logout = () => {
    clearTokens();
    setToken(null);
    setUser(null);
    toast.success("Logged out");
  };

  const refreshAuth = async () => {
    const rToken = getStoredRefreshToken();
    if (rToken && token) {
      await fetchUser(token);
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!token && !!user,
        loading,
        login,
        register,
        logout,
        refreshAuth,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
