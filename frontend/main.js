import * as ApexCharts from 'apexcharts/dist/apexcharts.min.js'
import * as tabler from '@tabler/core/dist/js/tabler.min.js'
import * as htmx from 'htmx.org/dist/htmx.min.js'
import * as passkeys from './passkeys';

import "@tabler/core/dist/css/tabler.min.css"
import "@tabler/core/dist/css/tabler-vendors.min.css"
import "@tabler/core/dist/css/tabler-themes.min.css"
import './modal'


window.passkeys = passkeys;

export { ApexCharts, tabler, htmx, passkeys }