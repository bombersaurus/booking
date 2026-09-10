// This workspace deliberately has one light theme, regardless of Swagger's saved theme.
function useLightTheme() {
    if (document.documentElement.classList.contains("dark-mode")) {
        document.documentElement.classList.remove("dark-mode");
    }
    document.documentElement.style.colorScheme = "light";
}
useLightTheme();
new MutationObserver(useLightTheme).observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["class"]
});
