import { useEffect, useRef } from "react";
import styles from "../../styles/chat.module.css";
import { MessageBubble } from "./MessageBubble";
import { TypingIndicator } from "./TypingIndicator";
import type { ChatMessage } from "./chat.types";

interface MessageListProps {
  messages: readonly ChatMessage[];
  isTyping?: boolean;
  onQuickPrompt?: (prompt: string) => void | Promise<void>;
}

const quickPrompts = [
  "Consultar stock de jeringas",
  "Ver prestamos atrasados",
  "Resumen del inventario",
] as const;

export const MessageList = ({
  messages,
  isTyping = false,
  onQuickPrompt,
}: MessageListProps) => {
  const endOfConversationRef = useRef<HTMLDivElement | null>(null);
  const quickPromptsDisabled = !onQuickPrompt || isTyping;

  useEffect(() => {
    endOfConversationRef.current?.scrollIntoView({
      behavior: "smooth",
      block: "end",
    });
  }, [isTyping, messages]);

  return (
    <div
      className={styles.messageList}
      role="log"
      aria-live="polite"
      aria-label="Conversacion con el asistente"
    >
      {messages.length === 0 ? (
        <div className={styles.messageEmpty}>
          <strong>Bienvenido al asistente de Panol</strong>
          <p>
            Puedo ayudarte a explorar inventario, prestamos y alertas operativas.
            Elige una sugerencia para comenzar mas rapido.
          </p>
          <div className={styles.quickPromptGroup}>
            {quickPrompts.map((prompt) => (
              <button
                key={prompt}
                type="button"
                className={styles.quickPromptButton}
                disabled={quickPromptsDisabled}
                onClick={() => {
                  if (!onQuickPrompt) {
                    return;
                  }

                  void onQuickPrompt(prompt);
                }}
              >
                {prompt}
              </button>
            ))}
          </div>
        </div>
      ) : (
        messages.map((message) => (
          <MessageBubble
            key={message.id}
            role={message.role}
            content={message.content}
            timestamp={message.timestamp}
          />
        ))
      )}
      {isTyping ? <TypingIndicator /> : null}
      <div ref={endOfConversationRef} aria-hidden="true" />
    </div>
  );
};
