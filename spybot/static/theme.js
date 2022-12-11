/**
 * demo-theme is specifically loaded right after the body and not deferred
 * to ensure we switch to the chosen dark/light theme as fast as possible.
 * This will prevent any flashes of the light theme (default) before switching.
 */

const themeStorageKey = 'tablerTheme'
let selectedTheme

function configureTheme() {
	console.log("configuring theme")
	// https://stackoverflow.com/a/901144
	const params = new Proxy(new URLSearchParams(window.location.search), {
		get: (searchParams, prop) => searchParams.get(prop),
	});

	const isSystemDark = window.matchMedia("(prefers-color-scheme: dark)").matches
	const defaultTheme = isSystemDark ? 'dark' : 'light'

	if (!!params.theme) {
		isParamDark = params.theme === 'dark'

		if (isParamDark !== isSystemDark)
			localStorage.setItem(themeStorageKey, params.theme)
		else
			localStorage.removeItem(themeStorageKey)
		selectedTheme = params.theme
	} else {
		const storedTheme = localStorage.getItem(themeStorageKey)
		selectedTheme = storedTheme ? storedTheme : defaultTheme
	}

	document.body.classList.remove('theme-dark', 'theme-light');
	document.body.classList.add(`theme-${selectedTheme}`);
}

window.matchMedia("(prefers-color-scheme: dark)").onchange = configureTheme
// configure theme now
configureTheme()