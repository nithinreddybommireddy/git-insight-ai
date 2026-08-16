import { useState, useEffect, useRef, type ReactNode } from "react";
import { ThemeContext, type Theme } from "@/hooks/useTheme";

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(() => {
    if (typeof window !== "undefined") {
      const stored = localStorage.getItem("gitinsight-theme");
      if (stored === "light" || stored === "dark") return stored;
    }
    return "dark";
  });

  // Tracks the temporary transition window so rapid toggles don't cut it short.
  const transitionTimeout = useRef<number | null>(null);

  useEffect(() => {
    const root = document.documentElement;
    root.classList.remove("dark", "light");
    root.classList.add(theme);
    localStorage.setItem("gitinsight-theme", theme);
  }, [theme]);

  useEffect(() => {
    return () => {
      if (transitionTimeout.current !== null) {
        window.clearTimeout(transitionTimeout.current);
      }
    };
  }, []);

  const toggleTheme = () => {
    const root = document.documentElement;

    // Enable the cross-fade only for the duration of the switch, then remove it
    // so everyday transitions (and performance) stay untouched.
    root.classList.add("theme-transition");
    if (transitionTimeout.current !== null) {
      window.clearTimeout(transitionTimeout.current);
    }
    transitionTimeout.current = window.setTimeout(() => {
      root.classList.remove("theme-transition");
      transitionTimeout.current = null;
    }, 500);

    setTheme((prev) => (prev === "dark" ? "light" : "dark"));
  };

  return (
    <ThemeContext.Provider value={{ theme, toggleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}
