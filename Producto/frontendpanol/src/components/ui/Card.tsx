import type { ReactNode } from "react";

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <article className={`detail-card ${className}`.trim()}>{children}</article>;
}

