/**
 * GitInsight-AI hero illustration — faithful SVG replica of the brand's
 * promo graphic: a central magnifying glass with the octocat silhouette,
 * floating analytics cards (pie chart, code, bar chart + trend) and a
 * neural-network badge. Pure inline SVG — no external assets.
 */
export function HeroIllustration() {
  return (
    <div className="relative mx-auto w-full max-w-[560px]">
      <svg
        viewBox="0 0 560 480"
        className="w-full h-auto drop-shadow-2xl"
        role="img"
        aria-label="GitInsight-AI analytics illustration"
      >
        <defs>
          <radialGradient id="gi-lens-glow" cx="0.5" cy="0.5" r="0.5">
            <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.5" />
            <stop offset="100%" stopColor="#3b82f6" stopOpacity="0" />
          </radialGradient>
          <radialGradient id="gi-lens" cx="0.35" cy="0.3" r="0.9">
            <stop offset="0%" stopColor="#7db8ff" />
            <stop offset="55%" stopColor="#3b82f6" />
            <stop offset="100%" stopColor="#1d4ed8" />
          </radialGradient>
          <linearGradient id="gi-bar" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#4d7cff" />
            <stop offset="100%" stopColor="#1e40af" />
          </linearGradient>
        </defs>

        {/* Soft glow behind the lens */}
        <circle cx="250" cy="245" r="180" fill="url(#gi-lens-glow)" />

        {/* Magnifying glass handle (behind the lens) */}
        <line
          x1="350" y1="340" x2="442" y2="434"
          stroke="#0b1220" strokeWidth="38" strokeLinecap="round"
        />
        <line
          x1="350" y1="340" x2="442" y2="434"
          stroke="#22304f" strokeWidth="10" strokeLinecap="round"
        />

        {/* Lens */}
        <circle cx="250" cy="245" r="155" fill="url(#gi-lens)" />
        <circle
          cx="250" cy="245" r="155" fill="none"
          stroke="#ffffff" strokeOpacity="0.3" strokeWidth="3"
        />

        {/* Solid black octocat silhouette inside the lens */}
        <g transform="translate(196 192) scale(6.4)">
          <path
            fill="#0a1122"
            fillRule="evenodd"
            clipRule="evenodd"
            d="M9.356 1.85C5.05 1.85 1.57 5.356 1.57 9.694a7.84 7.84 0 0 0 5.324 7.44c.387.079.528-.168.528-.376 0-.182-.013-.805-.013-1.454-2.165.467-2.616-.935-2.616-.935-.349-.91-.864-1.143-.864-1.143-.71-.48.051-.48.051-.48.787.051 1.2.805 1.2.805.695 1.194 1.817.857 2.268.649.064-.507.27-.857.49-1.052-1.728-.182-3.545-.857-3.545-3.87 0-.857.31-1.558.8-2.104-.078-.195-.349-1 .077-2.078 0 0 .657-.208 2.14.805a7.5 7.5 0 0 1 1.946-.26c.657 0 1.328.092 1.946.26 1.483-1.013 2.14-.805 2.14-.805.426 1.078.155 1.883.078 2.078.502.546.799 1.247.799 2.104 0 3.013-1.818 3.675-3.558 3.87.284.247.528.714.528 1.454 0 1.052-.012 1.896-.012 2.156 0 .208.142.455.528.377a7.84 7.84 0 0 0 5.324-7.441c.013-4.338-3.48-7.844-7.773-7.844"
          />
        </g>

        {/* Top-right card: pie chart + lines */}
        <g>
          <rect x="336" y="30" width="200" height="104" rx="16" fill="#ffffff" fillOpacity="0.97" />
          <rect x="336" y="30" width="200" height="104" rx="16" fill="none" stroke="#0f172a" strokeOpacity="0.08" strokeWidth="1" />
          <circle cx="358" cy="72" r="17" fill="none" stroke="#a78bfa" strokeWidth="9" strokeDasharray="42.7 64.1" />
          <circle cx="358" cy="72" r="17" fill="none" stroke="#34d399" strokeWidth="9" strokeDasharray="35.2 71.6" strokeDashoffset="-42.7" />
          <circle cx="358" cy="72" r="17" fill="none" stroke="#fb923c" strokeWidth="9" strokeDasharray="28.9 77.9" strokeDashoffset="-77.9" />
          <rect x="388" y="45" width="96" height="9" rx="4.5" fill="#94a3b8" />
          <rect x="388" y="68" width="76" height="9" rx="4.5" fill="#cbd5e1" />
          <rect x="388" y="91" width="54" height="9" rx="4.5" fill="#e2e8f0" />
        </g>

        {/* Middle-left card: code */}
        <g>
          <rect x="8" y="210" width="188" height="140" rx="16" fill="#221a4f" fillOpacity="0.97" />
          <rect x="8" y="210" width="188" height="140" rx="16" fill="none" stroke="#ffffff" strokeOpacity="0.12" strokeWidth="1" />
          <text x="28" y="244" fill="#22d3ee" fontFamily="'SF Mono', ui-monospace, Menlo, monospace" fontSize="18" fontWeight="700">{`</>`}</text>
          <circle cx="38" cy="278" r="5" fill="#67e8f9" />
          <rect x="54" y="271" width="120" height="7" rx="3.5" fill="#67e8f9" fillOpacity="0.95" />
          <circle cx="38" cy="302" r="5" fill="#4ade80" />
          <rect x="54" y="295" width="94" height="7" rx="3.5" fill="#4ade80" fillOpacity="0.95" />
          <circle cx="38" cy="326" r="5" fill="#e2e8f0" />
          <rect x="54" y="319" width="132" height="7" rx="3.5" fill="#e2e8f0" fillOpacity="0.9" />
          <circle cx="38" cy="340" r="5" fill="#67e8f9" fillOpacity="0.5" />
          <rect x="54" y="333" width="70" height="7" rx="3.5" fill="#67e8f9" fillOpacity="0.5" />
        </g>

        {/* Bottom-left card: bar chart + upward green trend */}
        <g>
          <rect x="12" y="352" width="214" height="112" rx="16" fill="#ffffff" fillOpacity="0.97" />
          <rect x="12" y="352" width="214" height="112" rx="16" fill="none" stroke="#0f172a" strokeOpacity="0.08" strokeWidth="1" />
          <rect x="44" y="398" width="22" height="34" rx="7" fill="url(#gi-bar)" />
          <rect x="84" y="382" width="22" height="50" rx="7" fill="url(#gi-bar)" />
          <rect x="124" y="364" width="22" height="68" rx="7" fill="url(#gi-bar)" />
          <rect x="164" y="340" width="22" height="92" rx="7" fill="url(#gi-bar)" />
          <path d="M54 398 L94 382 L134 364 L174 340" fill="none" stroke="#10b981" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
          <path d="M174 340 l -11 -9 M174 340 l 10 -7" fill="none" stroke="#10b981" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" />
        </g>

        {/* Right badge: glowing neural network */}
        <g>
          <circle cx="452" cy="368" r="44" fill="#142052" stroke="#60a5fa" strokeOpacity="0.5" strokeWidth="2" />
          <circle cx="452" cy="368" r="18" fill="#60a5fa" fillOpacity="0.25" />
          <g stroke="#e2e8f0" strokeOpacity="0.6" strokeWidth="2">
            <line x1="452" y1="350" x2="436" y2="372" />
            <line x1="452" y1="350" x2="468" y2="372" />
            <line x1="436" y1="372" x2="452" y2="388" />
            <line x1="468" y1="372" x2="452" y2="388" />
            <line x1="452" y1="350" x2="452" y2="388" />
          </g>
          <g fill="#ffffff">
            <circle cx="452" cy="350" r="5" />
            <circle cx="436" cy="372" r="5" />
            <circle cx="468" cy="372" r="5" />
            <circle cx="452" cy="388" r="5" />
          </g>
        </g>
      </svg>
    </div>
  );
}
