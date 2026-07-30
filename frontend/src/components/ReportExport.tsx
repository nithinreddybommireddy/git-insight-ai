import { useRef } from "react";
import { Button } from "@/components/ui/button";
import { FileText, Printer } from "lucide-react";
import type {
  DeveloperScore,
  GitHubProfile,
  Repository,
  LanguageBreakdown,
} from "@/services/api";
import toast from "react-hot-toast";

interface ReportExportProps {
  profile: GitHubProfile | null;
  score: DeveloperScore | null;
  repos: Repository[];
  languages: LanguageBreakdown[];
  username: string;
}

export function ReportExport({ profile, score, repos, languages, username }: ReportExportProps) {
  const reportRef = useRef<HTMLDivElement>(null);

  const getScoreColor = (v: number) =>
    v >= 80 ? "text-emerald-400" : v >= 65 ? "text-cyan-400" : v >= 50 ? "text-violet-400" : v >= 35 ? "text-amber-400" : "text-red-400";
  const getScoreBar = (v: number) =>
    v >= 80 ? "bg-emerald-500" : v >= 65 ? "bg-cyan-500" : v >= 50 ? "bg-violet-500" : v >= 35 ? "bg-amber-500" : "bg-red-500";

  const handlePrint = () => {
    if (!profile || !score) {
      toast.error("Load the developer profile first");
      return;
    }
    window.print();
  };

  const handleExport = async () => {
    if (!profile || !score) {
      toast.error("Load the developer profile first");
      return;
    }
    toast.success("Opening print dialog to save as PDF...");
    window.print();
  };

  // Hidden print-only report content
  return (
    <>
      {/* Controls visible on screen only */}
      <div className="flex gap-2">
        <Button
          variant="outline"
          size="sm"
          onClick={handlePrint}
          className="gap-1.5"
          disabled={!profile || !score}
        >
          <Printer className="w-4 h-4" />
          Print
        </Button>
        <Button
          variant="primary"
          size="sm"
          onClick={handleExport}
          className="gap-1.5"
          disabled={!profile || !score}
        >
          <FileText className="w-4 h-4" />
          Export PDF
        </Button>
      </div>

      {/* Report Layout — only visible during print */}
      <div
        ref={reportRef}
        className="print-report"
      >
        {/* Print-only wrapper */}
        <div className="hidden print:block">
          <div className="p-8 max-w-4xl mx-auto">
            {/* Header */}
            <div className="flex items-center gap-4 mb-6 pb-4 border-b border-gray-300">
              {profile?.avatarUrl && (
                <img
                  src={profile.avatarUrl}
                  alt=""
                  className="w-16 h-16 rounded-full"
                />
              )}
              <div>
                <h1 className="text-2xl font-bold text-gray-900">
                  {profile?.name || username}
                </h1>
                <p className="text-gray-500">@{username}</p>
                {profile?.bio && <p className="text-sm text-gray-600 mt-1">{profile.bio}</p>}
              </div>
              <div className="ml-auto text-right">
                <p className="text-3xl font-bold text-gray-900">{score?.overallScore || "—"}</p>
                <p className="text-sm text-gray-500">{score?.level || "No score"}</p>
              </div>
            </div>

            {/* Stats grid */}
            {profile && (
              <div className="grid grid-cols-4 gap-4 mb-6">
                {[
                  { label: "Repos", value: profile.publicRepositories },
                  { label: "Stars", value: score?.totalStars || 0 },
                  { label: "Followers", value: profile.followers },
                  { label: "Languages", value: score?.languageCount || 0 },
                ].map((s) => (
                  <div key={s.label} className="bg-gray-50 p-3 rounded-lg text-center">
                    <p className="text-lg font-bold text-gray-900">{s.value.toLocaleString()}</p>
                    <p className="text-xs text-gray-500">{s.label}</p>
                  </div>
                ))}
              </div>
            )}

            {/* Score breakdown */}
            {score && (
              <div className="mb-6">
                <h2 className="text-lg font-semibold text-gray-900 mb-3">10-Point Score Breakdown</h2>
                <div className="space-y-2">
                  {[
                    { label: "Contribution Recency", value: score.contributionRecency },
                    { label: "Commit Frequency", value: score.commitFrequency },
                    { label: "Repository Health", value: score.repositoryHealth },
                    { label: "Repository Quality", value: score.repositoryQuality },
                    { label: "Contribution Consistency", value: score.contributionConsistency },
                    { label: "Language Diversity", value: score.languageDiversity },
                    { label: "Collaboration", value: score.collaboration },
                    { label: "Open Source Impact", value: score.openSourceImpact },
                    { label: "Popularity", value: score.popularity },
                    { label: "Maintenance", value: score.maintenance },
                  ].map((m) => (
                    <div key={m.label} className="flex items-center gap-3">
                      <span className="text-sm text-gray-600 w-44">{m.label}</span>
                      <div className="flex-1 h-2 bg-gray-200 rounded-full overflow-hidden">
                        <div
                          className={`h-full rounded-full ${getScoreBar(m.value)}`}
                          style={{ width: `${m.value}%` }}
                        />
                      </div>
                      <span className={`text-sm font-bold ${getScoreColor(m.value)}`}>
                        {m.value}
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* AI Insights */}
            {score?.insights && (
              <div className="mb-6 p-4 bg-blue-50 rounded-lg">
                <h2 className="text-lg font-semibold text-gray-900 mb-2">AI Insights</h2>
                <p className="text-sm text-gray-700 mb-3">{score.insights.overallAssessment}</p>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div>
                    <span className="font-semibold text-emerald-600">Strongest:</span>{" "}
                    <span className="text-gray-700">{score.insights.strongestSkill}</span>
                  </div>
                  <div>
                    <span className="font-semibold text-amber-600">Weakest:</span>{" "}
                    <span className="text-gray-700">{score.insights.weakestArea}</span>
                  </div>
                </div>
                {score.insights.recommendations && (
                  <div className="mt-3">
                    <span className="font-semibold text-gray-800 text-sm">Recommendations:</span>
                    <p className="text-sm text-gray-600">{score.insights.recommendations}</p>
                  </div>
                )}
              </div>
            )}

            {/* Languages */}
            {languages.length > 0 && (
              <div className="mb-6">
                <h2 className="text-lg font-semibold text-gray-900 mb-3">Languages</h2>
                <div className="flex flex-wrap gap-2">
                  {languages.map((l) => (
                    <span
                      key={l.language}
                      className="px-3 py-1 bg-gray-100 text-gray-700 rounded-full text-sm"
                    >
                      {l.language} ({l.percentage}%)
                    </span>
                  ))}
                </div>
              </div>
            )}

            {/* Top Repos */}
            {repos.length > 0 && (
              <div>
                <h2 className="text-lg font-semibold text-gray-900 mb-3">
                  Top Repositories ({repos.length})
                </h2>
                <div className="space-y-2">
                  {repos.slice(0, 10).map((repo) => (
                    <div key={repo.name} className="p-3 bg-gray-50 rounded-lg">
                      <div className="flex items-center justify-between">
                        <div>
                          <p className="text-sm font-semibold text-gray-900">{repo.name}</p>
                          {repo.description && (
                            <p className="text-xs text-gray-500 line-clamp-1">{repo.description}</p>
                          )}
                        </div>
                        <div className="flex items-center gap-3 text-xs text-gray-500">
                          {repo.language && (
                            <span>{repo.language}</span>
                          )}
                          <span>⭐ {repo.stars}</span>
                          <span>🍴 {repo.forks}</span>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Footer */}
            <div className="mt-8 pt-4 border-t border-gray-300 text-center text-xs text-gray-400">
              Generated by GitInsight AI · github.com/nithinreddybommireddy/git-insight-ai
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
