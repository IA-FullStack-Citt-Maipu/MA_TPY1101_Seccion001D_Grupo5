import { ImageOff, Info, Link2, Save } from "lucide-react";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { InventoryLayout } from "../components/layout/InventoryLayout";
import { fetchActiveCategories } from "../services/activeCategoryService";
import { getApiErrorPayload, getErrorMessage } from "../services/apiClient";
import { createImplement, fetchImplementById, updateImplement } from "../services/implementService";
import { fetchLocations } from "../services/locationService";
import type { ActiveCategoryOption } from "../types/categoryActive";
import type { ImplementDetail } from "../types/implement";
import type { LocationOption } from "../types/location";

type ItemType = "consumable" | "reusable" | "individual";

interface FieldErrors {
  name?: string;
  categoryUuid?: string;
  itemType?: string;
  locationUuid?: string;
  minStock?: string;
  description?: string;
  barcode?: string;
  imgUrl?: string;
  observations?: string;
  form?: string;
}

const ITEM_TYPE_OPTIONS: Array<{ value: ItemType; label: string }> = [
  { value: "consumable", label: "Consumible" },
  { value: "reusable", label: "Reutilizable" },
  { value: "individual", label: "Individual" },
];

function mapApiErrorToFields(message: string): FieldErrors {
  const normalized = message.toLowerCase();
  const errors: FieldErrors = {};

  if (normalized.includes("nombre")) {
    errors.name = message;
    return errors;
  }
  if (normalized.includes("categoria")) {
    errors.categoryUuid = message;
    return errors;
  }
  if (normalized.includes("tipo")) {
    errors.itemType = message;
    return errors;
  }
  if (normalized.includes("ubicacion")) {
    errors.locationUuid = message;
    return errors;
  }
  if (normalized.includes("stock minimo")) {
    errors.minStock = message;
    return errors;
  }
  if (normalized.includes("descripcion")) {
    errors.description = message;
    return errors;
  }
  if (normalized.includes("barra")) {
    errors.barcode = message;
    return errors;
  }
  if (normalized.includes("imagen") || normalized.includes("url")) {
    errors.imgUrl = message;
    return errors;
  }
  if (normalized.includes("observaciones")) {
    errors.observations = message;
    return errors;
  }

  errors.form = message;
  return errors;
}

export function InventoryImplementCreatePage({
  embedded = false,
  implementUuid,
}: {
  embedded?: boolean;
  implementUuid?: string;
}) {
  const isEditMode = Boolean(implementUuid);

  const [implement, setImplement] = useState<ImplementDetail | null>(null);
  const [loadingImplement, setLoadingImplement] = useState(false);
  const [name, setName] = useState("");
  const [categoryUuidRaw, setCategoryUuidRaw] = useState("");
  const [itemTypeRaw, setItemTypeRaw] = useState<ItemType | "">("");
  const [locationUuidRaw, setLocationUuidRaw] = useState("");
  const [description, setDescription] = useState("");
  const [barcode, setBarcode] = useState("");
  const [imgUrl, setImgUrl] = useState("");
  const [minStockRaw, setMinStockRaw] = useState("");
  const [observations, setObservations] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [saving, setSaving] = useState(false);

  const [categories, setCategories] = useState<ActiveCategoryOption[]>([]);
  const [loadingCategories, setLoadingCategories] = useState(false);
  const [categoriesError, setCategoriesError] = useState<string | null>(null);

  const [locations, setLocations] = useState<LocationOption[]>([]);
  const [loadingLocations, setLoadingLocations] = useState(false);
  const [locationsError, setLocationsError] = useState<string | null>(null);

  const [previewFailed, setPreviewFailed] = useState(false);
  const normalizedImgUrl = imgUrl.trim();
  const isHttpImageUrl = /^https?:\/\//i.test(normalizedImgUrl);

  const pageTitle = isEditMode ? "Edicion de implemento" : "Agregar Nuevo Implemento";
  const pageDescription = isEditMode
    ? "Actualiza los detalles tecnicos, operativos y visuales del implemento."
    : "Completa los detalles tecnicos del nuevo equipo o insumo medico.";
  const cancelHash = isEditMode && implementUuid ? `#/inventory/implementos/${implementUuid}` : "#/inventory/implementos";

  function resetForm() {
    setImplement(null);
    setName("");
    setCategoryUuidRaw("");
    setItemTypeRaw("");
    setLocationUuidRaw("");
    setDescription("");
    setBarcode("");
    setImgUrl("");
    setMinStockRaw("");
    setObservations("");
    setFieldErrors({});
    setPreviewFailed(false);
  }

  function applyImplementToForm(detail: ImplementDetail) {
    setImplement(detail);
    setName(detail.name ?? "");
    setCategoryUuidRaw(detail.category_uuid ?? "");
    setItemTypeRaw(detail.item_type ?? "");
    setLocationUuidRaw(detail.location_uuid ?? "");
    setDescription(detail.description ?? "");
    setBarcode(detail.barcode ?? "");
    setImgUrl(detail.img_url ?? "");
    setMinStockRaw(detail.min_stock == null ? "" : String(detail.min_stock));
    setObservations(detail.observations ?? "");
  }

  useEffect(() => {
    let cancelled = false;

    setLoadingCategories(true);
    setCategoriesError(null);
    fetchActiveCategories()
      .then((result) => {
        if (cancelled) return;
        setCategories(result);
      })
      .catch((error) => {
        if (cancelled) return;
        setCategoriesError(getErrorMessage(error, "No se pudieron cargar las categorias activas."));
        setCategories([]);
      })
      .finally(() => {
        if (cancelled) return;
        setLoadingCategories(false);
      });

    setLoadingLocations(true);
    setLocationsError(null);
    fetchLocations()
      .then((result) => {
        if (cancelled) return;
        setLocations(result);
      })
      .catch((error) => {
        if (cancelled) return;
        setLocationsError(getErrorMessage(error, "No se pudieron cargar las ubicaciones."));
        setLocations([]);
      })
      .finally(() => {
        if (cancelled) return;
        setLoadingLocations(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    setFieldErrors({});

    if (!implementUuid) {
      setLoadingImplement(false);
      resetForm();
      return () => {
        cancelled = true;
      };
    }

    setLoadingImplement(true);
    setImplement(null);
    fetchImplementById(implementUuid)
      .then((detail) => {
        if (cancelled) return;
        applyImplementToForm(detail);
      })
      .catch((error) => {
        if (cancelled) return;
        setFieldErrors({
          form: getErrorMessage(error, "No se pudo cargar el implemento para editar."),
        });
      })
      .finally(() => {
        if (cancelled) return;
        setLoadingImplement(false);
      });

    return () => {
      cancelled = true;
    };
  }, [implementUuid]);

  useEffect(() => {
    setPreviewFailed(false);
  }, [normalizedImgUrl]);

  const isCategoryDisabled = useMemo(
    () => loadingCategories || (categories.length === 0 && !implement?.category),
    [categories.length, implement?.category, loadingCategories],
  );
  const isLocationDisabled = useMemo(
    () => loadingLocations || (locations.length === 0 && !locationUuidRaw.trim()),
    [loadingLocations, locationUuidRaw, locations.length],
  );

  const currentCategoryInactive = Boolean(implement?.category && !implement.category.active);
  const inactiveCategoryOption =
    currentCategoryInactive && implement?.category
      ? {
          uuid: implement.category.uuid,
          name: implement.category.name,
        }
      : null;

  const isUsingInactiveCategory = useMemo(() => {
    if (!inactiveCategoryOption) {
      return false;
    }
    return categoryUuidRaw.trim() === inactiveCategoryOption.uuid;
  }, [categoryUuidRaw, inactiveCategoryOption]);

  function validateClientSide(): FieldErrors {
    const errors: FieldErrors = {};
    const categoryUuid = categoryUuidRaw.trim();
    const locationUuid = locationUuidRaw.trim();
    const minStock = minStockRaw.trim() ? Number(minStockRaw) : NaN;
    const imgUrlValue = imgUrl.trim();

    if (name.trim().length === 0) {
      errors.name = "El nombre es obligatorio.";
    }
    if (name.trim().length > 150) {
      errors.name = "El nombre no puede superar 150 caracteres.";
    }
    if (!categoryUuid) {
      errors.categoryUuid = "La categoria es obligatoria.";
    }
    if (currentCategoryInactive && isUsingInactiveCategory) {
      errors.categoryUuid = "Debes seleccionar una categoria activa para guardar.";
    }
    if (itemTypeRaw.trim().length === 0) {
      errors.itemType = "El tipo de implemento es obligatorio.";
    }
    if (!locationUuid) {
      errors.locationUuid = "La ubicacion es obligatoria.";
    }
    if (!Number.isFinite(minStock) || minStock <= 0 || !Number.isInteger(minStock)) {
      errors.minStock = "El stock minimo debe ser un entero positivo.";
    }
    if (description.trim().length > 2000) {
      errors.description = "La descripcion no puede superar 2000 caracteres.";
    }
    if (observations.trim().length > 500) {
      errors.observations = "Las observaciones no pueden superar 500 caracteres.";
    }
    if (barcode.trim().length > 100) {
      errors.barcode = "El codigo de barras no puede superar 100 caracteres.";
    }
    if (imgUrlValue.length > 2000) {
      errors.imgUrl = "La URL de imagen no puede superar 2000 caracteres.";
    }
    if (imgUrlValue.length > 0 && !/^https?:\/\//i.test(imgUrlValue)) {
      errors.imgUrl = "La URL de imagen debe comenzar con http:// o https://.";
    }

    return errors;
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFieldErrors({});

    const clientErrors = validateClientSide();
    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors);
      return;
    }

    setSaving(true);
    try {
      if (isEditMode && implementUuid) {
        const updated = await updateImplement(implementUuid, {
          name: name.trim(),
          categoryUuid: categoryUuidRaw.trim(),
          item_type: itemTypeRaw as ItemType,
          locationUuid: locationUuidRaw.trim(),
          description: description.trim() ? description.trim() : null,
          barcode: barcode.trim() ? barcode.trim() : null,
          img_url: normalizedImgUrl ? normalizedImgUrl : null,
          min_stock: Number(minStockRaw),
          observations: observations.trim() ? observations.trim() : null,
        });
        window.location.hash = `#/inventory/implementos/${updated.uuid}`;
        return;
      }

      const created = await createImplement({
        name: name.trim(),
        categoryUuid: categoryUuidRaw.trim(),
        item_type: itemTypeRaw as ItemType,
        locationUuid: locationUuidRaw.trim(),
        description: description.trim() ? description.trim() : null,
        barcode: barcode.trim() ? barcode.trim() : null,
        img_url: normalizedImgUrl ? normalizedImgUrl : null,
        min_stock: Number(minStockRaw),
        observations: observations.trim() ? observations.trim() : null,
      });

      try {
        window.sessionStorage.setItem("inventory.justCreatedImplementId", created.uuid);
      } catch {
        // Si el storage no esta disponible, la redireccion igualmente debe continuar.
      }

      window.location.hash = `#/inventory/implementos/${created.uuid}`;
    } catch (error) {
      const payload = getApiErrorPayload(error);
      const message = payload?.message ?? getErrorMessage(error, isEditMode ? "No se pudo actualizar el implemento." : "No se pudo crear el implemento.");
      setFieldErrors(mapApiErrorToFields(message));
    } finally {
      setSaving(false);
    }
  }

  const previewMessage = useMemo(() => {
    if (normalizedImgUrl.length === 0) {
      return "Ingresa una URL para previsualizar la imagen";
    }
    if (!isHttpImageUrl) {
      return "La URL debe comenzar con http:// o https://";
    }
    if (previewFailed) {
      return "No se pudo cargar la imagen";
    }
    return null;
  }, [isHttpImageUrl, normalizedImgUrl.length, previewFailed]);

  const content = (
    <div className="implement-create-page">
      <section className="content-header implement-create-header">
        <div>
          <p className="inventory-items-header__eyebrow">Inventario</p>
          <h1>{pageTitle}</h1>
          <p>{pageDescription}</p>
        </div>
      </section>

      {fieldErrors.form ? <div className="error-banner">{fieldErrors.form}</div> : null}
      {isEditMode && loadingImplement ? (
        <section className="panel">
          <p className="text-muted">Cargando informacion del implemento...</p>
        </section>
      ) : null}
      {isEditMode && implement && !loadingImplement ? (
        <p className="field-hint">Editando implemento {implement.name}</p>
      ) : null}

      <section className="implement-create-layout">
        <article className="panel implement-create-panel">
          <form className="implement-create-form" onSubmit={handleSubmit}>
            <div className="implement-create-form__grid">
              <div className="implement-create-form__field implement-create-form__field--full">
                <label htmlFor="implement-create-name">Nombre del implemento *</label>
                <input
                  id="implement-create-name"
                  value={name}
                  onChange={(event) => {
                    setName(event.target.value);
                    setFieldErrors((current) => ({ ...current, name: undefined }));
                  }}
                  placeholder="Ej. Monitor multiparametro"
                  maxLength={150}
                  required
                  disabled={saving || loadingImplement}
                />
                {fieldErrors.name ? <p className="field-error">{fieldErrors.name}</p> : null}
              </div>

              <div className="implement-create-form__field">
                <label htmlFor="implement-create-category">Categoria *</label>
                <select
                  id="implement-create-category"
                  value={categoryUuidRaw}
                  onChange={(event) => {
                    setCategoryUuidRaw(event.target.value);
                    setFieldErrors((current) => ({ ...current, categoryUuid: undefined }));
                  }}
                  disabled={isCategoryDisabled || saving || loadingImplement}
                >
                  {inactiveCategoryOption ? (
                    <option value={inactiveCategoryOption.uuid} disabled>
                      {inactiveCategoryOption.name} [Inactiva]
                    </option>
                  ) : null}
                  <option value="">Seleccionar...</option>
                  {categories.map((category) => (
                    <option key={category.uuid} value={category.uuid}>
                      {category.name}
                    </option>
                  ))}
                </select>
                {categoriesError ? <p className="field-error">{categoriesError}</p> : null}
                {fieldErrors.categoryUuid ? <p className="field-error">{fieldErrors.categoryUuid}</p> : null}
              </div>

              <div className="implement-create-form__field">
                <label htmlFor="implement-create-location">Ubicacion *</label>
                <select
                  id="implement-create-location"
                  value={locationUuidRaw}
                  onChange={(event) => {
                    setLocationUuidRaw(event.target.value);
                    setFieldErrors((current) => ({ ...current, locationUuid: undefined }));
                  }}
                  disabled={isLocationDisabled || saving || loadingImplement}
                >
                  <option value="">Seleccionar...</option>
                  {locations.map((location) => (
                    <option key={location.uuid} value={location.uuid}>
                      {location.name}
                    </option>
                  ))}
                </select>
                {locationsError ? <p className="field-error">{locationsError}</p> : null}
                {fieldErrors.locationUuid ? <p className="field-error">{fieldErrors.locationUuid}</p> : null}
              </div>

              <div className="implement-create-form__field">
                <label htmlFor="implement-create-item-type">Tipo de item *</label>
                <select
                  id="implement-create-item-type"
                  value={itemTypeRaw}
                  onChange={(event) => {
                    setItemTypeRaw(event.target.value as ItemType | "");
                    setFieldErrors((current) => ({ ...current, itemType: undefined }));
                  }}
                  disabled={saving || loadingImplement}
                >
                  <option value="">Seleccionar...</option>
                  {ITEM_TYPE_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
                {fieldErrors.itemType ? <p className="field-error">{fieldErrors.itemType}</p> : null}
              </div>

              <div className="implement-create-form__field">
                <label htmlFor="implement-create-min-stock">Stock minimo *</label>
                <input
                  id="implement-create-min-stock"
                  type="number"
                  min={1}
                  step={1}
                  value={minStockRaw}
                  onChange={(event) => {
                    setMinStockRaw(event.target.value);
                    setFieldErrors((current) => ({ ...current, minStock: undefined }));
                  }}
                  placeholder="Ej. 5"
                  required
                  disabled={saving || loadingImplement}
                />
                {fieldErrors.minStock ? <p className="field-error">{fieldErrors.minStock}</p> : null}
              </div>

              <div className="implement-create-form__field implement-create-form__field--full">
                <label htmlFor="implement-create-barcode">ID / Codigo de barras</label>
                <input
                  id="implement-create-barcode"
                  value={barcode}
                  onChange={(event) => {
                    setBarcode(event.target.value);
                    setFieldErrors((current) => ({ ...current, barcode: undefined }));
                  }}
                  placeholder="Opcional"
                  maxLength={100}
                  disabled={saving || loadingImplement}
                />
                {fieldErrors.barcode ? <p className="field-error">{fieldErrors.barcode}</p> : null}
              </div>

              <div className="implement-create-form__field implement-create-form__field--full">
                <label htmlFor="implement-create-img-url">URL de imagen</label>
                <div className="implement-create-link-input">
                  <Link2 size={16} />
                  <input
                    id="implement-create-img-url"
                    value={imgUrl}
                    onChange={(event) => {
                      setImgUrl(event.target.value);
                      setFieldErrors((current) => ({ ...current, imgUrl: undefined }));
                    }}
                    placeholder="https://..."
                    maxLength={2000}
                    disabled={saving || loadingImplement}
                  />
                </div>
                {fieldErrors.imgUrl ? <p className="field-error">{fieldErrors.imgUrl}</p> : null}
              </div>

              <div className="implement-create-form__field implement-create-form__field--full">
                <label htmlFor="implement-create-description">Descripcion</label>
                <textarea
                  id="implement-create-description"
                  value={description}
                  onChange={(event) => {
                    setDescription(event.target.value);
                    setFieldErrors((current) => ({ ...current, description: undefined }));
                  }}
                  placeholder="Breve descripcion tecnica..."
                  rows={3}
                  maxLength={2000}
                  disabled={saving || loadingImplement}
                />
                {fieldErrors.description ? <p className="field-error">{fieldErrors.description}</p> : null}
              </div>

              <div className="implement-create-form__field implement-create-form__field--full">
                <label htmlFor="implement-create-observations">Observaciones</label>
                <textarea
                  id="implement-create-observations"
                  value={observations}
                  onChange={(event) => {
                    setObservations(event.target.value);
                    setFieldErrors((current) => ({ ...current, observations: undefined }));
                  }}
                  placeholder="Notas adicionales..."
                  rows={3}
                  maxLength={500}
                  disabled={saving || loadingImplement}
                />
                {fieldErrors.observations ? <p className="field-error">{fieldErrors.observations}</p> : null}
              </div>
            </div>

            <p className="field-hint">
              {isEditMode
                ? "Revisa categoria, ubicacion, stock minimo y datos descriptivos antes de guardar."
                : "Los implementos nuevos quedan con stock inicial 0 hasta registrar su primer ingreso en movimientos."}
            </p>

            <div className="implement-create-form__actions">
              <button
                type="button"
                className="button button--ghost button--lg"
                onClick={() => {
                  window.location.hash = cancelHash;
                }}
                disabled={saving}
              >
                Cancelar
              </button>
              <button
                type="submit"
                className="button button--primary button--lg"
                disabled={
                  saving ||
                  loadingImplement ||
                  name.trim().length === 0 ||
                  categoryUuidRaw.trim().length === 0 ||
                  itemTypeRaw.trim().length === 0 ||
                  locationUuidRaw.trim().length === 0 ||
                  minStockRaw.trim().length === 0 ||
                  (currentCategoryInactive && isUsingInactiveCategory)
                }
              >
                <Save size={16} />
                {saving ? "Guardando..." : isEditMode ? "Guardar cambios" : "Guardar Implemento"}
              </button>
            </div>
          </form>
        </article>

        <aside className="implement-create-side">
          <article className="panel implement-create-preview">
            <h3>Vista previa del implemento</h3>
            <div className={`implement-create-preview__surface${isHttpImageUrl && !previewFailed ? " is-image" : ""}`}>
              {isHttpImageUrl && !previewFailed ? (
                <img
                  src={normalizedImgUrl}
                  alt="Vista previa del implemento"
                  onError={() => setPreviewFailed(true)}
                />
              ) : (
                <div className="implement-create-preview__placeholder">
                  <ImageOff size={44} />
                  <p>{previewMessage}</p>
                </div>
              )}
            </div>
          </article>

          <article className="implement-create-note">
            <header>
              <Info size={16} />
              <strong>Recordatorio</strong>
            </header>
            <p>
              {isEditMode
                ? "Si cambias categoria o ubicacion, valida que sigan alineadas con la operacion real del panol."
                : "Todos los implementos nuevos se registran con estado sin stock hasta que se ingrese la primera entrada de almacen."}
            </p>
          </article>
        </aside>
      </section>
    </div>
  );

  if (embedded) {
    return content;
  }

  return <InventoryLayout activeSection="items">{content}</InventoryLayout>;
}
