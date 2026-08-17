import { Link } from "react-router-dom";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/hooks/useAuth";
import { ArrowLeft, LogOut, Shield, User as UserIcon } from "lucide-react";

export function Settings() {
  const { user, logout } = useAuth();

  if (!user) return null;

  const rows = [
    { label: "Name", value: user.name },
    { label: "Email", value: user.email },
    { label: "Role", value: user.role },
    { label: "GitHub username", value: user.githubUsername ?? "—" },
    {
      label: "Member since",
      value: user.createdAt ? new Date(user.createdAt).toLocaleDateString() : "—",
    },
  ];

  return (
    <div className="w-full max-w-3xl mx-auto px-4 sm:px-6 py-10">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.3, ease: "easeOut" }}
      >
        <Button variant="ghost" size="sm" className="gap-2 text-muted-foreground mb-6" asChild>
          <Link to="/dashboard">
            <ArrowLeft className="w-4 h-4" />
            Back to dashboard
          </Link>
        </Button>

        <div className="flex items-center gap-4 mb-8">
          <div className="w-12 h-12 rounded-full bg-gradient-to-br from-primary to-accent flex items-center justify-center text-white text-lg font-bold">
            {user.name.charAt(0).toUpperCase()}
          </div>
          <div>
            <h1 className="text-2xl font-bold">Settings</h1>
            <p className="text-sm text-muted-foreground">Manage your GitInsight-AI account</p>
          </div>
        </div>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <UserIcon className="w-4 h-4" />
              Account details
            </CardTitle>
            <p className="text-sm text-muted-foreground">
              Your profile information as registered with GitInsight-AI.
            </p>
          </CardHeader>
          <CardContent>
            <dl className="divide-y divide-border">
              {rows.map((row) => (
                <div key={row.label} className="flex items-center justify-between py-3 gap-4">
                  <dt className="text-sm text-muted-foreground">{row.label}</dt>
                  <dd className="text-sm font-medium text-right break-all">{row.value}</dd>
                </div>
              ))}
            </dl>

            <div className="mt-6 pt-6 border-t border-border">
              <Button variant="outline" className="gap-2 text-destructive" onClick={logout}>
                <LogOut className="w-4 h-4" />
                Logout
              </Button>
            </div>
          </CardContent>
        </Card>

        <p className="mt-6 flex items-center gap-2 text-xs text-muted-foreground">
          <Shield className="w-3.5 h-3.5" />
          Sessions are secured with short-lived access tokens. Sign in again if this screen
          looks wrong.
        </p>
      </motion.div>
    </div>
  );
}
