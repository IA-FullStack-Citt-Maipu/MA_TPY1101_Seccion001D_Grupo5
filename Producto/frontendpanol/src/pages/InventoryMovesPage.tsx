import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Check, ChevronDown, ChevronLeft, ChevronRight, Plus, Search, X } from "lucide-react";
import { InventoryLayout } from "../components/layout/InventoryLayout";
import { PresencePollingModal } from "../components/ui/PresencePollingModal";
import { useInactivityPollingGate } from "../hooks/useInactivityPollingGate";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import { fetchImplements } from "../services/implementService";
import {
  fetchInventoryMovements,
  registerManualMovement,
  type ManualMovementType,
} from "../services/movementService";
import { fetchImplementStock } from "../services/stockService";
import type { ImplementSummary, InventoryMovementDetail } from "../types/implement";
import type { IndividualItem, StockDetail } from "../types/stock";
import { getSessionUserRole } from "../utils/auth";
import { Button } from "../components/ui/Button";
import { Select } from "../components/ui/Select";
import { Input } from "../components/ui/Input";
import { Badge } from "../components/ui/Badge";
import { HelpTooltip } from "../components/ui/HelpTooltip";
import { Table } from "../components/ui/Table";
import {
  getMovementActionLabel,
  getMovementBadgeTone,
  MOVEMENT_ACTION_LABELS,
  MOVEMENT_ACTION_OPTIONS,
} from "../utils/movementPresentation";

const PAGE_SIZE = 10;

const ITEM_TYPE_LABELS: Record<NonNullable<ImplementSummary["item_type"]>, string> = {
  consumable: "Consumible",
  reusable: "Reutilizable",
  individual: "Activo",
};

function getItemTypeLabel(itemType?: ImplementSummary["item_type"] | null): string {
  if (!itemType) {
    return "Sin tipo";
  }
  return ITEM_TYPE_LABELS[itemType] ?? itemType;
}

function getItemTypeHelp(itemType?: ImplementSummary["item_type"] | null): string {
  if (itemType === "consumable") {
    return "Consumible: stock de uso unico que normalmente se gasta o consume.";
  }
  if (itemType === "reusable") {
    return "Reutilizable: se presta por cantidad y luego puede volver al stock.";
  }
  if (itemType === "individual") {
    return "Activo: cada unidad tiene codigo propio y trazabilidad por activo.";
  }
  return "Tipo operativo del implemento seleccionado.";
}

function getMovementHelp(action: ManualMovementType, itemType?: ImplementSummary["item_type"] | null): string {
  const actionLabel = MOVEMENT_ACTION_LABELS[action] ?? action;
  if (action === "loan_return" && itemType === "consumable") {
    return `${actionLabel}: no aplica a consumibles, porque se consumen al entregar y no vuelven a stock.`;
  }
  if (action === "loan_return" && itemType === "individual") {
    return `${actionLabel}: permite revisar los activos actualmente en prestamo para registrar la devolucion manual.`;
  }
  if (action === "loan_return" && itemType === "reusable") {
    return `${actionLabel}: usa la cantidad que efectivamente vuelve desde el estado prestado.`;
  }
  if (action === "loan_return") {
    return `${actionLabel}: registra una devolucion manual del implemento seleccionado.`;
  }
  return `${actionLabel}: define el tipo de movimiento que deseas registrar para el implemento seleccionado.`;
}

function getStatusHelp(status: IndividualItem["status"]): string {
  if (status === "loaned") {
    return "Prestado: la unidad ya fue entregada y aun no vuelve al stock disponible.";
  }
  if (status === "available") {
    return "Disponible: la unidad esta lista para uso o para un nuevo prestamo.";
  }
  if (status === "damaged") {
    return "Danado: la unidad requiere revision y no deberia usarse normalmente.";
  }
  if (status === "maintenance") {
    return "Mantencion: la unidad esta en revision o intervencion tecnica.";
  }
  if (status === "blocked") {
    return "Bloqueado: la unidad existe, pero no esta habilitada operativamente.";
  }
  if (status === "retired") {
    return "Retirado: la unidad ya no forma parte del stock operativo.";
  }
  return "Estado operativo actual de la unidad.";
}

function getAvailableManualMovementActionOptions(
  itemType?: ImplementSummary["item_type"] | null,
): Array<{ value: ManualMovementType; label: string }> {
  if (itemType === "consumable") {
    return MOVEMENT_ACTION_OPTIONS.filter((option) => option.value !== "loan_return");
  }
  return MOVEMENT_ACTION_OPTIONS;
}

function readInitialImplementRouteFilter(): { implementUuid: string; implementName: string } {
  if (typeof window === "undefined") {
    return { implementUuid: "", implementName: "" };
  }

  const [, queryString = ""] = window.location.hash.split("?");
  const params = new URLSearchParams(queryString);
  return {
    implementUuid: params.get("implementUuid")?.trim() ?? "",
    implementName: params.get("implementName")?.trim() ?? "",
  };
}

function clearMovesRouteFilter() {
  if (typeof window === "undefined") {
    return;
  }

  const { pathname, search } = window.location;
  window.history.replaceState(null, "", `${pathname}${search}#/inventory/moves`);
}

function getImplementSearchText(item: ImplementSummary): string {
  const individualAssetCodes = item.individualAssetCodes ?? item.individual_asset_codes ?? [];
  return [item.name, item.barcode ?? "", ...individualAssetCodes].join(" ").trim().toLowerCase();
}

function getImplementMeta(item: ImplementSummary): string {
  const meta: string[] = [];
  if (item.barcode) meta.push(`Codigo de barras: ${item.barcode}`);
  if (item.category?.name) meta.push(`Categoria: ${item.category.name}`);
  return meta.join(" | ");
}

function toDateStart(value: string): Date | null {
  if (!value) return null;
  const d = new Date(`${value}T00:00:00`);
  return Number.isNaN(d.getTime()) ? null : d;
}

function toDateEnd(value: string): Date | null {
  if (!value) return null;
  const d = new Date(`${value}T23:59:59.999`);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function InventoryMovesPage({ embedded = false }: { embedded?: boolean }) {
  const initialRouteFilter = useMemo(() => readInitialImplementRouteFilter(), []);
  const [implementsList, setImplementsList] = useState<ImplementSummary[]>([]);
  const [movements, setMovements] = useState<InventoryMovementDetail[]>([]);
  const [loadingList, setLoadingList] = useState(false);
  const [loadingMovements, setLoadingMovements] = useState(false);
  const [saving, setSaving] = useState(false);
  const [pageError, setPageError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

  const [implementNameFilter, setImplementNameFilter] = useState(initialRouteFilter.implementName);
  const [routeImplementUuidFilter, setRouteImplementUuidFilter] = useState(initialRouteFilter.implementUuid);
  const [categoryFilter, setCategoryFilter] = useState("");
  const [userFilter, setUserFilter] = useState("");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [page, setPage] = useState(1);

  const [manualImplementUuid, setManualImplementUuid] = useState<string>("");
  const [action, setAction] = useState<ManualMovementType>("stock_in");
  const [quantity, setQuantity] = useState("1");
  const [notes, setNotes] = useState("");
  const [movementError, setMovementError] = useState<string | null>(null);
  const [isManualMovementModalOpen, setIsManualMovementModalOpen] = useState(false);
  const [isManualImplementMenuOpen, setIsManualImplementMenuOpen] = useState(false);
  const [manualImplementSearch, setManualImplementSearch] = useState("");
  const [manualStockDetail, setManualStockDetail] = useState<StockDetail | null>(null);
  const [manualStockLoading, setManualStockLoading] = useState(false);
  const [manualStockError, setManualStockError] = useState<string | null>(null);
  const [manualSelectedLoanedIndividuals, setManualSelectedLoanedIndividuals] = useState<string[]>([]);
  const manualImplementMenuRef = useRef<HTMLDivElement | null>(null);
  const manualImplementSearchInputRef = useRef<HTMLInputElement | null>(null);

  const role = getSessionUserRole();
  const canCreateMovement = role === "COORDINADOR";

  const resetManualMovementForm = useCallback(() => {
    setManualImplementUuid("");
    setAction("stock_in");
    setQuantity("1");
    setNotes("");
    setMovementError(null);
    setManualImplementSearch("");
    setIsManualImplementMenuOpen(false);
    setManualStockDetail(null);
    setManualStockLoading(false);
    setManualStockError(null);
    setManualSelectedLoanedIndividuals([]);
  }, []);

  const closeManualMovementModal = useCallback(() => {
    if (saving) {
      return;
    }
    setIsManualMovementModalOpen(false);
    resetManualMovementForm();
  }, [resetManualMovementForm, saving]);

  const reloadMovements = useCallback(async () => {
    setLoadingMovements(true);
    try {
      const rows = await fetchInventoryMovements();
      setMovements(rows);
    } catch (requestError) {
      setPageError(getErrorMessage(requestError, "No se pudo recargar movimientos."));
    } finally {
      setLoadingMovements(false);
    }
  }, []);

  const { promptVisible, pollingPaused, countdownSeconds, resumePolling } = useInactivityPollingGate({
    onContinue: async () => {
      await reloadMovements();
    },
  });

  useEffect(() => {
    async function bootstrap() {
      setPageError(null);
      setLoadingList(true);
      setLoadingMovements(true);
      try {
        const [list, movementRows] = await Promise.all([
          fetchImplements(),
          fetchInventoryMovements(),
        ]);
        setImplementsList(list);
        setMovements(movementRows);
      } catch (requestError) {
        setPageError(getErrorMessage(requestError, "No se pudo cargar la informacion de movimientos."));
      } finally {
        setLoadingList(false);
        setLoadingMovements(false);
      }
    }

    void bootstrap();
  }, []);

  useEffect(() => {
    if (pollingPaused) {
      return;
    }

    const intervalId = window.setInterval(() => {
      void reloadMovements();
    }, 180000);

    return () => window.clearInterval(intervalId);
  }, [pollingPaused, reloadMovements]);

  useEffect(() => {
    if (!isManualImplementMenuOpen) {
      return;
    }

    const timeoutId = window.setTimeout(() => {
      manualImplementSearchInputRef.current?.focus();
    }, 0);

    return () => window.clearTimeout(timeoutId);
  }, [isManualImplementMenuOpen]);

  useEffect(() => {
    if (!isManualMovementModalOpen) {
      return;
    }

    document.body.classList.add("modal-open");

    function handleDocumentMouseDown(event: MouseEvent) {
      if (!isManualImplementMenuOpen) {
        return;
      }

      const target = event.target as Node;
      if (manualImplementMenuRef.current && !manualImplementMenuRef.current.contains(target)) {
        setIsManualImplementMenuOpen(false);
      }
    }

    function handleDocumentKeyDown(event: KeyboardEvent) {
      if (event.key !== "Escape") {
        return;
      }

      if (isManualImplementMenuOpen) {
        setIsManualImplementMenuOpen(false);
        return;
      }

      closeManualMovementModal();
    }

    document.addEventListener("mousedown", handleDocumentMouseDown);
    document.addEventListener("keydown", handleDocumentKeyDown);

    return () => {
      document.body.classList.remove("modal-open");
      document.removeEventListener("mousedown", handleDocumentMouseDown);
      document.removeEventListener("keydown", handleDocumentKeyDown);
    };
  }, [closeManualMovementModal, isManualImplementMenuOpen, isManualMovementModalOpen]);

  const implementByUuid = useMemo(() => {
    const map = new Map<string, ImplementSummary>();
    implementsList.forEach((item) => map.set(item.uuid, item));
    return map;
  }, [implementsList]);

  const categoryOptions = useMemo(() => {
    const map = new Map<string, string>();
    implementsList.forEach((item) => {
      if (item.category) map.set(item.category.uuid, item.category.name);
    });
    return Array.from(map.entries()).map(([uuid, name]) => ({ uuid, name }));
  }, [implementsList]);

  const selectedManualImplement = useMemo(() => {
    if (!manualImplementUuid) {
      return null;
    }
    return implementByUuid.get(manualImplementUuid) ?? null;
  }, [implementByUuid, manualImplementUuid]);

  const selectedManualItemType = selectedManualImplement?.item_type ?? null;
  const availableManualMovementOptions = useMemo(
    () => getAvailableManualMovementActionOptions(selectedManualItemType),
    [selectedManualItemType],
  );

  const selectedManualLoanedIndividuals = useMemo(
    () => (manualStockDetail?.individuals ?? []).filter((item) => item.status === "loaned"),
    [manualStockDetail],
  );

  const selectedManualLoanedCount = manualStockDetail?.stock.loaned ?? selectedManualImplement?.stock?.loaned ?? 0;

  const usesLoanedIndividualSelection =
    action === "loan_return" && selectedManualItemType === "individual" && selectedManualImplement != null;

  const showsLoanedContext =
    action === "loan_return" &&
    selectedManualImplement != null &&
    (selectedManualItemType === "individual" || selectedManualItemType === "reusable");

  useEffect(() => {
    if (!selectedManualImplement) {
      return;
    }
    if (availableManualMovementOptions.some((option) => option.value === action)) {
      return;
    }
    const fallbackAction = selectedManualItemType === "consumable" ? "consumption" : availableManualMovementOptions[0]?.value ?? "stock_in";
    setAction(fallbackAction);
  }, [action, availableManualMovementOptions, selectedManualImplement, selectedManualItemType]);

  const filteredManualImplements = useMemo(() => {
    const query = manualImplementSearch.trim().toLowerCase();
    if (!query) {
      return implementsList;
    }

    return implementsList.filter((item) => getImplementSearchText(item).includes(query));
  }, [implementsList, manualImplementSearch]);

  useEffect(() => {
    if (!isManualMovementModalOpen || !manualImplementUuid) {
      return;
    }

    let cancelled = false;

    async function loadManualImplementStock() {
      setManualStockLoading(true);
      setManualStockError(null);
      try {
        const detail = await fetchImplementStock(manualImplementUuid);
        if (!cancelled) {
          setManualStockDetail(detail);
        }
      } catch (requestError) {
        if (!cancelled) {
          setManualStockDetail(null);
          setManualStockError(getErrorMessage(requestError, "No se pudo cargar el stock operativo del implemento."));
        }
      } finally {
        if (!cancelled) {
          setManualStockLoading(false);
        }
      }
    }

    void loadManualImplementStock();

    return () => {
      cancelled = true;
    };
  }, [isManualMovementModalOpen, manualImplementUuid]);

  useEffect(() => {
    setManualSelectedLoanedIndividuals((current) => {
      if (!usesLoanedIndividualSelection) {
        return [];
      }
      const validIds = new Set(selectedManualLoanedIndividuals.map((item) => item.uuid));
      return current.filter((uuid) => validIds.has(uuid));
    });
  }, [selectedManualLoanedIndividuals, usesLoanedIndividualSelection]);

  useEffect(() => {
    if (
      action === "loan_return" &&
      selectedManualItemType === "reusable" &&
      selectedManualLoanedCount > 0
    ) {
      const currentQuantity = Number(quantity);
      if (Number.isFinite(currentQuantity) && currentQuantity > selectedManualLoanedCount) {
        setQuantity(String(selectedManualLoanedCount));
      }
    }
  }, [action, quantity, selectedManualItemType, selectedManualLoanedCount]);

  const filteredMovements = useMemo(() => {
    const implementQuery = implementNameFilter.trim().toLowerCase();
    const userQuery = userFilter.trim().toLowerCase();
    const fromDate = toDateStart(dateFrom);
    const toDate = toDateEnd(dateTo);

    return movements.filter((movement) => {
      const implementInfo = movement.implement_uuid ? implementByUuid.get(movement.implement_uuid) : undefined;
      const implementSearchText = implementInfo ? getImplementSearchText(implementInfo) : "";
      const categoryUuid = implementInfo?.category?.uuid ?? null;
      const movementDate = new Date(movement.timestamp);

      if (routeImplementUuidFilter && movement.implement_uuid !== routeImplementUuidFilter) return false;
      if (implementQuery && !implementSearchText.includes(implementQuery)) return false;
      if (categoryFilter && String(categoryUuid ?? "") !== categoryFilter) return false;
      const performedBy = (movement.performed_by ?? "").toLowerCase();
      if (userQuery && !performedBy.includes(userQuery)) return false;
      if (fromDate && movementDate < fromDate) return false;
      if (toDate && movementDate > toDate) return false;

      return true;
    });
  }, [movements, implementByUuid, implementNameFilter, routeImplementUuidFilter, categoryFilter, userFilter, dateFrom, dateTo]);

  const moveStats = useMemo(() => {
    return {
      total: filteredMovements.length,
      ingresos: filteredMovements.filter((m) => m.action === "stock_in").length,
      ajustes: filteredMovements.filter((m) => m.action === "manual_adjustment").length,
      implements: new Set(filteredMovements.map((m) => m.implement_uuid).filter((uuid): uuid is string => Boolean(uuid))).size,
    };
  }, [filteredMovements]);

  const totalPages = useMemo(() => Math.max(1, Math.ceil(filteredMovements.length / PAGE_SIZE)), [filteredMovements.length]);
  const safePage = useMemo(() => {
    if (page < 1) {
      return 1;
    }
    if (page > totalPages) {
      return totalPages;
    }
    return page;
  }, [page, totalPages]);
  const pageStart = (safePage - 1) * PAGE_SIZE;
  const pagedMovements = filteredMovements.slice(pageStart, pageStart + PAGE_SIZE);
  const rangeStart = filteredMovements.length === 0 ? 0 : pageStart + 1;
  const rangeEnd = filteredMovements.length === 0 ? 0 : Math.min(pageStart + pagedMovements.length, filteredMovements.length);
  const pageNumbers = useMemo(() => {
    const windowSize = 5;
    let start = Math.max(1, safePage - 2);
    const end = Math.min(totalPages, start + windowSize - 1);
    start = Math.max(1, end - windowSize + 1);
    return Array.from({ length: end - start + 1 }, (_, index) => start + index);
  }, [safePage, totalPages]);

  function clearFilters() {
    setImplementNameFilter("");
    setRouteImplementUuidFilter("");
    setCategoryFilter("");
    setUserFilter("");
    setDateFrom("");
    setDateTo("");
    setPage(1);
    clearMovesRouteFilter();
  }

  function openManualMovementModal() {
    resetManualMovementForm();
    setIsManualMovementModalOpen(true);
  }

  function toggleManualImplementMenu() {
    setMovementError(null);
    setIsManualImplementMenuOpen((current) => {
      const next = !current;
      if (!next) {
        setManualImplementSearch("");
      }
      return next;
    });
  }

  function selectManualImplement(implementUuid: string) {
    setManualImplementUuid(implementUuid);
    setMovementError(null);
    setManualImplementSearch("");
    setIsManualImplementMenuOpen(false);
    setManualSelectedLoanedIndividuals([]);
  }

  function toggleManualLoanedIndividual(individualUuid: string) {
    setManualSelectedLoanedIndividuals((current) =>
      current.includes(individualUuid)
        ? current.filter((uuid) => uuid !== individualUuid)
        : [...current, individualUuid],
    );
  }

  async function submitMovement() {
    const implementUuid = manualImplementUuid.trim();
    if (!implementUuid) {
      setMovementError("Debes seleccionar un implemento para registrar el movimiento.");
      return;
    }

    let qty = Number(quantity);
    if (usesLoanedIndividualSelection) {
      if (selectedManualLoanedIndividuals.length === 0) {
        setMovementError("No hay activos en prestamo disponibles para registrar la devolucion.");
        return;
      }
      if (manualSelectedLoanedIndividuals.length === 0) {
        setMovementError("Debes seleccionar al menos un activo prestado para registrar la devolucion.");
        return;
      }
      qty = manualSelectedLoanedIndividuals.length;
    } else {
      if (!Number.isFinite(qty) || qty <= 0 || !Number.isInteger(qty)) {
        setMovementError("La cantidad debe ser un entero positivo.");
        return;
      }
    }

    if (action === "loan_return" && selectedManualItemType === "reusable") {
      if (selectedManualLoanedCount <= 0) {
        setMovementError("Este implemento reutilizable no tiene unidades actualmente en prestamo.");
        return;
      }
      if (qty > selectedManualLoanedCount) {
        setMovementError(`Solo hay ${selectedManualLoanedCount} unidad(es) reutilizable(s) en prestamo para devolver.`);
        return;
      }
    }

    setSaving(true);
    setMovementError(null);
    setSuccess(null);
    try {
      await registerManualMovement(implementUuid, {
        action,
        quantity: qty,
        notes: notes.trim() ? notes.trim() : null,
      });
      await reloadMovements();
      setPage(1);
      setSuccess("Movimiento registrado correctamente.");
      setIsManualMovementModalOpen(false);
      resetManualMovementForm();
    } catch (requestError) {
      const payload = getApiErrorPayload(requestError);
      setMovementError(payload?.message ?? getErrorMessage(requestError, "No se pudo registrar movimiento."));
    } finally {
      setSaving(false);
    }
  }

  const content = (
    <>
      <section className="content-header">
        <div>
          <h1>Movimientos</h1>
          <p>Historial de movimientos de inventario registrados en PostgreSQL.</p>
        </div>
      </section>

      <section className="stat-grid">
        <article className="stat-card stat-card--blue">
          <p>Movimientos totales</p>
          <strong>{moveStats.total}</strong>
        </article>
        <article className="stat-card stat-card--green">
          <p>Ingresos</p>
          <strong>{moveStats.ingresos}</strong>
        </article>
        <article className="stat-card stat-card--orange">
          <p>Ajustes</p>
          <strong>{moveStats.ajustes}</strong>
        </article>
        <article className="stat-card stat-card--cyan">
          <p>Implementos con movimiento</p>
          <strong>{moveStats.implements}</strong>
        </article>
      </section>

      <section className="panel">
        {pageError ? <div className="error-banner">{pageError}</div> : null}
        {success ? <div className="success-banner">{success}</div> : null}

        {canCreateMovement ? (
          <div className="inventory-moves-toolbar">
            <Button className="inventory-toolbar-create__button inventory-moves-toolbar__button" onClick={openManualMovementModal}>
              <Plus size={18} />
              Registrar movimiento manual
            </Button>
          </div>
        ) : null}

        <div className="catalog-filters catalog-filters--moves">
          <div className="catalog-filters__item catalog-filters__item--search">
            <label>Implemento / codigo</label>
            <Input
              value={implementNameFilter}
              onChange={(event) => {
                setImplementNameFilter(event.target.value);
                if (routeImplementUuidFilter) {
                  setRouteImplementUuidFilter("");
                  clearMovesRouteFilter();
                }
                setPage(1);
              }}
              placeholder="Buscar por nombre o codigo de barras"
            />
          </div>
          <div className="catalog-filters__item">
            <label>Categoria</label>
            <Select
              value={categoryFilter}
              onChange={(event) => {
                setCategoryFilter(event.target.value);
                setPage(1);
              }}
              disabled={loadingList}
            >
              <option value="">Todas</option>
              {categoryOptions.map((category) => (
                <option key={category.uuid} value={category.uuid}>
                  {category.name}
                </option>
              ))}
            </Select>
          </div>
          <div className="catalog-filters__item catalog-filters__item--search">
            <label>Usuario</label>
            <Input
              value={userFilter}
              onChange={(event) => {
                setUserFilter(event.target.value);
                setPage(1);
              }}
              placeholder="Ej: Ana Perez"
            />
          </div>
          <div className="catalog-filters__item">
            <label>Desde</label>
            <Input
              type="date"
              value={dateFrom}
              onChange={(event) => {
                setDateFrom(event.target.value);
                setPage(1);
              }}
            />
          </div>
          <div className="catalog-filters__item">
            <label>Hasta</label>
            <Input
              type="date"
              value={dateTo}
              onChange={(event) => {
                setDateTo(event.target.value);
                setPage(1);
              }}
            />
          </div>
        </div>

        <div className="catalog-filters__summary">
          <p>
            Mostrando <strong>{filteredMovements.length}</strong> de <strong>{movements.length}</strong> movimientos
          </p>
          <Button variant="ghost" onClick={clearFilters}>Limpiar filtros</Button>
        </div>

        <Table>
          <thead>
            <tr>
              <th>Implemento</th>
              <th>Categoria</th>
              <th>Fecha</th>
              <th>Accion</th>
              <th>Cantidad</th>
              <th>Usuario</th>
              <th>Notas</th>
            </tr>
          </thead>
          <tbody>
            {loadingMovements ? (
              <tr>
                <td colSpan={7} className="table-hint">Cargando movimientos...</td>
              </tr>
            ) : filteredMovements.length === 0 ? (
              <tr>
                <td colSpan={7} className="table-hint">No hay movimientos para mostrar.</td>
              </tr>
            ) : (
              pagedMovements.map((movement) => {
                const implementInfo = movement.implement_uuid ? implementByUuid.get(movement.implement_uuid) : undefined;
                return (
                  <tr key={movement.uuid} className="table-row-hover">
                    <td>{implementInfo?.name ?? "Implemento no disponible"}</td>
                    <td>{implementInfo?.category?.name ?? "Sin categoria"}</td>
                    <td>{new Date(movement.timestamp).toLocaleString()}</td>
                    <td>
                      <Badge tone={getMovementBadgeTone(movement.action)}>
                        {getMovementActionLabel(movement.action)}
                      </Badge>
                    </td>
                    <td>{movement.quantity}</td>
                    <td>{movement.performed_by || "Usuario no identificado"}</td>
                    <td>{movement.notes ?? "-"}</td>
                  </tr>
                );
              })
            )}
          </tbody>
        </Table>

        {!loadingMovements ? (
          <div className="inventory-table-footer">
            <p>
              Mostrando {rangeStart} a {rangeEnd} de {filteredMovements.length} movimientos
            </p>
            <div className="inventory-pagination">
              <button
                type="button"
                className="inventory-pagination__btn"
                onClick={() => setPage((current) => Math.max(1, current - 1))}
                disabled={safePage <= 1}
              >
                <ChevronLeft size={16} />
              </button>
              {pageNumbers.map((pageNumber) => (
                <button
                  key={pageNumber}
                  type="button"
                  className={pageNumber === safePage ? "inventory-pagination__btn inventory-pagination__btn--active" : "inventory-pagination__btn"}
                  onClick={() => setPage(pageNumber)}
                >
                  {pageNumber}
                </button>
              ))}
              <button
                type="button"
                className="inventory-pagination__btn"
                onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
                disabled={safePage >= totalPages}
              >
                <ChevronRight size={16} />
              </button>
            </div>
          </div>
        ) : null}
      </section>

      {isManualMovementModalOpen ? (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-label="Registrar movimiento manual">
          <div className="modal modal--wide inventory-movement-modal">
            <div className="inventory-movement-modal__header">
              <div>
                <h3>Registrar movimiento manual</h3>
                <p>Selecciona el implemento y completa el ajuste que quieres guardar.</p>
              </div>
              <Button
                variant="ghost"
                size="sm"
                className="inventory-movement-modal__close"
                onClick={closeManualMovementModal}
                disabled={saving}
                aria-label="Cerrar modal"
              >
                <X size={14} />
              </Button>
            </div>

            {movementError ? <p className="field-error inventory-movement-modal__error">{movementError}</p> : null}

            <div className="modal-form">
              <div className="modal-form-section">
                <div className="stock-actions-grid inventory-movement-modal__grid">
                  <div className="modal-field modal-field--full">
                    <div className="field-label-with-help">
                      <label htmlFor="manual-movement-implement-trigger">Implemento</label>
                      <HelpTooltip
                        text="Selecciona primero el implemento. Eso habilita la accion, la cantidad y el contexto del movimiento."
                        ariaLabel="Ayuda sobre la seleccion del implemento"
                      />
                    </div>
                    <div ref={manualImplementMenuRef} className="inventory-multiselect inventory-implement-picker">
                      <button
                        id="manual-movement-implement-trigger"
                        type="button"
                        className="inventory-multiselect__trigger inventory-implement-picker__trigger"
                        onClick={toggleManualImplementMenu}
                        aria-expanded={isManualImplementMenuOpen}
                        aria-haspopup="listbox"
                        aria-controls="manual-movement-implement-menu"
                      >
                        <div className="inventory-implement-picker__trigger-copy">
                          <strong>{selectedManualImplement?.name ?? "Selecciona un implemento"}</strong>
                          <small>
                            {selectedManualImplement
                              ? getImplementMeta(selectedManualImplement) || "Implemento seleccionado"
                              : "Busca por nombre o codigo de barras"}
                          </small>
                        </div>
                        <ChevronDown size={16} className={isManualImplementMenuOpen ? "is-open" : ""} />
                      </button>

                      {isManualImplementMenuOpen ? (
                        <div
                          id="manual-movement-implement-menu"
                          className="inventory-multiselect__menu inventory-implement-picker__menu"
                          role="listbox"
                          aria-label="Seleccionar implemento"
                        >
                          <div className="inventory-search-input-wrap inventory-implement-picker__search">
                            <Search size={16} />
                            <input
                              ref={manualImplementSearchInputRef}
                              type="search"
                              value={manualImplementSearch}
                              onChange={(event) => setManualImplementSearch(event.target.value)}
                              placeholder="Buscar implemento o codigo de barras"
                            />
                          </div>

                          <div className="inventory-implement-picker__options">
                            {loadingList ? <p className="inventory-multiselect__hint">Cargando implementos...</p> : null}
                            {!loadingList && filteredManualImplements.length === 0 ? <p className="inventory-multiselect__hint">Sin resultados</p> : null}
                            {!loadingList && filteredManualImplements.map((item) => {
                              const optionMeta = getImplementMeta(item);
                              const isSelected = item.uuid === manualImplementUuid;
                              return (
                                <button
                                  key={item.uuid}
                                  type="button"
                                  className={isSelected ? "inventory-multiselect__option inventory-implement-picker__option is-selected" : "inventory-multiselect__option inventory-implement-picker__option"}
                                  onClick={() => selectManualImplement(item.uuid)}
                                  role="option"
                                  aria-selected={isSelected}
                                >
                                  <div className="inventory-implement-picker__option-copy">
                                    <strong>{item.name}</strong>
                                    {optionMeta ? <small>{optionMeta}</small> : null}
                                  </div>
                                  {isSelected ? <Check size={16} /> : null}
                                </button>
                              );
                            })}
                          </div>
                        </div>
                      ) : null}
                    </div>
                    {!selectedManualImplement ? (
                      <p className="field-hint inventory-movement-modal__field-hint">
                        Selecciona un implemento para habilitar el resto del formulario.
                      </p>
                    ) : (
                      <div className="inventory-movement-context__chips">
                        <span
                          className="inventory-movement-context__chip"
                          title={getItemTypeHelp(selectedManualItemType)}
                        >
                          Tipo: {getItemTypeLabel(selectedManualItemType)}
                        </span>
                        {selectedManualItemType === "individual" || selectedManualItemType === "reusable" ? (
                          <span
                            className="inventory-movement-context__chip"
                            title={getStatusHelp("loaned")}
                          >
                            En prestamo: {selectedManualLoanedCount}
                          </span>
                        ) : null}
                      </div>
                    )}
                  </div>

                  <div className="modal-field">
                    <div className="field-label-with-help">
                      <label htmlFor="manual-movement-action">Accion</label>
                      <HelpTooltip
                        text={getMovementHelp(action, selectedManualItemType)}
                        ariaLabel="Ayuda sobre el tipo de movimiento"
                      />
                    </div>
                    <Select
                      id="manual-movement-action"
                      value={action}
                      onChange={(event) => setAction(event.target.value as ManualMovementType)}
                      disabled={!selectedManualImplement || saving}
                    >
                      {availableManualMovementOptions.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </Select>
                    <p className="field-hint inventory-movement-modal__field-hint">
                      {selectedManualImplement
                        ? getMovementHelp(action, selectedManualItemType)
                        : "Primero selecciona el implemento para habilitar la accion."}
                    </p>
                  </div>

                  <div className="modal-field">
                    <div className="field-label-with-help">
                      <label htmlFor="manual-movement-quantity">Cantidad</label>
                      <HelpTooltip
                        text={
                          usesLoanedIndividualSelection
                            ? "La cantidad se calcula segun los activos prestados que selecciones."
                            : "Cantidad de unidades involucradas en el movimiento manual."
                        }
                        ariaLabel="Ayuda sobre la cantidad"
                      />
                    </div>
                    <Input
                      id="manual-movement-quantity"
                      value={usesLoanedIndividualSelection ? String(manualSelectedLoanedIndividuals.length) : quantity}
                      onChange={(event) => setQuantity(event.target.value)}
                      type="number"
                      min={1}
                      max={action === "loan_return" && selectedManualItemType === "reusable" ? selectedManualLoanedCount : undefined}
                      disabled={!selectedManualImplement || saving || usesLoanedIndividualSelection}
                    />
                    {action === "loan_return" && selectedManualItemType === "reusable" ? (
                      <p className="field-hint inventory-movement-modal__field-hint">
                        Reutilizables actualmente en prestamo: {selectedManualLoanedCount}.
                      </p>
                    ) : null}
                  </div>

                  {showsLoanedContext ? (
                    <div className="modal-field modal-field--full">
                      <div className="inventory-movement-context">
                        <div className="inventory-movement-context__header">
                          <div>
                            <strong>
                              {selectedManualItemType === "individual"
                                ? "Activos actualmente en prestamo"
                                : "Stock reutilizable actualmente en prestamo"}
                            </strong>
                            <span>
                              {selectedManualItemType === "individual"
                                ? "Selecciona los activos que estan regresando desde prestamo."
                                : "Usa la cantidad prestada actual como referencia para la devolucion."}
                            </span>
                          </div>
                          <span
                            className="inventory-movement-context__chip"
                            title={getStatusHelp("loaned")}
                          >
                            Estado: Prestado
                          </span>
                        </div>

                        {manualStockLoading ? <p className="field-hint">Cargando stock del implemento...</p> : null}
                        {!manualStockLoading && manualStockError ? <p className="field-error">{manualStockError}</p> : null}

                        {!manualStockLoading && !manualStockError && selectedManualItemType === "individual" ? (
                          selectedManualLoanedIndividuals.length === 0 ? (
                            <p className="field-hint">No hay activos en prestamo para este implemento.</p>
                          ) : (
                            <>
                              <div className="inventory-movement-context__list">
                                {selectedManualLoanedIndividuals.map((individual) => {
                                  const checked = manualSelectedLoanedIndividuals.includes(individual.uuid);
                                  return (
                                    <label
                                      key={individual.uuid}
                                      className={`inventory-movement-context__list-item${checked ? " is-selected" : ""}`}
                                    >
                                      <input
                                        type="checkbox"
                                        checked={checked}
                                        onChange={() => toggleManualLoanedIndividual(individual.uuid)}
                                        disabled={saving}
                                      />
                                      <div>
                                        <strong>{individual.asset_code}</strong>
                                        <small title={getStatusHelp(individual.status)}>
                                          Estado actual: {individual.status === "loaned" ? "Prestado" : individual.status}
                                        </small>
                                      </div>
                                    </label>
                                  );
                                })}
                              </div>
                              <p className="field-hint inventory-movement-modal__field-hint">
                                Activos seleccionados para devolucion: {manualSelectedLoanedIndividuals.length}
                              </p>
                            </>
                          )
                        ) : null}

                        {!manualStockLoading && !manualStockError && selectedManualItemType === "reusable" ? (
                          <p className="field-hint inventory-movement-modal__field-hint">
                            Este implemento reutilizable tiene {selectedManualLoanedCount} unidad(es) en prestamo.
                          </p>
                        ) : null}
                      </div>
                    </div>
                  ) : null}

                  <div className="modal-field modal-field--full">
                    <div className="field-label-with-help">
                      <label htmlFor="manual-movement-notes">Notas</label>
                      <HelpTooltip
                        text="Contexto opcional del ajuste manual para que el historial quede mas claro."
                        ariaLabel="Ayuda sobre las notas"
                      />
                    </div>
                    <textarea
                      id="manual-movement-notes"
                      className="inventory-movement-modal__notes"
                      value={notes}
                      onChange={(event) => setNotes(event.target.value)}
                      placeholder="Opcional"
                      disabled={!selectedManualImplement || saving}
                    />
                  </div>
                </div>
              </div>

              <div className="modal-actions">
                <Button variant="ghost" onClick={closeManualMovementModal} disabled={saving}>
                  Cancelar
                </Button>
                <Button onClick={() => void submitMovement()} disabled={saving || !selectedManualImplement}>
                  {saving ? "Guardando..." : "Registrar movimiento"}
                </Button>
              </div>
            </div>
          </div>
        </div>
      ) : null}

      <PresencePollingModal
        visible={promptVisible}
        pollingPaused={pollingPaused}
        countdownSeconds={countdownSeconds}
        onContinue={() => {
          void resumePolling();
        }}
      />
    </>
  );

  if (embedded) {
    return content;
  }

  return <InventoryLayout activeSection="moves">{content}</InventoryLayout>;
}
