import {
  ArrowLeft,
  CheckSquare,
  Clock3,
  Minus,
  PackageSearch,
  Plus,
  Search,
  SendHorizontal,
  Square,
} from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import { fetchImplements } from "../services/implementService";
import { fetchLoanByUuid, deliverLoan } from "../services/loanService";
import { fetchImplementStock } from "../services/stockService";
import type { DeliverLoanPayload, LoanSummary } from "../types/loan";
import type { ImplementSummary } from "../types/implement";
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
  approved: number;
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
  isAdditional: boolean;
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
  if (!item.selected || item.stockError) {
    return false;
  }
  if (!item.isAdditional && item.requiredQuantity <= 0) {
    return false;
  }

  if (item.itemType === "individual") {
    return item.selectedAssetCodes.length > 0 && item.selectedAssetCodes.length <= item.maxQuantity;
  }

  return item.quantity > 0 && item.quantity <= item.maxQuantity;
}

export function LoanDeliveryPage({ loanUuid, embedded = false }: { loanUuid: string; embedded?: boolean }) {
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [loan, setLoan] = useState<LoanSummary | null>(null);
  const [items, setItems] = useState<DeliveryItemState[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showModificationModal, setShowModificationModal] = useState(false);
  const [showDeliveryVariationModal, setShowDeliveryVariationModal] = useState(false);
  const [modificationTargetName, setModificationTargetName] = useState<string | null>(null);
  const [deliveryNotes, setDeliveryNotes] = useState("");
  const [additionalOptions, setAdditionalOptions] = useState<ImplementSummary[]>([]);
  const [additionalSearch, setAdditionalSearch] = useState("");
  const [selectedAdditionalUuid, setSelectedAdditionalUuid] = useState("");
  const [addingImplement, setAddingImplement] = useState(false);

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
          const approved = item.reserved_quantity;
          const outstanding = Math.max(0, approved - item.delivered_quantity);
          return {
            implementUuid: item.implement_uuid,
            implementName: item.implement_name,
            requested: item.requested_quantity,
            approved,
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
              approved: row.approved,
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
              isAdditional: false,
            };
          }

          const itemType = mapItemType(stock);
          if (itemType === "individual") {
            const availableAssetCodes = (stock.individuals ?? [])
              .filter(isIndividualSelectable)
              .map((individual) => individual.asset_code)
              .filter((value): value is string => typeof value === "string" && value.trim().length > 0);
            const maxQuantity = availableAssetCodes.length;
            const suggestedQuantity = Math.min(row.outstanding, maxQuantity);
            const suggestedAssetCodes = availableAssetCodes.slice(0, suggestedQuantity);
            const selectedAssetCodes = suggestedAssetCodes;
            const individualOptions = buildIndividualOptions(stock.individuals ?? [], suggestedAssetCodes);
            return {
              implementUuid: row.implementUuid,
              implementName: row.implementName,
              requested: row.requested,
              approved: row.approved,
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
              isAdditional: false,
            };
          }

          const availableStock = stock.stock?.available ?? null;
          const maxQuantity = availableStock == null
            ? row.outstanding
            : Math.max(0, availableStock);
          const quantity = row.outstanding > 0 ? Math.min(row.outstanding, maxQuantity) : 0;
          return {
            implementUuid: row.implementUuid,
            implementName: row.implementName,
            requested: row.requested,
            approved: row.approved,
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
            isAdditional: false,
          };
        });

        setItems(hydratedRows);
        const excludedUuids = new Set(detail.items.map((item) => item.implement_uuid));
        try {
          const implementOptions = await fetchImplements({
            stockStatus: "available",
          });
          if (!cancelled) {
            setAdditionalOptions(
              implementOptions.filter((implement) =>
                !excludedUuids.has(implement.uuid) &&
                implement.active !== false &&
                (implement.stock?.available ?? 0) > 0,
              ),
            );
          }
        } catch {
          if (!cancelled) {
            setAdditionalOptions([]);
          }
        }
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

  const additionalCandidates = useMemo(() => {
    const selectedUuids = new Set(items.map((item) => item.implementUuid));
    const normalizedSearch = additionalSearch.trim().toLowerCase();
    return additionalOptions
      .filter((implement) => !selectedUuids.has(implement.uuid))
      .filter((implement) => {
        if (!normalizedSearch) {
          return true;
        }
        return [
          implement.name,
          implement.category?.name ?? "",
          implement.location?.name ?? "",
          implement.barcode ?? "",
        ].join(" ").toLowerCase().includes(normalizedSearch);
      })
      .slice(0, 25);
  }, [additionalOptions, additionalSearch, items]);

  const summaryCount = useMemo(
    () => selectedItems.reduce((total, item) => total + (item.itemType === "individual" ? item.selectedAssetCodes.length : item.quantity), 0),
    [selectedItems],
  );

  const itemsReadyForDelivery = useMemo(
    () => selectedItems.filter(isItemReadyForDelivery),
    [selectedItems],
  );

  const itemsBlockingDelivery = useMemo(
    () => selectedItems.filter((item) => !isItemReadyForDelivery(item)),
    [selectedItems],
  );

  const hasDeliveryVariation = useMemo(
    () => items.some((item) => {
      if (item.isAdditional) {
        const selectedQuantity = item.itemType === "individual" ? item.selectedAssetCodes.length : item.quantity;
        return item.selected && selectedQuantity > 0;
      }
      if (item.outstanding <= 0) {
        return false;
      }
      if (!item.selected) {
        return true;
      }
      const selectedQuantity = item.itemType === "individual" ? item.selectedAssetCodes.length : item.quantity;
      return selectedQuantity !== item.requiredQuantity;
    }),
    [items],
  );

  const canSubmit = useMemo(() => {
    if (!loan || submitting || !deliveryAllowed) {
      return false;
    }
    if (loan.status !== "approved" && loan.status !== "prepared") {
      return false;
    }
    return selectedItems.length > 0 && itemsBlockingDelivery.length === 0 && itemsReadyForDelivery.length === selectedItems.length;
  }, [deliveryAllowed, itemsBlockingDelivery.length, itemsReadyForDelivery.length, loan, selectedItems.length, submitting]);

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
    setItems((previous) =>
      previous.map((item) => {
        if (item.implementUuid !== implementUuid || item.itemType === "individual") {
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

  async function addAdditionalImplement() {
    const selected = additionalOptions.find((implement) => implement.uuid === selectedAdditionalUuid);
    if (!selected) {
      return;
    }

    setAddingImplement(true);
    setError(null);
    try {
      const stock = await fetchImplementStock(selected.uuid);
      const itemType = mapItemType(stock);
      let nextItem: DeliveryItemState;

      if (itemType === "individual") {
        const availableAssetCodes = (stock.individuals ?? [])
          .filter(isIndividualSelectable)
          .map((individual) => individual.asset_code)
          .filter((value): value is string => typeof value === "string" && value.trim().length > 0);
        const selectedAssetCodes = availableAssetCodes.slice(0, 1);
        nextItem = {
          implementUuid: selected.uuid,
          implementName: selected.name,
          requested: 0,
          approved: 0,
          delivered: 0,
          outstanding: 0,
          requiredQuantity: 0,
          selected: selectedAssetCodes.length > 0,
          itemType,
          quantity: selectedAssetCodes.length,
          maxQuantity: availableAssetCodes.length,
          maxSelectableQuantity: availableAssetCodes.length,
          availableStock: availableAssetCodes.length,
          availableAssetCodes,
          suggestedAssetCodes: selectedAssetCodes,
          individualOptions: buildIndividualOptions(stock.individuals ?? [], selectedAssetCodes),
          selectedAssetCodes,
          stockError: availableAssetCodes.length > 0 ? null : "No hay unidades individuales disponibles.",
          isAdditional: true,
        };
      } else {
        const availableStock = stock.stock?.available ?? selected.stock?.available ?? 0;
        nextItem = {
          implementUuid: selected.uuid,
          implementName: selected.name,
          requested: 0,
          approved: 0,
          delivered: 0,
          outstanding: 0,
          requiredQuantity: 0,
          selected: availableStock > 0,
          itemType,
          quantity: availableStock > 0 ? 1 : 0,
          maxQuantity: Math.max(0, availableStock),
          maxSelectableQuantity: Math.max(0, availableStock),
          availableStock,
          availableAssetCodes: [],
          suggestedAssetCodes: [],
          individualOptions: [],
          selectedAssetCodes: [],
          stockError: availableStock > 0 ? null : "No hay stock disponible para agregar este implemento.",
          isAdditional: true,
        };
      }

      setItems((previous) => [...previous, nextItem]);
      setSelectedAdditionalUuid("");
      setAdditionalSearch("");
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No se pudo agregar el implemento adicional."));
    } finally {
      setAddingImplement(false);
    }
  }

  function closeDeliveryVariationModal() {
    setShowDeliveryVariationModal(false);
  }

  async function submitDelivery(confirmedVariation = false) {
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

    const invalidItem = selectedItems.find((item) => !isItemReadyForDelivery(item));
    if (invalidItem) {
      setError(`La entrega de ${invalidItem.implementName} debe tener al menos una unidad disponible y no superar lo pendiente.`);
      return;
    }

    if (hasDeliveryVariation && !confirmedVariation) {
      setShowDeliveryVariationModal(true);
      return;
    }

    setShowDeliveryVariationModal(false);
    setSubmitting(true);
    try {
      const updated = await deliverLoan(loan.uuid, {
        items: payloadItems,
        notes: deliveryNotes.trim() || null,
      });
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
                        {item.isAdditional ? (
                          <p>Adicional no solicitado | Se agregara a esta entrega</p>
                        ) : (
                          <p>
                            Solicitado: {item.requested} | Aprobado: {item.approved} | Ya entregado: {item.delivered} | Pendiente: {item.outstanding}
                          </p>
                        )}
                        {item.availableStock != null ? (
                          <small>Disponibles actuales: {item.availableStock}</small>
                        ) : null}
                        {item.stockError ? <small className="field-error">{item.stockError}</small> : null}
                        {!item.stockError && item.selected && item.outstanding > 0 && !isItemReadyForDelivery(item) ? (
                          <small className="field-error">Selecciona al menos una unidad disponible para incluirla en esta entrega.</small>
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

            <div className="loan-delivery-additional">
              <div className="loan-delivery-additional__header">
                <div>
                  <strong>Agregar implemento adicional</strong>
                  <p>Usa esto cuando durante la entrega se solicita algo que no estaba en el formulario.</p>
                </div>
              </div>
              <label className="loan-delivery-additional__search">
                <Search size={15} />
                <input
                  type="search"
                  value={additionalSearch}
                  onChange={(event) => setAdditionalSearch(event.target.value)}
                  placeholder="Buscar implemento disponible..."
                />
              </label>
              <div className="loan-delivery-additional__controls">
                <select
                  value={selectedAdditionalUuid}
                  onChange={(event) => setSelectedAdditionalUuid(event.target.value)}
                  disabled={additionalCandidates.length === 0 || addingImplement}
                >
                  <option value="">Selecciona un implemento</option>
                  {additionalCandidates.map((implement) => (
                    <option key={implement.uuid} value={implement.uuid}>
                      {implement.name} ({implement.stock?.available ?? 0} disp.)
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className="loan-secondary-btn"
                  onClick={() => void addAdditionalImplement()}
                  disabled={!selectedAdditionalUuid || addingImplement}
                >
                  <Plus size={15} />
                  {addingImplement ? "Agregando..." : "Agregar"}
                </button>
              </div>
              {additionalCandidates.length === 0 ? (
                <small className="field-hint">No hay implementos disponibles para agregar con el filtro actual.</small>
              ) : null}
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
              {hasDeliveryVariation ? (
                <p className="field-hint">
                  Hay diferencias entre lo solicitado y lo que se entregara. Se pedira confirmacion antes de guardar.
                </p>
              ) : null}
              {itemsBlockingDelivery.length > 0 ? (
                <p className="field-error">
                  Revisa los items marcados antes de confirmar la entrega.
                </p>
              ) : null}

              <label htmlFor="loan-delivery-notes">Notas de entrega (opcional)</label>
              <textarea
                id="loan-delivery-notes"
                rows={4}
                value={deliveryNotes}
                maxLength={1000}
                onChange={(event) => setDeliveryNotes(event.target.value)}
                placeholder="Ej: Se entregan menos unidades por disponibilidad real."
              />

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

      {showDeliveryVariationModal ? (
        <div className="modal-overlay" role="dialog" aria-modal="true">
          <div className="modal">
            <h3>Estas modificando la solicitud</h3>
            <p>
              Se esta registrando una entrega distinta a lo solicitado o pendiente. Seguro que quieres seguir?
            </p>
            <div className="modal-actions">
              <button type="button" className="loan-secondary-btn" onClick={closeDeliveryVariationModal}>
                Volver a revisar
              </button>
              <button
                type="button"
                className="loan-primary-btn"
                onClick={() => void submitDelivery(true)}
                disabled={submitting}
              >
                Si, continuar
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

