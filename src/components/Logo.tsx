"use client";

import { motion } from "framer-motion";
import { Music2 } from "lucide-react";

/**
 * Marque MUZZIK — même traitement que l'AnimatedLogo Movviz (orbite de
 * particules, anneaux ripple, halo, respiration), transposé en vert
 * (identité MUZZIK) avec une note de musique au lieu du clap de cinéma.
 * Réutilisation directe du mécanisme, pas du contenu (§1.1 du plan).
 */

const ORBIT_PARTICLES = [
  { radius: 24, duration: "4.5s", delay: "0s", reverse: false },
  { radius: 27, duration: "6.5s", delay: "-2s", reverse: true },
  { radius: 20, duration: "8s", delay: "-4.5s", reverse: false },
];

const SIZES = {
  sm: { outer: "h-9 w-9", inner: "h-9 w-9", icon: 16 },
  md: { outer: "h-12 w-12", inner: "h-10 w-10", icon: 19 },
  lg: { outer: "h-16 w-16", inner: "h-13 w-13", icon: 26 },
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
        <motion.div
          className={`brand-gradient flex items-center justify-center rounded-2xl shadow-[0_4px_20px_-4px_var(--brand-glow)] ${s.inner}`}
          animate={{ scale: [1, 1.05, 1] }}
          transition={{ duration: 3, repeat: Infinity, ease: "easeInOut" }}
        >
          <Music2 size={s.icon} className="text-black" strokeWidth={2.5} />
        </motion.div>
      ) : (
        <div className={`brand-gradient flex items-center justify-center rounded-2xl shadow-[0_4px_20px_-4px_var(--brand-glow)] ${s.inner}`}>
          <Music2 size={s.icon} className="text-black" strokeWidth={2.5} />
        </div>
      )}
    </div>
  );
}
