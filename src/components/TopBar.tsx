import { Logo } from "./Logo";

/** Barre de marque partagée (langage sidebar Movviz, transposé en top bar mobile). */
export function TopBar({ title }: { title: string }) {
  return (
    <div className="float-in mb-2 flex items-center gap-3">
      <Logo size="sm" />
      <div>
        <div className="text-[10px] font-bold uppercase tracking-[0.2em] text-[var(--ink-dim)]">MUZZIK</div>
        <h1 className="text-3xl font-extrabold tracking-tight">{title}</h1>
      </div>
    </div>
  );
}
