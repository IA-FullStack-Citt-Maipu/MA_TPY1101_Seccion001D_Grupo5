import { Bot, X } from "lucide-react";
import { useChatAssistant } from "../../hooks/useChatAssistant";
import styles from "../../styles/chat.module.css";
import { ChatInput } from "./ChatInput";
import { MessageList } from "./MessageList";
import type { ChatMessage } from "./chat.types";

export interface ChatLayoutProps {
  messages?: readonly ChatMessage[];
  onSubmit?: (message: string) => void | Promise<void>;
  isTyping?: boolean;
  onClose?: () => void;
  title?: string;
  subtitle?: string;
}

export const ChatLayout = ({
  messages,
  onSubmit,
  isTyping = false,
  onClose,
  title = "Asistente Panol",
  subtitle = "Soporte contextual para inventario clinico",
}: ChatLayoutProps) => {
  const assistantChat = useChatAssistant();
  const isControlled = messages !== undefined;
  const resolvedMessages = isControlled ? messages : assistantChat.messages;
  const resolvedTyping = isControlled ? isTyping : assistantChat.isTyping;
  const resolvedOnSubmit = isControlled ? onSubmit : assistantChat.sendMessage;
  const inputDisabled = isControlled ? !resolvedOnSubmit : false;

  return (
    <section className={styles.chatLayout}>
      <header className={styles.chatHeader}>
        <div className={styles.chatHeaderIdentity}>
          <div className={styles.chatHeaderIcon} aria-hidden="true">
            <Bot size={18} />
          </div>
          <div className={styles.chatHeaderCopy}>
            <strong>{title}</strong>
            <span>{subtitle}</span>
          </div>
        </div>
        {onClose ? (
          <button
            type="button"
            className={styles.chatCloseButton}
            onClick={onClose}
            aria-label="Cerrar asistente"
          >
            <X size={18} />
          </button>
        ) : null}
      </header>

      <MessageList
        messages={resolvedMessages}
        isTyping={resolvedTyping}
        onQuickPrompt={resolvedOnSubmit}
      />

      <ChatInput
        onSubmit={resolvedOnSubmit ?? (async () => {})}
        disabled={inputDisabled}
        isTyping={resolvedTyping}
        placeholder="Pregunta por stock, prestamos, ubicaciones o soporte..."
      />
    </section>
  );
};
