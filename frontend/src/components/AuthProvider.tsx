import { useState, useEffect, useCallback, type ReactNode } from "react";
import { AuthContext } from "@/hooks/useAuth";
import { authApi, onSessionChange, type User } from "@/services/api";
import toast from "react-hot-toast";

function storeUser(user: User | null) {
  if (user) {
    localStorage.setItem("gitinsight-user", JSON.stringify(user));
  } else {
    localStorage.removeItem("gitinsight-user");
  }
}

/**
 * Session management over HttpOnly cookies.
 *
 * Tokens live only in server-set HttpOnly cookies (login/register/refresh and
 * the GitHub OAuth callback all set them); this provider never sees or stores
 * a token. On mount it restores the session via /auth/me, and it subscribes to
 * silent-refresh events so a token rotation in the API layer keeps the UI's
 * user in sync.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchUser = useCallback(async () => {
    const response = await authApi.me();
    if (response.success) {
      setUser(response.data);
      storeUser(response.data);
      return response.data;
    }
    return null;
  }, []);

  useEffect(() => {
    let cancelled = false;

    // Instant paint from the cached profile, then reconcile with the server.
    const cached = localStorage.getItem("gitinsight-user");
    if (cached) {
      try {
        setUser(JSON.parse(cached));
      } catch {
        // ignore corrupt cache
      }
    }

    fetchUser()
      .catch(() => null)
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    const unsubscribe = onSessionChange((next) => {
      setUser(next);
      storeUser(next);
    });

    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [fetchUser]);

  const login = async (email: string, password: string) => {
    const response = await authApi.login(email, password);
    if (response.success) {
      setUser(response.data.user);
      storeUser(response.data.user);
      toast.success("Welcome back!");
    } else {
      throw new Error(response.message);
    }
  };

  const register = async (name: string, email: string, password: string) => {
    const response = await authApi.register(name, email, password);
    if (response.success) {
      setUser(response.data.user);
      storeUser(response.data.user);
      toast.success("Account created!");
    } else {
      throw new Error(response.message);
    }
  };

  const logout = () => {
    // Best-effort server-side cookie clear; the local state is dropped either way.
    authApi.logout().catch(() => {});
    setUser(null);
    storeUser(null);
    toast.success("Logged out");
  };

  const refreshAuth = async () => {
    await fetchUser();
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
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
