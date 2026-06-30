import { Pencil, Plus, Power, RotateCcw, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { ConfirmModal } from "../components/categories/ConfirmModal";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import {
  changeUserRole,
  createUser,
  deleteUser,
  listUsers,
  setUserActive,
  updateUser,
  type AdminRole,
  type UserAdminSummary,
} from "../services/userAdminService";
import { getSessionUser } from "../utils/auth";
import { cleanRut, formatRut, isValidRut } from "../utils/rut";

type CreateRole = "COORDINADOR" | "DOCENTE";
type UserActionKind = "role" | "edit" | "deactivate" | "reactivate" | "delete";
type ConfirmAction = {
  kind: "deactivate" | "delete";
  user: UserAdminSummary;
};

const ROLE_LABELS: Record<AdminRole, string> = {
  DIRECTOR: "Director",
  COORDINADOR: "Coordinador de laboratorio",
  DOCENTE: "Docente",
};

const INITIAL_FORM = {
  name: "",
  rut: "",
  email: "",
  password: "",
  role: "COORDINADOR" as CreateRole,
};

const SYSTEM_OUTBOX_USER_UUID = "99999999-9999-9999-9999-999999999999";

export function DirectorCreateUserPage({ embedded = false }: { embedded?: boolean }) {
  const [form, setForm] = useState(INITIAL_FORM);
  const [users, setUsers] = useState<UserAdminSummary[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [pendingAction, setPendingAction] = useState<{ userRef: string; kind: UserActionKind } | null>(null);
  const [creatingOpen, setCreatingOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserAdminSummary | null>(null);
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const sessionUser = useMemo(() => getSessionUser(), []);

  async function loadUsers() {
    setLoadingUsers(true);
    try {
      const rows = await listUsers();
      setUsers(rows);
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No fue posible cargar usuarios."));
    } finally {
      setLoadingUsers(false);
    }
  }

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      void loadUsers();
    }, 0);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, []);

  function onChange<K extends keyof typeof INITIAL_FORM>(key: K, value: (typeof INITIAL_FORM)[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  function validateCreateForm(): string | null {
    if (!form.name.trim()) return "El nombre es obligatorio.";
    if (!form.email.trim()) return "El correo es obligatorio.";
    if (!form.password.trim()) return "La contraseña es obligatoria.";
    if (form.password.trim().length < 6) return "La contraseña debe tener al menos 6 caracteres.";
    return null;
  }

  function getUserRef(user: UserAdminSummary): string {
    return user.uuid;
  }

  function isSystemUser(user: UserAdminSummary): boolean {
    return user.uuid === SYSTEM_OUTBOX_USER_UUID;
  }

  function isCurrentUser(user: UserAdminSummary): boolean {
    return sessionUser?.id === user.uuid;
  }

  function isRowBusy(user: UserAdminSummary): boolean {
    return pendingAction?.userRef === getUserRef(user);
  }

  function resolveUserActionError(requestError: unknown, fallbackMessage: string): string {
    const payload = getApiErrorPayload(requestError);
    switch (payload?.code) {
      case "USER_SELF_DEACTIVATION_NOT_ALLOWED":
        return "No puedes desactivar tu propio usuario.";
      case "USER_SYSTEM_DEACTIVATION_NOT_ALLOWED":
        return "El usuario tecnico del sistema no se puede desactivar.";
      case "USER_DELETE_REQUIRES_INACTIVE":
        return "Primero debes desactivar al usuario antes de eliminarlo.";
      case "USER_DELETE_NOT_ALLOWED":
        return "No se puede eliminar el usuario porque tiene historial asociado.";
      case "USER_SYSTEM_DELETION_NOT_ALLOWED":
        return "El usuario tecnico del sistema no se puede eliminar.";
      case "USER_SELF_DELETION_NOT_ALLOWED":
        return "No puedes eliminar tu propio usuario.";
      default:
        return getErrorMessage(requestError, fallbackMessage);
    }
  }

  async function handleSubmitCreate(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSuccess(null);

    const validationError = validateCreateForm();
    if (validationError) {
      setError(validationError);
      return;
    }

    const normalizedRut = cleanRut(form.rut);
    if (!isValidRut(form.rut)) {
      setError("El RUT no es válido.");
      return;
    }

    setSubmitting(true);
    try {
      await createUser({
        name: form.name.trim(),
        rut: normalizedRut,
        email: form.email.trim().toLowerCase(),
        password: form.password.trim(),
        role: form.role,
      });
      setSuccess("Usuario creado correctamente.");
      setForm(INITIAL_FORM);
      setCreatingOpen(false);
      await loadUsers();
    } catch (requestError) {
      const payload = getApiErrorPayload(requestError);
      if (payload?.code === "USER_DUPLICATED") {
        setError("Ya existe un usuario con ese RUT o correo.");
      } else if (payload?.code === "USER_EMAIL_REQUIRED") {
        setError("El correo es obligatorio.");
      } else if (payload?.code === "ROLE_NOT_SUPPORTED") {
        setError("El rol ingresado no es valido.");
      } else if (payload?.code === "ACCESS_DENIED") {
        setError("No tienes permisos para crear usuarios.");
      } else {
        setError(getErrorMessage(requestError, "No fue posible crear el usuario."));
      }
    } finally {
      setSubmitting(false);
    }
  }

  async function handleRoleChange(user: UserAdminSummary, role: AdminRole) {
    if (user.role === role) return;
    setError(null);
    setSuccess(null);
    const userRef = getUserRef(user);
    setPendingAction({ userRef, kind: "role" });
    try {
      await changeUserRole(userRef, role);
      setSuccess(`Rol actualizado para ${user.name}.`);
      await loadUsers();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No fue posible actualizar el rol."));
    } finally {
      setPendingAction(null);
    }
  }

  async function handleSetUserActive(user: UserAdminSummary, active: boolean) {
    setError(null);
    setSuccess(null);
    const userRef = getUserRef(user);
    setPendingAction({ userRef, kind: active ? "reactivate" : "deactivate" });
    try {
      await setUserActive(userRef, active);
      setSuccess(active ? `Usuario ${user.name} reactivado.` : `Usuario ${user.name} desactivado.`);
      setConfirmAction(null);
      await loadUsers();
    } catch (requestError) {
      setError(resolveUserActionError(requestError, active ? "No fue posible reactivar usuario." : "No fue posible desactivar usuario."));
    } finally {
      setPendingAction(null);
    }
  }

  async function handleDelete(user: UserAdminSummary) {
    setError(null);
    setSuccess(null);
    const userRef = getUserRef(user);
    setPendingAction({ userRef, kind: "delete" });
    try {
      await deleteUser(userRef);
      setSuccess(`Usuario ${user.name} eliminado definitivamente.`);
      setConfirmAction(null);
      await loadUsers();
    } catch (requestError) {
      setError(resolveUserActionError(requestError, "No fue posible eliminar usuario."));
    } finally {
      setPendingAction(null);
    }
  }

  async function handleUpdateUser(event: React.FormEvent) {
    event.preventDefault();
    if (!editingUser) return;

    if (!editingUser.name.trim()) {
      setError("El nombre es obligatorio.");
      return;
    }

    const normalizedRut = cleanRut(editingUser.rut);
    if (!isValidRut(editingUser.rut)) {
      setError("El RUT no es válido.");
      return;
    }
    if (!(editingUser.email ?? "").trim()) {
      setError("El correo es obligatorio.");
      return;
    }

    setError(null);
    setSuccess(null);
    const userRef = getUserRef(editingUser);
    setPendingAction({ userRef, kind: "edit" });
    try {
      await updateUser(userRef, {
        name: editingUser.name.trim(),
        rut: normalizedRut,
        email: (editingUser.email ?? "").trim().toLowerCase(),
      });
      setSuccess(`Usuario ${editingUser.name} actualizado.`);
      setEditingUser(null);
      await loadUsers();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No fue posible actualizar el usuario."));
    } finally {
      setPendingAction(null);
    }
  }

  const sortedUsers = useMemo(() => [...users].sort((a, b) => a.name.localeCompare(b.name)), [users]);
  const editingUserBusy = editingUser ? isRowBusy(editingUser) : false;

  const content = (
    <>
      <section className="content-header">
        <div>
          <h1>Gestion de usuarios</h1>
          <p>Administración de usuarios por Director de carrera.</p>
        </div>
        <div className="content-header__actions">
          <button type="button" className="button" onClick={() => setCreatingOpen(true)}>
            <Plus size={16} /> Nuevo usuario
          </button>
        </div>
      </section>

      {error ? <div className="error-banner">{error}</div> : null}
      {success ? <div className="success-banner">{success}</div> : null}

      <section className="panel">
        <div className="panel__head">
          <h2>Usuarios actuales</h2>
          <p>Los usuarios activos se desactivan primero. Solo los inactivos pueden eliminarse de forma definitiva.</p>
        </div>

        {loadingUsers ? <div className="field-hint">Cargando usuarios...</div> : null}

        {!loadingUsers ? (
          <div className="table-wrapper">
            <table className="category-table">
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>RUT</th>
                  <th>Correo</th>
                  <th>Rol</th>
                  <th>Estado</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {sortedUsers.map((user) => {
                  const rowBusy = isRowBusy(user);
                  const selfUser = isCurrentUser(user);
                  const systemUser = isSystemUser(user);
                  const protectedUser = systemUser || selfUser;
                  const destructiveActionTitle = systemUser
                    ? "El usuario tecnico del sistema no admite acciones destructivas."
                    : selfUser
                      ? "No puedes aplicar acciones destructivas sobre tu propio usuario."
                      : undefined;

                  return (
                    <tr key={getUserRef(user)}>
                      <td>{user.name}</td>
                      <td>{formatRut(user.rut)}</td>
                      <td>{user.email ?? "-"}</td>
                      <td>
                        <select
                          value={user.role}
                          onChange={(event) => void handleRoleChange(user, event.target.value as AdminRole)}
                          disabled={rowBusy || !user.active}
                        >
                          <option value="DIRECTOR">{ROLE_LABELS.DIRECTOR}</option>
                          <option value="COORDINADOR">{ROLE_LABELS.COORDINADOR}</option>
                          <option value="DOCENTE">{ROLE_LABELS.DOCENTE}</option>
                        </select>
                      </td>
                      <td>
                        <span className={user.active ? "badge badge--active" : "badge badge--inactive"}>
                          {user.active ? "Activo" : "Inactivo"}
                        </span>
                      </td>
                      <td>
                        <div className="table-actions">
                          <button
                            type="button"
                            className="button button--ghost button--table"
                            onClick={() => setEditingUser({ ...user, rut: formatRut(user.rut), email: user.email ?? "" })}
                            disabled={rowBusy}
                          >
                            <Pencil size={14} /> {rowBusy && pendingAction?.kind === "edit" ? "Guardando..." : "Editar"}
                          </button>

                          {user.active ? (
                            <button
                              type="button"
                              className="button button--table button--warn"
                              onClick={() => setConfirmAction({ kind: "deactivate", user })}
                              disabled={rowBusy || protectedUser}
                              title={destructiveActionTitle}
                            >
                              <Power size={14} /> {rowBusy && pendingAction?.kind === "deactivate" ? "Procesando..." : "Desactivar"}
                            </button>
                          ) : (
                            <>
                              <button
                                type="button"
                                className="button button--table"
                                onClick={() => void handleSetUserActive(user, true)}
                                disabled={rowBusy}
                              >
                                <RotateCcw size={14} /> {rowBusy && pendingAction?.kind === "reactivate" ? "Procesando..." : "Reactivar"}
                              </button>
                              <button
                                type="button"
                                className="button button--table button--danger"
                                onClick={() => setConfirmAction({ kind: "delete", user })}
                                disabled={rowBusy || protectedUser}
                                title={destructiveActionTitle}
                              >
                                <Trash2 size={14} /> {rowBusy && pendingAction?.kind === "delete" ? "Procesando..." : "Eliminar"}
                              </button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>

      {creatingOpen ? (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-label="Crear usuario">
          <form className="modal" onSubmit={handleSubmitCreate}>
            <h3>Nuevo usuario</h3>
            <p>Completa los datos para crear un usuario.</p>

            <label>Nombre</label>
            <input value={form.name} onChange={(event) => onChange("name", event.target.value)} disabled={submitting} />

            <label>RUT</label>
            <input
              inputMode="text"
              value={formatRut(form.rut)}
              onChange={(event) => onChange("rut", cleanRut(event.target.value))}
              placeholder="12.345.678-9"
              disabled={submitting}
            />

            <label>Correo</label>
            <input
              type="email"
              value={form.email}
              onChange={(event) => onChange("email", event.target.value)}
              placeholder="correo@duocuc.cl"
              required
              disabled={submitting}
            />

            <label>Contraseña</label>
            <input type="password" value={form.password} onChange={(event) => onChange("password", event.target.value)} disabled={submitting} />

            <label>Rol</label>
            <select value={form.role} onChange={(event) => onChange("role", event.target.value as CreateRole)} disabled={submitting}>
              <option value="COORDINADOR">Coordinador de laboratorio</option>
              <option value="DOCENTE">Docente</option>
            </select>

            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={() => setCreatingOpen(false)} disabled={submitting}>
                Cancelar
              </button>
              <button type="submit" className="button" disabled={submitting}>
                {submitting ? "Creando..." : "Crear usuario"}
              </button>
            </div>
          </form>
        </div>
      ) : null}

      {editingUser ? (
        <div className="modal-overlay" role="dialog" aria-modal="true" aria-label="Editar usuario">
          <form className="modal" onSubmit={handleUpdateUser}>
            <h3>Editar usuario</h3>
            <p>Actualiza los datos del usuario.</p>

            <label>Nombre</label>
            <input
              value={editingUser.name}
              onChange={(event) => setEditingUser((prev) => prev ? { ...prev, name: event.target.value } : prev)}
              disabled={editingUserBusy}
            />

            <label>RUT</label>
            <input
              inputMode="text"
              value={formatRut(editingUser.rut)}
              onChange={(event) => setEditingUser((prev) => prev ? { ...prev, rut: cleanRut(event.target.value) } : prev)}
              placeholder="12.345.678-9"
              disabled={editingUserBusy}
            />

            <label>Correo</label>
            <input
              type="email"
              value={editingUser.email ?? ""}
              onChange={(event) => setEditingUser((prev) => prev ? { ...prev, email: event.target.value } : prev)}
              placeholder="correo@duocuc.cl"
              required
              disabled={editingUserBusy}
            />

            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={() => setEditingUser(null)} disabled={editingUserBusy}>
                Cancelar
              </button>
              <button type="submit" className="button" disabled={editingUserBusy}>
                {editingUserBusy ? "Guardando..." : "Guardar cambios"}
              </button>
            </div>
          </form>
        </div>
      ) : null}

      <ConfirmModal
        isOpen={confirmAction?.kind === "deactivate"}
        title="Desactivar usuario"
        message={
          confirmAction?.kind === "deactivate"
            ? `El usuario ${confirmAction.user.name} perdera acceso al sistema hasta que sea reactivado.`
            : ""
        }
        confirmLabel="Desactivar"
        tone="warn"
        loading={confirmAction?.kind === "deactivate" && isRowBusy(confirmAction.user)}
        onClose={() => {
          if (confirmAction?.kind === "deactivate" && !isRowBusy(confirmAction.user)) {
            setConfirmAction(null);
          }
        }}
        onConfirm={async () => {
          if (confirmAction?.kind === "deactivate") {
            await handleSetUserActive(confirmAction.user, false);
          }
        }}
      />

      <ConfirmModal
        isOpen={confirmAction?.kind === "delete"}
        title="Eliminar usuario"
        message={
          confirmAction?.kind === "delete"
            ? `La eliminacion de ${confirmAction.user.name} es definitiva y puede fallar si el usuario tiene historial asociado.`
            : ""
        }
        confirmLabel="Eliminar definitivamente"
        tone="danger"
        loading={confirmAction?.kind === "delete" && isRowBusy(confirmAction.user)}
        onClose={() => {
          if (confirmAction?.kind === "delete" && !isRowBusy(confirmAction.user)) {
            setConfirmAction(null);
          }
        }}
        onConfirm={async () => {
          if (confirmAction?.kind === "delete") {
            await handleDelete(confirmAction.user);
          }
        }}
      />
    </>
  );

  return embedded ? content : content;
}
