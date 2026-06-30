from __future__ import annotations

from dataclasses import dataclass
from typing import Literal
import re
import unicodedata


IntentCategory = Literal[
    "read_query",
    "identifier_request",
    "write_or_mutation",
    "sensitive_traceability",
    "prompt_injection_or_bypass",
    "out_of_scope",
]


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
        r"revela (el )?prompt",
        r"system prompt",
    )
]

_SENSITIVE_PATTERNS = [
    re.compile(pattern)
    for pattern in (
        r"\b(quien|quienes)\b.{0,24}\b(pidio|movio|hizo|solicito|autorizo)\b",
        r"\b(solicitante|requester|requester_uuid|performed_by)\b",
        r"\b(rut|correo|email|actor_name|actor_email)\b",
        r"\b(asset[\s_-]?code|codigo patrimonial|codigo de activo|numero de serie|serial)\b",
        r"\b(notas? internas?|observaciones internas?|trazabilidad|historial de movimientos?)\b",
        r"\bindividual_uuid\b",
    )
]

_IDENTIFIER_PATTERNS = [
    re.compile(pattern)
    for pattern in (
        r"\buuid\b",
        r"\bid\b.{0,16}\bimplement",
        r"\bidentificador(?: tecnico| interno)?\b",
        r"\bdame el id\b",
        r"\bdame el uuid\b",
    )
]

_OUT_OF_SCOPE_PATTERNS = [
    re.compile(pattern)
    for pattern in (
        r"\b(contrasena|password|token|credencial)\b",
        r"\b(escribe codigo|programa|script)\b",
    )
]


@dataclass(frozen=True)
class PolicyDecision:
    allowed: bool
    intent: IntentCategory
    message: str | None = None
    code: str | None = None


def evaluate_message_policy(role: str, message: str) -> PolicyDecision:
    normalized = _normalize_text(message)
    normalized_role = (role or "").strip().upper()

    if any(pattern.search(normalized) for pattern in _WRITE_PATTERNS):
        return PolicyDecision(
            allowed=False,
            intent="write_or_mutation",
            code="POLICY_WRITE_FORBIDDEN",
            message=(
                "Solo puedo ayudarte con consultas de lectura. "
                "Las acciones de aprobacion, edicion o cambios de estado deben hacerse en el sistema principal."
            ),
        )

    if any(pattern.search(normalized) for pattern in _PROMPT_INJECTION_PATTERNS):
        return PolicyDecision(
            allowed=False,
            intent="prompt_injection_or_bypass",
            code="POLICY_PROMPT_INJECTION",
            message=(
                "No puedo ignorar permisos, roles ni restricciones del sistema. "
                "Si necesitas una consulta valida, reformulala dentro del alcance permitido."
            ),
        )

    if any(pattern.search(normalized) for pattern in _SENSITIVE_PATTERNS):
        return PolicyDecision(
            allowed=False,
            intent="sensitive_traceability",
            code="POLICY_SENSITIVE_DATA",
            message=(
                "No puedo entregar datos sensibles, identidad de terceros ni trazabilidad fina desde el chat. "
                "Puedo ayudarte con resumenes operativos o ejecutivos sin exponer informacion personal."
            ),
        )

    if any(pattern.search(normalized) for pattern in _OUT_OF_SCOPE_PATTERNS):
        return PolicyDecision(
            allowed=False,
            intent="out_of_scope",
            code="POLICY_OUT_OF_SCOPE",
            message="Puedo ayudarte solo con consultas seguras de inventario, stock y prestamos dentro del sistema Pañol.",
        )

    if any(pattern.search(normalized) for pattern in _IDENTIFIER_PATTERNS):
        if normalized_role != "COORDINADOR":
            return PolicyDecision(
                allowed=False,
                intent="identifier_request",
                code="POLICY_IDENTIFIER_SCOPE",
                message=(
                    "Los identificadores tecnicos solo pueden entregarse a Coordinacion y solo cuando se solicitan de forma explicita."
                ),
            )

        return PolicyDecision(
            allowed=True,
            intent="identifier_request",
        )

    return PolicyDecision(
        allowed=True,
        intent="read_query",
    )
