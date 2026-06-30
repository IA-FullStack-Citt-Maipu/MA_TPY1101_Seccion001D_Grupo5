import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "ghost" | "warn" | "danger";
type Size = "md" | "sm";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  children: ReactNode;
}

export function Button({ variant = "primary", size = "md", className = "", children, ...props }: ButtonProps) {
  const variantClass =
    variant === "ghost"
      ? "button--ghost"
      : variant === "warn"
        ? "button--warn"
        : variant === "danger"
          ? "button--danger"
          : "";
  const sizeClass = size === "sm" ? "button--sm" : "";
  return (
    <button type="button" className={`button ${variantClass} ${sizeClass} ${className}`.trim()} {...props}>
      {children}
    </button>
  );
}

