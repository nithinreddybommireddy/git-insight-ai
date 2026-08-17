import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Loader2, AlertCircle } from "lucide-react";
import { authApi } from "@/services/api";

/**
 * Destination of the GitHub OAuth redirect. The backend sets the HttpOnly
 * session cookies and redirects here with a CLEAN URL — no tokens in the query
 * string (tokens in URLs leak through history, referrers, and proxy logs).
 * This page simply confirms the session via /auth/me and routes to the
 * dashboard; the AuthProvider picks up the same session on reload.
 */
export function OAuthCallback() {
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const errorMsg = params.get("error");

    if (errorMsg) {
      setError(decodeURIComponent(errorMsg));
      return;
    }

    let cancelled = false;
    authApi
      .me()
      .then((res) => {
        if (cancelled) return;
        if (res.success) {
          window.location.assign("/dashboard");
        } else {
          navigate("/login", { replace: true });
        }
      })
      .catch(() => {
        if (!cancelled) navigate("/login", { replace: true });
      });

    return () => {
      cancelled = true;
    };
  }, [navigate]);

  return (
    <div className="min-h-screen flex flex-col">
      <div className="flex-1 flex items-center justify-center px-4 py-20">
        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          className="w-full max-w-md text-center"
        >
          {error ? (
            <div className="rounded-2xl border border-border bg-card p-8 shadow-lg">
              <div className="w-14 h-14 rounded-xl bg-red-500/10 flex items-center justify-center mx-auto mb-4">
                <AlertCircle className="w-7 h-7 text-red-500" />
              </div>
              <h1 className="text-xl font-bold mb-2">GitHub sign-in failed</h1>
              <p className="text-sm text-muted-foreground mb-6">{error}</p>
              <Button variant="primary" asChild className="w-full">
                <Link to="/login">Back to sign in</Link>
              </Button>
            </div>
          ) : (
            <div className="flex flex-col items-center gap-4 py-12">
              <Loader2 className="w-8 h-8 animate-spin text-primary" />
              <p className="text-sm text-muted-foreground">Signing you in…</p>
            </div>
          )}
        </motion.div>
      </div>
    </div>
  );
}
