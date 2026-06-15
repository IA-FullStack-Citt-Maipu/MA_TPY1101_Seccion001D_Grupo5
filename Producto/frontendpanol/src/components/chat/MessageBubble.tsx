import { Bot, User } from "lucide-react";
import type { Components } from "react-markdown";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import styles from "../../styles/chat.module.css";
import type { ChatRole } from "./chat.types";

interface MessageBubbleProps {
  role: ChatRole;
  content: string;
  timestamp?: string;
}

const markdownComponents: Components = {
  table: ({ children, ...props }) => (
    <div className={styles.markdownTableWrap}>
      <table {...props}>{children}</table>
    </div>
  ),
  code: ({ children, className, ...props }) => (
    <code className={className ? `${styles.markdownCode} ${className}` : styles.markdownCode} {...props}>
      {children}
    </code>
  ),
};

export const MessageBubble = ({ role, content, timestamp }: MessageBubbleProps) => {
  const isUserMessage = role === "user";
  const AvatarIcon = isUserMessage ? User : Bot;

  return (
    <article
      className={`${styles.messageRow} ${isUserMessage ? styles.messageRowUser : styles.messageRowAssistant}`}
    >
      <div
        className={`${styles.messageAvatar} ${isUserMessage ? styles.messageAvatarUser : styles.messageAvatarAssistant}`}
        aria-hidden="true"
      >
        <AvatarIcon size={16} />
      </div>

      <div className={styles.messageContentGroup}>
        <div
          className={`${styles.messageBubble} ${isUserMessage ? styles.messageBubbleUser : styles.messageBubbleAssistant}`}
        >
          {isUserMessage ? (
            <p className={styles.messageText}>{content}</p>
          ) : (
            <div className={styles.markdownBody}>
              <ReactMarkdown components={markdownComponents} remarkPlugins={[remarkGfm]}>
                {content}
              </ReactMarkdown>
            </div>
          )}
        </div>
        {timestamp ? <span className={styles.messageTimestamp}>{timestamp}</span> : null}
      </div>
    </article>
  );
};
