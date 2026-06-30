type HelpTooltipProps = {
  text: string;
  ariaLabel?: string;
  align?: "start" | "end";
  variant?: "floating" | "inline";
  className?: string;
};

export function HelpTooltip({
  text,
  ariaLabel = "Ayuda contextual",
  align = "start",
  variant = "floating",
  className = "",
}: HelpTooltipProps) {
  return (
    <span
      className={`help-tooltip help-tooltip--${align} help-tooltip--${variant}${className ? ` ${className}` : ""}`}
      tabIndex={0}
    >
      <span className="field-help-chip" aria-label={ariaLabel}>
        ?
      </span>
      <span className="help-tooltip__bubble" role="tooltip">
        {text}
      </span>
    </span>
  );
}
