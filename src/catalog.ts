/**
 * Probe metadata: the machine-readable inventory of every probe, plus the config validation that needs
 * it. Kept out of the main entry point on purpose.
 *
 * The catalog is documentation in data form — titles, purposes, notes, field lists, data categories. The
 * collection runtime reads none of it: it validates a config against the probes actually registered, and
 * emits whatever the probes return. But Metro does not tree-shake, so anything reachable from `index`
 * ships to every device whether it is used or not, and the catalog alone is ~15 KB minified — around 45%
 * of this package's bundle footprint.
 *
 * Import it explicitly when you need it — consent screens, config validation, generating a data
 * dictionary, tests:
 *
 * ```ts
 * import {PROBE_CATALOG, validateProbeConfig} from "react-native-device-risk-signals/catalog";
 * ```
 */
export type {ProbeDescriptor, ProbeId, ProbePlatform, ProbeSensitivity} from "./probeCatalog";
export {getProbeDescriptor, PROBE_CATALOG} from "./probeCatalog";

import type {ProbeConfig, ProbeConfigIssue} from "./config/probeConfig";
import {validateAgainstIds} from "./config/probeConfig";
import {getProbeDescriptor, PROBE_CATALOG} from "./probeCatalog";

/**
 * Checks a config against the catalog: unknown probe ids, invalid timeouts, and — unlike the runtime's
 * own check — unknown field names in `fields`, which only the catalog knows about.
 *
 * The SDK never calls this itself; the runtime validates against the registered probes instead. Use it
 * in tests or tooling where a typo in a config should fail loudly rather than silently do nothing.
 */
export function validateProbeConfig(config: ProbeConfig): ProbeConfigIssue[] {
  return validateAgainstIds(
    config,
    new Set(PROBE_CATALOG.map(({id}) => id)),
    (id) => getProbeDescriptor(id)?.fields,
  );
}
