export type ChatRole = "user" | "assistant";

export interface ChatMessage {
  id: string;
  role: ChatRole;
  content: string;
  timestamp?: string;
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
}

export interface BotChatErrorPayload {
  detail?: string;
  message?: string;
}
