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
  Activity,
  Rocket,
  Zap,
  Sparkles,
  CheckCircle2,
} from "lucide-react";

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

const stats = [
  { label: "Profiles Analyzed", value: "1,000+", icon: Search },
  { label: "GitHub Data Points", value: "50K+", icon: Activity },
  { label: "Active Users", value: "500+", icon: Users },
  { label: "Developer Score", value: "AI-Powered", icon: Sparkles },
];

export function Landing() {
  const [username, setUsername] = useState("");
  const navigate = useNavigate();

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    if (username.trim()) {
      navigate(`/search?q=${encodeURIComponent(username.trim())}`);
    }
  };

  return (
    <div className="min-h-screen overflow-x-clip">
      {/* Hero Section */}
      <section className="relative min-h-[90vh] flex items-center justify-center overflow-hidden">
        <div className="absolute inset-0 animated-gradient opacity-25" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_center,_var(--tw-gradient-stops))] from-primary/25 via-transparent to-transparent" />
        <div className="absolute top-1/4 left-1/4 w-64 h-64 rounded-full bg-primary/15 blur-3xl animate-pulse" />
        <div className="absolute bottom-1/4 right-1/4 w-96 h-96 rounded-full bg-accent/15 blur-3xl animate-pulse delay-1000" />

        <motion.div
          variants={containerVariants}
          initial="hidden"
          animate="visible"
          className="relative z-10 max-w-4xl mx-auto px-4 text-center"
        >
          <motion.div variants={itemVariants} className="mb-6">
            <span className="inline-flex items-center gap-2 px-4 py-2 rounded-full glass text-sm text-muted-foreground border border-primary/20">
              <Sparkles className="w-4 h-4 text-primary" />
              AI-Powered GitHub Analytics Platform
            </span>
          </motion.div>

          <motion.h1 variants={itemVariants} className="text-5xl sm:text-6xl lg:text-7xl font-bold tracking-tight mb-6 leading-tight">
            <span className="gradient-text">GitInsight AI</span>
            <br />
            <span className="text-foreground">
              Understand Any Developer's GitHub Profile
            </span>
          </motion.h1>

          <motion.p variants={itemVariants} className="text-lg sm:text-xl text-muted-foreground max-w-2xl mx-auto mb-10 leading-relaxed">
            Analyze GitHub profiles with AI-powered insights. Get developer scores,
            contribution analytics, repository health checks, and portfolio reviews
            in seconds.
          </motion.p>

          <motion.form variants={itemVariants} onSubmit={handleSearch} className="max-w-xl mx-auto">
            <div className="relative group">
              <div className="absolute -inset-1 bg-gradient-to-r from-primary via-accent to-primary rounded-2xl opacity-25 blur group-hover:opacity-50 transition-opacity duration-500" />
              <div className="relative flex items-center glass-strong rounded-2xl p-1.5">
                <Search className="w-5 h-5 ml-4 text-muted-foreground shrink-0" />
                <Input
                  type="text"
                  placeholder="Enter a GitHub username..."
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="flex-1 border-0 bg-transparent focus-visible:ring-0 text-base"
                />
                <Button type="submit" variant="primary" size="lg" className="shrink-0 gap-2 rounded-xl" disabled={!username.trim()}>
                  Analyze
                  <ArrowRight className="w-4 h-4" />
                </Button>
              </div>
            </div>
          </motion.form>

          <motion.p variants={itemVariants} className="mt-4 text-xs text-muted-foreground">
            Try it now &mdash; enter any public GitHub username, e.g. &quot;nithinreddybommireddy&quot;
          </motion.p>

          {/* Hero CTAs */}
          <motion.div variants={itemVariants} className="flex items-center justify-center gap-4 mt-8">
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
              Free to use
            </div>
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
              No login required
            </div>
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
              Real GitHub data
            </div>
          </motion.div>
        </motion.div>
      </section>

      {/* Stats Counter */}
      <section className="relative mt-10 sm:mt-12 z-10 px-4">
        <div className="max-w-4xl mx-auto">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: "easeOut", delay: 0.25 }}
            className="grid grid-cols-2 md:grid-cols-4 gap-4"
          >
            {stats.map((stat) => (
              <Card key={stat.label} className="text-center py-6 hover:scale-[1.02] transition-transform duration-200">
                <stat.icon className="w-6 h-6 mx-auto mb-2 text-primary" />
                <p className="text-xl font-bold text-foreground">{stat.value}</p>
                <p className="text-xs text-muted-foreground mt-1">{stat.label}</p>
              </Card>
            ))}
          </motion.div>
        </div>
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
            <div className="w-16 h-16 rounded-2xl bg-primary/10 flex items-center justify-center mx-auto mb-6">
              <Rocket className="w-8 h-8 text-primary" />
            </div>
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
