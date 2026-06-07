from collections.abc import Sequence

from app.tools.catalog import buscar_implementos
from app.tools.loans import listar_prestamos
from app.tools.stock import consultar_stock


def get_tools() -> Sequence[object]:
    return [buscar_implementos, consultar_stock, listar_prestamos]
