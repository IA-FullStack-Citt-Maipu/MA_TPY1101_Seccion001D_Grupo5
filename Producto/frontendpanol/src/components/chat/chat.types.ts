export type ChatRole = "user" | "assistant";

export interface ChatMessage {
  id: string;
  role: ChatRole;
  content: string;
  timestamp?: string;
  uiBlocks?: ChatUiBlock[];
}

export interface ChatHistoryMessage {
  role: ChatRole;
  content: string;
}

export interface SendChatAssistantParams {
  message: string;
  conversationId?: string | null;
  history: readonly ChatHistoryMessage[];
}

export interface BotChatRequestBody {
  message: string;
  conversation_id?: string;
  history: ChatHistoryMessage[];
}

export interface BotChatResponse {
  response: string;
  conversation_id: string;
  tools_used: string[];
  ui_blocks?: ChatUiBlock[];
}

export interface ChatUiEntity {
  title: string;
  subtitle?: string | null;
  meta: string[];
  badges: string[];
}

export interface ChatUiEntityListBlock {
  type: "entity_list";
  title: string;
  entities: ChatUiEntity[];
}

export interface ChatUiStatItem {
  label: string;
  value: string;
}

export interface ChatUiStatGroupBlock {
  type: "stat_group";
  title: string;
  stats: ChatUiStatItem[];
}

export type ChatUiBlock = ChatUiEntityListBlock | ChatUiStatGroupBlock;

export interface BotChatErrorPayload {
  detail?: string;
  message?: string;
}
