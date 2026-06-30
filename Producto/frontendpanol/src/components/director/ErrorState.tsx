export function ErrorState({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="error-banner">
      <p>{message}</p>
      <button type="button" className="button button--ghost button--table" onClick={onRetry}>Reintentar</button>
    </div>
  );
}
