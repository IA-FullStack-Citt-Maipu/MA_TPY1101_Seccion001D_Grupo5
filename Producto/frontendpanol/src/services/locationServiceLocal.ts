import { localDB } from "./localDB";
import type { LocationOption } from "../types/location";

const LOCATIONS_COLLECTION = "locations";

let initPromise: Promise<void> | null = null;

async function initializeLocations() {
  const existing = await localDB.get<LocationOption[]>(LOCATIONS_COLLECTION);
  if (!existing || existing.length === 0) {
    try {
      const data = await localDB.loadJSON<{ locations: LocationOption[] }>("/db/locations.json");
      await localDB.set(LOCATIONS_COLLECTION, data.locations);
    } catch (error) {
      console.error("Error initializing locations:", error);
      await localDB.set(LOCATIONS_COLLECTION, []);
    }
  }
}

async function ensureInit() {
  if (!initPromise) {
    initPromise = initializeLocations();
  }
  await initPromise;
}

export async function fetchLocationsForManagement(): Promise<LocationOption[]> {
  await ensureInit();
  return (await localDB.get<LocationOption[]>(LOCATIONS_COLLECTION)) || [];
}

export async function fetchActiveLocations(): Promise<LocationOption[]> {
  await ensureInit();
  const locations = (await localDB.get<LocationOption[]>(LOCATIONS_COLLECTION)) || [];
  return locations.filter((l) => l.active !== false);
}

export async function createLocation(payload: { name: string; description: string | null }): Promise<LocationOption> {
  await ensureInit();

  const newLocation: LocationOption = {
    uuid: `loc-${Date.now()}`,
    name: payload.name,
    description: payload.description,
    active: true,
  };

  await localDB.addItem(LOCATIONS_COLLECTION, newLocation);
  return newLocation;
}

export async function updateLocation(uuid: string, payload: { name: string; description: string | null }): Promise<LocationOption> {
  await ensureInit();

  const locations = (await localDB.get<LocationOption[]>(LOCATIONS_COLLECTION)) || [];
  const location = locations.find((l) => l.uuid === uuid);

  if (!location) {
    throw new Error(`Ubicacion con uuid ${uuid} no encontrada`);
  }

  const updated: LocationOption = {
    ...location,
    name: payload.name,
    description: payload.description,
  };

  await localDB.updateItem(LOCATIONS_COLLECTION, uuid, updated);
  return updated;
}

export async function setLocationActive(uuid: string, active: boolean): Promise<LocationOption> {
  await ensureInit();

  const locations = (await localDB.get<LocationOption[]>(LOCATIONS_COLLECTION)) || [];
  const location = locations.find((l) => l.uuid === uuid);

  if (!location) {
    throw new Error(`Ubicacion con uuid ${uuid} no encontrada`);
  }

  const updated: LocationOption = {
    ...location,
    active,
  };

  await localDB.updateItem(LOCATIONS_COLLECTION, uuid, updated);
  return updated;
}

export async function deleteLocation(uuid: string): Promise<void> {
  await localDB.deleteItem(LOCATIONS_COLLECTION, uuid);
}
