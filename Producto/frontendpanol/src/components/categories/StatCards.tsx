interface StatCardsProps {
  total: number;
  active: number;
  inactive: number;
  implementCount: number;
}

export function StatCards({ total, active, inactive, implementCount }: StatCardsProps) {
  const cards = [
    { label: "Categorías registradas", value: total, tone: "blue" },
    { label: "Categorías activas", value: active, tone: "green" },
    { label: "Categorías inactivas", value: inactive, tone: "orange" },
    { label: "Implementos asociados", value: implementCount, tone: "cyan" },
  ];

  return (
    <div className="stat-grid">
      {cards.map((card) => (
        <article key={card.label} className={`stat-card stat-card--${card.tone}`}>
          <p>{card.label}</p>
          <strong>{card.value}</strong>
        </article>
      ))}
    </div>
  );
}

