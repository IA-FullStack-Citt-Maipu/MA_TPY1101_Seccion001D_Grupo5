import { Bot, User } from "lucide-react";
import type { Components } from "react-markdown";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import styles from "../../styles/chat.module.css";
import type { ChatRole, ChatUiBlock } from "./chat.types";

interface MessageBubbleProps {
  role: ChatRole;
  content: string;
  timestamp?: string;
  uiBlocks?: ChatUiBlock[];
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

function renderUiBlock(block: ChatUiBlock, index: number) {
  if (block.type === "entity_list") {
    return (
      <section key={`${block.type}-${index}`} className={styles.chatUiBlock}>
        <header className={styles.chatUiBlockHeader}>{block.title}</header>
        <div className={styles.chatEntityList}>
          {block.entities.map((entity, entityIndex) => (
            <article key={`${entity.title}-${entityIndex}`} className={styles.chatEntityCard}>
              <div className={styles.chatEntityTitleRow}>
                <strong>{entity.title}</strong>
                {entity.badges.length > 0 ? (
                  <div className={styles.chatBadgeGroup}>
                    {entity.badges.map((badge) => (
                      <span key={badge} className={styles.chatBadge}>{badge}</span>
                    ))}
                  </div>
                ) : null}
              </div>
              {entity.subtitle ? <p className={styles.chatEntitySubtitle}>{entity.subtitle}</p> : null}
              {entity.meta.length > 0 ? (
                <ul className={styles.chatEntityMetaList}>
                  {entity.meta.map((meta) => (
                    <li key={meta}>{meta}</li>
                  ))}
                </ul>
              ) : null}
            </article>
          ))}
        </div>
      </section>
    );
  }

  return (
    <section key={`${block.type}-${index}`} className={styles.chatUiBlock}>
      <header className={styles.chatUiBlockHeader}>{block.title}</header>
      <div className={styles.chatStatsGrid}>
        {block.stats.map((stat) => (
          <article key={stat.label} className={styles.chatStatCard}>
            <span className={styles.chatStatLabel}>{stat.label}</span>
            <strong className={styles.chatStatValue}>{stat.value}</strong>
          </article>
        ))}
      </div>
    </section>
  );
}

export const MessageBubble = ({ role, content, timestamp, uiBlocks }: MessageBubbleProps) => {
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
            <div className={styles.assistantBubbleBody}>
              {uiBlocks && uiBlocks.length > 0 ? (
                <div className={styles.chatUiBlockStack}>
                  {uiBlocks.map((block, index) => renderUiBlock(block, index))}
                </div>
              ) : null}
              {content.trim().length > 0 ? (
                <div className={styles.markdownBody}>
                  <ReactMarkdown components={markdownComponents} remarkPlugins={[remarkGfm]}>
                    {content}
                  </ReactMarkdown>
                </div>
              ) : null}
            </div>
          )}
        </div>
        {timestamp ? <span className={styles.messageTimestamp}>{timestamp}</span> : null}
      </div>
    </article>
  );
};
