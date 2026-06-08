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
import type { IndividualItem, StockDetail } from "../types/stock";

interface DeliveryIndividualOption {
  assetCode: string;
  selectable: boolean;
  visualStatus: "available" | "reserved" | "unavailable";
}

interface DeliveryItemState {
  implementUuid: string;
  implementName: string;
  requested: number;
  delivered: number;
  outstanding: number;
  requiredQuantity: number;
  selected: boolean;
  itemType: "consumable" | "reusable" | "individual" | "unknown";
  quantity: number;
  maxQuantity: number;
  maxSelectableQuantity: number;
  availableStock: number | null;
  availableAssetCodes: string[];
  suggestedAssetCodes: string[];
  individualOptions: DeliveryIndividualOption[];
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

function mapItemType(detail: StockDetail): DeliveryItemState["itemType"] {
  if (detail.item_type === "consumable") {
    return "consumable";
  }
  if (detail.item_type === "reusable") {
    return "reusable";
  }
  if (detail.item_type === "individual") {
    return "individual";
  }
  return "unknown";
}

function isIndividualSelectable(individual: IndividualItem): boolean {
  return Boolean(individual.active) && individual.status === "available";
}

function buildIndividualOptions(individuals: IndividualItem[], suggestedAssetCodes: string[]): DeliveryIndividualOption[] {
  const suggestedSet = new Set(suggestedAssetCodes);

  return individuals
    .filter((individual) => Boolean(individual.active))
    .map((individual) => {
      const assetCode = individual.asset_code?.trim();
      if (!assetCode) {
        return null;
      }

      const selectable = isIndividualSelectable(individual);
      let visualStatus: DeliveryIndividualOption["visualStatus"];
      if (!selectable) {
        visualStatus = "unavailable";
      } else if (suggestedSet.has(assetCode)) {
        visualStatus = "reserved";
      } else {
        visualStatus = "available";
      }

      return {
        assetCode,
        selectable,
        visualStatus,
      };
    })
    .filter((option): option is DeliveryIndividualOption => option !== null);
}

function isItemReadyForDelivery(item: DeliveryItemState): boolean {
  if (!item.selected || item.stockError || item.requiredQuantity <= 0) {
    return false;
  }

  if (item.itemType === "individual") {
    return item.selectedAssetCodes.length === item.requiredQuantity;
  }

  return item.quantity === item.requiredQuantity;
}

export function LoanDeliveryPage({ loanUuid, embedded = false }: { loanUuid: string; embedded?: boolean }) {
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [loan, setLoan] = useState<LoanSummary | null>(null);
  const [items, setItems] = useState<DeliveryItemState[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showModificationModal, setShowModificationModal] = useState(false);
  const [modificationTargetName, setModificationTargetName] = useState<string | null>(null);

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
            requiredQuantity: outstanding,
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
              requiredQuantity: row.requiredQuantity,
              selected: row.outstanding > 0,
              itemType: "unknown",
              quantity: row.outstanding > 0 ? 1 : 0,
              maxQuantity: Math.max(0, fallbackMax),
              maxSelectableQuantity: Math.max(0, fallbackMax),
              availableStock: null,
              availableAssetCodes: [],
              suggestedAssetCodes: [],
              individualOptions: [],
              selectedAssetCodes: [],
              stockError: errorMessage,
            };
          }

          const itemType = mapItemType(stock);
          if (itemType === "individual") {
            const availableAssetCodes = (stock.individuals ?? [])
              .filter(isIndividualSelectable)
              .map((individual) => individual.asset_code)
              .filter((value): value is string => typeof value === "string" && value.trim().length > 0);
            const maxQuantity = Math.min(row.outstanding, availableAssetCodes.length);
            const suggestedAssetCodes = availableAssetCodes.slice(0, maxQuantity);
            const selectedAssetCodes = suggestedAssetCodes;
            const individualOptions = buildIndividualOptions(stock.individuals ?? [], suggestedAssetCodes);
            return {
              implementUuid: row.implementUuid,
              implementName: row.implementName,
              requested: row.requested,
              delivered: row.delivered,
              outstanding: row.outstanding,
              requiredQuantity: row.requiredQuantity,
              selected: selectedAssetCodes.length > 0,
              itemType,
              quantity: selectedAssetCodes.length,
              maxQuantity,
              maxSelectableQuantity: availableAssetCodes.length,
              availableStock: availableAssetCodes.length,
              availableAssetCodes,
              suggestedAssetCodes,
              individualOptions,
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
              requiredQuantity: row.requiredQuantity,
              selected: quantity > 0,
              itemType,
              quantity,
            maxQuantity,
            maxSelectableQuantity: availableStock == null ? maxQuantity : Math.max(0, availableStock),
            availableStock,
            availableAssetCodes: [],
            suggestedAssetCodes: [],
            individualOptions: [],
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

  const deliveryAllowed = useMemo(
    () => (loan ? loan.status === "approved" || loan.status === "prepared" : false),
    [loan],
  );

  const selectedItems = useMemo(() => {
    return items.filter((item) => {
      if (!item.selected) {
        return false;
      }
      if (item.itemType === "individual") {
        return item.selectedAssetCodes.length > 0;
      }
      return item.quantity > 0;
    });
  }, [items]);

  const summaryCount = useMemo(
    () => selectedItems.reduce((total, item) => total + (item.itemType === "individual" ? item.selectedAssetCodes.length : item.quantity), 0),
    [selectedItems],
  );

  const itemsReadyForDelivery = useMemo(
    () => items.filter((item) => item.outstanding > 0 && isItemReadyForDelivery(item)),
    [items],
  );

  const itemsBlockingDelivery = useMemo(
    () => items.filter((item) => item.outstanding > 0 && !isItemReadyForDelivery(item)),
    [items],
  );

  const canSubmit = useMemo(() => {
    if (!loan || submitting || !deliveryAllowed) {
      return false;
    }
    if (loan.status !== "approved" && loan.status !== "prepared") {
      return false;
    }
    return items.length > 0 && itemsBlockingDelivery.length === 0 && itemsReadyForDelivery.length === items.filter((item) => item.outstanding > 0).length;
  }, [deliveryAllowed, items, itemsBlockingDelivery.length, itemsReadyForDelivery.length, loan, submitting]);

  function goBack() {
    if (window.history.length > 1) {
      window.history.back();
      return;
    }
    window.location.hash = `#/inventory/prestamos/${loanUuid}`;
  }

  function openModificationModal(itemName: string) {
    setModificationTargetName(itemName);
    setShowModificationModal(true);
  }

  function closeModificationModal() {
    setShowModificationModal(false);
    setModificationTargetName(null);
  }

  function goToLoanDetail() {
    closeModificationModal();
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
        if (item.itemType === "individual") {
          const selectedAssetCodes = item.selectedAssetCodes.length > 0
            ? item.selectedAssetCodes
            : item.suggestedAssetCodes.slice(0, item.maxQuantity);
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
    let modalItemName: string | null = null;
    setItems((previous) =>
      previous.map((item) => {
        if (item.implementUuid !== implementUuid || item.itemType === "individual") {
          return item;
        }
        if (delta > 0 && item.quantity >= item.maxQuantity && item.maxSelectableQuantity > item.maxQuantity) {
          modalItemName = item.implementName;
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
    if (modalItemName) {
      openModificationModal(modalItemName);
    }
  }

  function toggleAssetCode(implementUuid: string, assetCode: string) {
    let modalItemName: string | null = null;
    setItems((previous) =>
      previous.map((item) => {
        if (item.implementUuid !== implementUuid || item.itemType !== "individual") {
          return item;
        }

        const alreadySelected = item.selectedAssetCodes.includes(assetCode);
        let selectedAssetCodes: string[];
        if (alreadySelected) {
          selectedAssetCodes = item.selectedAssetCodes.filter((code) => code !== assetCode);
        } else {
          if (item.selectedAssetCodes.length >= item.maxQuantity) {
            if (item.maxSelectableQuantity > item.maxQuantity) {
              modalItemName = item.implementName;
            }
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
    if (modalItemName) {
      openModificationModal(modalItemName);
    }
  }

  async function submitDelivery() {
    setError(null);
    if (!loan) {
      setError("No hay datos de prestamo para registrar la entrega.");
      return;
    }
    if (loan.status !== "approved" && loan.status !== "prepared") {
      setError("Solo puedes entregar solicitudes en estado aprobado o preparado.");
      return;
    }

    const payloadItems: DeliverLoanPayload["items"] = [];
    for (const item of selectedItems) {
      if (item.itemType === "individual") {
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

    const invalidItem = items.find((item) => item.outstanding > 0 && !isItemReadyForDelivery(item));
    if (invalidItem) {
      setError(`La entrega de ${invalidItem.implementName} debe coincidir con la cantidad solicitada y solo puede usar unidades disponibles.`);
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
              <div className={`loan-delivery-window ${deliveryAllowed ? "is-open" : "is-closed"}`}>
                <Clock3 size={16} />
                {deliveryAllowed ? "Entrega habilitada para este estado" : "Estado no habilitado para entrega"}
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
                        {!item.stockError && item.outstanding > 0 && !isItemReadyForDelivery(item) ? (
                          <small className="field-error">
                            {item.availableStock != null && item.availableStock < item.requiredQuantity
                              ? `No hay disponibilidad suficiente para completar la entrega (${item.availableStock}/${item.requiredQuantity}).`
                              : `La entrega debe incluir exactamente ${item.requiredQuantity} unidad(es) disponibles.`}
                          </small>
                        ) : null}
                      </div>
                    </div>

                    {item.itemType === "individual" ? (
                      <div className="loan-delivery-individuals">
                        <p>Selecciona unidades individuales ({item.selectedAssetCodes.length}/{item.requiredQuantity})</p>
                        <div className="loan-delivery-individuals__legend">
                          <span><i className="legend-dot legend-dot--available" />Disponible</span>
                          <span><i className="legend-dot legend-dot--reserved" />Sugerido para esta entrega</span>
                          <span><i className="legend-dot legend-dot--damaged" />No disponible</span>
                        </div>
                        {item.individualOptions.length === 0 ? (
                          <small className="field-error">No hay unidades individuales disponibles para este implemento.</small>
                        ) : (
                          <div className="loan-delivery-individuals__list">
                            {item.individualOptions.map((option) => {
                              const assetCode = option.assetCode;
                              const checked = item.selectedAssetCodes.includes(assetCode);
                              return (
                                <label
                                  key={`${item.implementUuid}-${assetCode}`}
                                  className={`loan-delivery-individuals__option loan-delivery-individuals__option--${option.visualStatus}${checked ? " is-selected" : ""}`}
                                >
                                  <input
                                    type="checkbox"
                                    checked={checked}
                                    disabled={!item.selected || !option.selectable}
                                    onChange={() => toggleAssetCode(item.implementUuid, assetCode)}
                                  />
                                  <span className="loan-delivery-individuals__option-copy">
                                    <strong>{assetCode}</strong>
                                    <small>
                                      {option.visualStatus === "reserved"
                                        ? "Sugerido para entrega"
                                        : option.visualStatus === "unavailable"
                                          ? "No disponible"
                                          : "Disponible"}
                                    </small>
                                  </span>
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
                            disabled={!item.selected || item.quantity >= item.maxSelectableQuantity}
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
              {itemsBlockingDelivery.length > 0 ? (
                <p className="field-error">
                  Debes dejar listas todas las lineas del prestamo antes de confirmar la entrega.
                </p>
              ) : null}

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

      {showModificationModal ? (
        <div className="modal-overlay" role="dialog" aria-modal="true">
          <div className="modal">
            <h3>Estas modificando la solicitud del prestamo</h3>
            <p>
              Intentaste entregar mas unidades de <strong>{modificationTargetName ?? "este implemento"}</strong> de las que
              fueron solicitadas. Para agregar mas implementos o unidades, primero revisa la solicitud del prestamo.
            </p>
            <div className="modal-actions">
              <button type="button" className="loan-secondary-btn" onClick={closeModificationModal}>
                Cancelar
              </button>
              <button type="button" className="loan-primary-btn" onClick={goToLoanDetail}>
                Revisar solicitud
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );

  if (embedded) {
    return content;
  }

  return content;
}

