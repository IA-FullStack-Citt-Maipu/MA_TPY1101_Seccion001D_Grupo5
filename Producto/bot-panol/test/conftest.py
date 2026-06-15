import pytest


def pytest_collection_modifyitems(config: pytest.Config, items: list[pytest.Item]) -> None:
    markexpr = (config.option.markexpr or "").strip()
    run_integration = "integration" in markexpr
    if run_integration:
        return

    skip_marker = pytest.mark.skip(reason="Integration tests are opt-in. Use: pytest -m integration")
    for item in items:
        if "integration" in item.keywords:
            item.add_marker(skip_marker)
