import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./styles/theme.css";
import App from "./App";
import { applyThemeMode, getStoredThemeMode } from "./utils/theme";

applyThemeMode(getStoredThemeMode());

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

