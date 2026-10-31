// ApexCharts is assembled from its modular build: the bare core plus only the chart types the
// templates use (matching chart.type) and the legend feature. The batteries-included entry would
// add ~450 KB to main.js for chart types nothing here draws.
import ApexCharts from 'apexcharts/core'
import 'apexcharts/line'
import 'apexcharts/bar'
import 'apexcharts/heatmap'
import 'apexcharts/rangeBar'
import 'apexcharts/treemap'
import 'apexcharts/features/legend'
import * as tabler from '@tabler/core/dist/js/tabler.min.js'
import htmx from 'htmx.org'
import { RelativeTimeElement } from '@github/relative-time-element'
import * as passkeys from './passkeys';

import "@tabler/core/dist/css/tabler.min.css"
import "@tabler/core/dist/css/tabler-vendors.min.css"
import "@tabler/core/dist/css/tabler-themes.min.css"
import './modal'

// Tabler 1.7 describes its colors with CSS color functions (oklch(), color-mix(), light-dark()).
// ApexCharts 8 cannot work with those: it sees "srgb" inside color-mix() and parses it as an
// rgb() colour, which fails and paints the series silver, and an SVG stroke or fill in a modern
// colour is only as safe as the browser's SVG support. Resolve any CSS colour to the plain
// rgb()/rgba() string charts understand; the templates use these instead of tabler.getColor().
const resolveCssColor = (value, alpha = 1) => {
    const probe = document.createElement('span')
    probe.style.color = String(value).trim()
    if (!probe.style.color) return value
    document.body.appendChild(probe)
    const computed = getComputedStyle(probe).color
    probe.remove()
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 1
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    ctx.fillStyle = computed
    ctx.fillRect(0, 0, 1, 1)
    const [r, g, b, a] = ctx.getImageData(0, 0, 1, 1).data
    const opacity = +((a / 255) * alpha).toFixed(3)
    return opacity === 1 ? `rgb(${r}, ${g}, ${b})` : `rgba(${r}, ${g}, ${b}, ${opacity})`
}
// The templates call tabler.getColor through the page-level `tabler` global; the namespace import
// above does not expose it the same way inside the bundle, so use the global at call time too.
const chartColor = (name, alpha = 1) => resolveCssColor(window.tabler.tabler.getColor(name), alpha)

window.htmx = htmx;
// Same for ApexCharts: the templates build charts with `new ApexCharts(...)` inline.
window.ApexCharts = ApexCharts;
window.passkeys = passkeys;
window.resolveCssColor = resolveCssColor;
window.chartColor = chartColor;

export { ApexCharts, tabler, htmx, passkeys, RelativeTimeElement }
