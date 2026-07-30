import { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Footer } from "@/components/Footer";
import { AISummaryPanel } from "@/components/AISummaryPanel";
import { useAuth } from "@/hooks/useAuth";
import {
  recruiterApi,
  githubApi,
  githubApiEnhanced,
  type SavedCandidate,
  type RecruiterStats,
  type RecruiterNote,
  type LanguageBreakdown,
} from "@/services/api";
import toast from "react-hot-toast";
import {
  Search,
  Users,
  Bookmark,
  FileText,
  Star,
  Trash2,
  LogOut,
  ExternalLink,
  Loader2,
  UserPlus,
  TrendingUp,
  MoreHorizontal,
  BarChart3,
  MessageSquare,
  X,
} from "lucide-react";

export function RecruiterDashboard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState("");
  const [candidates, setCandidates] = useState<SavedCandidate[]>([]);
  const [notes, setNotes] = useState<Record<string, RecruiterNote[]>>({});
  const [languages, setLanguages] = useState<Record<string, LanguageBreakdown[]>>({});
  const [scores, setScores] = useState<Record<string, number>>({});
  const [stats, setStats] = useState<RecruiterStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [_addingNote, setAddingNote] = useState<string | null>(null);
  const [noteContent, setNoteContent] = useState("");
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [expandedNotes, setExpandedNotes] = useState<Record<string, boolean>>({});
  const [filterBookmarked, setFilterBookmarked] = useState(false);

  const loadCandidates = useCallback(async () => {
    try {
      const candidatesRes = await recruiterApi.listSavedCandidates();
      if (candidatesRes.success) {
        setCandidates(candidatesRes.data || []);

        // Load scores, notes, and languages in background
        for (const c of (candidatesRes.data || [])) {
          const username = c.candidateUsername;

          // Load score
          githubApi.getDeveloperScore(username).then((r) => {
            if (r.success) setScores((prev) => ({ ...prev, [username]: r.data.overallScore }));
          }).catch(() => {});

          // Load notes
          recruiterApi.getNotes(username).then((r) => {
            if (r.success) setNotes((prev) => ({ ...prev, [username]: r.data }));
          }).catch(() => {});

          // Load languages
          githubApiEnhanced.getLanguageBreakdown(username).then((r) => {
            if (r.success) setLanguages((prev) => ({ ...prev, [username]: r.data }));
          }).catch(() => {});
        }
      }
    } catch {
      toast.error("Failed to load candidates");
    }
  }, []);

  const loadData = useCallback(async () => {
    try {
      const [candidatesRes, statsRes] = await Promise.all([
        recruiterApi.listSavedCandidates(),
        recruiterApi.getStats(),
      ]);
      if (candidatesRes.success) {
        setCandidates(candidatesRes.data || []);

        // Background enrichments
        for (const c of (candidatesRes.data || [])) {
          const uname = c.candidateUsername;
          githubApi.getDeveloperScore(uname).then((r) => {
            if (r.success) setScores((prev) => ({ ...prev, [uname]: r.data.overallScore }));
          }).catch(() => {});
          recruiterApi.getNotes(uname).then((r) => {
            if (r.success) setNotes((prev) => ({ ...prev, [uname]: r.data }));
          }).catch(() => {});
          githubApiEnhanced.getLanguageBreakdown(uname).then((r) => {
            if (r.success) setLanguages((prev) => ({ ...prev, [uname]: r.data }));
          }).catch(() => {});
        }
      }
      if (statsRes.success) setStats(statsRes.data);
    } catch {
      toast.error("Failed to load recruiter data");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadData(); }, [loadData]);

  const handleSaveProfile = async () => {
    if (!searchQuery.trim()) return;
    try {
      const profile = await githubApi.getProfile(searchQuery.trim());
      if (profile.success) {
        const p = profile.data;
        await recruiterApi.saveCandidate({
          username: p.username,
          name: p.name || undefined,
          avatarUrl: p.avatarUrl,
          githubId: p.githubId,
        });
        toast.success(`${p.name || p.username} saved as candidate!`);
        loadCandidates();
        setSearchQuery("");
      }
    } catch {
      toast.error("Could not find that GitHub user");
    }
  };

  const handleUnsave = async (username: string) => {
    try {
      await recruiterApi.unsaveCandidate(username);
      setCandidates(prev => prev.filter(c => c.candidateUsername !== username));
      toast.success("Candidate removed");
    } catch {
      toast.error("Failed to remove candidate");
    }
  };

  const handleToggleBookmark = async (username: string, bookmarked: boolean) => {
    try {
      await recruiterApi.toggleBookmark(username, bookmarked);
      setCandidates(prev => prev.map(c =>
        c.candidateUsername === username ? { ...c, bookmarked } : c
      ));
    } catch {
      toast.error("Failed to update bookmark");
    }
  };

  const handleAddNote = async (username: string) => {
    if (!noteContent.trim()) return;
    try {
      await recruiterApi.addNote(username, noteContent, "Quick note");
      setNoteContent("");
      setAddingNote(null);
      // Refresh notes
      const notesRes = await recruiterApi.getNotes(username);
      if (notesRes.success) setNotes((prev) => ({ ...prev, [username]: notesRes.data }));
      toast.success("Note added");
    } catch {
      toast.error("Failed to add note");
    }
  };

  const handleDeleteNote = async (noteId: number, username: string) => {
    try {
      await recruiterApi.deleteNote(noteId);
      setNotes((prev) => ({
        ...prev,
        [username]: (prev[username] || []).filter((n) => n.id !== noteId),
      }));
      toast.success("Note deleted");
    } catch {
      toast.error("Failed to delete note");
    }
  };

  if (!user) return null;

  const displayedCandidates = filterBookmarked
    ? candidates.filter((c) => c.bookmarked)
    : candidates;

  return (
    <div className="min-h-screen pt-20 pb-16">
      <div className="max-w-6xl mx-auto px-4">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-8"
        >
          <div className="flex items-center justify-between flex-wrap gap-4">
            <div className="flex items-center gap-4">
              <div className="w-14 h-14 rounded-2xl bg-gradient-to-br from-cyan-500 to-blue-500 flex items-center justify-center shadow-lg shadow-cyan-500/20">
                <Users className="w-7 h-7 text-white" />
              </div>
              <div>
                <h1 className="text-2xl font-bold">Recruiter Dashboard</h1>
                <p className="text-sm text-muted-foreground">
                  Search, save, and evaluate developer candidates
                </p>
              </div>
            </div>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={() => navigate("/dashboard")} className="gap-2">
                <TrendingUp className="w-4 h-4" />
                My Dashboard
              </Button>
              <Button variant="ghost" size="sm" onClick={logout} className="gap-2 text-muted-foreground">
                <LogOut className="w-4 h-4" />
                Logout
              </Button>
            </div>
          </div>
        </motion.div>

        {/* Stats */}
        {stats && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
            className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8"
          >
            <Card className="text-center py-5">
              <Users className="w-6 h-6 mx-auto mb-2 text-primary" />
              <p className="text-2xl font-bold">{stats.savedCandidates}</p>
              <p className="text-xs text-muted-foreground">Saved Candidates</p>
            </Card>
            <Card className="text-center py-5">
              <FileText className="w-6 h-6 mx-auto mb-2 text-accent" />
              <p className="text-2xl font-bold">{stats.totalNotes}</p>
              <p className="text-xs text-muted-foreground">Total Notes</p>
            </Card>
            <Card className="text-center py-5">
              <Bookmark className="w-6 h-6 mx-auto mb-2 text-amber-400" />
              <p className="text-2xl font-bold">{candidates.filter(c => c.bookmarked).length}</p>
              <p className="text-xs text-muted-foreground">Bookmarked</p>
            </Card>
            <Card className="text-center py-5">
              <Star className="w-6 h-6 mx-auto mb-2 text-emerald-400" />
              <p className="text-2xl font-bold">10</p>
              <p className="text-xs text-muted-foreground">Metrics per Profile</p>
            </Card>
          </motion.div>
        )}

        {/* Search & Save */}
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.15 }}
          className="mb-8"
        >
          <Card>
            <CardContent className="!py-5">
              <form onSubmit={(e) => { e.preventDefault(); handleSaveProfile(); }} className="flex gap-3">
                <div className="flex-1 relative">
                  <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    type="text"
                    placeholder="Enter GitHub username to analyze & save..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                    className="pl-9"
                  />
                </div>
                <Button type="submit" variant="primary" className="gap-2 shrink-0" disabled={!searchQuery.trim()}>
                  <UserPlus className="w-4 h-4" />
                  Save Candidate
                </Button>
              </form>
            </CardContent>
          </Card>
        </motion.div>

        {/* Filter */}
        <div className="flex items-center gap-2 mb-4">
          <div className="flex items-center gap-2">
            <Users className="w-5 h-5 text-muted-foreground" />
            <h2 className="text-lg font-semibold">Saved Candidates</h2>
          </div>
          <div className="flex items-center gap-1.5 ml-auto">
            <span className="text-xs text-muted-foreground">{displayedCandidates.length} of {candidates.length}</span>
            <button
              onClick={() => setFilterBookmarked(!filterBookmarked)}
              className={`text-[11px] px-2.5 py-1 rounded-lg transition-all ${
                filterBookmarked ? "bg-amber-500/10 text-amber-400" : "text-muted-foreground hover:text-foreground hover:bg-muted/50"
              }`}
            >
              <Bookmark className={`w-3 h-3 inline mr-1 ${filterBookmarked ? "fill-amber-400" : ""}`} />
              Bookmarked only
            </button>
          </div>
        </div>

        {/* Candidates */}
        {loading ? (
          <div className="flex justify-center py-16">
            <Loader2 className="w-8 h-8 text-primary animate-spin" />
          </div>
        ) : displayedCandidates.length === 0 ? (
          <Card className="p-12 text-center">
            <div className="w-20 h-20 rounded-3xl bg-gradient-to-br from-cyan-500/10 to-blue-500/10 flex items-center justify-center mx-auto mb-5">
              <Users className="w-10 h-10 text-cyan-400" />
            </div>
            <h3 className="text-lg font-semibold mb-2">{filterBookmarked ? "No Bookmarked Candidates" : "No Candidates Yet"}</h3>
            <p className="text-sm text-muted-foreground max-w-md mx-auto mb-6">
              {filterBookmarked
                ? "Bookmark candidates to filter them here."
                : "Search for GitHub users and save them as candidates to start building your recruitment list."}
            </p>
            <Button variant="primary" onClick={() => navigate("/search?q=torvalds")} className="gap-2">
              <Search className="w-4 h-4" />
              Try Searching Developers
            </Button>
          </Card>
        ) : (
          <div className="grid gap-3">
            {displayedCandidates.map((candidate) => {
              const username = candidate.candidateUsername;
              const isExpanded = expanded[username] || false;
              const showNotes = expandedNotes[username] || false;
              const candidateNotes = notes[username] || [];
              const candidateLangs = languages[username] || [];
              const candidateScore = scores[username] ?? candidate.candidateScore;

              return (
                <motion.div
                  key={candidate.id}
                  initial={{ opacity: 0, y: 5 }}
                  animate={{ opacity: 1, y: 0 }}
                >
                  <Card className="hover:scale-[1.003] transition-all duration-200">
                    <CardContent className="!p-4">
                      {/* Main row */}
                      <div className="flex items-center gap-4">
                        {/* Avatar */}
                        <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-primary to-accent flex items-center justify-center text-white font-bold shrink-0 overflow-hidden">
                          {candidate.candidateAvatarUrl ? (
                            <img src={candidate.candidateAvatarUrl} alt="" className="w-full h-full object-cover" />
                          ) : (
                            (candidate.candidateName || username).charAt(0).toUpperCase()
                          )}
                        </div>

                        {/* Info */}
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <button
                              onClick={() => navigate(`/recruiter/candidate/${username}`)}
                              className="font-medium text-sm hover:text-primary transition-colors text-left"
                            >
                              {candidate.candidateName || username}
                            </button>
                            {candidate.candidateLevel && (
                              <span className="text-[10px] px-2 py-0.5 rounded-full bg-primary/10 text-primary font-medium">
                                {candidate.candidateLevel}
                              </span>
                            )}
                            {candidate.bookmarked && (
                              <Bookmark className="w-3 h-3 text-amber-400 fill-amber-400" />
                            )}
                          </div>
                          <p className="text-xs text-muted-foreground mt-0.5">@{username}</p>

                          {/* Score bar */}
                          {candidateScore != null && (
                            <div className="flex items-center gap-2 mt-1">
                              <div className="flex-1 h-1 rounded-full bg-muted/20 max-w-[120px]">
                                <div
                                  className="h-full rounded-full bg-gradient-to-r from-primary to-accent"
                                  style={{ width: `${Math.min(candidateScore, 100)}%` }}
                                />
                              </div>
                              <span className="text-[10px] font-bold tabular-nums">{candidateScore}</span>
                            </div>
                          )}

                          {/* Lang chips */}
                          {candidateLangs.length > 0 && (
                            <div className="flex flex-wrap gap-1 mt-1">
                              {candidateLangs.slice(0, 3).map((l) => (
                                <span key={l.language} className="text-[9px] px-1.5 py-0.5 rounded-full bg-muted/30 text-muted-foreground">
                                  {l.language}
                                </span>
                              ))}
                              {candidateLangs.length > 3 && (
                                <span className="text-[9px] text-muted-foreground">+{candidateLangs.length - 3}</span>
                              )}
                            </div>
                          )}
                        </div>

                        {/* Actions */}
                        <div className="flex items-center gap-1 shrink-0">
                          <Button variant="ghost" size="sm" className="w-8 h-8 p-0" asChild>
                            <a href={`https://github.com/${username}`} target="_blank" rel="noopener noreferrer">
                              <ExternalLink className="w-3.5 h-3.5" />
                            </a>
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="w-8 h-8 p-0"
                            onClick={() => handleToggleBookmark(username, !candidate.bookmarked)}
                          >
                            <Bookmark className={`w-3.5 h-3.5 ${candidate.bookmarked ? "fill-amber-400 text-amber-400" : ""}`} />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="w-8 h-8 p-0"
                            onClick={() => navigate(`/recruiter/candidate/${username}`)}
                            title="View candidate details"
                          >
                            <BarChart3 className="w-3.5 h-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="w-8 h-8 p-0"
                            onClick={() => setExpanded((prev) => ({ ...prev, [username]: !isExpanded }))}
                            title="Toggle details"
                          >
                            <MoreHorizontal className="w-3.5 h-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="w-8 h-8 p-0 text-red-400 hover:text-red-300"
                            onClick={() => handleUnsave(username)}
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </Button>
                        </div>
                      </div>

                      {/* Expandable section */}
                      {isExpanded && (
                        <motion.div
                          initial={{ opacity: 0, height: 0 }}
                          animate={{ opacity: 1, height: "auto" }}
                          className="mt-3 pt-3 border-t border-border/50 space-y-3"
                        >
                          {/* Quick actions */}
                          <div className="flex flex-wrap gap-2">
                            <Button
                              variant="outline"
                              size="sm"
                              className="text-[11px] gap-1.5"
                              onClick={() => navigate(`/recruiter/candidate/${username}`)}
                            >
                              <BarChart3 className="w-3 h-3" />
                              View Profile
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              className="text-[11px] gap-1.5"
                              onClick={() => navigate(`/compare?user1=${username}&user2=addyosmani`)}
                            >
                              <TrendingUp className="w-3 h-3" />
                              Compare
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              className="text-[11px] gap-1.5"
                              onClick={() => setExpandedNotes((prev) => ({ ...prev, [username]: !showNotes }))}
                            >
                              <MessageSquare className="w-3 h-3" />
                              Notes ({candidateNotes.length})
                            </Button>
                          </div>

                          {/* Notes section */}
                          {showNotes && (
                            <div className="space-y-2">
                              <div className="flex gap-2">
                                <Input
                                  placeholder="Add a note..."
                                  value={noteContent}
                                  onChange={(e) => setNoteContent(e.target.value)}
                                  className="text-sm"
                                />
                                <Button size="sm" variant="primary" onClick={() => handleAddNote(username)}>
                                  Add
                                </Button>
                              </div>
                              {candidateNotes.length === 0 ? (
                                <p className="text-[11px] text-muted-foreground">No notes yet</p>
                              ) : (
                                <div className="space-y-1.5 max-h-40 overflow-y-auto">
                                  {candidateNotes.map((note) => (
                                    <div key={note.id} className="flex items-start justify-between gap-2 p-2 rounded-lg bg-muted/20">
                                      <div>
                                        <p className="text-xs">{note.content}</p>
                                        <p className="text-[9px] text-muted-foreground mt-0.5">
                                          {new Date(note.createdAt).toLocaleDateString("en-US", {
                                            month: "short", day: "numeric"
                                          })}
                                        </p>
                                      </div>
                                      <button
                                        onClick={() => handleDeleteNote(note.id, username)}
                                        className="text-red-400 hover:text-red-300 shrink-0 mt-0.5"
                                      >
                                        <X className="w-3 h-3" />
                                      </button>
                                    </div>
                                  ))}
                                </div>
                              )}
                            </div>
                          )}

                          {/* AI Summary compact */}
                          <AISummaryPanel
                            insights={null}
                            username={username}
                            variant="compact"
                          />
                        </motion.div>
                      )}

                      {/* Date */}
                      <p className="text-[10px] text-muted-foreground/60 mt-2">
                        Saved {new Date(candidate.createdAt).toLocaleDateString("en-US", {
                          month: "short", day: "numeric", year: "numeric"
                        })}
                      </p>
                    </CardContent>
                  </Card>
                </motion.div>
              );
            })}
          </div>
        )}
      </div>
      <Footer />
    </div>
  );
}
