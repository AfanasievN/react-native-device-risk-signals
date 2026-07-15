// Test-only helper. Under the project's `noUncheckedIndexedAccess: true`, `arr[0]`, destructured
// `const [x] = arr`, and RegExp capture groups are all typed `T | undefined`. Specs know the element
// is present (they assert length / matched a literal), so this narrows the type AND fails loudly with
// a useful message instead of a downstream `undefined` if the assumption is ever wrong.
//
// Not exported from the package barrel (src/index.ts) — it is not part of the public API.
export function assertDefined<T>(value: T | null | undefined, message = "expected a defined value"): T {
  if (value === null || value === undefined) {
    throw new Error(message);
  }
  return value;
}
