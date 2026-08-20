import { useState } from "react";
import { Link, useSearchParams, useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent } from "@/components/ui/card";
import { authApi } from "@/services/api";
import toast from "react-hot-toast";
import {
  Lock,
  Eye,
  EyeOff,
  ArrowRight,
  ArrowLeft,
  Loader2,
  CheckCircle2,
  AlertCircle,
} from "lucide-react";

export function ResetPassword() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get("token");

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);

  // No token provided
  if (!token) {
    return (
      <div className="min-h-screen flex flex-col">
        <div className="flex-1 flex items-center justify-center px-4 py-20">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="w-full max-w-md"
          >
            <Card>
              <CardContent className="p-8 text-center">
                <div className="w-14 h-14 rounded-xl bg-red-500/10 flex items-center justify-center mx-auto mb-4">
                  <AlertCircle className="w-7 h-7 text-red-400" />
                </div>
                <h1 className="text-2xl font-bold mb-2">Invalid reset link</h1>
                <p className="text-muted-foreground mb-6">
                  This password reset link is invalid or missing a token. Please
                  request a new one.
                </p>
                <Link to="/auth/forgot-password">
                  <Button
                    variant="primary"
                    size="lg"
                    className="w-full gap-2"
                  >
                    Request a new reset link
                  </Button>
                </Link>
              </CardContent>
            </Card>
          </motion.div>
        </div>
      </div>
    );
  }

  const passwordChecks = {
    minLength: newPassword.length >= 6,
    hasUpper: /[A-Z]/.test(newPassword),
    hasLower: /[a-z]/.test(newPassword),
    hasNumber: /[0-9]/.test(newPassword),
    match:
      newPassword === confirmPassword && confirmPassword.length > 0,
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newPassword || newPassword !== confirmPassword) return;

    setLoading(true);
    try {
      const response = await authApi.resetPassword(token, newPassword);
      if (response.success) {
        setSuccess(true);
        toast.success("Password reset successful!");
      } else {
        toast.error(response.message || "Password reset failed");
      }
    } catch (err: any) {
      const message =
        err.response?.data?.message ||
        err.message ||
        "Password reset failed. The link may have expired.";
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <div className="min-h-screen flex flex-col">
        <div className="flex-1 flex items-center justify-center px-4 py-20">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="w-full max-w-md"
          >
            <Card>
              <CardContent className="p-8 text-center">
                <div className="w-14 h-14 rounded-xl bg-emerald-500/10 flex items-center justify-center mx-auto mb-4">
                  <CheckCircle2 className="w-7 h-7 text-emerald-400" />
                </div>
                <h1 className="text-2xl font-bold mb-2">
                  Password reset successful
                </h1>
                <p className="text-muted-foreground mb-6">
                  Your password has been updated. Please sign in with your new
                  password.
                </p>
                <Button
                  variant="primary"
                  size="lg"
                  className="w-full gap-2"
                  onClick={() => navigate("/login", { replace: true })}
                >
                  <ArrowRight className="w-4 h-4" />
                  Sign In
                </Button>
              </CardContent>
            </Card>
          </motion.div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col">
      <div className="flex-1 flex items-center justify-center px-4 py-20">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="w-full max-w-md"
        >
          <Card>
            <CardContent className="p-8">
              <div className="text-center mb-8">
                <img
                  src="/icon.svg"
                  alt="GitInsight AI"
                  className="w-14 h-14 rounded-xl mx-auto mb-4 ring-1 ring-primary/20"
                />
                <h1 className="text-2xl font-bold">Set new password</h1>
                <p className="text-sm text-muted-foreground mt-1">
                  Choose a strong password for your account.
                </p>
              </div>

              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="space-y-1.5">
                  <label className="text-sm font-medium">New Password</label>
                  <div className="relative">
                    <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                    <Input
                      type={showPassword ? "text" : "password"}
                      placeholder="Create a strong password"
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      className="pl-10 pr-10"
                      required
                      autoFocus
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                    >
                      {showPassword ? (
                        <EyeOff className="w-4 h-4" />
                      ) : (
                        <Eye className="w-4 h-4" />
                      )}
                    </button>
                  </div>
                  <div className="flex flex-wrap gap-2 mt-2">
                    {[
                      {
                        key: "minLength",
                        label: "6+ chars",
                        ok: passwordChecks.minLength,
                      },
                      {
                        key: "hasUpper",
                        label: "Uppercase",
                        ok: passwordChecks.hasUpper,
                      },
                      {
                        key: "hasLower",
                        label: "Lowercase",
                        ok: passwordChecks.hasLower,
                      },
                      {
                        key: "hasNumber",
                        label: "Number",
                        ok: passwordChecks.hasNumber,
                      },
                    ].map((check) => (
                      <span
                        key={check.key}
                        className={`inline-flex items-center gap-1 text-[10px] px-2 py-0.5 rounded-full transition-all ${
                          check.ok
                            ? "bg-emerald-500/10 text-emerald-400"
                            : newPassword.length > 0
                            ? "bg-red-500/10 text-red-400"
                            : "bg-muted/50 text-muted-foreground"
                        }`}
                      >
                        <CheckCircle2 className="w-2.5 h-2.5" />
                        {check.label}
                      </span>
                    ))}
                  </div>
                </div>

                <div className="space-y-1.5">
                  <label className="text-sm font-medium">
                    Confirm Password
                  </label>
                  <div className="relative">
                    <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                    <Input
                      type="password"
                      placeholder="Repeat your password"
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      className="pl-10"
                      required
                    />
                  </div>
                  {confirmPassword.length > 0 && !passwordChecks.match && (
                    <p className="text-[11px] text-red-400 mt-1">
                      Passwords don't match
                    </p>
                  )}
                </div>

                <Button
                  type="submit"
                  variant="primary"
                  size="lg"
                  className="w-full gap-2"
                  disabled={loading || !passwordChecks.match}
                >
                  {loading ? (
                    <Loader2 className="w-4 h-4 animate-spin" />
                  ) : (
                    <ArrowRight className="w-4 h-4" />
                  )}
                  {loading ? "Resetting..." : "Reset Password"}
                </Button>
              </form>

              <div className="flex items-center justify-between mt-6">
                <Link
                  to="/auth/forgot-password"
                  className="text-sm text-muted-foreground hover:text-foreground transition-colors"
                >
                  Request a new link
                </Link>
                <Link
                  to="/login"
                  className="text-sm text-muted-foreground hover:text-foreground transition-colors inline-flex items-center gap-1"
                >
                  <ArrowLeft className="w-3 h-3" />
                  Back to Sign In
                </Link>
              </div>
            </CardContent>
          </Card>
        </motion.div>
      </div>
    </div>
  );
}
