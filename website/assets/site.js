const menuButton = document.querySelector("[data-menu-button]");
const navigation = document.querySelector("[data-navigation]");

menuButton?.addEventListener("click", () => {
  const isOpen = navigation?.dataset.open === "true";
  if (navigation) navigation.dataset.open = String(!isOpen);
  menuButton.setAttribute("aria-expanded", String(!isOpen));
});

function closeNavigation() {
  if (!navigation || !menuButton) return;
  navigation.dataset.open = "false";
  menuButton.setAttribute("aria-expanded", "false");
}

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape") {
    closeNavigation();
    menuButton?.focus();
  }
});

document.addEventListener("click", (event) => {
  if (!navigation?.dataset.open || navigation.dataset.open !== "true") return;
  if (navigation.contains(event.target) || menuButton?.contains(event.target)) return;
  closeNavigation();
});

navigation?.addEventListener("click", (event) => {
  if (event.target.closest("a")) closeNavigation();
});

async function copyText(button, text) {
  const original = button.textContent;
  try {
    await navigator.clipboard.writeText(text);
    button.textContent = "Copied";
  } catch {
    button.textContent = "Select and copy";
  }
  window.setTimeout(() => { button.textContent = original; }, 1600);
}

for (const button of document.querySelectorAll("[data-copy]")) {
  button.addEventListener("click", async () => {
    const target = document.getElementById(button.dataset.copy);
    if (!target) return;
    await copyText(button, target.textContent.trim());
  });
}

for (const button of document.querySelectorAll("[data-copy-path]")) {
  button.addEventListener("click", () => copyText(button, button.dataset.copyPath));
}

const filterButtons = document.querySelectorAll("[data-filter]");
const signalRows = document.querySelectorAll("tr[data-probe-id]");
const catalogSearch = document.querySelector("[data-catalog-search]");
const catalogStatus = document.querySelector("[data-catalog-status]");
let activeFilter = "all";

function matchesFilter(row) {
  if (activeFilter === "all") return true;
  if (activeFilter === "android" || activeFilter === "ios") {
    return row.dataset.platform.split(" ").includes(activeFilter);
  }
  if (activeFilter === "default-on") return row.dataset.default === "on";
  if (activeFilter === "default-off") return row.dataset.default === "off";
  if (activeFilter === "permission") return row.dataset.permission === "yes";
  if (activeFilter === "high") return row.dataset.sensitivity === "high";
  return true;
}

function updateCatalog() {
  const query = catalogSearch?.value.trim().toLocaleLowerCase("en") ?? "";
  let visibleProbes = 0;
  let visibleFields = 0;
  for (const row of signalRows) {
    const fields = [...row.querySelectorAll("[data-field-name]")];
    const matchingFields = query.length === 0
      ? fields
      : fields.filter((field) => field.dataset.fieldSearch.includes(query));
    const matchesSearch = query.length === 0 || row.dataset.search.includes(query);
    row.hidden = !matchesFilter(row) || !matchesSearch;
    if (!row.hidden) {
      visibleProbes += 1;
      const shouldNarrowFields = query.length > 0 && matchingFields.length > 0;
      for (const field of fields) {
        field.hidden = shouldNarrowFields && !matchingFields.includes(field);
      }
      visibleFields += shouldNarrowFields ? matchingFields.length : fields.length;
      const disclosure = row.querySelector(".field-disclosure");
      if (query.length >= 2 && disclosure) disclosure.open = true;
    } else {
      for (const field of fields) field.hidden = false;
    }
  }
  if (catalogStatus) {
    catalogStatus.textContent = visibleProbes === 0
      ? "No probes match this search and filter."
      : `Showing ${visibleProbes} ${visibleProbes === 1 ? "probe" : "probes"} and ${visibleFields} ${visibleFields === 1 ? "field" : "fields"}.`;
  }
}

for (const button of filterButtons) {
  button.addEventListener("click", () => {
    activeFilter = button.dataset.filter;
    for (const candidate of filterButtons) {
      candidate.setAttribute("aria-pressed", String(candidate === button));
    }
    updateCatalog();
  });
}

catalogSearch?.addEventListener("input", updateCatalog);

function revealCatalogFieldFromHash() {
  if (!window.location.hash.startsWith("#field-")) return;
  const target = document.getElementById(decodeURIComponent(window.location.hash.slice(1)));
  if (!target?.matches("[data-field-name]")) return;
  const disclosure = target.closest("details");
  if (disclosure) disclosure.open = true;
  target.scrollIntoView({block: "center"});
  window.requestAnimationFrame(() => target.scrollIntoView({block: "center"}));
}

window.addEventListener("hashchange", revealCatalogFieldFromHash);
revealCatalogFieldFromHash();
