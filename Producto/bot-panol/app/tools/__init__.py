from collections.abc import Sequence

from app.tools.alerts import listar_implementos_bajo_stock_minimo
from app.tools.analytics import (
    contar_prestamos_por_producto,
    recomendar_reposicion,
    resumen_inventario_por_categoria,
)
from app.tools.catalog import buscar_implementos
from app.tools.categories import listar_categorias
from app.tools.details import detalle_implemento
from app.tools.loans import listar_prestamos, listar_prestamos_programados
from app.tools.locations import listar_ubicaciones
from app.tools.stock import consultar_stock


def get_tools(role: str = "COORDINADOR") -> Sequence[object]:
    normalized_role = (role or "").strip().upper()
    shared_tools = [
        buscar_implementos,
        consultar_stock,
        listar_prestamos,
        listar_prestamos_programados,
        listar_implementos_bajo_stock_minimo,
        contar_prestamos_por_producto,
        recomendar_reposicion,
        resumen_inventario_por_categoria,
    ]

    if normalized_role == "DIRECTOR":
        return shared_tools

    return [
        *shared_tools,
        detalle_implemento,
        listar_ubicaciones,
        listar_categorias,
    ]
