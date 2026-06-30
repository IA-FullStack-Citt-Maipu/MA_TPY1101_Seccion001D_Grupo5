import { useCallback, useEffect, useRef, useState } from "react";

const ACTIVITY_EVENTS: Array<keyof WindowEventMap> = [
  "pointerdown",
  "keydown",
  "mousemove",
  "scroll",
  "touchstart",
];

interface UseInactivityPollingGateOptions {
  inactiveAfterMs?: number;
  responseWindowMs?: number;
  onContinue?: () => void | Promise<void>;
}

export function useInactivityPollingGate({
  inactiveAfterMs = 5 * 60 * 1000,
  responseWindowMs = 30 * 1000,
  onContinue,
}: UseInactivityPollingGateOptions = {}) {
  const [promptVisible, setPromptVisible] = useState(false);
  const [pollingPaused, setPollingPaused] = useState(false);
  const [countdownSeconds, setCountdownSeconds] = useState(
    Math.ceil(responseWindowMs / 1000),
  );

  const lastActivityAtRef = useRef(Date.now());
  const promptVisibleRef = useRef(false);
  const pollingPausedRef = useRef(false);
  const responseDeadlineRef = useRef<number | null>(null);

  useEffect(() => {
    promptVisibleRef.current = promptVisible;
  }, [promptVisible]);

  useEffect(() => {
    pollingPausedRef.current = pollingPaused;
  }, [pollingPaused]);

  useEffect(() => {
    function markActivity() {
      if (promptVisibleRef.current) {
        return;
      }
      lastActivityAtRef.current = Date.now();
    }

    ACTIVITY_EVENTS.forEach((eventName) => {
      window.addEventListener(eventName, markActivity, { passive: true });
    });

    return () => {
      ACTIVITY_EVENTS.forEach((eventName) => {
        window.removeEventListener(eventName, markActivity);
      });
    };
  }, []);

  useEffect(() => {
    const intervalId = window.setInterval(() => {
      const now = Date.now();

      if (!promptVisibleRef.current && now - lastActivityAtRef.current >= inactiveAfterMs) {
        responseDeadlineRef.current = now + responseWindowMs;
        setCountdownSeconds(Math.ceil(responseWindowMs / 1000));
        setPromptVisible(true);
        return;
      }

      if (!promptVisibleRef.current || responseDeadlineRef.current == null) {
        return;
      }

      const remainingMs = Math.max(0, responseDeadlineRef.current - now);
      const remainingSeconds = Math.ceil(remainingMs / 1000);
      setCountdownSeconds(remainingSeconds);

      if (remainingMs === 0 && !pollingPausedRef.current) {
        setPollingPaused(true);
        responseDeadlineRef.current = null;
      }
    }, 1000);

    return () => window.clearInterval(intervalId);
  }, [inactiveAfterMs, responseWindowMs]);

  const resumePolling = useCallback(async () => {
    lastActivityAtRef.current = Date.now();
    responseDeadlineRef.current = null;
    setCountdownSeconds(Math.ceil(responseWindowMs / 1000));
    setPromptVisible(false);
    setPollingPaused(false);
    await onContinue?.();
  }, [onContinue, responseWindowMs]);

  return {
    promptVisible,
    pollingPaused,
    countdownSeconds,
    resumePolling,
  };
}
