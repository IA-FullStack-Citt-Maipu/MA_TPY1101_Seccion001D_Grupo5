import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { fetchActiveCategories } from "../../services/activeCategoryService";
import { getApiErrorPayload, getErrorMessage } from "../../services/apiClient";
import { fetchLocations } from "../../services/locationService";
import type { ActiveCategoryOption } from "../../types/categoryActive";
import type { LocationOption } from "../../types/location";

type ItemType = "fungible" | "no_fungible";

interface CreateImplementFormPayload {
  name: string;
  categoryUuid: string;
  itemType: ItemType;
  locationUuid: string;
  description: string | null;
  barcode: string | null;
  imgUrl: string | null;
  minStock: number;
  observations: string | null;
}

interface ImplementFormModalProps {
  isOpen: boolean;
  saving: boolean;
  onClose: () => void;
  onSubmit: (payload: CreateImplementFormPayload) => Promise<void>;
}

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
  { value: "fungible", label: "Fungible" },
  { value: "no_fungible", label: "No fungible" },
];

export function ImplementFormModal({ isOpen, saving, onClose, onSubmit }: ImplementFormModalProps) {
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

  const [categories, setCategories] = useState<ActiveCategoryOption[]>([]);
  const [loadingCategories, setLoadingCategories] = useState(false);
  const [categoriesError, setCategoriesError] = useState<string | null>(null);

  const [locations, setLocations] = useState<LocationOption[]>([]);
  const [loadingLocations, setLoadingLocations] = useState(false);
  const [locationsError, setLocationsError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

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

    setCategories([]);
    setCategoriesError(null);
    setLoadingCategories(true);
    setLocations([]);
    setLocationsError(null);
    setLoadingLocations(true);

    fetchActiveCategories()
      .then((result) => setCategories(result))
      .catch((error) => {
        setCategoriesError(getErrorMessage(error, "No se pudo cargar las categorias."));
      })
      .finally(() => setLoadingCategories(false));

    fetchLocations()
      .then((result) => setLocations(result))
      .catch((error) => {
        setLocationsError(getErrorMessage(error, "No se pudo cargar las ubicaciones."));
      })
      .finally(() => setLoadingLocations(false));
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    document.body.classList.add("modal-open");
    return () => {
      document.body.classList.remove("modal-open");
    };
  }, [isOpen]);

  const isCategoryDisabled = useMemo(
    () => loadingCategories || Boolean(categoriesError) || categories.length === 0,
    [categories.length, categoriesError, loadingCategories],
  );

  const isLocationDisabled = useMemo(
    () => loadingLocations || Boolean(locationsError) || locations.length === 0,
    [loadingLocations, locations.length, locationsError],
  );

  if (!isOpen) {
    return null;
  }

  function validateClientSide(): FieldErrors {
    const errors: FieldErrors = {};
    const categoryUuid = categoryUuidRaw.trim();
    const locationUuid = locationUuidRaw.trim();
    const minStock = minStockRaw.trim() ? Number(minStockRaw) : NaN;

    if (name.trim().length === 0) {
      errors.name = "El nombre es obligatorio.";
    }
    if (name.trim().length > 150) {
      errors.name = "El nombre no puede superar 150 caracteres.";
    }
    if (!categoryUuid) {
      errors.categoryUuid = "La categoria es obligatoria.";
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

    return errors;
  }

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

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFieldErrors({});

    const clientErrors = validateClientSide();
    if (Object.keys(clientErrors).length > 0) {
      setFieldErrors(clientErrors);
      return;
    }

    const categoryUuid = categoryUuidRaw.trim();
    const locationUuid = locationUuidRaw.trim();
    const minStock = Number(minStockRaw);

    try {
      await onSubmit({
        name: name.trim(),
        categoryUuid,
        itemType: itemTypeRaw as ItemType,
        locationUuid,
        description: description.trim() ? description.trim() : null,
        barcode: barcode.trim() ? barcode.trim() : null,
        imgUrl: imgUrl.trim() ? imgUrl.trim() : null,
        minStock,
        observations: observations.trim() ? observations.trim() : null,
      });
    } catch (error) {
      const payload = getApiErrorPayload(error);
      const message = payload?.message ?? getErrorMessage(error, "No se pudo crear el implemento.");
      setFieldErrors(mapApiErrorToFields(message));
    }
  }

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true">
      <div className="modal">
        <h3>Nuevo implemento</h3>
        <p>Completa la informacion del producto.</p>
        {fieldErrors.form ? <p className="field-error">{fieldErrors.form}</p> : null}

        <form onSubmit={handleSubmit}>
          <label htmlFor="implement-name">Nombre</label>
          <input
            id="implement-name"
            value={name}
            onChange={(event) => {
              setName(event.target.value);
              setFieldErrors((current) => ({ ...current, name: undefined }));
            }}
            placeholder="Ej: Guantes latex"
            maxLength={150}
            required
          />
          {fieldErrors.name ? <p className="field-error">{fieldErrors.name}</p> : null}

          <label htmlFor="implement-category">Categoria</label>
          <select
            id="implement-category"
            value={categoryUuidRaw}
            onChange={(event) => {
              setCategoryUuidRaw(event.target.value);
              setFieldErrors((current) => ({ ...current, categoryUuid: undefined }));
            }}
            disabled={isCategoryDisabled}
          >
            <option value="" disabled>
              Selecciona una categoria
            </option>
            {categories.map((category) => (
              <option key={category.uuid} value={category.uuid}>
                {category.name}
              </option>
            ))}
          </select>
          {categoriesError ? <p className="field-error">{categoriesError}</p> : null}
          {fieldErrors.categoryUuid ? <p className="field-error">{fieldErrors.categoryUuid}</p> : null}

          <label htmlFor="implement-item-type">Tipo de implemento</label>
          <select
            id="implement-item-type"
            value={itemTypeRaw}
            onChange={(event) => {
              setItemTypeRaw(event.target.value as ItemType | "");
              setFieldErrors((current) => ({ ...current, itemType: undefined }));
            }}
          >
            <option value="" disabled>
              Selecciona un tipo
            </option>
            {ITEM_TYPE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          {fieldErrors.itemType ? <p className="field-error">{fieldErrors.itemType}</p> : null}

          <label htmlFor="implement-location">Ubicacion</label>
          <select
            id="implement-location"
            value={locationUuidRaw}
            onChange={(event) => {
              setLocationUuidRaw(event.target.value);
              setFieldErrors((current) => ({ ...current, locationUuid: undefined }));
            }}
            disabled={isLocationDisabled}
          >
            <option value="" disabled>
              Selecciona una ubicacion
            </option>
            {locations.map((location) => (
              <option key={location.uuid} value={location.uuid}>
                {location.name}
              </option>
            ))}
          </select>
          {locationsError ? <p className="field-error">{locationsError}</p> : null}
          {fieldErrors.locationUuid ? <p className="field-error">{fieldErrors.locationUuid}</p> : null}

          <label htmlFor="implement-description">Descripcion</label>
          <textarea
            id="implement-description"
            value={description}
            onChange={(event) => {
              setDescription(event.target.value);
              setFieldErrors((current) => ({ ...current, description: undefined }));
            }}
            placeholder="Descripcion opcional"
            maxLength={2000}
          />
          {fieldErrors.description ? <p className="field-error">{fieldErrors.description}</p> : null}

          <label htmlFor="implement-barcode">Codigo de barras</label>
          <input
            id="implement-barcode"
            value={barcode}
            onChange={(event) => {
              setBarcode(event.target.value);
              setFieldErrors((current) => ({ ...current, barcode: undefined }));
            }}
            placeholder="Opcional"
            maxLength={100}
          />
          {fieldErrors.barcode ? <p className="field-error">{fieldErrors.barcode}</p> : null}

          <label htmlFor="implement-img-url">URL de imagen</label>
          <input
            id="implement-img-url"
            value={imgUrl}
            onChange={(event) => {
              setImgUrl(event.target.value);
              setFieldErrors((current) => ({ ...current, imgUrl: undefined }));
            }}
            placeholder="https://..."
            maxLength={2000}
          />
          {fieldErrors.imgUrl ? <p className="field-error">{fieldErrors.imgUrl}</p> : null}

          <label htmlFor="implement-min-stock">Stock minimo</label>
          <input
            id="implement-min-stock"
            type="number"
            min={1}
            step={1}
            value={minStockRaw}
            onChange={(event) => {
              setMinStockRaw(event.target.value);
              setFieldErrors((current) => ({ ...current, minStock: undefined }));
            }}
            placeholder="Ej: 5"
            required
          />
          <p className="field-hint">
            El stock inicial del producto serÃ¡ 0. Para agregar unidades usa el ingreso de lote.
          </p>
          {fieldErrors.minStock ? <p className="field-error">{fieldErrors.minStock}</p> : null}

          <label htmlFor="implement-observations">Observaciones</label>
          <textarea
            id="implement-observations"
            value={observations}
            onChange={(event) => {
              setObservations(event.target.value);
              setFieldErrors((current) => ({ ...current, observations: undefined }));
            }}
            placeholder="Observaciones opcionales"
            maxLength={500}
          />
          {fieldErrors.observations ? <p className="field-error">{fieldErrors.observations}</p> : null}

          <div className="modal-actions">
            <button type="button" className="button button--ghost" onClick={onClose}>
              Cancelar
            </button>
            <button type="submit" className="button" disabled={saving}>
              {saving ? "Guardando..." : "Guardar"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
