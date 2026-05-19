import { Plus, Pencil, Trash2 } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import {
  changeUserRole,
  createUser,
  deleteUser,
  listUsers,
  updateUser,
  type AdminRole,
  type UserAdminSummary,
} from "../services/userAdminService";

type CreateRole = "COORDINADOR" | "DOCENTE";

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

function digitsOnly(value: string): string {
  return value.replace(/\D/g, "").slice(0, 9);
}

function formatRutDisplay(value: string): string {
  const digits = digitsOnly(value);
  if (!digits) return "";
  const body = digits.slice(0, -1);
  const dv = digits.slice(-1);
  const withDots = body.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return body.length > 0 ? `${withDots}-${dv}` : dv;
}

function isValidRutDigits(value: string): boolean {
  const clean = digitsOnly(value);
  if (clean.length < 8 || clean.length > 9) return false;
  const rutWithoutDv = clean.slice(0, -1);
  return /^\d+$/.test(rutWithoutDv) && (rutWithoutDv.length === 7 || rutWithoutDv.length === 8);
}

export function DirectorCreateUserPage({ embedded = false }: { embedded?: boolean }) {
  const [form, setForm] = useState(INITIAL_FORM);
  const [users, setUsers] = useState<UserAdminSummary[]>([]);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [updatingUserId, setUpdatingUserId] = useState<string | null>(null);
  const [creatingOpen, setCreatingOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<UserAdminSummary | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);

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
    void loadUsers();
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

  async function handleSubmitCreate(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSuccess(null);

    const validationError = validateCreateForm();
    if (validationError) {
      setError(validationError);
      return;
    }

    const cleanDigits = digitsOnly(form.rut);
    if (!isValidRutDigits(form.rut)) {
      setError("El RUT no es válido.");
      return;
    }

    setSubmitting(true);
    try {
      await createUser({
        name: form.name.trim(),
        rut: cleanDigits.slice(0, -1),
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
    setUpdatingUserId(userRef);
    try {
      await changeUserRole(userRef, role);
      setSuccess(`Rol actualizado para ${user.name}.`);
      await loadUsers();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No fue posible actualizar el rol."));
    } finally {
      setUpdatingUserId(null);
    }
  }

  async function handleDelete(user: UserAdminSummary) {
    const confirmation = window.confirm(`¿Eliminar usuario ${user.name}? Esta acción lo desactivará.`);
    if (!confirmation) return;

    setError(null);
    setSuccess(null);
    const userRef = getUserRef(user);
    setUpdatingUserId(userRef);
    try {
      await deleteUser(userRef);
      setSuccess(`Usuario ${user.name} eliminado (desactivado).`);
      await loadUsers();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No fue posible eliminar usuario."));
    } finally {
      setUpdatingUserId(null);
    }
  }

  async function handleUpdateUser(event: React.FormEvent) {
    event.preventDefault();
    if (!editingUser) return;

    if (!editingUser.name.trim()) {
      setError("El nombre es obligatorio.");
      return;
    }

    const cleanEditingDigits = digitsOnly(editingUser.rut);
    if (!isValidRutDigits(editingUser.rut)) {
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
    setUpdatingUserId(userRef);
    try {
      await updateUser(userRef, {
        name: editingUser.name.trim(),
        rut: cleanEditingDigits.slice(0, -1),
        email: (editingUser.email ?? "").trim().toLowerCase(),
      });
      setSuccess(`Usuario ${editingUser.name} actualizado.`);
      setEditingUser(null);
      await loadUsers();
    } catch (requestError) {
      setError(getErrorMessage(requestError, "No fue posible actualizar el usuario."));
    } finally {
      setUpdatingUserId(null);
    }
  }

  const sortedUsers = useMemo(() => [...users].sort((a, b) => a.name.localeCompare(b.name)), [users]);

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
          <p>Modificar datos, cambiar rol y eliminar (desactivar) usuarios.</p>
        </div>

        {loadingUsers ? <div className="field-hint">Cargando usuarios...</div> : null}

        {!loadingUsers ? (
          <div className="table-wrapper">
            <table className="category-table">
              <thead>
                <tr>
                  <th>UUID</th>
                  <th>Nombre</th>
                  <th>RUT</th>
                  <th>Correo</th>
                  <th>Rol</th>
                  <th>Estado</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {sortedUsers.map((user) => (
                  <tr key={getUserRef(user)}>
                    <td>{user.uuid}</td>
                    <td>{user.name}</td>
                    <td>{formatRutDisplay(user.rut)}</td>
                    <td>{user.email ?? "-"}</td>
                    <td>
                      <select
                        value={user.role}
                        onChange={(event) => void handleRoleChange(user, event.target.value as AdminRole)}
                        disabled={updatingUserId === getUserRef(user) || !user.active}
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
                          onClick={() => setEditingUser({ ...user, rut: formatRutDisplay(user.rut), email: user.email ?? "" })}
                          disabled={updatingUserId === getUserRef(user)}
                        >
                          <Pencil size={14} /> Editar
                        </button>
                        <button
                          type="button"
                          className="button button--ghost button--table"
                          onClick={() => void handleDelete(user)}
                          disabled={updatingUserId === getUserRef(user) || !user.active}
                        >
                          <Trash2 size={14} /> Eliminar
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
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
              value={formatRutDisplay(form.rut)}
              onChange={(event) => onChange("rut", digitsOnly(event.target.value))}
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
              <button type="button" className="button button--ghost" onClick={() => setCreatingOpen(false)} disabled={submitting}>Cancelar</button>
              <button type="submit" className="button" disabled={submitting}>{submitting ? "Creando..." : "Crear usuario"}</button>
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
              onChange={(event) => setEditingUser((prev) => prev ? ({ ...prev, name: event.target.value }) : prev)}
            />

            <label>RUT</label>
            <input
              value={formatRutDisplay(editingUser.rut)}
              onChange={(event) => setEditingUser((prev) => prev ? ({ ...prev, rut: digitsOnly(event.target.value) }) : prev)}
              placeholder="12.345.678-9"
            />

            <label>Correo</label>
            <input
              type="email"
              value={editingUser.email ?? ""}
              onChange={(event) => setEditingUser((prev) => prev ? ({ ...prev, email: event.target.value }) : prev)}
              placeholder="correo@duocuc.cl"
              required
            />

            <div className="modal-actions">
              <button type="button" className="button button--ghost" onClick={() => setEditingUser(null)} disabled={updatingUserId === getUserRef(editingUser)}>Cancelar</button>
              <button type="submit" className="button" disabled={updatingUserId === getUserRef(editingUser)}>{updatingUserId === getUserRef(editingUser) ? "Guardando..." : "Guardar cambios"}</button>
            </div>
          </form>
        </div>
      ) : null}
    </>
  );

  return embedded ? content : content;
}
  function getUserRef(user: UserAdminSummary): string {
    return user.uuid;
  }
