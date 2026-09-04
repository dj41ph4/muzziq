"use client";

import { motion } from "framer-motion";

/**
 * Marque officielle MuzziQ.
 *
 * Le symbole reprend exactement le vecteur déjà utilisé par l'application
 * Android : cercle noir + forme verte. Les anneaux, le halo et la respiration
 * restent des effets d'interface autour du symbole ; ils ne modifient jamais
 * le logo lui-même.
 */

const ORBIT_PARTICLES = [
  { radius: 24, duration: "4.5s", delay: "0s", reverse: false },
  { radius: 27, duration: "6.5s", delay: "-2s", reverse: true },
  { radius: 20, duration: "8s", delay: "-4.5s", reverse: false },
];

const SIZES = {
  sm: { outer: "h-9 w-9", mark: "h-8 w-8" },
  md: { outer: "h-12 w-12", mark: "h-11 w-11" },
  lg: { outer: "h-16 w-16", mark: "h-15 w-15" },
} as const;

export function Logo({ size = "md", animated = true }: { size?: keyof typeof SIZES; animated?: boolean }) {
  const s = SIZES[size];

  return (
    <div className={`relative flex flex-shrink-0 items-center justify-center ${s.outer}`}>
      {animated && (
        <>
          <span className="logo-ripple absolute inset-0 rounded-full border border-[var(--brand)]/40" />
          <span className="logo-ripple absolute inset-0 rounded-full border border-[var(--brand-2)]/25" style={{ animationDelay: "-1.5s" }} />
        </>
      )}
      <span className="logo-halo absolute -inset-2 -z-10 rounded-full opacity-90" />
      {animated &&
        ORBIT_PARTICLES.map((p, i) => (
          <div
            key={i}
            aria-hidden
            className="pointer-events-none absolute left-1/2 top-1/2 h-0 w-0"
            style={{
              animation: `logo-spin ${p.duration} linear infinite`,
              animationDelay: p.delay,
              animationDirection: p.reverse ? "reverse" : "normal",
            }}
          >
            <span
              className="absolute h-1.5 w-1.5 rounded-full bg-[var(--brand)]"
              style={{ boxShadow: "0 0 8px 2px var(--brand-glow)", transform: `translate(${p.radius}px, -50%)` }}
            />
          </div>
        ))}
      {animated ? (
        <motion.svg
          viewBox="0 0 108 108"
          aria-hidden="true"
          className={`drop-shadow-[0_4px_20px_var(--brand-glow)] ${s.mark}`}
          animate={{ scale: [1, 1.05, 1] }}
          transition={{ duration: 3, repeat: Infinity, ease: "easeInOut" }}
        >
          <OfficialMark />
        </motion.svg>
      ) : (
        <svg viewBox="0 0 108 108" aria-hidden="true" className={`drop-shadow-[0_4px_20px_var(--brand-glow)] ${s.mark}`}>
          <OfficialMark />
        </svg>
      )}
    </div>
  );
}

function OfficialMark() {
  return (
    <>
      <circle cx="54" cy="54" r="44" fill="#0A0A0C" />
      <path
        fill="#1ED760"
        d="M54,24 C40,24 29,35 29,49 C29,63 40,74 54,74 C63,74 71,68 74,60 L74,84 C74,89 78,93 83,93 C88,93 92,89 92,84 L92,30 C92,26 89,24 85,24 L54,24 Z M54,66 C44,66 37,58.5 37,49 C37,39.5 44,32 54,32 C64,32 71,39.5 71,49 C71,58.5 64,66 54,66 Z"
      />
    </>
  );
}
