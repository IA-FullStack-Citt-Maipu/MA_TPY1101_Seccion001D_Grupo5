import { useEffect, useMemo, useState } from "react";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { Table } from "../components/ui/Table";
import { getErrorMessage } from "../services/apiClient";
import { fetchLoanByUuid } from "../services/loanService";
import {
  clearLastCreatedLoan,
  loadLastCreatedLoan,
} from "../services/loanSessionService";
import type { LoanSummary } from "../types/loan";

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
}

function normalizeStatus(status: string): string {
  const map: Record<string, string> = {
    pending: "Pendiente",
    approved: "Aprobado",
    rejected: "Rechazado",
    delivered: "Entregado",
    cancelled: "Cancelado",
    completed: "Completado",
    expired: "Expirado",
  };
  return map[status] ?? status;
}

function statusTone(status: string): "active" | "inactive" | "warn" {
  if (status === "approved" || status === "completed") {
    return "active";
  }
  if (status === "rejected" || status === "cancelled" || status === "expired") {
    return "warn";
  }
  return "inactive";
}

export function LoanDetailPage({
  loanUuid,
  embedded = false,
}: {
  loanUuid: string;
  embedded?: boolean;
}) {
  const [loan, setLoan] = useState<LoanSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showCreatedBanner, setShowCreatedBanner] = useState(false);

  useEffect(() => {
    const cached = loadLastCreatedLoan(loanUuid);
    if (cached) {
      setLoan(cached);
      setShowCreatedBanner(true);
    }
  }, [loanUuid]);

  useEffect(() => {
    let cancelled = false;

    async function loadDetail() {
      setLoading(true);
      setError(null);
      try {
        const resolved = await fetchLoanByUuid(loanUuid);
        if (cancelled) {
          return;
        }
        if (!resolved) {
          setError("No se encontro la solicitud indicada.");
          return;
        }
        setLoan(resolved);
      } catch (requestError) {
        if (cancelled) {
          return;
        }
        setError(
          getErrorMessage(requestError, "No se pudo cargar el detalle de la solicitud."),
        );
      } finally {
        if (cancelled) {
          return;
        }
        setLoading(false);
      }
    }

    void loadDetail();

    return () => {
      cancelled = true;
    };
  }, [loanUuid]);

  useEffect(() => {
    if (showCreatedBanner) {
      clearLastCreatedLoan();
    }
  }, [showCreatedBanner]);

  const subjectLabel = useMemo(() => {
    if (!loan?.subject) {
      return "Sin asignatura";
    }
    return loan.subject.name;
  }, [loan]);

  const content = (
    <>
      <section className="content-header">
        <div>
          <h1>Detalle de solicitud</h1>
          <p>Consulta el estado y resumen de la solicitud enviada.</p>
        </div>
        <div className="content-header__actions">
          <Button
            variant="ghost"
            onClick={() => {
              window.location.hash = "#/inventory/prestamos/nuevo";
            }}
          >
            Nueva solicitud
          </Button>
        </div>
      </section>

      {showCreatedBanner && loan ? (
        <div className="success-banner">
          Solicitud creada correctamente para {formatDateTime(loan.scheduled_at)} en sala{" "}
          {loan.room?.name ?? "sin sala"} con {loan.items.length} item(s).
        </div>
      ) : null}

      {error ? <div className="error-banner">{error}</div> : null}

      {loading && !loan ? (
        <section className="panel">
          <p className="text-muted">Cargando detalle de la solicitud...</p>
        </section>
      ) : null}

      {!loading && !loan ? (
        <section className="panel">
          <p className="text-muted">
            No se pudo recuperar informacion para esta solicitud.
          </p>
        </section>
      ) : null}

      {loan ? (
        <>
          <section className="loan-grid">
            <Card>
              <h2>Resumen</h2>
              <p>
                <strong>UUID:</strong> {loan.uuid}
              </p>
              <p>
                <strong>Estado:</strong>{" "}
                <Badge tone={statusTone(loan.status)}>
                  {normalizeStatus(loan.status)}
                </Badge>
              </p>
              <p>
                <strong>Creada:</strong> {formatDateTime(loan.created_at)}
              </p>
              <p>
                <strong>Programada:</strong> {formatDateTime(loan.scheduled_at)}
              </p>
            </Card>

            <Card>
              <h2>Confirmacion de envio</h2>
              <p>
                <strong>Sala:</strong> {loan.room?.name ?? "Sin sala"}
              </p>
              <p>
                <strong>Asignatura:</strong> {subjectLabel}
              </p>
              <p>
                <strong>Solicitante:</strong> {loan.requester_uuid}
              </p>
            </Card>
          </section>

          <section className="panel">
            <h2>Implementos solicitados</h2>
            <Table>
              <thead>
                <tr>
                  <th>Implemento</th>
                  <th>Solicitado</th>
                  <th>Reservado</th>
                  <th>Entregado</th>
                </tr>
              </thead>
              <tbody>
                {loan.items.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="table-hint">
                      La solicitud no contiene items.
                    </td>
                  </tr>
                ) : (
                  loan.items.map((item) => (
                    <tr key={item.implement_uuid}>
                      <td>{item.implement_name}</td>
                      <td>{item.requested_quantity}</td>
                      <td>{item.reserved_quantity}</td>
                      <td>{item.delivered_quantity}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </Table>
          </section>
        </>
      ) : null}
    </>
  );

  if (embedded) {
    return content;
  }

  return content;
}
