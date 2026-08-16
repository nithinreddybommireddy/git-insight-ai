import { motion } from "framer-motion";
import { useNavigate } from "react-router-dom";
import { Card, CardContent } from "@/components/ui/card";
import { Avatar, AvatarImage, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import toast from "react-hot-toast";
import type { GitHubProfile } from "@/services/api";
import {
  MapPin,
  Building2,
  Link as LinkIcon,
  MessageCircle,
  Users,
  GitFork,
  Calendar,
  ExternalLink,
  BookOpen,
  Share2,
  GitCompare,
  FolderGit2,
  Download,
  Star,
  Clock,
  Hash,
} from "lucide-react";

interface ProfileCardProps {
  profile: GitHubProfile;
}

export function ProfileCard({ profile }: ProfileCardProps) {
  const navigate = useNavigate();

  const stats = [
    { label: "Repositories", value: profile.publicRepositories, icon: BookOpen, color: "from-violet-500 to-purple-500" },
    { label: "Followers", value: profile.followers, icon: Users, color: "from-cyan-500 to-blue-500" },
    { label: "Following", value: profile.following, icon: GitFork, color: "from-emerald-500 to-teal-500" },
    { label: "Gists", value: profile.publicGists, icon: Hash, color: "from-amber-500 to-orange-500" },
  ];

  const links = [
    { icon: MapPin, text: profile.location, show: !!profile.location },
    { icon: Building2, text: profile.company, show: !!profile.company },
    {
      icon: LinkIcon,
      text: profile.website,
      show: !!profile.website,
      href: profile.website?.startsWith("http") ? profile.website : `https://${profile.website}`,
    },
    {
      icon: MessageCircle,
      text: profile.twitterUsername ? `@${profile.twitterUsername}` : null,
      show: !!profile.twitterUsername,
      href: `https://x.com/${profile.twitterUsername}`,
    },
  ];

  const handleShare = async () => {
    const url = profile.profileUrl;
    try {
      await navigator.clipboard.writeText(url);
      toast.success("Profile URL copied to clipboard!");
    } catch {
      toast.error("Failed to copy URL");
    }
  };

  const handleCompare = () => {
    // Navigate to the (fully implemented) Compare page, pre-filling Developer 1.
    navigate(`/compare?user1=${encodeURIComponent(profile.username)}&user2=`);
  };

  const handleViewRepos = () => {
    window.open(`${profile.profileUrl}?tab=repositories`, "_blank", "noopener noreferrer");
  };

  const handleDownloadReport = () => {
    // Navigate to the (fully implemented) Reports page for this developer,
    // which auto-generates the report and offers PDF export.
    navigate(`/reports/${encodeURIComponent(profile.username)}`);
  };

  const daysSinceJoin = Math.floor(
    (Date.now() - new Date(profile.createdAt).getTime()) / (1000 * 60 * 60 * 24)
  );

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5 }}
    >
      <Card className="overflow-hidden group">
        {/* Gradient header with overlay pattern */}
        <div className="h-36 animated-gradient relative overflow-hidden">
          <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_50%,rgba(255,255,255,0.1)_1px,transparent_1px)] bg-[length:20px_20px]" />
        </div>

        <CardContent className="relative">
          {/* Avatar - overlaps the gradient */}
          <div className="flex flex-col lg:flex-row gap-6 lg:gap-8 -mt-24">
            <motion.div
              initial={{ scale: 0, rotate: -10 }}
              animate={{ scale: 1, rotate: 0 }}
              transition={{ delay: 0.2, type: "spring", stiffness: 200, damping: 15 }}
              className="shrink-0"
            >
              <Avatar className="w-36 h-36 ring-4 ring-background shadow-xl rounded-full">
                <AvatarImage src={profile.avatarUrl} alt={profile.name || profile.username} />
                <AvatarFallback className="text-4xl font-bold bg-gradient-to-br from-primary to-accent text-white">
                  {(profile.name || profile.username).charAt(0).toUpperCase()}
                </AvatarFallback>
              </Avatar>
            </motion.div>

            <div className="flex-1 pt-4 lg:pt-6">
              <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
                <div>
                  <h2 className="text-2xl lg:text-3xl font-bold text-foreground">
                    {profile.name || profile.username}
                  </h2>
                  <p className="text-muted-foreground flex items-center gap-1.5 mt-0.5">
                    @{profile.username}
                    {profile.hireable && (
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 text-xs font-medium">
                        <Star className="w-3 h-3 fill-emerald-400" />
                        Available for hire
                      </span>
                    )}
                  </p>
                </div>

                {/* Action Buttons */}
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleShare}
                    className="gap-1.5 group/btn"
                  >
                    <Share2 className="w-3.5 h-3.5 group-hover/btn:scale-110 transition-transform" />
                    <span className="hidden sm:inline">Share</span>
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleCompare}
                    className="gap-1.5 group/btn"
                  >
                    <GitCompare className="w-3.5 h-3.5 group-hover/btn:scale-110 transition-transform" />
                    <span className="hidden sm:inline">Compare</span>
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    asChild
                    className="gap-1.5"
                  >
                    <a href={profile.profileUrl} target="_blank" rel="noopener noreferrer">
                      <ExternalLink className="w-3.5 h-3.5" />
                      <span className="hidden sm:inline">GitHub</span>
                    </a>
                  </Button>
                </div>
              </div>

              {profile.bio && (
                <motion.p
                  initial={{ opacity: 0 }}
                  animate={{ opacity: 1 }}
                  transition={{ delay: 0.3 }}
                  className="mt-4 text-sm text-foreground/80 leading-relaxed max-w-2xl"
                >
                  {profile.bio}
                </motion.p>
              )}

              {/* Info chips */}
              <div className="flex flex-wrap gap-2.5 mt-4">
                {links.map(
                  (link) =>
                    link.show && link.text && (
                      <a
                        key={link.text}
                        href={link.href || "#"}
                        target={link.href ? "_blank" : undefined}
                        rel={link.href ? "noopener noreferrer" : undefined}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg glass text-xs text-muted-foreground hover:text-foreground hover:scale-105 transition-all duration-200"
                      >
                        <link.icon className="w-3.5 h-3.5" />
                        <span className="max-w-[150px] truncate">{link.text}</span>
                      </a>
                    )
                )}
                <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg glass text-xs text-muted-foreground">
                  <Calendar className="w-3.5 h-3.5" />
                  Joined{" "}
                  {new Date(profile.createdAt).toLocaleDateString("en-US", {
                    month: "long",
                    year: "numeric",
                  })}
                </span>
                <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg glass text-xs text-muted-foreground">
                  <Clock className="w-3.5 h-3.5" />
                  {daysSinceJoin} days on GitHub
                </span>
              </div>
            </div>
          </div>

          {/* Stats Grid */}
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.4 }}
            className="grid grid-cols-2 sm:grid-cols-4 gap-3 mt-8"
          >
            {stats.map((stat) => (
              <div
                key={stat.label}
                className="glass rounded-xl p-4 text-center hover:scale-[1.03] hover:-translate-y-0.5 transition-all duration-200 cursor-default group/stat"
              >
                <div className={`w-10 h-10 rounded-lg bg-gradient-to-br ${stat.color} flex items-center justify-center mx-auto mb-2.5 group-hover/stat:scale-110 transition-transform duration-200`}>
                  <stat.icon className="w-5 h-5 text-white" />
                </div>
                <p className="text-2xl font-bold text-foreground tabular-nums">
                  {stat.value.toLocaleString()}
                </p>
                <p className="text-xs text-muted-foreground mt-0.5">
                  {stat.label}
                </p>
              </div>
            ))}
          </motion.div>

          {/* Bottom Action Row */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.6 }}
            className="flex flex-wrap items-center justify-between gap-3 mt-6 pt-6 border-t border-border"
          >
            <div className="flex flex-wrap gap-2">
              <Button
                variant="secondary"
                size="sm"
                onClick={handleViewRepos}
                className="gap-2"
              >
                <FolderGit2 className="w-4 h-4" />
                View Repositories
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleDownloadReport}
                className="gap-2"
              >
                <Download className="w-4 h-4" />
                Download Report
              </Button>
            </div>
            <p className="text-xs text-muted-foreground">
              Profile last updated{" "}
              {new Date(profile.updatedAt).toLocaleDateString("en-US", {
                month: "short",
                day: "numeric",
                year: "numeric",
              })}
            </p>
          </motion.div>
        </CardContent>
      </Card>
    </motion.div>
  );
}
