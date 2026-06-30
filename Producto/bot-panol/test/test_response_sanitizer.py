from app.agent.response_sanitizer import sanitize_response_text


def test_sanitize_response_text_keeps_only_context_when_ui_blocks_exist() -> None:
    text = """
    Aqui tienes un resumen del inventario por categoria:

    Inventario por categoria

    - Herramientas
      - Implementos: 3
      - Stock disponible: 111
      - Alertas: 0
    """

    ui_blocks = [
        {
            "type": "entity_list",
            "title": "Inventario por categoria",
            "entities": [
                {
                    "title": "Herramientas",
                    "subtitle": None,
                    "meta": [
                        "Implementos: 3",
                        "Stock disponible: 111",
                        "Alertas: 0",
                    ],
                    "badges": [],
                }
            ],
        }
    ]

    sanitized = sanitize_response_text(text, allow_identifiers=False, ui_blocks=ui_blocks)

    assert sanitized == "Aqui tienes un resumen del inventario por categoria:"


def test_sanitize_response_text_drops_duplicate_title_only_when_ui_blocks_exist() -> None:
    ui_blocks = [
        {
            "type": "entity_list",
            "title": "Inventario por categoria",
            "entities": [],
        }
    ]

    sanitized = sanitize_response_text(
        "Inventario por categoria",
        allow_identifiers=False,
        ui_blocks=ui_blocks,
    )

    assert sanitized == ""


def test_sanitize_response_text_preserves_regular_markdown_without_ui_blocks() -> None:
    text = "Resumen ejecutivo.\n\n- Punto 1\n- Punto 2"

    sanitized = sanitize_response_text(text, allow_identifiers=False)

    assert sanitized == text
