import { Search } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { Badge } from "../components/ui/Badge";
import { Button } from "../components/ui/Button";
import { Card } from "../components/ui/Card";
import { Input } from "../components/ui/Input";
import { Select } from "../components/ui/Select";
import { Table } from "../components/ui/Table";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import { fetchImplements } from "../services/implementService";
import { createLoan } from "../services/loanService";
import { saveLastCreatedLoan } from "../services/loanSessionService";
import { fetchRooms } from "../services/roomService";
import { fetchSubjects } from "../services/subjectService";
import type { ImplementSummary } from "../types/implement";
import type { CreateLoanPayload } from "../types/loan";
import type { RoomOption } from "../types/room";
import type { SubjectOption } from "../types/subject";

interface LoanCartItem {
  implement_uuid: string;
  implement_name: string;
  requested_quantity: number;
}

function pad(value: number): string {
  return value.toString().padStart(2, "0");
}

function buildScheduledAtIso(dateValue: string, timeValue: string): string | null {
  if (!dateValue || !timeValue) {
    return null;
  }

  const dateParts = dateValue.split("-").map(Number);
  const timeParts = timeValue.split(":").map(Number);
  if (dateParts.length !== 3 || timeParts.length < 2) {
    return null;
  }

  const [year, month, day] = dateParts;
  const [hours, minutes] = timeParts;
  const local = new Date(year, month - 1, day, hours, minutes, 0, 0);
  if (Number.isNaN(local.getTime())) {
    return null;
  }

  const offsetMinutes = -local.getTimezoneOffset();
  const sign = offsetMinutes >= 0 ? "+" : "-";
  const absoluteOffsetMinutes = Math.abs(offsetMinutes);
  const offsetHoursPart = pad(Math.floor(absoluteOffsetMinutes / 60));
  const offsetMinutesPart = pad(absoluteOffsetMinutes % 60);

  return `${year}-${pad(month)}-${pad(day)}T${pad(hours)}:${pad(minutes)}:00${sign}${offsetHoursPart}:${offsetMinutesPart}`;
}

export function LoanCreatePage({ embedded = false }: { embedded?: boolean }) {
  const [rooms, setRooms] = useState<RoomOption[]>([]);
  const [subjects, setSubjects] = useState<SubjectOption[]>([]);
  const [loadingOptions, setLoadingOptions] = useState(false);
  const [saving, setSaving] = useState(false);
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [roomInlineError, setRoomInlineError] = useState<string | null>(null);
  const [searchInlineError, setSearchInlineError] = useState<string | null>(null);
  const [duplicateWarning, setDuplicateWarning] = useState<string | null>(null);

  const [roomUuid, setRoomUuid] = useState("");
  const [subjectUuid, setSubjectUuid] = useState("");
  const [dateValue, setDateValue] = useState("");
  const [timeValue, setTimeValue] = useState("");

  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchResults, setSearchResults] = useState<ImplementSummary[]>([]);
  const [cart, setCart] = useState<LoanCartItem[]>([]);

  useEffect(() => {
    const timeout = window.setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => window.clearTimeout(timeout);
  }, [search]);

  useEffect(() => {
    async function bootstrap() {
      setLoadingOptions(true);
      setGlobalError(null);
      try {
        const [roomsResponse, subjectsResponse] = await Promise.all([fetchRooms(), fetchSubjects()]);
        setRooms(roomsResponse);
        setSubjects(subjectsResponse);
      } catch (requestError) {
        setGlobalError(getErrorMessage(requestError, "No se pudieron cargar salas y asignaturas."));
      } finally {
        setLoadingOptions(false);
      }
    }

    void bootstrap();
  }, []);

  useEffect(() => {
    if (debouncedSearch.length < 2) {
      setSearchResults([]);
      setSearchLoading(false);
      return;
    }

    let cancelled = false;
    setSearchLoading(true);
    fetchImplements({ name: debouncedSearch })
      .then((rows) => {
        if (cancelled) {
          return;
        }
        setSearchResults(rows.slice(0, 10));
      })
      .catch(() => {
        if (cancelled) {
          return;
        }
        setSearchResults([]);
      })
      .finally(() => {
        if (cancelled) {
          return;
        }
        setSearchLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [debouncedSearch]);

  const scheduledAt = buildScheduledAtIso(dateValue, timeValue);
  const hasValidQuantities = cart.every(
    (item) => Number.isInteger(item.requested_quantity) && item.requested_quantity > 0,
  );
  const canSubmit = Boolean(roomUuid && scheduledAt && cart.length > 0 && hasValidQuantities && !saving);
  const cartUuids = useMemo(() => new Set(cart.map((item) => item.implement_uuid)), [cart]);

  function addImplement(implement: ImplementSummary) {
    setSearchInlineError(null);
    setDuplicateWarning(null);

    setCart((previous) => {
      const exists = previous.some((item) => item.implement_uuid === implement.uuid);
      if (exists) {
        setDuplicateWarning("Este implemento ya fue agregado al carrito.");
        return previous;
      }
      return [
        ...previous,
        {
          implement_uuid: implement.uuid,
          implement_name: implement.name,
          requested_quantity: 1,
        },
      ];
    });
  }

  function updateQuantity(implementUuid: string, rawValue: string) {
    const parsed = Number(rawValue);
    setCart((previous) =>
      previous.map((item) =>
        item.implement_uuid !== implementUuid
          ? item
          : {
              ...item,
              requested_quantity:
                !Number.isFinite(parsed) || parsed < 1 ? 1 : Math.floor(parsed),
            },
      ),
    );
  }

  function removeImplement(implementUuid: string) {
    setCart((previous) => previous.filter((item) => item.implement_uuid !== implementUuid));
  }

  async function submitLoan() {
    setGlobalError(null);
    setRoomInlineError(null);
    setSearchInlineError(null);
    setDuplicateWarning(null);

    if (!roomUuid) {
      setRoomInlineError("Debes seleccionar una sala.");
      return;
    }
    if (!scheduledAt) {
      setGlobalError("Debes completar fecha y hora validas.");
      return;
    }
    if (cart.length === 0) {
      setSearchInlineError("Debes agregar al menos un implemento al carrito.");
      return;
    }
    if (!hasValidQuantities) {
      setSearchInlineError("Todas las cantidades deben ser enteros mayores a cero.");
      return;
    }

    const payload: CreateLoanPayload = {
      room_uuid: roomUuid,
      subject_uuid: subjectUuid || null,
      scheduled_at: scheduledAt,
      items: cart.map((item) => ({
        implement_uuid: item.implement_uuid,
        requested_quantity: item.requested_quantity,
      })),
    };

    setSaving(true);
    try {
      const created = await createLoan(payload);
      saveLastCreatedLoan(created);
      window.location.hash = `#/inventory/prestamos/${created.uuid}`;
    } catch (requestError) {
      const payloadError = getApiErrorPayload(requestError);
      if (payloadError?.code === "LOAN_DUPLICATE_REQUEST") {
        setSearchInlineError(payloadError.message);
      } else if (
        payloadError?.code === "LOAN_ROOM_NOT_FOUND" ||
        payloadError?.code === "ROOM_NOT_FOUND"
      ) {
        setRoomInlineError(payloadError.message);
      } else {
        setGlobalError(payloadError?.message ?? getErrorMessage(requestError, "No se pudo enviar la solicitud."));
      }
    } finally {
      setSaving(false);
    }
  }

  const content = (
    <>
      <section className="content-header">
        <div>
          <h1>Nueva solicitud de prestamo</h1>
          <p>Completa sala, fecha/hora e implementos para generar la solicitud.</p>
        </div>
      </section>

      {globalError ? <div className="error-banner">{globalError}</div> : null}

      <section className="loan-grid">
        <Card>
          <h2>Datos de la solicitud</h2>
          <div className="loan-form-grid">
            <div>
              <label htmlFor="loan-room">Sala</label>
              <Select
                id="loan-room"
                value={roomUuid}
                disabled={loadingOptions || saving}
                onChange={(event) => {
                  setRoomUuid(event.target.value);
                  setRoomInlineError(null);
                }}
              >
                <option value="">Selecciona una sala</option>
                {rooms.map((room) => (
                  <option key={room.uuid} value={room.uuid}>
                    {room.name}
                  </option>
                ))}
              </Select>
              {roomInlineError ? <p className="field-error">{roomInlineError}</p> : null}
            </div>

            <div>
              <label htmlFor="loan-subject">Asignatura (opcional)</label>
              <Select
                id="loan-subject"
                value={subjectUuid}
                disabled={loadingOptions || saving}
                onChange={(event) => setSubjectUuid(event.target.value)}
              >
                <option value="">Sin asignatura</option>
                {subjects.map((subject) => (
                  <option key={subject.uuid} value={subject.uuid}>
                    {subject.code} - {subject.name}
                  </option>
                ))}
              </Select>
            </div>

            <div>
              <label htmlFor="loan-date">Fecha</label>
              <Input
                id="loan-date"
                type="date"
                value={dateValue}
                disabled={saving}
                onChange={(event) => setDateValue(event.target.value)}
              />
            </div>

            <div>
              <label htmlFor="loan-time">Hora</label>
              <Input
                id="loan-time"
                type="time"
                value={timeValue}
                disabled={saving}
                onChange={(event) => setTimeValue(event.target.value)}
              />
            </div>
          </div>
        </Card>

        <Card>
          <h2>Buscar implementos</h2>
          <div className="loan-search">
            <Search size={16} />
            <Input
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Escribe al menos 2 caracteres"
              disabled={saving}
            />
          </div>

          {duplicateWarning ? <p className="field-error">{duplicateWarning}</p> : null}
          {searchInlineError ? <p className="field-error">{searchInlineError}</p> : null}

          <div className="loan-search-results">
            {search.trim().length < 2 ? (
              <p className="text-muted">Ingresa 2 o mas caracteres para buscar.</p>
            ) : searchLoading ? (
              <p className="text-muted">Buscando implementos...</p>
            ) : searchResults.length === 0 ? (
              <p className="text-muted">Sin resultados para esa busqueda.</p>
            ) : (
              searchResults.map((result) => {
                const inCart = cartUuids.has(result.uuid);
                return (
                  <article key={result.uuid} className="loan-search-item">
                    <div>
                      <strong>{result.name}</strong>
                      <p>{result.category?.name ?? "Sin categoria"}</p>
                    </div>
                    <Button
                      variant={inCart ? "ghost" : "primary"}
                      size="sm"
                      disabled={saving}
                      onClick={() => addImplement(result)}
                    >
                      {inCart ? "Agregar de nuevo" : "Agregar"}
                    </Button>
                  </article>
                );
              })
            )}
          </div>
        </Card>
      </section>

      <section className="panel">
        <div className="catalog-filters__summary">
          <p>
            Implementos seleccionados: <strong>{cart.length}</strong>
          </p>
          <Badge tone={cart.length > 0 ? "active" : "inactive"}>
            {cart.length > 0 ? "Listo para enviar" : "Carrito vacio"}
          </Badge>
        </div>

        <Table>
          <thead>
            <tr>
              <th>Implemento</th>
              <th>Cantidad</th>
              <th>Accion</th>
            </tr>
          </thead>
          <tbody>
            {cart.length === 0 ? (
              <tr>
                <td colSpan={3} className="table-hint">No hay implementos en el carrito.</td>
              </tr>
            ) : (
              cart.map((item) => (
                <tr key={item.implement_uuid}>
                  <td>{item.implement_name}</td>
                  <td style={{ maxWidth: 140 }}>
                    <Input
                      type="number"
                      min={1}
                      value={String(item.requested_quantity)}
                      disabled={saving}
                      onChange={(event) => updateQuantity(item.implement_uuid, event.target.value)}
                    />
                  </td>
                  <td>
                    <Button
                      variant="danger"
                      size="sm"
                      disabled={saving}
                      onClick={() => removeImplement(item.implement_uuid)}
                    >
                      Eliminar
                    </Button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </Table>

        <div className="loan-submit">
          <Button disabled={!canSubmit} onClick={() => void submitLoan()}>
            {saving ? "Enviando solicitud..." : "Enviar solicitud"}
          </Button>
        </div>
      </section>
    </>
  );

  if (embedded) {
    return content;
  }

  return content;
}
