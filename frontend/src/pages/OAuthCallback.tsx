import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Loader2, AlertCircle } from "lucide-react";

/**
 * Destination of the GitHub OAuth redirect. The backend signs the browser back
 * here with ?token=...&refreshToken=... (or ?error=...). Tokens are stored in
 * localStorage and the page is fully reloaded so AuthProvider re-initializes
 * from the stored session and fetches /me.
 */
export function OAuthCallback() {
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    const token = params.get("token");
    const refreshToken = params.get("refreshToken");
    const errorMsg = params.get("error");

    if (errorMsg) {
      setError(decodeURIComponent(errorMsg));
      return;
    }

    if (token && refreshToken) {
      localStorage.setItem("gitinsight-token", token);
      localStorage.setItem("gitinsight-refresh-token", refreshToken);
      window.location.assign("/dashboard");
      return;
    }

    navigate("/login", { replace: true });
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
