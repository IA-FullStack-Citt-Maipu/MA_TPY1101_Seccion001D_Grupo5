import { localDB } from "./localDB";
import type { ImplementSummary, ImplementDetail, ImplementFilters, ImplementCreatePayload, ImplementUpdatePayload } from "../types/implement";
import type { Categoria } from "../types/category";
import type { LocationOption } from "../types/location";

const IMPLEMENTS_COLLECTION = "implements";
const CATEGORIES_COLLECTION = "categories";
const LOCATIONS_COLLECTION = "locations";

interface RawImplement {
  uuid: string;
  name: string;
  description: string | null;
  item_type: "fungible" | "no_fungible";
  category_uuid: string;
  location_uuid: string;
  barcode: string | null;
  img_url: string | null;
  min_stock: number;
  observations: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  stock: {
    total_stock: number;
    available: number;
    reserved: number;
    loaned: number;
    damaged: number;
  };
}

let initPromise: Promise<void> | null = null;

async function initializeImplements() {
  const existing = await localDB.get<RawImplement[]>(IMPLEMENTS_COLLECTION);
  if (!existing || existing.length === 0) {
    try {
      const data = await localDB.loadJSON<{ implements: RawImplement[] }>("/db/implements.json");
      await localDB.set(IMPLEMENTS_COLLECTION, data.implements);
    } catch (error) {
      console.error("Error initializing implements:", error);
      await localDB.set(IMPLEMENTS_COLLECTION, []);
    }
  }
}

async function ensureInit() {
  if (!initPromise) {
    initPromise = initializeImplements();
  }
  await initPromise;
}

async function getCategories(): Promise<Categoria[]> {
  return (await localDB.get<Categoria[]>(CATEGORIES_COLLECTION)) || [];
}

async function getLocations(): Promise<LocationOption[]> {
  return (await localDB.get<LocationOption[]>(LOCATIONS_COLLECTION)) || [];
}

export async function fetchImplements(filters?: ImplementFilters): Promise<ImplementSummary[]> {
  await ensureInit();
  
  const implements_ = (await localDB.get<RawImplement[]>(IMPLEMENTS_COLLECTION)) || [];
  const categories = await getCategories();
  const locations = await getLocations();

  let filtered = implements_;

  if (filters?.name) {
    const searchTerm = filters.name.toLowerCase();
    filtered = filtered.filter((i) => i.name.toLowerCase().includes(searchTerm));
  }

  if (filters?.categoryUuid) {
    filtered = filtered.filter((i) => i.category_uuid === filters.categoryUuid);
  }

  if (filters?.stockStatus && filters.stockStatus !== "all") {
    filtered = filtered.filter((i) => {
      const stock = i.stock;
      switch (filters.stockStatus) {
        case "available": return (stock?.available ?? 0) > 0;
        case "reserved": return (stock?.reserved ?? 0) > 0;
        case "loaned": return (stock?.loaned ?? 0) > 0;
        case "damaged": return (stock?.damaged ?? 0) > 0;
        default: return true;
      }
    });
  }

  return filtered.map((impl) => {
    const category = categories.find((c) => c.uuid === impl.category_uuid);
    const location = locations.find((l) => l.uuid === impl.location_uuid);

    return {
      uuid: impl.uuid,
      name: impl.name,
      description: impl.description,
      barcode: impl.barcode,
      imgUrl: impl.img_url,
      active: impl.active,
      available: (impl.stock?.available ?? 0) > 0,
      category: category ? {
        uuid: category.uuid,
        name: category.nombre,
        active: category.activa,
      } : null,
      location: location ? {
        uuid: location.uuid,
        name: location.name,
        description: location.description ?? null,
      } : null,
      stock: impl.stock ? {
        total_stock: impl.stock.total_stock,
        min_stock: impl.min_stock,
        available: impl.stock.available,
        reserved: impl.stock.reserved,
        loaned: impl.stock.loaned,
        damaged: impl.stock.damaged,
        available_display: `${impl.stock.available} disponibles`,
      } : null,
    };
  });
}

export async function fetchImplementDetail(uuid: string): Promise<ImplementDetail | null> {
  await ensureInit();
  
  const implements_ = (await localDB.get<RawImplement[]>(IMPLEMENTS_COLLECTION)) || [];
  const categories = await getCategories();
  const locations = await getLocations();

  const impl = implements_.find((i) => i.uuid === uuid);
  if (!impl) return null;

  const category = categories.find((c) => c.uuid === impl.category_uuid);
  const location = locations.find((l) => l.uuid === impl.location_uuid);

  return {
    uuid: impl.uuid,
    name: impl.name,
    description: impl.description,
    item_type: impl.item_type,
    display_location: location?.name ?? null,
    category: category ? {
      uuid: category.uuid,
      name: category.nombre,
      active: category.activa,
    } : null,
    location: location ? {
      uuid: location.uuid,
      name: location.name,
      description: location.description ?? null,
    } : null,
    category_uuid: impl.category_uuid,
    location_uuid: impl.location_uuid,
    min_stock: impl.min_stock,
    barcode: impl.barcode,
    img_url: impl.img_url,
    observations: impl.observations,
    active: impl.active,
    createdAt: impl.createdAt,
    updatedAt: impl.updatedAt,
    stock: impl.stock ? {
      available: impl.stock.available,
      reserved: impl.stock.reserved,
      loaned: impl.stock.loaned,
      damaged: impl.stock.damaged,
      total_stock: impl.stock.total_stock,
      available_display: `${impl.stock.available} de ${impl.stock.total_stock}`,
    } : null,
    recent_movements: [],
  };
}

export async function createImplement(payload: ImplementCreatePayload): Promise<ImplementSummary> {
  await ensureInit();

  const newImpl: RawImplement = {
    uuid: `impl-${Date.now()}`,
    name: payload.name,
    description: payload.description,
    item_type: payload.item_type,
    category_uuid: payload.categoryUuid,
    location_uuid: payload.locationUuid,
    barcode: payload.barcode,
    img_url: payload.img_url,
    min_stock: payload.min_stock,
    observations: payload.observations,
    active: true,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    stock: {
      total_stock: 0,
      available: 0,
      reserved: 0,
      loaned: 0,
      damaged: 0,
    },
  };

  await localDB.addItem(IMPLEMENTS_COLLECTION, newImpl);

  const categories = await getCategories();
  const locations = await getLocations();
  const category = categories.find((c) => c.uuid === newImpl.category_uuid);
  const location = locations.find((l) => l.uuid === newImpl.location_uuid);

  return {
    uuid: newImpl.uuid,
    name: newImpl.name,
    description: newImpl.description,
    barcode: newImpl.barcode,
    imgUrl: newImpl.img_url,
    active: newImpl.active,
    available: false,
    category: category ? {
      uuid: category.uuid,
      name: category.nombre,
      active: category.activa,
    } : null,
    location: location ? {
      uuid: location.uuid,
      name: location.name,
      description: location.description ?? null,
    } : null,
    stock: null,
  };
}

export async function updateImplement(uuid: string, payload: ImplementUpdatePayload): Promise<ImplementDetail> {
  await ensureInit();

  const implements_ = (await localDB.get<RawImplement[]>(IMPLEMENTS_COLLECTION)) || [];
  const impl = implements_.find((i) => i.uuid === uuid);

  if (!impl) {
    throw new Error(`Implemento con uuid ${uuid} no encontrado`);
  }

  const updated: RawImplement = {
    ...impl,
    name: payload.name,
    description: payload.description,
    item_type: payload.item_type,
    category_uuid: payload.categoryUuid,
    location_uuid: payload.locationUuid,
    barcode: payload.barcode,
    img_url: payload.img_url,
    min_stock: payload.min_stock,
    observations: payload.observations,
    updatedAt: new Date().toISOString(),
  };

  await localDB.updateItem(IMPLEMENTS_COLLECTION, uuid, updated);

  const detail = await fetchImplementDetail(uuid);
  if (!detail) throw new Error("Error al obtener detalle actualizado");
  return detail;
}

export async function deleteImplement(uuid: string): Promise<void> {
  await localDB.deleteItem(IMPLEMENTS_COLLECTION, uuid);
}
