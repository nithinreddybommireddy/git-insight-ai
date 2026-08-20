import { vi } from "vitest";
import "@testing-library/jest-dom/vitest";

// Mock window.matchMedia for jsdom (required by react-hot-toast and framer-motion)
if (typeof window.matchMedia !== "function") {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}
