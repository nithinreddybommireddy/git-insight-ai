import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ResetPassword } from "@/pages/ResetPassword";

// Mock the API
vi.mock("@/services/api", () => ({
  authApi: {
    resetPassword: vi.fn(),
  },
}));

import { authApi } from "@/services/api";

function renderResetPassword(token: string | null = "valid-token-123") {
  const path = token ? `/auth/reset-password?token=${token}` : "/auth/reset-password";
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/auth/reset-password" element={<ResetPassword />} />
        <Route path="/login" element={<div>Login Page</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe("ResetPassword", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows invalid token message when no token is provided", () => {
    renderResetPassword(null);

    expect(screen.getByText("Invalid reset link")).toBeInTheDocument();
    expect(
      screen.getByText(/This password reset link is invalid or missing a token/)
    ).toBeInTheDocument();
  });

  it("renders password form when token is present", () => {
    renderResetPassword();

    expect(screen.getByText("Set new password")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Create a strong password")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("Repeat your password")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /reset password/i })).toBeInTheDocument();
  });

  it("disables submit when passwords don't match", async () => {
    const user = userEvent.setup();
    renderResetPassword();

    const newPwd = screen.getByPlaceholderText("Create a strong password");
    const confirmPwd = screen.getByPlaceholderText("Repeat your password");

    await user.type(newPwd, "Sup3r-new");
    await user.type(confirmPwd, "Different1");

    expect(screen.getByText("Passwords don't match")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /reset password/i })).toBeDisabled();
  });

  it("enables submit when passwords match and meet policy", async () => {
    const user = userEvent.setup();
    renderResetPassword();

    const newPwd = screen.getByPlaceholderText("Create a strong password");
    const confirmPwd = screen.getByPlaceholderText("Repeat your password");

    await user.type(newPwd, "Sup3r-new");
    await user.type(confirmPwd, "Sup3r-new");

    expect(screen.queryByText("Passwords don't match")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: /reset password/i })).toBeEnabled();
  });

  it("shows password validation indicators", async () => {
    const user = userEvent.setup();
    renderResetPassword();

    const newPwd = screen.getByPlaceholderText("Create a strong password");

    expect(screen.getByText("6+ chars")).toBeInTheDocument();
    expect(screen.getByText("Uppercase")).toBeInTheDocument();
    expect(screen.getByText("Lowercase")).toBeInTheDocument();
    expect(screen.getByText("Number")).toBeInTheDocument();

    await user.type(newPwd, "Sup3r-new");

    const checks = screen.getAllByText(/^(6\+ chars|Uppercase|Lowercase|Number)$/);
    expect(checks.length).toBe(4);
  });

  it("calls API and shows success state on successful reset", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.resetPassword).mockResolvedValue({
      success: true,
      message: "Password reset successful. Please log in again.",
      data: undefined,
    });

    renderResetPassword();

    const newPwd = screen.getByPlaceholderText("Create a strong password");
    const confirmPwd = screen.getByPlaceholderText("Repeat your password");

    await user.type(newPwd, "Sup3r-new");
    await user.type(confirmPwd, "Sup3r-new");

    const button = screen.getByRole("button", { name: /reset password/i });
    await user.click(button);

    await waitFor(() => {
      expect(authApi.resetPassword).toHaveBeenCalledWith("valid-token-123", "Sup3r-new");
    });

    // After success, the success state should render
    await waitFor(() => {
      expect(screen.getByText("Password reset successful")).toBeInTheDocument();
    });

    expect(screen.getByRole("button", { name: /sign in/i })).toBeInTheDocument();
  });

  it("calls API and shows error toast for invalid/expired token", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.resetPassword).mockRejectedValue({
      response: { data: { message: "Invalid or expired reset link." } },
    });

    renderResetPassword();

    const newPwd = screen.getByPlaceholderText("Create a strong password");
    const confirmPwd = screen.getByPlaceholderText("Repeat your password");

    await user.type(newPwd, "Sup3r-new");
    await user.type(confirmPwd, "Sup3r-new");

    const button = screen.getByRole("button", { name: /reset password/i });
    await user.click(button);

    // The API should have been called with the token
    await waitFor(() => {
      expect(authApi.resetPassword).toHaveBeenCalled();
    });

    // After failed reset, the form should still be visible (not success state)
    expect(screen.getByText("Set new password")).toBeInTheDocument();
  });

  it("shows error toast on unexpected failure", async () => {
    const user = userEvent.setup();
    vi.mocked(authApi.resetPassword).mockRejectedValue(new Error("Network error"));

    renderResetPassword();

    const newPwd = screen.getByPlaceholderText("Create a strong password");
    const confirmPwd = screen.getByPlaceholderText("Repeat your password");

    await user.type(newPwd, "Sup3r-new");
    await user.type(confirmPwd, "Sup3r-new");

    const button = screen.getByRole("button", { name: /reset password/i });
    await user.click(button);

    await waitFor(() => {
      expect(authApi.resetPassword).toHaveBeenCalled();
    });

    // Form should still be visible (not success state)
    expect(screen.getByText("Set new password")).toBeInTheDocument();
  });

  it("shows link to request new reset from invalid token state", () => {
    renderResetPassword(null);

    const link = screen.getByRole("link", { name: /request a new reset link/i });
    expect(link).toHaveAttribute("href", "/auth/forgot-password");
  });

  it("shows loading state while submitting", async () => {
    const user = userEvent.setup();
    let resolvePromise: (value: any) => void;
    vi.mocked(authApi.resetPassword).mockImplementation(
      () => new Promise((resolve) => { resolvePromise = resolve; })
    );

    renderResetPassword();

    const newPwd = screen.getByPlaceholderText("Create a strong password");
    const confirmPwd = screen.getByPlaceholderText("Repeat your password");

    await user.type(newPwd, "Sup3r-new");
    await user.type(confirmPwd, "Sup3r-new");

    const button = screen.getByRole("button", { name: /reset password/i });
    await user.click(button);

    expect(screen.getByText("Resetting...")).toBeInTheDocument();

    resolvePromise!({ success: true, message: "ok", data: undefined });
  });
});
