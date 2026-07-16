const menuButton = document.querySelector("[data-menu-button]");
const navigation = document.querySelector("[data-navigation]");

menuButton?.addEventListener("click", () => {
  const isOpen = navigation?.dataset.open === "true";
  if (navigation) navigation.dataset.open = String(!isOpen);
  menuButton.setAttribute("aria-expanded", String(!isOpen));
});

for (const button of document.querySelectorAll("[data-copy]")) {
  button.addEventListener("click", async () => {
    const target = document.getElementById(button.dataset.copy);
    if (!target) return;
    const original = button.textContent;
    try {
      await navigator.clipboard.writeText(target.textContent.trim());
      button.textContent = "Copied";
    } catch {
      button.textContent = "Select and copy";
    }
    window.setTimeout(() => { button.textContent = original; }, 1600);
  });
}

const filterButtons = document.querySelectorAll("[data-filter]");
const signalCards = document.querySelectorAll("[data-platform]");

for (const button of filterButtons) {
  button.addEventListener("click", () => {
    const filter = button.dataset.filter;
    for (const candidate of filterButtons) {
      candidate.setAttribute("aria-pressed", String(candidate === button));
    }
    for (const card of signalCards) {
      card.hidden = filter !== "all" && !card.dataset.platform.split(" ").includes(filter);
    }
  });
}
