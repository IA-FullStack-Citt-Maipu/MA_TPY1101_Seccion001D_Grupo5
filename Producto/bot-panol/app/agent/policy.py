from __future__ import annotations

from dataclasses import dataclass
import re
import unicodedata


def _normalize_text(value: str) -> str:
    collapsed = " ".join(value.split()).strip().lower()
    normalized = unicodedata.normalize("NFKD", collapsed)
    return "".join(char for char in normalized if not unicodedata.combining(char))


_WRITE_PATTERNS = [
    re.compile(pattern)
    for pattern in (
        r"\b(aprueba|aprobar|rechaza|rechazar|entrega|entregar|devuelve|devolver)\b",
        r"\b(completa|completar|cancela|cancelar|crea|crear|edita|editar)\b",
        r"\b(actualiza|actualizar|modifica|modificar|elimina|eliminar)\b",
        r"\b(activa|activar|desactiva|desactivar|registra|registrar)\b",
        r"\b(mueve|mover|ingresa|ingresar|ajusta|ajustar)\b.{0,30}\b(stock|prestamo|implemento)\b",
    )
]

_PROMPT_INJECTION_PATTERNS = [
    re.compile(pattern)
    for pattern in (
        r"ignora (las )?(reglas|instrucciones|restricciones|politicas)",
        r"actua como (director|coordinador|admin|administrador)",
        r"(omite|saltate|bypassea|burlar?) (los )?(permisos|roles|restricciones)",
        r"muestrame igual",
        r"hazlo aunque no puedas",
    )
]

_SENSITIVE_PATTERNS = [
    re.compile(pattern)
    for pattern in (
        r"\b(quien|quien(es)?|quienes)\b.{0,20}\b(pidio|pidieron|movio|movieron|hizo|hicieron|solicito|solicitaron)\b",
        r"\b(solicitante|requester|requester_uuid|performed_by)\b",
        r"\b(rut|correo|email|actor_name|actor_email)\b",
        r"\b(asset[\s_-]?code|codigo patrimonial|codigo de activo|numero de serie|serial)\b",
        r"\b(notas? internas?|observaciones internas?|trazabilidad|historial de movimientos?)\b",
    )
]


@dataclass(frozen=True)
class PolicyDecision:
    allowed: bool
    message: str | None = None
    code: str | None = None


def evaluate_message_policy(role: str, message: str) -> PolicyDecision:
    normalized = _normalize_text(message)

    if any(pattern.search(normalized) for pattern in _WRITE_PATTERNS):
        return PolicyDecision(
            allowed=False,
            code="POLICY_WRITE_FORBIDDEN",
            message=(
                "Solo puedo ayudarte con consultas de lectura. "
                "Las acciones de aprobacion, edicion o cambios de estado deben hacerse en el sistema principal."
            ),
        )

    if any(pattern.search(normalized) for pattern in _PROMPT_INJECTION_PATTERNS):
        return PolicyDecision(
            allowed=False,
            code="POLICY_PROMPT_INJECTION",
            message=(
                "No puedo ignorar permisos, roles ni restricciones del sistema. "
                "Si necesitas una consulta valida, reformulala dentro del alcance permitido."
            ),
        )

    if any(pattern.search(normalized) for pattern in _SENSITIVE_PATTERNS):
        return PolicyDecision(
            allowed=False,
            code="POLICY_SENSITIVE_DATA",
            message=(
                "No puedo entregar datos sensibles, identidad de terceros ni trazabilidad fina desde el chat. "
                "Puedo ayudarte con resumenes operativos o ejecutivos sin exponer informacion personal."
            ),
        )

    normalized_role = (role or "").strip().upper()
    if normalized_role == "DIRECTOR" and "detalle completo" in normalized:
        return PolicyDecision(
            allowed=False,
            code="POLICY_DIRECTOR_OPERATIONAL_SCOPE",
            message=(
                "Para el rol Director solo puedo entregar resumenes agregados y ejecutivos. "
                "No puedo responder con detalle operativo completo desde el chat."
            ),
        )

    return PolicyDecision(allowed=True)
