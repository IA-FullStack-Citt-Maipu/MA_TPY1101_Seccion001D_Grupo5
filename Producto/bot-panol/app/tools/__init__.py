from app.tools.alerts import listar_implementos_bajo_stock_minimo
from collections.abc import Sequence

from app.tools.catalog import buscar_implementos
from app.tools.categories import listar_categorias
from app.tools.details import detalle_implemento
from app.tools.loans import listar_prestamos, listar_prestamos_programados
from app.tools.locations import listar_ubicaciones
from app.tools.stock import consultar_stock


def get_tools() -> Sequence[object]:
    return [
        buscar_implementos,
        consultar_stock,
        listar_prestamos,
        listar_prestamos_programados,
        detalle_implemento,
        listar_ubicaciones,
        listar_categorias,
        listar_implementos_bajo_stock_minimo,
    ]
