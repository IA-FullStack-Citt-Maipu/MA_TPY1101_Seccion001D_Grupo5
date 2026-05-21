import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { X, Tag, FileText, Loader2, Check, AlertCircle } from "lucide-react";
import type { Categoria } from "../../types/category";

interface CategoryFormModalProps {
  mode: "create" | "edit";
  category?: Categoria;
  isOpen: boolean;
  saving: boolean;
  fieldError: string | null;
  onClose: () => void;
  onSubmit: (name: string, description: string) => Promise<void>;
}

export function CategoryFormModal({
  mode,
  category,
  isOpen,
  saving,
  fieldError,
  onClose,
  onSubmit,
}: CategoryFormModalProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [touched, setTouched] = useState({ name: false, description: false });
  const nameInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    setName(category?.nombre ?? "");
    setDescription(category?.descripcion ?? "");
    setTouched({ name: false, description: false });
    
    // Focus en el input de nombre al abrir
    setTimeout(() => nameInputRef.current?.focus(), 100);
  }, [category?.nombre, category?.descripcion, isOpen]);

  // Bloquear scroll del body cuando el modal esta abierto
  useEffect(() => {
    if (isOpen) {
      document.body.style.overflow = "hidden";
    }
    return () => {
      document.body.style.overflow = "";
    };
  }, [isOpen]);

  // Cerrar con Escape
  useEffect(() => {
    if (!isOpen) return;
    
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape" && !saving) {
        onClose();
      }
    }
    
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, saving, onClose]);

  if (!isOpen) {
    return null;
  }

  const nameError = touched.name && name.trim().length === 0 ? "El nombre es obligatorio" : null;
  const isValid = name.trim().length > 0;
  const charCount = name.length;
  const maxChars = 100;

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!isValid) {
      setTouched({ name: true, description: true });
      return;
    }
    await onSubmit(name.trim(), description);
  }

  return (
    <div className="modal-overlay" role="dialog" aria-modal="true" onClick={(e) => e.target === e.currentTarget && !saving && onClose()}>
      <div className="modal modal--enhanced">
        <div className="modal__header">
          <div className="modal__header-content">
            <div className="modal__icon">
              <Tag size={24} />
            </div>
            <div>
              <h3>{mode === "create" ? "Nueva categoria" : "Editar categoria"}</h3>
              <p>Define un nombre unico para organizar el catalogo de inventario.</p>
            </div>
          </div>
          <button 
            type="button" 
            className="modal__close" 
            onClick={onClose}
            disabled={saving}
            aria-label="Cerrar modal"
          >
            <X size={20} />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="modal__form">
          <div className={`form-group ${nameError || fieldError ? "form-group--error" : ""} ${touched.name && isValid ? "form-group--valid" : ""}`}>
            <label htmlFor="category-name" className="form-label">
              <Tag size={16} />
              Nombre de la categoria
              <span className="form-label__required">*</span>
            </label>
            <div className="form-input-wrapper">
              <input
                ref={nameInputRef}
                id="category-name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                onBlur={() => setTouched((prev) => ({ ...prev, name: true }))}
                placeholder="Ej: Equipos clinicos, Material de laboratorio..."
                maxLength={maxChars}
                required
                className="form-input"
                autoComplete="off"
              />
              {touched.name && isValid && (
                <span className="form-input__icon form-input__icon--success">
                  <Check size={16} />
                </span>
              )}
              {(nameError || fieldError) && (
                <span className="form-input__icon form-input__icon--error">
                  <AlertCircle size={16} />
                </span>
              )}
            </div>
            <div className="form-field-footer">
              {nameError ? (
                <p className="field-error">{nameError}</p>
              ) : fieldError ? (
                <p className="field-error">{fieldError}</p>
              ) : (
                <p className="field-hint">Usa un nombre descriptivo y facil de identificar</p>
              )}
              <span className={`char-count ${charCount > maxChars * 0.9 ? "char-count--warning" : ""}`}>
                {charCount}/{maxChars}
              </span>
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="category-description" className="form-label">
              <FileText size={16} />
              Descripcion
              <span className="form-label__optional">(opcional)</span>
            </label>
            <textarea
              id="category-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder="Ej: Equipamiento utilizado en practicas clinicas y simulaciones medicas."
              maxLength={255}
              rows={3}
              className="form-textarea"
            />
            <div className="form-field-footer">
              <p className="field-hint">Agrega detalles que ayuden a identificar los implementos de esta categoria</p>
              <span className="char-count">{description.length}/255</span>
            </div>
          </div>

          <div className="modal__footer">
            <button 
              type="button" 
              className="button button--ghost button--lg" 
              onClick={onClose}
              disabled={saving}
            >
              Cancelar
            </button>
            <button 
              type="submit" 
              className="button button--primary button--lg" 
              disabled={saving || !isValid}
            >
              {saving ? (
                <>
                  <Loader2 size={18} className="button__spinner" />
                  Guardando...
                </>
              ) : (
                <>
                  <Check size={18} />
                  {mode === "create" ? "Crear categoria" : "Guardar cambios"}
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
