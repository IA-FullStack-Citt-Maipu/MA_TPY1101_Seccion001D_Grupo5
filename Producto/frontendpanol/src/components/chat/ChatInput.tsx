import { Send } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import type { FormEvent, KeyboardEvent } from "react";
import styles from "../../styles/chat.module.css";

interface ChatInputProps {
  onSubmit: (message: string) => void | Promise<void>;
  placeholder?: string;
  disabled?: boolean;
  isTyping?: boolean;
}

const minimumTextareaHeight = 56;
const maximumTextareaHeight = 168;

export const ChatInput = ({
  onSubmit,
  placeholder = "Escribe tu mensaje...",
  disabled = false,
  isTyping = false,
}: ChatInputProps) => {
  const [value, setValue] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);

  const isBusy = disabled || isTyping || isSubmitting;

  function resizeTextarea() {
    const textarea = textareaRef.current;
    if (!textarea) {
      return;
    }

    textarea.style.height = `${minimumTextareaHeight}px`;
    textarea.style.overflowY = "hidden";

    const nextHeight = Math.min(textarea.scrollHeight, maximumTextareaHeight);
    textarea.style.height = `${nextHeight}px`;

    if (textarea.scrollHeight > maximumTextareaHeight) {
      textarea.style.overflowY = "auto";
    }
  }

  useEffect(() => {
    resizeTextarea();
  }, [value]);

  async function submitMessage() {
    const trimmedValue = value.trim();
    if (!trimmedValue || isBusy) {
      return;
    }

    setIsSubmitting(true);
    try {
      await Promise.resolve(onSubmit(trimmedValue));
      setValue("");
      window.requestAnimationFrame(() => {
        resizeTextarea();
        textareaRef.current?.focus();
      });
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    await submitMessage();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      void submitMessage();
    }
  }

  return (
    <form className={styles.chatInput} onSubmit={(event) => void handleSubmit(event)}>
      <div className={styles.chatComposer}>
        <textarea
          ref={textareaRef}
          className={styles.chatTextarea}
          placeholder={placeholder}
          value={value}
          rows={1}
          disabled={isBusy}
          onChange={(event) => setValue(event.target.value)}
          onKeyDown={handleKeyDown}
        />

        <button
          type="submit"
          className={styles.chatSendButton}
          aria-label="Enviar mensaje"
          disabled={isBusy || value.trim().length === 0}
        >
          <Send size={18} />
        </button>
      </div>

      <p className={styles.chatComposerHint}>
        {isTyping ? "El asistente esta respondiendo..." : "Enter envia - Shift + Enter agrega un salto de linea"}
      </p>
    </form>
  );
};
