import { Bot } from "lucide-react";
import styles from "../../styles/chat.module.css";

export const TypingIndicator = () => (
  <div className={`${styles.messageRow} ${styles.messageRowAssistant} ${styles.typingRow}`}>
    <div className={`${styles.messageAvatar} ${styles.messageAvatarAssistant}`} aria-hidden="true">
      <Bot size={16} />
    </div>
    <div
      className={styles.typingBubble}
      role="status"
      aria-live="polite"
      aria-label="Asistente escribiendo"
    >
      <span className={styles.typingDots} aria-hidden="true">
        <span className={styles.typingDot} />
        <span className={styles.typingDot} />
        <span className={styles.typingDot} />
      </span>
    </div>
  </div>
);
