import * as ApexCharts from 'apexcharts/dist/apexcharts.min.js'
import * as tabler from '@tabler/core/dist/js/tabler.min.js'
import htmx from 'htmx.org'
import { RelativeTimeElement } from '@github/relative-time-element'
import * as passkeys from './passkeys';

import "@tabler/core/dist/css/tabler.min.css"
import "@tabler/core/dist/css/tabler-vendors.min.css"
import "@tabler/core/dist/css/tabler-themes.min.css"
import './modal'

// htmx 2 no longer assigns itself to window; the templates' inline scripts (htmx.trigger(...))
// and htmx's own hx-* processing of swapped-in content expect the global.
window.htmx = htmx;
window.passkeys = passkeys;

export { ApexCharts, tabler, htmx, passkeys, RelativeTimeElement }
