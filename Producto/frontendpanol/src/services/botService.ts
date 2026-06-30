import axios, { AxiosError } from "axios";
import { apiClient } from "./apiClient";
import type {
  BotChatErrorPayload,
  BotChatRequestBody,
  BotChatResponse,
  SendChatAssistantParams,
} from "../components/chat/chat.types";

const defaultApiBaseUrl = "http://localhost:18080";
const defaultBotRequestTimeoutMs = 80000;

interface BotAccessTokenResponse {
  token: string;
  expiresInSeconds: number;
}

export class BotServiceError extends Error {
  statusCode?: number;

  constructor(message: string, statusCode?: number, options?: ErrorOptions) {
    super(message, options);
    this.name = "BotServiceError";
    this.statusCode = statusCode;
  }
}

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

function getBotRequestTimeoutMs(): number {
  const rawValue = import.meta.env.VITE_BOT_REQUEST_TIMEOUT_MS?.toString().trim();
  if (!rawValue) {
    return defaultBotRequestTimeoutMs;
  }

  const parsedValue = Number.parseInt(rawValue, 10);
  return Number.isFinite(parsedValue) && parsedValue > 0 ? parsedValue : defaultBotRequestTimeoutMs;
}

function normalizeBotErrorMessage(error: unknown): string {
  const fallbackMessage = "No fue posible contactar al asistente en este momento.";

  if (!(error instanceof AxiosError)) {
    return fallbackMessage;
  }

  if (error.code === "ECONNABORTED") {
    return "El asistente está demorando más de lo esperado. Intenta nuevamente en unos segundos.";
  }

  const payload = error.response?.data as BotChatErrorPayload | undefined;

  if (payload?.detail === "LLM_TIMEOUT") {
    return "El asistente está demorando más de lo esperado. Intenta nuevamente en unos segundos.";
  }

  if (payload?.detail === "LLM_RATE_LIMITED") {
    return "El asistente está recibiendo demasiadas solicitudes en este momento. Intenta nuevamente en unos segundos.";
  }

  if (typeof payload?.message === "string" && payload.message.trim().length > 0) {
    return payload.message.trim();
  }

  if (typeof payload?.detail === "string" && payload.detail.trim().length > 0) {
    return payload.detail.trim();
  }

  return fallbackMessage;
}

export async function requestBotAccessToken(): Promise<BotAccessTokenResponse> {
  const { data } = await apiClient.post<BotAccessTokenResponse>("/api/v2/auth/me/bot-token");
  return data;
}

function buildChatRequestBody(params: SendChatAssistantParams): BotChatRequestBody {
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

  return requestBody;
}

function toBotServiceError(error: unknown): BotServiceError {
  const message = normalizeBotErrorMessage(error);
  const statusCode = error instanceof AxiosError ? error.response?.status : undefined;
  return new BotServiceError(message, statusCode, { cause: error });
}

export function isBotUnauthorizedError(error: unknown): boolean {
  return error instanceof BotServiceError && error.statusCode === 401;
}

async function sendChatMessage(params: SendChatAssistantParams, authToken: string): Promise<BotChatResponse> {
  try {
    const { data } = await axios.post<BotChatResponse>(
      resolveBotChatUrl(),
      buildChatRequestBody(params),
      {
        timeout: getBotRequestTimeoutMs(),
        headers: {
          Authorization: `Bearer ${authToken}`,
          "Content-Type": "application/json",
        },
      },
    );
    return data;
  } catch (error) {
    throw toBotServiceError(error);
  }
}

export const botService = {
  requestBotAccessToken,
  sendChatMessage,
};
