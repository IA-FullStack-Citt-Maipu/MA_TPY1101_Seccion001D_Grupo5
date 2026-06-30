import type { SelectHTMLAttributes } from "react";

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
}

export function Select({ label, id, className = "", children, ...props }: SelectProps) {
  return (
    <>
      {label ? <label htmlFor={id}>{label}</label> : null}
      <select id={id} className={`ui-select ${className}`.trim()} {...props}>
        {children}
      </select>
    </>
  );
}

