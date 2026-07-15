import type {RawSignalEvent} from "../DeviceIntel";

/** Typed JSON envelope. Transport confidentiality is provided by HTTPS/TLS. */
export type WireEnvelope = {kind: "plaintext"; payload: RawSignalEvent};

export function serializePlaintext(event: RawSignalEvent): WireEnvelope {
  return {kind: "plaintext", payload: event};
}
