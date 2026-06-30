import type { ReactNode } from "react";

export function Table({ children }: { children: ReactNode }) {
  return (
    <div className="table-wrapper">
      <table className="category-table">{children}</table>
    </div>
  );
}

