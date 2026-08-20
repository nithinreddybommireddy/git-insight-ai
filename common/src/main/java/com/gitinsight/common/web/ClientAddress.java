package com.gitinsight.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client IP resolution safe against header spoofing.
 *
 * <p>Header priority:
 * <ol>
 *   <li>{@code X-Real-IP} — set by Railway and most reverse proxies. Trusted
 *       because Railway strips client-supplied values before setting it.</li>
 *   <li>{@code X-Forwarded-For} — honored ONLY when the direct peer is a proxy
 *       this project deploys behind (loopback = Vite dev proxy; RFC1918 = Docker
 *       nginx gateway). First hop wins.</li>
 *   <li>{@code request.getRemoteAddr()} — the raw socket address.</li>
 * </ol>
 *
 * <p>A public peer sending forged {@code X-Forwarded-For} is keyed by its real
 * socket address, so an attacker who can reach the service directly cannot rotate
 * the header to bypass IP-based rate limiting.
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String resolve(HttpServletRequest request) {
        // 1. X-Real-IP — Railway and most CDNs set this; not spoofable by clients.
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.split(",")[0].trim();
        }

        // 2. X-Forwarded-For — only trust when the direct peer is a known proxy.
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }

        // 3. Direct socket address.
        return remoteAddr;
    }

    private static boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        if (remoteAddr.startsWith("127.") || remoteAddr.equals("::1")
                || remoteAddr.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        // RFC1918 private ranges: 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16
        if (remoteAddr.startsWith("10.") || remoteAddr.startsWith("192.168.")) {
            return true;
        }
        if (remoteAddr.startsWith("172.")) {
            int end = remoteAddr.indexOf('.', 4);
            if (end <= 4) {
                return false;
            }
            try {
                int second = Integer.parseInt(remoteAddr.substring(4, end));
                return second >= 16 && second <= 31;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }
}
