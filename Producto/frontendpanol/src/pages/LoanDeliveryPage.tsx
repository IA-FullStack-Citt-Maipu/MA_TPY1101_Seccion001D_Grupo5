import {
  ArrowLeft,
  CheckSquare,
  Clock3,
  Minus,
  PackageSearch,
  Plus,
  SendHorizontal,
  Square,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import { fetchLoanByUuid, deliverLoan } from "../services/loanService";
import { fetchImplementStock } from "../services/stockService";
import type { DeliverLoanPayload, LoanSummary } from "../types/loan";
import type { StockDetail } from "../types/stock";
import { getDeliveryWindowOpenAt } from "../utils/loanSchedule";

interface DeliveryItemState {
  implementUuid: string;
  implementName: string;
  requested: number;
  delivered: number;
  outstanding: number;
  selected: boolean;
  itemType: "fungible" | "no_fungible" | "unknown";
  quantity: number;
  maxQuantity: number;
  availableStock: number | null;
  availableAssetCodes: string[];
  selectedAssetCodes: string[];
  stockError: string | null;
}

function parseDate(value: string): Date | null {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return null;
  }
  return date;
}

function formatDateTime(value: string): string {
  const date = parseDate(value);
  if (!date) {
    return value;
  }
  return new Intl.DateTimeFormat("es-CL", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

function formatTime(value: Date | null): string {
  if (!value) {
    return "--:--";
  }
  return new Intl.DateTimeFormat("es-CL", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(value);
}

function mapItemType(detail: StockDetail): DeliveryItemState["itemType"] {
  if (detail.item_type === "fungible") {
    return "fungible";
  }
  if (detail.item_type === "no_fungible") {
    return "no_fungible";
  }
  return "unknown";
}

export function LoanDeliveryPage({ loanUuid, embedded = false }: { loanUuid: string; embedded?: boolean }) {
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [loan, setLoan] = useState<LoanSummary | null>(null);
  const [items, setItems] = useState<DeliveryItemState[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function bootstrap() {
      setLoading(true);
      setError(null);

      try {
        const detail = await fetchLoanByUuid(loanUuid);
        if (cancelled) {
          return;
        }
        if (!detail) {
          setError("No se encontro la solicitud indicada.");
          setLoading(false);
          return;
        }

        setLoan(detail);

        const baseRows = detail.items.map((item) => {
          const outstanding = Math.max(0, item.requested_quantity - item.delivered_quantity);
          return {
            implementUuid: item.implement_uuid,
            implementName: item.implement_name,
            requested: item.requested_quantity,
            delivered: item.delivered_quantity,
            outstanding,
          };
        });

        const stockResults = await Promise.all(
          baseRows.map(async (row) => {
            try {
              const stock = await fetchImplementStock(row.implementUuid);
              return { row, stock, errorMessage: null as string | null };
            } catch (requestError) {
              return {
                row,
                stock: null as StockDetail | null,
                errorMessage: getErrorMessage(requestError, "No se pudo consultar stock del implemento."),
              };
            }
          }),
        );

        if (cancelled) {
          return;
        }

        const hydratedRows: DeliveryItemState[] = stockResults.map(({ row, stock, errorMessage }) => {
          if (!stock) {
            const fallbackMax = row.outstanding;
            return {
              implementUuid: row.implementUuid,
              implementName: row.implementName,
              requested: row.requested,
              delivered: row.delivered,
              outstanding: row.outstanding,
              selected: row.outstanding > 0,
              itemType: "unknown",
              quantity: row.outstanding > 0 ? 1 : 0,
              maxQuantity: Math.max(0, fallbackMax),
              availableStock: null,
              availableAssetCodes: [],
              selectedAssetCodes: [],
              stockError: errorMessage,
            };
          }

          const itemType = mapItemType(stock);
          if (itemType === "no_fungible") {
            const availableAssetCodes = (stock.individuals ?? [])
              .filter((individual) => individual.active && individual.status === "available")
              .map((individual) => individual.asset_code)
              .filter((value): value is string => typeof value === "string" && value.trim().length > 0);
            const maxQuantity = Math.min(row.outstanding, availableAssetCodes.length);
            const selectedAssetCodes = availableAssetCodes.slice(0, maxQuantity);
            return {
              implementUuid: row.implementUuid,
              implementName: row.implementName,
              requested: row.requested,
              delivered: row.delivered,
              outstanding: row.outstanding,
              selected: selectedAssetCodes.length > 0,
              itemType,
              quantity: selectedAssetCodes.length,
              maxQuantity,
              availableStock: availableAssetCodes.length,
              availableAssetCodes,
              selectedAssetCodes,
              stockError: null,
            };
          }

          const availableStock = stock.stock?.available ?? null;
          const maxQuantity = availableStock == null
            ? row.outstanding
            : Math.max(0, Math.min(row.outstanding, availableStock));
          const quantity = maxQuantity > 0 ? maxQuantity : 0;
          return {
            implementUuid: row.implementUuid,
            implementName: row.implementName,
            requested: row.requested,
            delivered: row.delivered,
            outstanding: row.outstanding,
            selected: quantity > 0,
            itemType,
            quantity,
            maxQuantity,
            availableStock,
            availableAssetCodes: [],
            selectedAssetCodes: [],
            stockError: null,
          };
        });

        setItems(hydratedRows);
      } catch (requestError) {
        if (cancelled) {
          return;
        }
        setError(getErrorMessage(requestError, "No se pudo cargar la solicitud para entrega."));
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [loanUuid]);

  const deliveryWindowOpensAt = useMemo(
    () => (loan ? getDeliveryWindowOpenAt(loan.scheduled_at) : null),
    [loan],
  );
  const isWindowOpen = useMemo(
    () => (deliveryWindowOpensAt ? Date.now() >= deliveryWindowOpensAt.getTime() : false),
    [deliveryWindowOpensAt],
  );

  const selectedItems = useMemo(() => {
    return items.filter((item) => {
      if (!item.selected) {
        return false;
      }
      if (item.itemType === "no_fungible") {
        return item.selectedAssetCodes.length > 0;
      }
      return item.quantity > 0;
    });
  }, [items]);

  const summaryCount = useMemo(
    () => selectedItems.reduce((total, item) => total + (item.itemType === "no_fungible" ? item.selectedAssetCodes.length : item.quantity), 0),
    [selectedItems],
  );

  const canSubmit = useMemo(() => {
    if (!loan || submitting || !isWindowOpen) {
      return false;
    }
    if (loan.status !== "approved") {
      return false;
    }
    return selectedItems.length > 0;
  }, [loan, submitting, isWindowOpen, selectedItems.length]);

  function goBack() {
    if (window.history.length > 1) {
      window.history.back();
      return;
    }
    window.location.hash = `#/inventory/prestamos/${loanUuid}`;
  }

  function toggleItemSelected(implementUuid: string) {
    setItems((previous) =>
      previous.map((item) => {
        if (item.implementUuid !== implementUuid) {
          return item;
        }
        const nextSelected = !item.selected;
        if (!nextSelected) {
          return { ...item, selected: false };
        }
        if (item.itemType === "no_fungible") {
          const selectedAssetCodes = item.selectedAssetCodes.length > 0
            ? item.selectedAssetCodes
            : item.availableAssetCodes.slice(0, Math.min(item.outstanding, item.maxQuantity));
          return {
            ...item,
            selected: selectedAssetCodes.length > 0,
            selectedAssetCodes,
            quantity: selectedAssetCodes.length,
          };
        }
        const quantity = item.quantity > 0 ? item.quantity : Math.min(1, item.maxQuantity);
        return { ...item, selected: quantity > 0, quantity };
      }),
    );
  }

  function adjustFungibleQuantity(implementUuid: string, delta: number) {
    setItems((previous) =>
      previous.map((item) => {
        if (item.implementUuid !== implementUuid || item.itemType === "no_fungible") {
          return item;
        }
        const next = Math.max(0, Math.min(item.maxQuantity, item.quantity + delta));
        return {
          ...item,
          quantity: next,
          selected: next > 0,
        };
      }),
    );
  }

  function toggleAssetCode(implementUuid: string, assetCode: string) {
    setItems((previous) =>
      previous.map((item) => {
        if (item.implementUuid !== implementUuid || item.itemType !== "no_fungible") {
          return item;
        }

        const alreadySelected = item.selectedAssetCodes.includes(assetCode);
        let selectedAssetCodes: string[];
        if (alreadySelected) {
          selectedAssetCodes = item.selectedAssetCodes.filter((code) => code !== assetCode);
        } else {
          if (item.selectedAssetCodes.length >= item.maxQuantity) {
            return item;
          }
          selectedAssetCodes = [...item.selectedAssetCodes, assetCode];
        }

        return {
          ...item,
          selectedAssetCodes,
          quantity: selectedAssetCodes.length,
          selected: selectedAssetCodes.length > 0,
        };
      }),
    );
  }

  async function submitDelivery() {
    setError(null);
    if (!loan) {
      setError("No hay datos de prestamo para registrar la entrega.");
      return;
    }
    if (loan.status !== "approved") {
      setError("Solo puedes entregar solicitudes en estado aprobado.");
      return;
    }
    if (!isWindowOpen) {
      setError("La entrega se habilita 10 minutos antes de la hora programada.");
      return;
    }

    const payloadItems: DeliverLoanPayload["items"] = [];
    for (const item of selectedItems) {
      if (item.itemType === "no_fungible") {
        if (item.selectedAssetCodes.length <= 0) {
          continue;
        }
        payloadItems.push({
          implement_uuid: item.implementUuid,
          quantity: item.selectedAssetCodes.length,
          asset_codes: item.selectedAssetCodes,
        });
      } else {
        if (item.quantity <= 0) {
          continue;
        }
        payloadItems.push({
          implement_uuid: item.implementUuid,
          quantity: item.quantity,
        });
      }
    }

    if (payloadItems.length === 0) {
      setError("Debes seleccionar al menos un implemento para entregar.");
      return;
    }

    setSubmitting(true);
    try {
      const updated = await deliverLoan(loan.uuid, { items: payloadItems });
      window.location.hash = `#/inventory/prestamos/${updated.uuid}`;
    } catch (requestError) {
      const apiError = getApiErrorPayload(requestError);
      setError(apiError?.message ?? getErrorMessage(requestError, "No se pudo registrar la entrega."));
    } finally {
      setSubmitting(false);
    }
  }

  const content = (
    <div className="loan-delivery-page">
      <section className="loan-delivery-header">
        <button type="button" className="loan-delivery-back-btn" onClick={goBack}>
          <ArrowLeft size={16} />
          Volver
        </button>
        <p className="loan-delivery-header__eyebrow">Prestamos / Entrega</p>
        <h1>Confirmar entrega</h1>
        <p>Marca lo que se entregara ahora. Por defecto los implementos quedan seleccionados.</p>
      </section>

      {error ? <div className="error-banner">{error}</div> : null}

      {loading ? (
        <section className="panel">
          <p className="text-muted">Cargando solicitud para entrega...</p>
        </section>
      ) : null}

      {!loading && loan ? (
        <section className="loan-delivery-layout">
          <article className="loan-delivery-main">
            <header className="loan-delivery-main__meta">
              <div>
                <p><strong>Prestamo:</strong> {loan.uuid}</p>
                <p><strong>Sala:</strong> {loan.room?.name ?? "Sin sala"}</p>
                <p><strong>Programado:</strong> {formatDateTime(loan.scheduled_at)}</p>
              </div>
              <div className={`loan-delivery-window ${isWindowOpen ? "is-open" : "is-closed"}`}>
                <Clock3 size={16} />
                {isWindowOpen
                  ? "Ventana de entrega habilitada"
                  : `Se habilita a las ${formatTime(deliveryWindowOpensAt)}`}
              </div>
            </header>

            <div className="loan-delivery-items">
              {items.length === 0 ? (
                <p className="text-muted">No hay items para entregar.</p>
              ) : (
                items.map((item) => (
                  <article key={item.implementUuid} className="loan-delivery-item-card">
                    <div className="loan-delivery-item-card__top">
                      <button
                        type="button"
                        className="loan-delivery-check-btn"
                        onClick={() => toggleItemSelected(item.implementUuid)}
                        aria-label={item.selected ? "Quitar item de entrega" : "Marcar item para entrega"}
                      >
                        {item.selected ? <CheckSquare size={18} /> : <Square size={18} />}
                      </button>
                      <div className="loan-delivery-item-card__copy">
                        <strong>{item.implementName}</strong>
                        <p>
                          Solicitado: {item.requested} | Ya entregado: {item.delivered} | Pendiente: {item.outstanding}
                        </p>
                        {item.availableStock != null ? (
                          <small>Disponibles actuales: {item.availableStock}</small>
                        ) : null}
                        {item.stockError ? <small className="field-error">{item.stockError}</small> : null}
                      </div>
                    </div>

                    {item.itemType === "no_fungible" ? (
                      <div className="loan-delivery-individuals">
                        <p>Selecciona unidades individuales ({item.selectedAssetCodes.length}/{item.maxQuantity})</p>
                        {item.availableAssetCodes.length === 0 ? (
                          <small className="field-error">No hay unidades individuales disponibles para este implemento.</small>
                        ) : (
                          <div className="loan-delivery-individuals__list">
                            {item.availableAssetCodes.map((assetCode) => {
                              const checked = item.selectedAssetCodes.includes(assetCode);
                              const limitReached = !checked && item.selectedAssetCodes.length >= item.maxQuantity;
                              return (
                                <label key={`${item.implementUuid}-${assetCode}`} className="loan-delivery-individuals__option">
                                  <input
                                    type="checkbox"
                                    checked={checked}
                                    disabled={!item.selected || limitReached}
                                    onChange={() => toggleAssetCode(item.implementUuid, assetCode)}
                                  />
                                  <span>{assetCode}</span>
                                </label>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    ) : (
                      <div className="loan-delivery-stepper">
                        <span>Cantidad a entregar</span>
                        <div className="loan-stepper">
                          <button
                            type="button"
                            onClick={() => adjustFungibleQuantity(item.implementUuid, -1)}
                            disabled={!item.selected || item.quantity <= 0}
                          >
                            <Minus size={14} />
                          </button>
                          <span className="loan-stepper__value">{item.quantity}</span>
                          <button
                            type="button"
                            onClick={() => adjustFungibleQuantity(item.implementUuid, 1)}
                            disabled={!item.selected || item.quantity >= item.maxQuantity}
                          >
                            <Plus size={14} />
                          </button>
                        </div>
                      </div>
                    )}
                  </article>
                ))
              )}
            </div>
          </article>

          <aside className="loan-delivery-side">
            <div className="loan-delivery-side__card">
              <h3>
                <PackageSearch size={18} />
                Resumen de entrega
              </h3>
              <p>Items marcados: {selectedItems.length}</p>
              <p>Unidades a entregar: {summaryCount}</p>

              <div className="loan-delivery-side__actions">
                <button type="button" className="loan-secondary-btn" onClick={goBack} disabled={submitting}>
                  Cancelar
                </button>
                <button
                  type="button"
                  className="loan-primary-btn"
                  onClick={() => void submitDelivery()}
                  disabled={!canSubmit}
                >
                  <SendHorizontal size={15} />
                  {submitting ? "Entregando..." : "Entregar"}
                </button>
              </div>
            </div>
          </aside>
        </section>
      ) : null}
    </div>
  );

  if (embedded) {
    return content;
  }

  return content;
}

