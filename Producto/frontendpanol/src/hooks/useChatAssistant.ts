import { startTransition, useEffect, useRef, useState } from "react";
import type { ChatHistoryMessage, ChatMessage, ChatRole } from "../components/chat/chat.types";
import { botService, isBotUnauthorizedError } from "../services/botService";

interface BotAccessTokenCacheEntry {
  token: string;
  expiresAtMs: number;
}

function createTimestamp(date = new Date()): string {
  return new Intl.DateTimeFormat("es-CL", {
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

function createMessage(role: ChatRole, content: string): ChatMessage {
  return {
    id: crypto.randomUUID(),
    role,
    content,
    timestamp: createTimestamp(),
  };
}

function toHistoryEntry(message: ChatMessage): ChatHistoryMessage {
  return {
    role: message.role,
    content: message.content,
  };
}

function resolveAssistantText(responseText: string): string {
  const normalizedText = responseText.trim();
  return normalizedText.length > 0
    ? normalizedText
    : "El asistente no devolvio contenido util. Intenta reformular tu consulta.";
}

function resolveErrorText(error: unknown): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return `No fue posible obtener respuesta del asistente. ${error.message}`;
  }

  return "No fue posible obtener respuesta del asistente. Intenta nuevamente en unos segundos.";
}

export function useChatAssistant() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [isTyping, setIsTyping] = useState(false);
  const messagesRef = useRef<ChatMessage[]>([]);
  const conversationIdRef = useRef<string | null>(null);
  const historyRef = useRef<ChatHistoryMessage[]>([]);
  const isTypingRef = useRef(false);
  const botAccessTokenRef = useRef<BotAccessTokenCacheEntry | null>(null);

  useEffect(() => {
    messagesRef.current = messages;
  }, [messages]);

  useEffect(() => {
    conversationIdRef.current = conversationId;
  }, [conversationId]);

  function appendUiMessage(message: ChatMessage) {
    setMessages((currentMessages) => {
      const nextMessages = [...currentMessages, message];
      messagesRef.current = nextMessages;
      return nextMessages;
    });
  }

  function invalidateBotAccessToken() {
    botAccessTokenRef.current = null;
  }

  function hasFreshBotAccessToken() {
    const cachedToken = botAccessTokenRef.current;
    if (!cachedToken) {
      return false;
    }
    return cachedToken.expiresAtMs > Date.now() + 5_000;
  }

  async function resolveBotAccessToken(forceRefresh = false): Promise<string> {
    if (!forceRefresh && hasFreshBotAccessToken()) {
      return botAccessTokenRef.current!.token;
    }

    const response = await botService.requestBotAccessToken();
    botAccessTokenRef.current = {
      token: response.token,
      expiresAtMs: Date.now() + (response.expiresInSeconds * 1000),
    };
    return response.token;
  }

  async function sendMessage(text: string): Promise<void> {
    const trimmedText = text.trim();
    if (!trimmedText || isTypingRef.current) {
      return;
    }

    const priorHistory = historyRef.current.map((entry) => ({ ...entry }));
    const userMessage = createMessage("user", trimmedText);

    appendUiMessage(userMessage);
    isTypingRef.current = true;
    setIsTyping(true);

    try {
      let authToken = await resolveBotAccessToken();
      let response;

      try {
        response = await botService.sendChatMessage({
          message: trimmedText,
          conversationId: conversationIdRef.current,
          history: priorHistory,
        }, authToken);
      } catch (error) {
        if (!isBotUnauthorizedError(error)) {
          throw error;
        }

        invalidateBotAccessToken();
        authToken = await resolveBotAccessToken(true);
        response = await botService.sendChatMessage({
          message: trimmedText,
          conversationId: conversationIdRef.current,
          history: priorHistory,
        }, authToken);
      }

      const assistantMessage = createMessage("assistant", resolveAssistantText(response.response));
      historyRef.current = [
        ...priorHistory,
        toHistoryEntry(userMessage),
        toHistoryEntry(assistantMessage),
      ];
      conversationIdRef.current = response.conversation_id;
      setConversationId(response.conversation_id);

      startTransition(() => {
        appendUiMessage(assistantMessage);
      });
    } catch (error) {
      invalidateBotAccessToken();
      const assistantErrorMessage = createMessage("assistant", resolveErrorText(error));
      startTransition(() => {
        appendUiMessage(assistantErrorMessage);
      });
    } finally {
      isTypingRef.current = false;
      setIsTyping(false);
    }
  }

  return {
    messages,
    isTyping,
    sendMessage,
  };
}
