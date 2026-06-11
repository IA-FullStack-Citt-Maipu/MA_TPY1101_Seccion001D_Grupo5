import { MessageCircleMore } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import styles from "../../styles/chat.module.css";
import { ChatLayout } from "./ChatLayout";

export const ChatWidget = () => {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  const panelRef = useRef<HTMLDivElement | null>(null);
  const triggerRef = useRef<HTMLButtonElement | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }

    panelRef.current?.focus();

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== "Escape") {
        return;
      }

      setOpen(false);
      triggerRef.current?.focus();
    }

    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [open]);

  function closeWidget() {
    setOpen(false);
    triggerRef.current?.focus();
  }

  return (
    <div className={styles.chatWidgetRoot}>
      {open ? (
        <button
          type="button"
          className={styles.chatWidgetBackdrop}
          aria-label="Cerrar asistente virtual"
          onClick={closeWidget}
        />
      ) : null}

      <div
        ref={panelRef}
        id={panelId}
        role="dialog"
        tabIndex={-1}
        aria-modal="false"
        aria-hidden={!open}
        aria-label="Asistente virtual Panol"
        className={`${styles.chatWidgetPanel} ${open ? styles.chatWidgetPanelOpen : styles.chatWidgetPanelClosed}`}
      >
        <ChatLayout onClose={closeWidget} />
      </div>

      <button
        ref={triggerRef}
        type="button"
        aria-haspopup="dialog"
        aria-controls={panelId}
        aria-expanded={open}
        aria-label={open ? "Cerrar asistente virtual" : "Abrir asistente virtual"}
        className={`${styles.chatLauncher} ${open ? styles.chatLauncherOpen : ""}`}
        onClick={() => setOpen((currentValue) => !currentValue)}
      >
        <span className={styles.chatLauncherIcon} aria-hidden="true">
          <MessageCircleMore size={20} />
        </span>
        <span className={styles.chatLauncherCopy}>
          <strong>Asistente IA</strong>
          <small>Inventario y prestamos</small>
        </span>
      </button>
    </div>
  );
};
