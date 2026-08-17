package com.gitinsight.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client IP resolution that is safe against {@code X-Forwarded-For} spoofing.
 *
 * <p>The header is honored ONLY when the direct peer is a proxy this project
 * deploys behind (loopback = the Vite dev proxy; RFC1918 = the Docker nginx
 * gateway). A public peer sending a forged header is keyed by its real socket
 * address instead, so an attacker who can reach the service directly cannot
 * rotate the header to bypass IP-based rate limiting. First hop wins, matching
 * how the Vite/nginx proxies append the real client address first.
 */
public final class ClientAddress {

    private ClientAddress() {
    }

    public static String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String forwarded = isTrustedProxy(remoteAddr) ? request.getHeader("X-Forwarded-For") : null;
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : remoteAddr;
    }

    private static boolean isTrustedProxy(String remoteAddr) {
        if (remoteAddr == null) {
            return false;
        }
        if (remoteAddr.startsWith("127.") || remoteAddr.equals("::1") || remoteAddr.equals("0:0:0:0:0:0:0:1")) {
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
