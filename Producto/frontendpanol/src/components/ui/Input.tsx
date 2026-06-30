import type { InputHTMLAttributes } from "react";

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
}

export function Input({ label, id, className = "", ...props }: InputProps) {
  return (
    <>
      {label ? <label htmlFor={id}>{label}</label> : null}
      <input id={id} className={`ui-input ${className}`.trim()} {...props} />
    </>
  );
}

