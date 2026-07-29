import { Code2, Heart } from "lucide-react";

export function Footer() {
  return (
    <footer className="border-t border-border bg-background/50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <div className="flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2 text-sm text-muted-foreground">
            <Code2 className="w-4 h-4" />
            <span>GitInsight AI</span>
            <span className="hidden sm:inline">&bull;</span>
            <span className="hidden sm:inline">
              AI-Powered GitHub Analytics
            </span>
          </div>
          <p className="text-sm text-muted-foreground flex items-center gap-1">
            Made with <Heart className="w-3.5 h-3.5 text-red-500 fill-red-500" /> by
            Nithin Reddy
          </p>
        </div>
      </div>
    </footer>
  );
}
