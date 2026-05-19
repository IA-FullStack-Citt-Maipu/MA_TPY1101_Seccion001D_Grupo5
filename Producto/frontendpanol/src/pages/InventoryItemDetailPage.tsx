import { useEffect, useState } from "react";
import {
  AlertTriangle,
  ArrowLeft,
  Barcode,
  Bookmark,
  Box,
  Boxes,
  CircleArrowUp,
  CircleMinus,
  CircleCheck,
  ClipboardList,
  Edit3,
  Image as ImageIcon,
  Info,
  MapPin,
  PackageCheck,
  Package,
  Tag,
  TrendingUp,
  X,
} from "lucide-react";
import { InventoryLayout } from "../components/layout/InventoryLayout";
import { ImplementEditModal } from "../components/implements/ImplementEditModal";
import { getErrorMessage } from "../services/apiClient";
import { fetchImplementById } from "../services/implementService";
import { fetchLabelsPdfBlob, type LabelScope } from "../services/labelService";
import { fetchLocations } from "../services/locationService";
import { registerManualMovement, type ManualMovementType } from "../services/movementService";
import { addStockEntry, applyStockMovement, fetchImplementStock, updateIndividualState } from "../services/stockService";
import type { ImplementDetail } from "../types/implement";
import type { LocationOption } from "../types/location";
import type { IndividualItem, StockMovementType } from "../types/stock";
import { getUserRoleFromToken, type UserRole } from "../utils/auth";

const ITEM_TYPE_LABELS: Record<"fungible" | "no_fungible", string> = {
  fungible: "Fungible",
  no_fungible: "No fungible",
};

const MOVEMENT_OPTIONS: { value: StockMovementType; label: string }[] = [
  { value: "STOCK_OUT", label: "Salida de stock" },
  { value: "LOAN_DELIVERY", label: "Entrega de prestamo" },
  { value: "LOAN_RETURN", label: "Devolucion de prestamo" },
  { value: "DAMAGE_REPORT", label: "Reporte de dano" },
  { value: "MANUAL_ADJUSTMENT", label: "Ajuste manual" },
];

const INDIVIDUAL_STATUS_OPTIONS: Array<IndividualItem["status"]> = [
  "available",
  "loaned",
  "maintenance",
  "damaged",
];

const INDIVIDUAL_CONDITION_OPTIONS: Array<IndividualItem["condition"]> = [
  "good",
  "fair",
  "poor",
];

function statusLabel(status: string) {
  if (status === "available") return "Disponible";
  if (status === "loaned") return "Prestado";
  if (status === "maintenance") return "Mantención";
  if (status === "damaged") return "Dañado";
  return status;
}

function conditionLabel(condition: string) {
  if (condition === "good") return "Bueno";
  if (condition === "fair") return "Regular";
  if (condition === "poor") return "Deficiente";
  return condition;
}

function buildPseudoBarcodeBars(value: string) {
  const source = (value || "0").trim();
  const bars: Array<{ x: number; w: number }> = [];
  let x = 2;

  for (let i = 0; i < source.length; i += 1) {
    const code = source.charCodeAt(i);
    const widths = [1 + (code % 3), 1 + ((code >> 2) % 3), 1 + ((code >> 4) % 3)];
    widths.forEach((w, idx) => {
      bars.push({ x, w });
      x += w;
      if (idx < widths.length - 1) x += 1;
    });
    x += 2;
  }

  return { bars, width: Math.max(x + 2, 120) };
}

const STOCK_KPI_META = [
  {
    key: "available",
    label: "Disponibles",
    helper: "Unidades listas para uso",
    icon: Box,
    tone: "available",
  },
  {
    key: "loaned",
    label: "Prestados",
    helper: "Actualmente en préstamo",
    icon: PackageCheck,
    tone: "loaned",
  },
  {
    key: "reserved",
    label: "Reservados",
    helper: "Reservas activas",
    icon: Bookmark,
    tone: "warn",
  },
  {
    key: "damaged",
    label: "Dañados",
    helper: "Requieren revisión",
    icon: AlertTriangle,
    tone: "danger",
  },
  {
    key: "min_stock",
    label: "Stock mínimo",
    helper: "Nivel mínimo configurado",
    icon: TrendingUp,
    tone: "min",
  },
] as const;

type AdjustOperation = "increase" | "decrease";
type AdjustIncreaseMode = "batch" | "single";
type AdjustStep = 1 | 2;

export function InventoryItemDetailPage({
  implementUuid,
  embedded = false,
}: {
  implementUuid: string;
  embedded?: boolean;
}) {
  const [implement, setImplement] = useState<ImplementDetail | null>(null);
  const [stockDetail, setStockDetail] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [stockLoading, setStockLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [stockError, setStockError] = useState<string | null>(null);
  const [isEditing, setIsEditing] = useState(false);
  const [isStockAdjustModalOpen, setIsStockAdjustModalOpen] = useState(false);
  const [isMovementModalOpen, setIsMovementModalOpen] = useState(false);

  const [userRole, setUserRole] = useState<UserRole>("UNKNOWN");
  const isDocente = userRole === "DOCENTE";

  const [locations, setLocations] = useState<LocationOption[]>([]);
  const [locationsError, setLocationsError] = useState<string | null>(null);

  const [entryQuantity, setEntryQuantity] = useState("1");
  const [assetCodesRaw, setAssetCodesRaw] = useState("");
  const [adjustStep, setAdjustStep] = useState<AdjustStep>(1);
  const [adjustOperation, setAdjustOperation] = useState<AdjustOperation>("increase");
  const [adjustIncreaseMode, setAdjustIncreaseMode] = useState<AdjustIncreaseMode>("batch");
  const [adjustUseSameState, setAdjustUseSameState] = useState(true);
  const [adjustStatus, setAdjustStatus] = useState<IndividualItem["status"]>("available");
  const [adjustCondition, setAdjustCondition] = useState<IndividualItem["condition"]>("good");
  const [adjustNotes, setAdjustNotes] = useState("");
  const [adjustSingleAssetCode, setAdjustSingleAssetCode] = useState("");
  const [adjustReduceIndividualIds, setAdjustReduceIndividualIds] = useState<string[]>([]);
  const [adjustReduceQuantity, setAdjustReduceQuantity] = useState("1");

  const [movementType, setMovementType] = useState<StockMovementType>("STOCK_OUT");
  const [movementQuantity, setMovementQuantity] = useState("1");
  const [movementSelectedIndividualIds, setMovementSelectedIndividualIds] = useState<string[]>([]);
  const [movementNotes, setMovementNotes] = useState("");
  const [stockBusy, setStockBusy] = useState(false);
  const [editingIndividual, setEditingIndividual] = useState<IndividualItem | null>(null);
  const [individualStatus, setIndividualStatus] = useState<IndividualItem["status"]>("available");
  const [individualCondition, setIndividualCondition] = useState<IndividualItem["condition"]>("good");
  const [individualNotes, setIndividualNotes] = useState("");
  const [individualLocationId, setIndividualLocationId] = useState<string>("");
  const [individualActive, setIndividualActive] = useState(true);

  const [isLabelModalOpen, setIsLabelModalOpen] = useState(false);
  const [labelScope, setLabelScope] = useState<LabelScope>("GENERAL");
  const [labelIndividualUuid, setLabelIndividualUuid] = useState<string | null>(null);
  const [labelPreviewUrl, setLabelPreviewUrl] = useState<string | null>(null);
  const [labelBusy, setLabelBusy] = useState(false);

  useEffect(() => {
    const hasModalOpen =
      isEditing ||
      editingIndividual != null ||
      isLabelModalOpen ||
      isStockAdjustModalOpen ||
      isMovementModalOpen;
    if (!hasModalOpen) {
      return;
    }
    document.body.classList.add("modal-open");
    return () => {
      document.body.classList.remove("modal-open");
    };
  }, [isEditing, editingIndividual, isLabelModalOpen, isStockAdjustModalOpen, isMovementModalOpen]);

  useEffect(() => {
    setLoading(true);
    setError(null);
    setSuccess(null);
    setUserRole(getUserRoleFromToken());

    fetchImplementById(implementUuid)
      .then((detail) => {
        setImplement(detail);
      })
      .catch((requestError) => setError(getErrorMessage(requestError, "No se pudo cargar la ficha del producto.")))
      .finally(() => setLoading(false));
  }, [implementUuid]);

  useEffect(() => {
    setStockLoading(true);
    setStockError(null);
    fetchImplementStock(implementUuid)
      .then(setStockDetail)
      .catch((requestError) => setStockError(getErrorMessage(requestError, "No se pudo cargar el stock.")))
      .finally(() => setStockLoading(false));
  }, [implementUuid]);

  useEffect(() => {
    fetchLocations()
      .then((result) => setLocations(result))
      .catch((requestError) => setLocationsError(getErrorMessage(requestError, "No se pudo cargar las ubicaciones.")))
      .finally(() => undefined);
  }, []);

  async function refreshStock() {
    const detail = await fetchImplementStock(implementUuid);
    setStockDetail(detail);
  }

  async function refreshImplement() {
    const detail = await fetchImplementById(implementUuid);
    setImplement(detail);
  }

  async function refreshDetailData() {
    await Promise.all([refreshImplement(), refreshStock()]);
  }

  function resetAdjustStockState() {
    setAdjustStep(1);
    setAdjustOperation("increase");
    setAdjustIncreaseMode("batch");
    setAdjustUseSameState(true);
    setAdjustStatus("available");
    setAdjustCondition("good");
    setAdjustNotes("");
    setAdjustSingleAssetCode("");
    setAdjustReduceIndividualIds([]);
    setEntryQuantity("1");
    setAssetCodesRaw("");
    setAdjustReduceQuantity("1");
  }

  function openAdjustStockModal() {
    resetAdjustStockState();
    setIsStockAdjustModalOpen(true);
  }

  function closeAdjustStockModal() {
    setIsStockAdjustModalOpen(false);
    resetAdjustStockState();
  }

  function openMovementModal() {
    setMovementType("STOCK_OUT");
    setMovementQuantity("1");
    setMovementSelectedIndividualIds([]);
    setMovementNotes("");
    setIsMovementModalOpen(true);
  }

  function closeMovementModal() {
    setIsMovementModalOpen(false);
    setMovementSelectedIndividualIds([]);
    setMovementNotes("");
  }

  function goToAdjustStep2() {
    if (!implement) return;
    setStockError(null);
    setAdjustStep(2);
  }

  function buildBatchAssetCodes(quantity: number) {
    const parsed = assetCodesRaw
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);

    if (parsed.length === quantity) {
      return parsed;
    }

    const stamp = Date.now();
    return Array.from({ length: quantity }).map((_, idx) => `IMP-${implementUuid.slice(0, 8)}-${stamp}-${idx + 1}`);
  }

  async function registerInventoryTrace(action: ManualMovementType, quantity: number, notes: string) {
    try {
      await registerManualMovement(implementUuid, {
        action,
        quantity,
        notes: notes.trim() ? notes.trim() : null,
      });
    } catch {
      // El ajuste de stock es exitoso aunque falle el registro complementario de trazabilidad.
    }
  }

  async function handleAdjustStockSave() {
    if (!implement) return;
    setStockBusy(true);
    setStockError(null);

    try {
      if (adjustOperation === "increase") {
        if (implement.item_type === "no_fungible") {
          const quantity = adjustIncreaseMode === "single" ? 1 : Number(entryQuantity);
          if (!Number.isFinite(quantity) || quantity <= 0 || !Number.isInteger(quantity)) {
            setStockError("La cantidad debe ser un entero positivo.");
            setStockBusy(false);
            return;
          }

          const assetCodes =
            adjustIncreaseMode === "single"
              ? [adjustSingleAssetCode.trim()]
              : buildBatchAssetCodes(quantity);

          if (assetCodes.some((code) => code.length === 0)) {
            setStockError("Debes ingresar código para el implemento individual.");
            setStockBusy(false);
            return;
          }

          let nextDetail = await addStockEntry(implementUuid, {
            quantity,
            asset_codes: assetCodes,
          });

          if (adjustUseSameState) {
            const created = (nextDetail.individuals ?? []).filter((row: IndividualItem) =>
              assetCodes.includes(row.asset_code),
            );
            for (const row of created) {
              nextDetail = await updateIndividualState(implementUuid, row.uuid, {
                status: adjustStatus,
                condition: adjustCondition,
              });
            }
          }

          setStockDetail(nextDetail);
          await registerInventoryTrace("STOCK_IN", quantity, adjustNotes);
        } else {
          const quantity = Number(entryQuantity);
          if (!Number.isFinite(quantity) || quantity <= 0 || !Number.isInteger(quantity)) {
            setStockError("La cantidad debe ser un entero positivo.");
            setStockBusy(false);
            return;
          }
          setStockDetail(await addStockEntry(implementUuid, { quantity }));
          await registerInventoryTrace("STOCK_IN", quantity, adjustNotes);
        }
      } else {
        if (implement.item_type === "no_fungible") {
          if (adjustReduceIndividualIds.length === 0) {
            setStockError("Selecciona al menos un implemento individual para reducir.");
            setStockBusy(false);
            return;
          }
          setStockDetail(
            await applyStockMovement(implementUuid, {
              movement_type: "STOCK_OUT",
              individual_uuids: adjustReduceIndividualIds,
              condition: adjustCondition,
            }),
          );
          await registerInventoryTrace("STOCK_OUT", adjustReduceIndividualIds.length, adjustNotes);
        } else {
          const quantity = Number(adjustReduceQuantity);
          if (!Number.isFinite(quantity) || quantity <= 0 || !Number.isInteger(quantity)) {
            setStockError("La cantidad debe ser un entero positivo.");
            setStockBusy(false);
            return;
          }
          setStockDetail(
            await applyStockMovement(implementUuid, {
              movement_type: "STOCK_OUT",
              quantity,
            }),
          );
          await registerInventoryTrace("STOCK_OUT", quantity, adjustNotes);
        }
      }

      await refreshDetailData();
      setSuccess("Ajuste de stock registrado.");
      setIsStockAdjustModalOpen(false);
      resetAdjustStockState();
    } catch (requestError) {
      setStockError(getErrorMessage(requestError, "No se pudo guardar el ajuste de stock."));
    } finally {
      setStockBusy(false);
    }
  }

  async function handleMovementSave() {
    if (implement?.item_type === "no_fungible" && movementSelectedIndividualIds.length === 0) {
      setStockError("Debes seleccionar al menos una unidad individual.");
      return;
    }
    if (implement?.item_type !== "no_fungible") {
      const quantity = Number(movementQuantity);
      if (!Number.isFinite(quantity) || quantity <= 0 || !Number.isInteger(quantity)) {
        setStockError("La cantidad del movimiento debe ser un entero positivo.");
        return;
      }
    }
    setStockBusy(true);
    setStockError(null);
    try {
      const payload: any = { movement_type: movementType };
      let qtyForTrace = 0;
      if (implement?.item_type === "no_fungible") {
        payload.individual_uuids = movementSelectedIndividualIds;
        qtyForTrace = movementSelectedIndividualIds.length;
      } else {
        payload.quantity = Number(movementQuantity);
        qtyForTrace = Number(movementQuantity);
      }
      setStockDetail(await applyStockMovement(implementUuid, payload));
      await registerInventoryTrace(movementType as ManualMovementType, qtyForTrace, movementNotes);
      await refreshDetailData();
      setSuccess("Movimiento interno aplicado.");
      setIsMovementModalOpen(false);
      setMovementSelectedIndividualIds([]);
      setMovementNotes("");
    } catch (requestError) {
      setStockError(getErrorMessage(requestError, "No se pudo aplicar el movimiento interno."));
    } finally {
      setStockBusy(false);
    }
  }

  function toggleMovementIndividual(individualUuid: string) {
    setMovementSelectedIndividualIds((prev) =>
      prev.includes(individualUuid) ? prev.filter((id) => id !== individualUuid) : [...prev, individualUuid],
    );
  }

  async function handleMarkIndividualAvailable(individual: IndividualItem) {
    setStockBusy(true);
    try {
      setStockDetail(await updateIndividualState(implementUuid, individual.uuid, { status: "available", condition: "good" }));
      setSuccess(`Individual ${individual.asset_code} actualizado.`);
    } catch (requestError) {
      setStockError(getErrorMessage(requestError, "No se pudo actualizar el individual."));
    } finally {
      setStockBusy(false);
    }
  }

  function openIndividualEditor(individual: IndividualItem) {
    setEditingIndividual(individual);
    setIndividualStatus(individual.status);
    setIndividualCondition(individual.condition);
    setIndividualNotes(individual.notes ?? "");
    setIndividualLocationId(individual.current_location_uuid == null ? "" : String(individual.current_location_uuid));
    setIndividualActive(individual.active);
  }

  function closeIndividualEditor() {
    setEditingIndividual(null);
    setIndividualNotes("");
  }

  async function handleSaveIndividual() {
    if (!editingIndividual) return;
    setStockBusy(true);
    setStockError(null);
    try {
      const payload = {
        status: individualStatus,
        condition: individualCondition,
        notes: individualNotes.trim() ? individualNotes.trim() : null,
        current_location_uuid: individualLocationId.trim() ? individualLocationId : null,
        active: individualActive,
      };
      setStockDetail(await updateIndividualState(implementUuid, editingIndividual.uuid, payload));
      setSuccess(`Unidad ${editingIndividual.asset_code} actualizada.`);
      closeIndividualEditor();
    } catch (requestError) {
      setStockError(getErrorMessage(requestError, "No se pudo actualizar la unidad."));
    } finally {
      setStockBusy(false);
    }
  }

  function openLabelsModal(scope: LabelScope, individualUuid: string | null = null) {
    setLabelScope(scope);
    setLabelIndividualUuid(individualUuid);
    setLabelPreviewUrl(null);
    setIsLabelModalOpen(true);
    void handleGenerateLabelsPdf(scope, individualUuid);
  }

  async function handleGenerateLabelsPdf(scopeArg: LabelScope, individualUuidArg: string | null) {
    if (!implement) return;
    setLabelBusy(true);
    setStockError(null);
    try {
      const blob = await fetchLabelsPdfBlob(implement.uuid, scopeArg, 1, individualUuidArg == null ? undefined : individualUuidArg);
      if (labelPreviewUrl) {
        window.URL.revokeObjectURL(labelPreviewUrl);
      }
      setLabelPreviewUrl(window.URL.createObjectURL(blob));
    } catch (requestError) {
      setStockError(getErrorMessage(requestError, "No se pudo generar el PDF de etiquetas."));
    } finally {
      setLabelBusy(false);
    }
  }

  function closeLabelModal() {
    setIsLabelModalOpen(false);
    if (labelPreviewUrl) {
      window.URL.revokeObjectURL(labelPreviewUrl);
    }
    setLabelPreviewUrl(null);
    setLabelIndividualUuid(null);
  }

  function printLabelPreview() {
    if (!labelPreviewUrl) return;
    const popup = window.open(labelPreviewUrl, "_blank", "noopener,noreferrer");
    if (!popup) return;
    popup.focus();
    window.setTimeout(() => popup.print(), 450);
  }

  const content = (
    <>
      <section className="content-header detail-header">
        <div>
          <h1>Detalle de implemento</h1>
          <p>Ficha completa</p>
        </div>
        <div className="content-header__actions">
          <a className="button button--ghost" href="#/inventory/implementos"><ArrowLeft size={16} />Volver al listado</a>
          {!isDocente && (
            <>
              <button type="button" className="button" onClick={() => setIsEditing(true)} disabled={loading || !implement}><Edit3 size={16} />Editar implemento</button>
              <button
                type="button"
                className="button button--ghost"
                onClick={openAdjustStockModal}
                disabled={loading || !implement}
              >
                <Boxes size={16} />
                Ajustar stock
              </button>
              <button
                type="button"
                className="button button--ghost"
                onClick={openMovementModal}
                disabled={loading || !implement}
              >
                <CircleMinus size={16} />
                Movimiento
              </button>
            </>
          )}
        </div>
      </section>

      <section className="panel">
        {loading ? (
          <div className="detail-skeleton-layout">
            <div className="detail-skeleton-left">
              <div className="skeleton skeleton-hero" />
              <div className="detail-skeleton-kpis">
                {Array.from({ length: 5 }).map((_, idx) => (
                  <div key={`detail-kpi-skeleton-${idx}`} className="skeleton skeleton-kpi-card" />
                ))}
              </div>
            </div>
            <div className="detail-skeleton-right">
              <div className="skeleton skeleton-side-card" />
              <div className="skeleton skeleton-side-card" />
              <div className="skeleton skeleton-side-card" />
            </div>
          </div>
        ) : null}
        {error ? <div className="error-banner">{error}</div> : null}
        {success ? <div className="success-banner">{success}</div> : null}
        {stockError ? <div className="error-banner">{stockError}</div> : null}

        {implement && !loading ? (
          <div className="implement-detail-layout implement-detail-layout--inspired">
            <div className="implement-left-column">
              <article className="detail-card detail-card--hero">
                <div className="implement-hero-detail">
                  <div className="implement-hero">
                    <img src={implement.img_url ?? "https://placehold.co/420x260/e9edf5/4d6284?text=Sin+imagen"} alt={implement.name} className="implement-hero__img" />
                    <button type="button" className="button button--ghost button--sm" disabled><ImageIcon size={14} />Ver imagen</button>
                  </div>
                  <div>
                    <h2>{implement.name}</h2>
                    <div className="meta-rows">
                      <p><Tag size={16} /><strong>Categoría</strong><span>{implement.category?.name ?? "Sin categoría"}</span></p>
                      <p><CircleCheck size={16} /><strong>Estado</strong><span className={`badge ${implement.active ? "badge--active" : "badge--inactive"}`}>{implement.active ? "Activo" : "Inactivo"}</span></p>
                      <p><ClipboardList size={16} /><strong>Fecha de ingreso</strong><span>{implement.createdAt ? new Date(implement.createdAt).toLocaleDateString() : "-"}</span></p>
                      <p><Package size={16} /><strong>Tipo</strong><span>{implement.item_type ? ITEM_TYPE_LABELS[implement.item_type] : "Sin tipo"}</span></p>
                      <p><Barcode size={16} /><strong>Código de barras</strong><span>{implement.barcode ?? "No informado"}</span></p>
                      <p><MapPin size={16} /><strong>Ubicación principal</strong>
                        <span>{implement.display_location ?? implement.location?.name ?? "Sin ubicación"}</span>
                      </p>
                      <p className="meta-row-full"><Info size={16} /><strong>Observaciones</strong><span>{implement.observations ?? "Sin observaciones"}</span></p>
                    </div>
                    {locationsError ? <p className="field-error">{locationsError}</p> : null}
                  </div>
                </div>
              </article>

              {isDocente ? (
                <section className="stock-kpi-grid stock-kpi-grid--detail">
                  <article className="stock-kpi stock-kpi--available">
                    <span>Disponibilidad</span>
                    <strong style={{ fontSize: "1.5rem" }}>
                      {implement.stock?.available_display ?? (implement.stock?.available && implement.stock.available > 0 ? "Disponible" : "No disponible")}
                    </strong>
                    <small>Estado actual</small>
                  </article>
                </section>
              ) : (
                <>
                  {stockLoading ? <p className="field-hint">Cargando stock...</p> : null}
                  {stockDetail ? (
                    <section className="stock-kpi-grid stock-kpi-grid--detail inspired-kpis">
                      {STOCK_KPI_META.map((meta) => {
                        const Icon = meta.icon;
                        const value = stockDetail.stock[meta.key];
                        return (
                          <article key={meta.key} className={`stock-kpi stock-kpi--${meta.tone}`}>
                            <div className="stock-kpi__head">
                              <span className={`stock-kpi__icon stock-kpi__icon--${meta.tone}`}>
                                <Icon size={16} />
                              </span>
                              <span>{meta.label}</span>
                            </div>
                            <strong>{value}</strong>
                            <small>{meta.helper}</small>
                          </article>
                        );
                      })}
                    </section>
                  ) : null}
                </>
              )}

              {!isDocente && implement.item_type === "no_fungible" ? (
                <article className="detail-card units-card">
                  <h3>Unidades asociadas</h3>
                  <div className="table-wrapper">
                    <table className="category-table">
                      <thead>
                        <tr><th>ID individual</th><th>Código</th><th>Estado</th><th>Condición</th><th>Ubicación</th><th>Acciones</th></tr>
                      </thead>
                      <tbody>
                        {(stockDetail?.individuals ?? []).map((individual: IndividualItem) => (
                          <tr key={individual.uuid}>
                            <td>{individual.uuid}</td>
                            <td>{individual.asset_code}</td>
                            <td>
                              <span className={`badge ${individual.status === "available" ? "badge--active" : individual.status === "damaged" ? "badge--danger" : "badge--inactive"}`}>
                                {statusLabel(individual.status)}
                              </span>
                            </td>
                            <td><span className="badge badge--active">{conditionLabel(individual.condition)}</span></td>
                            <td>{individual.current_location_uuid ?? "-"}</td>
                            <td className="table-actions">
                              <button type="button" className="button button--table button--ghost" disabled={stockBusy} onClick={() => openIndividualEditor(individual)}><Edit3 size={14} />Editar</button>
                              <button type="button" className="button button--table button--ghost" disabled={stockBusy || individual.status === "available"} onClick={() => handleMarkIndividualAvailable(individual)}><CircleCheck size={14} />Marcar disponible</button>
                              <button type="button" className="button button--table button--ghost" disabled={stockBusy} onClick={() => openLabelsModal("INDIVIDUAL", individual.uuid)}><Barcode size={14} />Código de barras</button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </article>
              ) : null}
            </div>

            <aside className="implement-right-column">
              <article className="detail-card side-info-card">
                <h3 className="side-card-title"><span className="side-card-icon"><ClipboardList size={16} /></span>Descripción</h3>
                <p>{implement.description ?? "Sin descripción"}</p>
              </article>
              <article className="detail-card side-info-card">
                <h3 className="side-card-title"><span className="side-card-icon"><Info size={16} /></span>Información de inventario</h3>
                <p><strong>Observaciones:</strong> {implement.observations ?? "Sin observaciones"}</p>
                <p><strong>Total stock:</strong> {stockDetail?.stock?.total_stock ?? 0}</p>
                <p><strong>Última actualización:</strong> {implement.updatedAt ? new Date(implement.updatedAt).toLocaleString() : "-"}</p>
                {implement.item_type !== "no_fungible" && !isDocente ? (
                  <div style={{ marginTop: 10 }}>
                    <button type="button" className="button button--ghost button--sm" onClick={() => openLabelsModal("GENERAL")}><Barcode size={14} />Código de barras general</button>
                  </div>
                ) : null}
              </article>

              {!isDocente && (
                <article className="detail-card side-info-card">
                  <h3 className="side-card-title"><span className="side-card-icon"><CircleCheck size={16} /></span>Últimos movimientos</h3>
                  <div className="table-wrapper">
                    <table className="category-table category-table--compact movements-compact-table">
                      <thead><tr><th>Fecha</th><th>Acción</th><th>Cantidad</th><th>Usuario</th></tr></thead>
                      <tbody>
                        {!implement.recent_movements || implement.recent_movements.length === 0 ? (
                          <tr><td colSpan={4} style={{ textAlign: "center" }}>No hay movimientos recientes registrados</td></tr>
                        ) : (
                          implement.recent_movements.map((mov) => (
                            <tr key={mov.uuid}>
                              <td>{new Date(mov.timestamp).toLocaleString()}</td>
                              <td><span className="badge badge--inactive">{mov.action}</span></td>
                              <td>{mov.quantity}</td>
                              <td>{mov.performed_by}</td>
                            </tr>
                          ))
                        )}
                      </tbody>
                    </table>
                  </div>
                  <a className="side-link" href="#/inventory/moves">Ver todos los movimientos</a>
                </article>
              )}
            </aside>
          </div>
        ) : null}
      </section>

      <ImplementEditModal
        implementUuid={implementUuid}
        isOpen={isEditing}
        onClose={() => setIsEditing(false)}
        onSaved={async (updated) => {
          setImplement(updated);
          setSuccess("Producto actualizado correctamente.");
          await refreshStock();
        }}
      />

      {isStockAdjustModalOpen && implement ? (
        <div className="modal-overlay" role="dialog" aria-modal="true">
          <div className="modal modal--wide modal--wizard">
            <div className="wizard-progress">
              <span className="wizard-progress__fill" style={{ width: `${adjustStep === 1 ? 50 : 100}%` }} />
            </div>
            <div className="wizard-head">
              <button
                type="button"
                className="button button--ghost button--sm"
                onClick={() => {
                  if (adjustStep === 1) {
                    closeAdjustStockModal();
                    return;
                  }
                  setAdjustStep(1);
                }}
                disabled={stockBusy}
              >
                <ArrowLeft size={14} />
                {adjustStep === 1 ? "Cerrar" : "Volver"}
              </button>
              <button
                type="button"
                className="button button--ghost button--sm"
                onClick={closeAdjustStockModal}
                disabled={stockBusy}
              >
                <X size={14} />
              </button>
            </div>

            <h3><CircleArrowUp size={18} style={{ marginRight: 8, verticalAlign: "text-bottom" }} />Ajustar stock</h3>
            <p>{adjustStep === 1 ? "Selecciona operación y forma de ajuste." : "Completa la información para guardar el ajuste."}</p>

            {adjustStep === 1 ? (
              <>
                <label htmlFor="adjust-operation">Operación</label>
                <select
                  id="adjust-operation"
                  value={adjustOperation}
                  onChange={(event) => setAdjustOperation(event.target.value as AdjustOperation)}
                >
                  <option value="increase">Aumentar stock</option>
                  <option value="decrease">Reducir stock</option>
                </select>

                {implement.item_type === "no_fungible" && adjustOperation === "increase" ? (
                  <>
                    <label htmlFor="adjust-increase-mode">Tipo de ingreso</label>
                    <select
                      id="adjust-increase-mode"
                      value={adjustIncreaseMode}
                      onChange={(event) => setAdjustIncreaseMode(event.target.value as AdjustIncreaseMode)}
                    >
                      <option value="batch">Ingresar lote</option>
                      <option value="single">Ingresar solo un implemento individual</option>
                    </select>
                  </>
                ) : null}
              </>
            ) : (
              <>
                {adjustOperation === "increase" ? (
                  <>
                    <label htmlFor="entry-quantity">Cantidad a ingresar</label>
                    <input
                      id="entry-quantity"
                      type="number"
                      min={1}
                      value={adjustIncreaseMode === "single" ? "1" : entryQuantity}
                      onChange={(event) => setEntryQuantity(event.target.value)}
                      placeholder="Cantidad"
                      disabled={adjustIncreaseMode === "single"}
                    />

                    {implement.item_type === "no_fungible" && adjustIncreaseMode === "single" ? (
                      <>
                        <label htmlFor="single-asset-code">Código del individual</label>
                        <input
                          id="single-asset-code"
                          type="text"
                          value={adjustSingleAssetCode}
                          onChange={(event) => setAdjustSingleAssetCode(event.target.value)}
                          placeholder="Ej: IND-001"
                        />
                      </>
                    ) : null}

                    {implement.item_type === "no_fungible" && adjustIncreaseMode === "batch" ? (
                      <>
                        <label htmlFor="entry-asset-codes">Códigos individuales (opcional, uno por línea)</label>
                        <textarea
                          id="entry-asset-codes"
                          value={assetCodesRaw}
                          onChange={(event) => setAssetCodesRaw(event.target.value)}
                          placeholder="Si no completas todos, se autogenerarán."
                        />
                      </>
                    ) : null}

                    {implement.item_type === "no_fungible" ? (
                      <>
                        <label className="modal-checkbox">
                          <input
                            type="checkbox"
                            checked={adjustUseSameState}
                            onChange={(event) => setAdjustUseSameState(event.target.checked)}
                          />
                          Todos con el mismo estado/condición
                        </label>

                        {adjustUseSameState ? (
                          <>
                            <label htmlFor="adjust-status">Estado inicial</label>
                            <select
                              id="adjust-status"
                              value={adjustStatus}
                              onChange={(event) => setAdjustStatus(event.target.value as IndividualItem["status"])}
                            >
                              {INDIVIDUAL_STATUS_OPTIONS.map((status) => (
                                <option key={`adjust-status-${status}`} value={status}>
                                  {status === "available" ? "Nuevo (disponible)" : statusLabel(status)}
                                </option>
                              ))}
                            </select>

                            <label htmlFor="adjust-condition">Condición inicial</label>
                            <select
                              id="adjust-condition"
                              value={adjustCondition}
                              onChange={(event) => setAdjustCondition(event.target.value as IndividualItem["condition"])}
                            >
                              {INDIVIDUAL_CONDITION_OPTIONS.map((condition) => (
                                <option key={`adjust-cond-${condition}`} value={condition}>
                                  {conditionLabel(condition)}
                                </option>
                              ))}
                            </select>
                          </>
                        ) : null}
                      </>
                    ) : null}
                  </>
                ) : (
                  <>
                    {implement.item_type === "no_fungible" ? (
                      <>
                        <label>Selecciona implementos a retirar (borrado lógico)</label>
                        <div className="table-wrapper wizard-table-select">
                          <table className="category-table category-table--compact">
                            <thead>
                              <tr>
                                <th />
                                <th>ID</th>
                                <th>Código</th>
                                <th>Estado</th>
                              </tr>
                            </thead>
                            <tbody>
                              {(stockDetail?.individuals ?? []).map((individual: IndividualItem) => (
                                <tr key={`reduce-${individual.uuid}`}>
                                  <td>
                                    <input
                                      type="checkbox"
                                      checked={adjustReduceIndividualIds.includes(individual.uuid)}
                                      onChange={() =>
                                        setAdjustReduceIndividualIds((prev) =>
                                          prev.includes(individual.uuid)
                                            ? prev.filter((id) => id !== individual.uuid)
                                            : [...prev, individual.uuid],
                                        )
                                      }
                                    />
                                  </td>
                                  <td>{individual.uuid}</td>
                                  <td>{individual.asset_code}</td>
                                  <td>{statusLabel(individual.status)}</td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                        <p className="field-hint">Seleccionados: {adjustReduceIndividualIds.length}</p>
                        <label htmlFor="reduce-condition">Condición de retiro</label>
                        <select
                          id="reduce-condition"
                          value={adjustCondition}
                          onChange={(event) => setAdjustCondition(event.target.value as IndividualItem["condition"])}
                        >
                          {INDIVIDUAL_CONDITION_OPTIONS.map((condition) => (
                            <option key={`reduce-cond-${condition}`} value={condition}>
                              {conditionLabel(condition)}
                            </option>
                          ))}
                        </select>
                      </>
                    ) : (
                      <>
                        <label htmlFor="reduce-quantity">Cantidad a reducir</label>
                        <input
                          id="reduce-quantity"
                          type="number"
                          min={1}
                          value={adjustReduceQuantity}
                          onChange={(event) => setAdjustReduceQuantity(event.target.value)}
                          placeholder="Cantidad"
                        />
                      </>
                    )}
                  </>
                )}

                <label htmlFor="adjust-notes">Nota del ajuste (opcional)</label>
                <textarea
                  id="adjust-notes"
                  value={adjustNotes}
                  onChange={(event) => setAdjustNotes(event.target.value)}
                  placeholder="Describe motivo del lote o ajuste."
                />
              </>
            )}

            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={closeAdjustStockModal} disabled={stockBusy}>
                Cancelar
              </button>
              {adjustStep === 1 ? (
                <button type="button" className="button" onClick={goToAdjustStep2} disabled={stockBusy}>
                  Continuar
                </button>
              ) : (
                <button type="button" className="button" onClick={() => void handleAdjustStockSave()} disabled={stockBusy}>
                  {stockBusy ? "Guardando..." : "Guardar"}
                </button>
              )}
            </div>
          </div>
        </div>
      ) : null}

      {isMovementModalOpen && implement ? (
        <div className="modal-overlay" role="dialog" aria-modal="true">
          <div className="modal modal--wide modal--wizard">
            <div className="wizard-progress">
              <span className="wizard-progress__fill" style={{ width: "100%" }} />
            </div>
            <div className="wizard-head">
              <button type="button" className="button button--ghost button--sm" onClick={closeMovementModal} disabled={stockBusy}>
                <ArrowLeft size={14} />
                Cerrar
              </button>
              <button type="button" className="button button--ghost button--sm" onClick={closeMovementModal} disabled={stockBusy}>
                <X size={14} />
              </button>
            </div>
            <h3><CircleMinus size={18} style={{ marginRight: 8, verticalAlign: "text-bottom" }} />Movimiento interno</h3>
            <p>Gestiona disponibles, prestados, reservados o dañados con el stock actual.</p>

            <label htmlFor="movement-type">Tipo de movimiento</label>
            <select
              id="movement-type"
              value={movementType}
              onChange={(event) => setMovementType(event.target.value as StockMovementType)}
            >
              {MOVEMENT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>

            {implement.item_type === "no_fungible" ? (
              <>
                <label>Selecciona unidades</label>
                <div className="table-wrapper" style={{ maxHeight: 260, border: "1px solid var(--line)", borderRadius: 10 }}>
                  <table className="category-table category-table--compact">
                    <thead>
                      <tr>
                        <th />
                        <th>ID</th>
                        <th>Código</th>
                        <th>Estado</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(stockDetail?.individuals ?? []).map((individual: IndividualItem) => (
                        <tr key={`move-${individual.uuid}`}>
                          <td>
                            <input
                              type="checkbox"
                              checked={movementSelectedIndividualIds.includes(individual.uuid)}
                              onChange={() => toggleMovementIndividual(individual.uuid)}
                            />
                          </td>
                          <td>{individual.uuid}</td>
                          <td>{individual.asset_code}</td>
                          <td>{statusLabel(individual.status)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <p className="field-hint">Seleccionadas: {movementSelectedIndividualIds.length}</p>
              </>
            ) : (
              <>
                <label htmlFor="movement-quantity">Cantidad</label>
                <input
                  id="movement-quantity"
                  type="number"
                  min={1}
                  value={movementQuantity}
                  onChange={(event) => setMovementQuantity(event.target.value)}
                  placeholder="Cantidad"
                />
              </>
            )}

            <label htmlFor="movement-notes">Nota del movimiento (opcional)</label>
            <textarea
              id="movement-notes"
              value={movementNotes}
              onChange={(event) => setMovementNotes(event.target.value)}
              placeholder="Motivo o contexto del movimiento interno."
            />

            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={closeMovementModal} disabled={stockBusy}>
                Cancelar
              </button>
              <button type="button" className="button" onClick={() => void handleMovementSave()} disabled={stockBusy}>
                {stockBusy ? "Guardando..." : "Guardar"}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {editingIndividual ? (
        <div className="modal-overlay" role="dialog" aria-modal="true">
          <div className="modal">
            <h3>Editar unidad {editingIndividual.asset_code}</h3>
            <p>Ajusta estado, condición, ubicación y vigencia operativa de la unidad.</p>

            <label htmlFor="individual-status">Estado</label>
            <select id="individual-status" value={individualStatus} onChange={(e) => setIndividualStatus(e.target.value as IndividualItem["status"])}>
              {INDIVIDUAL_STATUS_OPTIONS.map((status) => (<option key={status} value={status}>{statusLabel(status)}</option>))}
            </select>

            <label htmlFor="individual-condition">Condición</label>
            <select id="individual-condition" value={individualCondition} onChange={(e) => setIndividualCondition(e.target.value as IndividualItem["condition"])}>
              {INDIVIDUAL_CONDITION_OPTIONS.map((condition) => (<option key={condition} value={condition}>{conditionLabel(condition)}</option>))}
            </select>

            <label htmlFor="individual-location">Ubicación actual</label>
            <select id="individual-location" value={individualLocationId} onChange={(e) => setIndividualLocationId(e.target.value)}>
              <option value="">Sin ubicación</option>
              {locations.map((location) => (<option key={location.uuid} value={location.uuid}>{location.name}</option>))}
            </select>

            <label htmlFor="individual-notes">Notas</label>
            <textarea
              id="individual-notes"
              value={individualNotes}
              onChange={(e) => setIndividualNotes(e.target.value)}
              placeholder="Observaciones de la unidad"
            />

            <label htmlFor="individual-active">Activo</label>
            <select id="individual-active" value={individualActive ? "true" : "false"} onChange={(e) => setIndividualActive(e.target.value === "true")}>
              <option value="true">Sí</option>
              <option value="false">No</option>
            </select>

            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={closeIndividualEditor} disabled={stockBusy}>Cancelar</button>
              <button type="button" className="button" onClick={() => void handleSaveIndividual()} disabled={stockBusy}>{stockBusy ? "Guardando..." : "Guardar cambios"}</button>
            </div>
          </div>
        </div>
      ) : null}

      {isLabelModalOpen && implement ? (
        <div className="modal-overlay" role="dialog" aria-modal="true">
          <div className="modal" style={{ width: "min(100%, 820px)" }}>
            <h3>Vista previa código de barras</h3>
            <p>{labelScope === "INDIVIDUAL" ? `Unidad individual #${labelIndividualUuid ?? "-"}` : "Código general del implemento"}</p>
            <div className="barcode-preview-surface">
              {labelBusy ? (
                <div className="field-hint" style={{ padding: 12 }}>Cargando vista previa...</div>
              ) : (
                <div className="barcode-preview-label">
                  <strong>{implement.name}</strong>
                  <small>{labelScope === "INDIVIDUAL" ? "Unidad individual" : "Código general"}</small>
                  <svg
                    viewBox={`0 0 ${buildPseudoBarcodeBars(
                      labelScope === "INDIVIDUAL"
                        ? ((stockDetail?.individuals ?? []).find((row: IndividualItem) => row.uuid === labelIndividualUuid)?.asset_code ??
                          `IND-${labelIndividualUuid ?? ""}`)
                        : (implement.barcode ?? `IMP-${implement.uuid}`)
                    ).width} 36`}
                    preserveAspectRatio="none"
                    className="barcode-preview-svg"
                  >
                    {buildPseudoBarcodeBars(
                      labelScope === "INDIVIDUAL"
                        ? ((stockDetail?.individuals ?? []).find((row: IndividualItem) => row.uuid === labelIndividualUuid)?.asset_code ??
                          `IND-${labelIndividualUuid ?? ""}`)
                        : (implement.barcode ?? `IMP-${implement.uuid}`)
                    ).bars.map((bar, idx) => (
                      <rect key={`bar-${idx}`} x={bar.x} y={2} width={bar.w} height={30} fill="#1b1b1b" />
                    ))}
                  </svg>
                  <span className="barcode-preview-code">
                    {labelScope === "INDIVIDUAL"
                      ? ((stockDetail?.individuals ?? []).find((row: IndividualItem) => row.uuid === labelIndividualUuid)?.asset_code ??
                        `IND-${labelIndividualUuid ?? ""}`)
                      : (implement.barcode ?? `IMP-${implement.uuid}`)}
                  </span>
                </div>
              )}
            </div>

            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={closeLabelModal} disabled={labelBusy}>Cancelar</button>
              <button type="button" className="button" onClick={printLabelPreview} disabled={labelBusy || !labelPreviewUrl}>Imprimir</button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );

  if (embedded) {
    return content;
  }

  return <InventoryLayout activeSection="items">{content}</InventoryLayout>;
}
