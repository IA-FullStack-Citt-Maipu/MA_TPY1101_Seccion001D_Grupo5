export type ThemeMode = "light" | "dark";

const THEME_STORAGE_KEY = "panol_theme_mode";

export function getStoredThemeMode(): ThemeMode {
  if (typeof window === "undefined") {
    return "light";
  }
  const raw = window.localStorage.getItem(THEME_STORAGE_KEY);
  return raw === "dark" ? "dark" : "light";
}

export function applyThemeMode(mode: ThemeMode) {
  if (typeof document === "undefined") {
    return;
  }
  document.documentElement.dataset.theme = mode;
  document.documentElement.style.colorScheme = mode;
}

export function persistThemeMode(mode: ThemeMode) {
  if (typeof window !== "undefined") {
    window.localStorage.setItem(THEME_STORAGE_KEY, mode);
  }
  applyThemeMode(mode);
}
