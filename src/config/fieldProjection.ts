import type {FieldSelection} from "./probeConfig";

/**
 * Trim a probe's collected payload to the configured top-level fields. Pure and total: anything that
 * is not a plain object (arrays, primitives, null) is returned unchanged, so a probe whose data is a
 * scalar is never corrupted. `include` keeps only the listed keys; `exclude` drops the listed keys;
 * no selection returns the data untouched.
 *
 * This is the field-level lever for "what is collected and sent": the projected object is what lands
 * in RawSignalEvent.probes[id].data (see DeviceIntel.collect), which is exactly what Transport sends.
 */
export function projectData<T>(data: T, selection?: FieldSelection): T {
  if (!selection || data === null || typeof data !== "object" || Array.isArray(data)) {
    return data;
  }
  const source = data as Record<string, unknown>;
  const out: Record<string, unknown> = {};

  if ("include" in selection) {
    const keep = new Set(selection.include);
    for (const key of Object.keys(source)) {
      if (keep.has(key)) out[key] = source[key];
    }
  } else {
    const drop = new Set(selection.exclude);
    for (const key of Object.keys(source)) {
      if (!drop.has(key)) out[key] = source[key];
    }
  }

  return out as T;
}
