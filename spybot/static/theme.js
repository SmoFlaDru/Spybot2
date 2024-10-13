/**
 * demo-theme is specifically loaded right after the body and not deferred
 * to ensure we switch to the chosen dark/light theme as fast as possible.
 * This will prevent any flashes of the light theme (default) before switching.
 */

const themeStorageKey = 'tablerTheme'

function configureTheme(wantedTheme) {
    function readTheme(wantedTheme, urlParams, allowedValues) {
        if (allowedValues.has(wantedTheme)) {
            localStorage.setItem(themeStorageKey, wantedTheme)
            return wantedTheme;
        }
        else if (!!urlParams.theme && allowedValues.has(urlParams.theme)) {
            const t = urlParams.theme
            localStorage.setItem(themeStorageKey, t)
            return t;
        } else if (allowedValues.has(localStorage.getItem(themeStorageKey))) {
            return localStorage.getItem(themeStorageKey)
        } else {
            return "auto"
        }
    }

    function applyTheme(theme) {
        let realTheme = theme
        if (realTheme === "auto") {
            realTheme = window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"
        }
        document.body.classList.remove('theme-dark', 'theme-light');
        document.body.classList.add(`theme-${realTheme}`);
        if (realTheme === 'dark') {
            if (document.body.getAttribute('data-bs-theme') !== realTheme) {
                document.body.setAttribute("data-bs-theme", realTheme)
            }
        } else {
            document.body.removeAttribute("data-bs-theme")
        }

        document.body.setAttribute("data-spybot-theme", theme)
    }

    console.log("configuring theme")
    // https://stackoverflow.com/a/901144
    const urlParams = new Proxy(new URLSearchParams(window.location.search), {
        get: (searchParams, prop) => searchParams.get(prop),
    });

    const allowedValues = new Set(["light", "dark", "auto"])

    const theme = readTheme(wantedTheme, urlParams, allowedValues);
    applyTheme(theme);
}

window.matchMedia("(prefers-color-scheme: dark)").addEventListener('change', configureTheme)
// configure theme now
configureTheme()