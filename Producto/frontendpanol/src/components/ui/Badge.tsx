import type { ReactNode } from "react";

type Tone = "active" | "inactive" | "warn";

export function Badge({ tone = "inactive", children }: { tone?: Tone; children: ReactNode }) {
  const toneClass = tone === "active" ? "badge--active" : tone === "warn" ? "badge--warn" : "badge--inactive";
  return <span className={`badge ${toneClass}`}>{children}</span>;
}

