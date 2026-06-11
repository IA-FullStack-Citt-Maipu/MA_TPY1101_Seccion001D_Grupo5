import { AxiosError } from "axios";
import { apiClient } from "./apiClient";
import type {
  BotChatErrorPayload,
  BotChatRequestBody,
  BotChatResponse,
  SendChatAssistantParams,
} from "../components/chat/chat.types";

const defaultApiBaseUrl = "http://localhost:18080";

function getBotApiBaseUrl(): string {
  const envBotBaseUrl = import.meta.env.VITE_BOT_API_BASE_URL?.toString().trim();
  if (envBotBaseUrl) {
    return envBotBaseUrl;
  }

  const envApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.toString().trim();
  if (envApiBaseUrl) {
    return envApiBaseUrl;
  }

  const clientBaseUrl = apiClient.defaults.baseURL?.toString().trim();
  if (clientBaseUrl) {
    return clientBaseUrl;
  }

  return defaultApiBaseUrl;
}

function resolveBotChatUrl(): string {
  const baseUrl = getBotApiBaseUrl();
  const normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`;
  return new URL("api/v1/chat", normalizedBaseUrl).toString();
}

function normalizeBotErrorMessage(error: unknown): string {
  const fallbackMessage = "No fue posible contactar al asistente en este momento.";

  if (!(error instanceof AxiosError)) {
    return fallbackMessage;
  }

  const payload = error.response?.data as BotChatErrorPayload | undefined;

  if (typeof payload?.message === "string" && payload.message.trim().length > 0) {
    return payload.message.trim();
  }

  if (typeof payload?.detail === "string" && payload.detail.trim().length > 0) {
    return payload.detail.trim();
  }

  return fallbackMessage;
}

async function sendChatMessage(params: SendChatAssistantParams): Promise<BotChatResponse> {
  const requestBody: BotChatRequestBody = {
    message: params.message,
    history: params.history.map((entry) => ({
      role: entry.role,
      content: entry.content,
    })),
  };

  if (typeof params.conversationId === "string" && params.conversationId.trim().length > 0) {
    requestBody.conversation_id = params.conversationId;
  }

  try {
    const { data } = await apiClient.post<BotChatResponse>(resolveBotChatUrl(), requestBody);
    return data;
  } catch (error) {
    throw new Error(normalizeBotErrorMessage(error), { cause: error });
  }
}

export const botService = {
  sendChatMessage,
};
