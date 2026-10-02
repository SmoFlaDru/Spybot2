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
    // Only a theme picked by the user (not the initial load or an OS change) gets the animation.
    if (typeof wantedTheme === "string") {
        applyThemeWithReveal(theme, applyTheme)
    } else {
        applyTheme(theme);
    }
}

/**
 * Switches the theme with a circular reveal that grows out of the theme button, using a
 * same-document view transition. Falls back to an instant switch where view transitions are
 * unsupported, when the user prefers reduced motion, or when the theme would not change.
 */
function applyThemeWithReveal(theme, applyTheme) {
    const isDark = () => document.body.classList.contains("theme-dark")
    const nextIsDark = theme === "dark" || (theme === "auto" && window.matchMedia("(prefers-color-scheme: dark)").matches)
    const button = document.getElementById("theme-switch-btn")
    if (!document.startViewTransition
        || !button
        || nextIsDark === isDark()
        || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        applyTheme(theme)
        return
    }

    const rect = button.getBoundingClientRect()
    const x = rect.left + rect.width / 2
    const y = rect.top + rect.height / 2
    const radius = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y))

    // The marker lets styles.css suspend the nav/page view-transition names, so the whole page
    // is one snapshot that the circle reveals.
    const root = document.documentElement
    root.setAttribute("data-theme-reveal", "")
    const transition = document.startViewTransition(() => applyTheme(theme))
    transition.ready.then(() => {
        root.animate(
            {clipPath: [`circle(0 at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`]},
            {duration: 600, easing: "ease-in-out", pseudoElement: "::view-transition-new(root)"}
        )
    }).catch(() => {})
    transition.finished.finally(() => root.removeAttribute("data-theme-reveal"))
}

window.matchMedia("(prefers-color-scheme: dark)").addEventListener('change', configureTheme)
// configure theme now
configureTheme()