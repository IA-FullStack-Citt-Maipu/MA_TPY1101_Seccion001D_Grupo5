export function NotFoundPage() {
  return (
    <div className="not-found-template">
      <div className="not-found-template__card">
        <span>404</span>
        <h1>Página no encontrada</h1>
        <p>La ruta que intentaste abrir no existe o fue movida.</p>
        <a href="#/inventory/categories" className="button">
          Volver al panel
        </a>
      </div>
    </div>
  );
}

