// Servicio para simular una base de datos usando JSON files y localStorage

const DB_PREFIX = "panol_db_";

class LocalDB {
  private cache: Map<string, unknown> = new Map();

  async loadJSON<T>(filePath: string): Promise<T> {
    const cached = this.cache.get(filePath);
    if (cached) return cached as T;

    try {
      const response = await fetch(filePath);
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json() as T;
      this.cache.set(filePath, data);
      return data;
    } catch (error) {
      console.error(`Error loading ${filePath}:`, error);
      throw error;
    }
  }

  // Obtener datos de localStorage (datos persistentes entre sesiones)
  async get<T>(key: string): Promise<T | null> {
    const stored = localStorage.getItem(DB_PREFIX + key);
    if (stored) {
      try {
        return JSON.parse(stored) as T;
      } catch {
        return null;
      }
    }
    return null;
  }

  // Guardar datos en localStorage
  async set<T>(key: string, value: T): Promise<void> {
    localStorage.setItem(DB_PREFIX + key, JSON.stringify(value));
    this.cache.delete(key);
  }

  // Agregar elemento a una colección
  async addItem<T extends { id?: string; uuid?: string }>(collection: string, item: T): Promise<void> {
    const items = (await this.get<T[]>(collection)) || [];
    items.push(item);
    await this.set(collection, items);
  }

  // Actualizar elemento por id o uuid
  async updateItem<T extends { id?: string; uuid?: string }>(collection: string, id: string, updates: Partial<T>): Promise<void> {
    const items = (await this.get<T[]>(collection)) || [];
    const index = items.findIndex((item) => (item.id === id || item.uuid === id));
    if (index !== -1) {
      items[index] = { ...items[index], ...updates } as T;
      await this.set(collection, items);
    }
  }

  // Eliminar elemento por id o uuid
  async deleteItem(collection: string, id: string): Promise<void> {
    const items = (await this.get<Array<{ id?: string; uuid?: string }>>(collection)) || [];
    const filtered = items.filter((item) => item.id !== id && item.uuid !== id);
    await this.set(collection, filtered);
  }

  // Limpiar cache
  clearCache(): void {
    this.cache.clear();
  }
}

export const localDB = new LocalDB();
