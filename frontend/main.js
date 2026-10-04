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

window.htmx = htmx;
// Same for ApexCharts: the templates build charts with `new ApexCharts(...)` inline.
window.ApexCharts = ApexCharts;
window.passkeys = passkeys;

export { ApexCharts, tabler, htmx, passkeys, RelativeTimeElement }
