import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { motion } from "framer-motion";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card } from "@/components/ui/card";
import {
  Search,
  BarChart3,
  Brain,
  Shield,
  TrendingUp,
  Users,
  ArrowRight,
  Zap,
  Sparkles,
  CheckCircle2,
  Building2,
  Code2,
  Network,
} from "lucide-react";
import { HeroIllustration } from "@/components/HeroIllustration";

const containerVariants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: { staggerChildren: 0.1 },
  },
};

const itemVariants = {
  hidden: { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0 },
};

const heroPills = [
  { label: "Profile Analysis", icon: BarChart3, color: "#3b82f6" },
  { label: "Code Quality", icon: Code2, color: "#8b5cf6" },
  { label: "Contribution Trends", icon: Network, color: "#10b981" },
  { label: "AI Insights", icon: Brain, color: "#f97316" },
];

const features = [
  {
    icon: Search,
    title: "GitHub Profile Analysis",
    description:
      "Analyze any public GitHub profile instantly. View comprehensive stats, repositories, and contribution history.",
    gradient: "from-violet-500 to-purple-500",
  },
  {
    icon: BarChart3,
    title: "Contribution Analytics",
    description:
      "Track coding consistency with beautiful charts. See commit frequency, language distribution, and activity trends.",
    gradient: "from-cyan-500 to-blue-500",
  },
  {
    icon: Brain,
    title: "AI Portfolio Review",
    description:
      "Get AI-generated insights about your coding strengths, weaknesses, and personalized improvement suggestions.",
    gradient: "from-emerald-500 to-teal-500",
  },
  {
    icon: Shield,
    title: "Repository Health Score",
    description:
      "Evaluate repository quality with health scores. Measure documentation, maintenance, testing, and security.",
    gradient: "from-amber-500 to-orange-500",
  },
  {
    icon: TrendingUp,
    title: "Developer Score",
    description:
      "A comprehensive developer score based on contributions, code quality, consistency, and open-source impact.",
    gradient: "from-rose-500 to-pink-500",
  },
  {
    icon: Users,
    title: "Compare Developers",
    description:
      "Compare two GitHub profiles side by side. Perfect for recruiters and hiring managers.",
    gradient: "from-indigo-500 to-violet-500",
  },
];

const howItWorks = [
  { step: "01", title: "Enter a GitHub Username", description: "Type any public GitHub username into the search bar.", icon: Search },
  { step: "02", title: "AI Analyzes the Profile", description: "Our system fetches data and AI generates deep insights.", icon: Brain },
  { step: "03", title: "Get Actionable Insights", description: "View scores, charts, and recommendations instantly.", icon: TrendingUp },
];

export function Landing() {
  const [username, setUsername] = useState("");
  const [orgName, setOrgName] = useState("");
  const navigate = useNavigate();

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (username.trim()) {
      navigate(`/search?q=${encodeURIComponent(username.trim())}`);
    }
  };

  const handleOrgSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (orgName.trim()) {
      navigate(`/org/${encodeURIComponent(orgName.trim())}`);
    }
  };

  return (
    <div className="min-h-screen overflow-x-clip">
      {/* Hero Section */}
      <section className="relative overflow-hidden">
        {/* Subtle navy vignette — clean, no animated gradients or glow blobs */}
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_rgba(37,99,235,0.10),_transparent_55%)]" />

        <motion.div
          variants={containerVariants}
          initial="hidden"
          animate="visible"
          className="relative z-10 max-w-7xl mx-auto px-4 pt-24 pb-14 lg:pt-28 lg:pb-16"
        >
          <div className="grid grid-cols-1 lg:grid-cols-[0.85fr_1.15fr] gap-12 lg:gap-14 items-center">
            {/* Illustration — left on desktop, after the form on mobile */}
            <motion.div variants={itemVariants} className="order-2 lg:order-1 flex justify-center">
              <div className="w-full max-w-[440px] lg:max-w-[500px]">
                <HeroIllustration />
              </div>
            </motion.div>

            {/* Content — right column */}
            <motion.div variants={itemVariants} className="order-1 lg:order-2 text-center lg:text-left">
              {/* Eyebrow */}
              <motion.div variants={itemVariants} className="mb-4">
                <span className="inline-flex items-center gap-2 rounded-full border border-[#0a84ff]/25 bg-[#0a84ff]/10 px-3.5 py-1.5 text-xs font-semibold tracking-wide text-[#2f7bd6] dark:text-[#9cc6ff] shadow-[0_0_20px_rgba(10,132,255,0.12)]">
                  <Sparkles className="w-3.5 h-3.5" />
                  AI-Powered GitHub Analytics Platform
                </span>
              </motion.div>

              {/* Product name — above the heading, smaller than it, brand colors */}
              <motion.p variants={itemVariants} className="text-xl sm:text-2xl font-bold tracking-tight mb-1.5">
                <span className="text-[#1c2f6b] dark:text-[#a8c4e0]">GitInsight</span>
                <span className="text-[#0a84ff]">-AI</span>
              </motion.p>

              {/* Main heading */}
              <motion.h1 variants={itemVariants} className="text-4xl sm:text-5xl font-extrabold tracking-tight leading-[1.1] mb-4 text-slate-900 dark:text-white">
                Understand Any Developer&apos;s{" "}
                <span className="text-[#0a84ff]">GitHub Profile</span>
              </motion.h1>

              {/* Supporting text */}
              <motion.p variants={itemVariants} className="text-base sm:text-lg text-slate-600 dark:text-slate-400 max-w-xl mx-auto lg:mx-0 mb-6 leading-relaxed">
                Analyze repositories, contributions, code quality, activity trends and AI-generated insights.
              </motion.p>

              {/* Feature cards — compact 2x2 grid */}
              <motion.div variants={itemVariants} className="grid grid-cols-1 sm:grid-cols-2 gap-3 max-w-xl mx-auto lg:mx-0 mb-7">
                {heroPills.map((pill) => (
                  <div
                    key={pill.label}
                    className="flex items-center gap-3 rounded-xl border border-slate-200 dark:border-white/10 bg-white dark:bg-white/5 px-3.5 py-3 transition-colors duration-200 hover:border-[#0a84ff]/40 hover:bg-[#0a84ff]/[0.04] dark:hover:bg-white/[0.08]"
                  >
                    <div className="w-8 h-8 shrink-0 rounded-lg bg-[#0a84ff]/10 dark:bg-white/10 flex items-center justify-center">
                      <pill.icon className="w-4 h-4" style={{ color: pill.color }} />
                    </div>
                    <span className="text-sm font-medium text-slate-800 dark:text-slate-100">{pill.label}</span>
                  </div>
                ))}
              </motion.div>

              {/* GitHub analysis — the primary action */}
              <motion.form variants={itemVariants} onSubmit={handleSearch} className="max-w-xl mx-auto lg:mx-0 mb-2">
                <div className="flex items-center rounded-xl border border-slate-200 dark:border-white/10 bg-white dark:bg-white/5 shadow-sm transition-all focus-within:border-[#0a84ff]/60 focus-within:ring-4 focus-within:ring-[#0a84ff]/10">
                  <Search className="w-5 h-5 ml-4 text-slate-400 shrink-0" />
                  <Input
                    type="text"
                    placeholder="Enter a GitHub username..."
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    className="h-12 flex-1 border-0 bg-transparent px-3 focus-visible:ring-0 text-[15px]"
                  />
                  <Button type="submit" variant="primary" size="lg" className="shrink-0 gap-1.5 rounded-lg mr-1.5 h-10 px-5" disabled={!username.trim()}>
                    Analyze
                    <ArrowRight className="w-4 h-4" />
                  </Button>
                </div>
              </motion.form>

              <motion.p variants={itemVariants} className="text-xs text-slate-500 dark:text-slate-500 mb-5">
                Try it now &mdash; enter any public GitHub username, e.g. &quot;nithinreddybommireddy&quot;
              </motion.p>

              {/* Organization analysis — visually secondary */}
              <motion.div variants={itemVariants} className="mb-6">
                <p className="text-[11px] text-slate-500 dark:text-slate-500 mb-1.5 flex items-center justify-center lg:justify-start gap-1.5">
                  <Building2 className="w-3.5 h-3.5 text-[#0a84ff]/70" />
                  Analyzing a team instead? Enter a GitHub organization:
                </p>
                <form onSubmit={handleOrgSearch} className="max-w-md mx-auto lg:mx-0">
                  <div className="flex items-center rounded-lg border border-slate-200/80 dark:border-white/10 bg-white/70 dark:bg-white/5 transition-all focus-within:border-[#0a84ff]/50 focus-within:ring-4 focus-within:ring-[#0a84ff]/10">
                    <Building2 className="w-4 h-4 ml-3 text-slate-400 shrink-0" />
                    <Input
                      type="text"
                      placeholder="Organization name (e.g. vercel, facebook, google)"
                      value={orgName}
                      onChange={(e) => setOrgName(e.target.value)}
                      className="h-10 flex-1 border-0 bg-transparent px-2.5 focus-visible:ring-0 text-sm"
                    />
                    <Button type="submit" variant="ghost" size="sm" className="shrink-0 gap-1 mr-1 text-xs text-slate-500 dark:text-slate-400 hover:text-[#0a84ff]" disabled={!orgName.trim()}>
                      Analyze org
                      <ArrowRight className="w-3 h-3" />
                    </Button>
                  </div>
                </form>
              </motion.div>

              {/* Trust indicators — one clean row, subtle */}
              <motion.div variants={itemVariants} className="flex flex-wrap items-center justify-center lg:justify-start gap-x-5 gap-y-2">
                <div className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500/80 dark:text-emerald-400/90" />
                  Free to use
                </div>
                <div className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500/80 dark:text-emerald-400/90" />
                  No login required
                </div>
                <div className="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-500/80 dark:text-emerald-400/90" />
                  Real GitHub data
                </div>
              </motion.div>
            </motion.div>
          </div>

          {/* Bottom tagline — subtle, anchored to the hero */}
          <motion.div variants={itemVariants} className="mt-12 lg:mt-14 flex items-center justify-center gap-4">
            <div className="h-px w-16 sm:w-40 bg-slate-300 dark:bg-slate-700" />
            <p className="text-[11px] font-semibold tracking-[0.28em] text-slate-500 dark:text-slate-500">
              ANALYZE<span className="text-[#0a84ff]"> • </span>UNDERSTAND
              <span className="text-[#0a84ff]"> • </span>HIRE
              <span className="text-[#0a84ff]"> • </span>GROW
            </p>
            <div className="h-px w-16 sm:w-40 bg-slate-300 dark:bg-slate-700" />
          </motion.div>
        </motion.div>
      </section>

      {/* Features Section */}
      <section className="relative py-32 px-4">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_bottom_left,_var(--tw-gradient-stops))] from-primary/8 via-transparent to-transparent" />
        <div className="max-w-7xl mx-auto relative z-10">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: "easeOut", delay: 0.1 }}
            className="text-center mb-16"
          >
            <h2 className="text-3xl sm:text-4xl font-bold mb-4">
              Everything You Need to{" "}
              <span className="gradient-text">Analyze Developers</span>
            </h2>
            <p className="text-muted-foreground max-w-2xl mx-auto">
              From individual developers to enterprise recruiting, GitInsight AI
              provides the tools you need to evaluate GitHub profiles effectively.
            </p>
          </motion.div>

          <motion.div
            variants={containerVariants}
            initial="hidden"
            animate="visible"
            className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6"
          >
            {features.map((feature) => (
              <motion.div key={feature.title} variants={itemVariants}>
                <Card className="group h-full hover:scale-[1.02] hover:-translate-y-1 cursor-default transition-all duration-300">
                  <div className={`w-12 h-12 rounded-xl bg-gradient-to-br ${feature.gradient} flex items-center justify-center mb-4 shadow-lg`}>
                    <feature.icon className="w-6 h-6 text-white" />
                  </div>
                  <h3 className="text-lg font-semibold mb-2">{feature.title}</h3>
                  <p className="text-sm text-muted-foreground leading-relaxed">{feature.description}</p>
                </Card>
              </motion.div>
            ))}
          </motion.div>
        </div>
      </section>

      {/* How It Works */}
      <section className="relative py-24 px-4">
        <div className="max-w-7xl mx-auto">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: "easeOut", delay: 0.1 }}
            className="text-center mb-16"
          >
            <h2 className="text-3xl sm:text-4xl font-bold mb-4">
              How It <span className="gradient-text">Works</span>
            </h2>
            <p className="text-muted-foreground max-w-2xl mx-auto">
              Three simple steps to get powerful developer insights.
            </p>
          </motion.div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
            {howItWorks.map((step, index) => (
              <motion.div
                key={step.step}
                initial={{ opacity: 0, y: 20 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.5, ease: "easeOut", delay: 0.2 + index * 0.15 }}
                className="relative"
              >
                {index < howItWorks.length - 1 && (
                  <div className="hidden md:block absolute top-12 left-[60%] w-full h-0.5 bg-gradient-to-r from-primary/30 to-transparent" />
                )}
                <Card className="text-center h-full hover:scale-[1.02] transition-transform duration-200">
                  <div className="w-16 h-16 rounded-full bg-primary/10 flex items-center justify-center mx-auto mb-4">
                    <step.icon className="w-8 h-8 text-primary" />
                  </div>
                  <span className="text-xs font-bold text-primary tracking-widest">STEP {step.step}</span>
                  <h3 className="text-lg font-semibold mt-2 mb-3">{step.title}</h3>
                  <p className="text-sm text-muted-foreground">{step.description}</p>
                </Card>
              </motion.div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="relative py-24 px-4">
        <div className="absolute inset-0 animated-gradient opacity-10" />
        <div className="max-w-3xl mx-auto text-center relative z-10">
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.5, ease: "easeOut" }}
          >
            <img
              src="/logo.svg"
              alt="GitInsight-AI — Analyze. Understand. Grow."
              className="w-full max-w-sm mx-auto mb-8 rounded-3xl shadow-2xl shadow-black/40 ring-1 ring-primary/20"
            />
            <h2 className="text-3xl sm:text-4xl font-bold mb-4">
              Ready to Explore Developer Analytics?
            </h2>
            <p className="text-muted-foreground mb-8 max-w-xl mx-auto">
              Enter any GitHub username to get instant AI-powered insights about their coding journey.
            </p>
            <div className="flex flex-col sm:flex-row items-center justify-center gap-4">
              <Button variant="primary" size="lg" onClick={() => navigate("/search")} className="gap-2 text-base">
                <Search className="w-5 h-5" />
                Get Started Now
                <ArrowRight className="w-5 h-5" />
              </Button>
              <Button variant="outline" size="lg" onClick={() => {
                const examples = ["torvalds", "addyosmani", "gaearon"];
                navigate(`/search?q=${examples[Math.floor(Math.random() * examples.length)]}`);
              }} className="gap-2 text-base">
                <Zap className="w-5 h-5" />
                Try a Random Profile
              </Button>
            </div>
          </motion.div>
        </div>
      </section>

    </div>
  );
}
