import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { ForgotPassword } from "@/pages/ForgotPassword";

// Mock the API
vi.mock("@/services/api", () => ({
  authApi: {
    forgotPassword: vi.fn(),
  },
}));

import { authApi } from "@/services/api";

function renderForgotPassword() {
  return render(
    <MemoryRouter>
      <ForgotPassword />
    </MemoryRouter>
  );
}

describe("ForgotPassword", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders the form with email input and submit button", () => {
    renderForgotPassword();

    expect(screen.getByText("Forgot your password?")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("you@example.com")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /send reset link/i })).toBeInTheDocument();
  });

  it("renders a link back to login", () => {
    renderForgotPassword();

    const signInLink = screen.getByText("Sign in");
    expect(signInLink).toHaveAttribute("href", "/login");
  });

  it("disables submit when email is empty", async () => {
    renderForgotPassword();

    const button = screen.getByRole("button", { name: /send reset link/i });
    expect(button).toBeDisabled();
  });

  it("enables submit when email is entered", async () => {
    const user = userEvent.setup();
    renderForgotPassword();

    const emailInput = screen.getByPlaceholderText("you@example.com");
    await user.type(emailInput, "test@example.com");

    const button = screen.getByRole("button", { name: /send reset link/i });
    expect(button).toBeEnabled();
  });

  it("calls forgotPassword API and shows generic success message", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.forgotPassword).mockResolvedValue({
      success: true,
      message: "If the account exists, a password reset link has been sent.",
      data: undefined,
    });

    renderForgotPassword();

    const emailInput = screen.getByPlaceholderText("you@example.com");
    await user.type(emailInput, "user@example.com");

    const button = screen.getByRole("button", { name: /send reset link/i });
    await user.click(button);

    await waitFor(() => {
      expect(authApi.forgotPassword).toHaveBeenCalledWith("user@example.com");
    });

    // Should show generic success message regardless of whether account exists
    await waitFor(() => {
      expect(screen.getByText("Check your email")).toBeInTheDocument();
    });
    expect(
      screen.getByText(/If an account exists for/i)
    ).toBeInTheDocument();
  });

  it("shows success message even when API fails (prevents enumeration)", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.forgotPassword).mockRejectedValue(new Error("Network error"));

    renderForgotPassword();

    const emailInput = screen.getByPlaceholderText("you@example.com");
    await user.type(emailInput, "unknown@example.com");

    const button = screen.getByRole("button", { name: /send reset link/i });
    await user.click(button);

    await waitFor(() => {
      // Even on error, shows the same generic success message
      expect(screen.getByText("Check your email")).toBeInTheDocument();
    });
  });

  it("shows the email address in the success message", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.forgotPassword).mockResolvedValue({
      success: true,
      message: "If the account exists, a password reset link has been sent.",
      data: undefined,
    });

    renderForgotPassword();

    const emailInput = screen.getByPlaceholderText("you@example.com");
    await user.type(emailInput, "test@example.com");

    const button = screen.getByRole("button", { name: /send reset link/i });
    await user.click(button);

    await waitFor(() => {
      expect(screen.getByText("test@example.com")).toBeInTheDocument();
    });
  });

  it("shows loading state while submitting", async () => {
    const user = userEvent.setup();
    // Create a promise we control
    let resolvePromise: (value: any) => void;
    vi.mocked(authApi.forgotPassword).mockImplementation(
      () => new Promise((resolve) => { resolvePromise = resolve; })
    );

    renderForgotPassword();

    const emailInput = screen.getByPlaceholderText("you@example.com");
    await user.type(emailInput, "test@example.com");

    const button = screen.getByRole("button", { name: /send reset link/i });
    await user.click(button);

    // Button should show loading text
    expect(screen.getByText("Sending...")).toBeInTheDocument();

    // Resolve to clean up
    resolvePromise!({ success: true, message: "ok", data: undefined });
  });
});
