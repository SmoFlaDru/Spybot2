import { nodeResolve } from '@rollup/plugin-node-resolve';
import css from "rollup-plugin-import-css";

export default {
  context: 'window',
  input: 'main.js',
  output: {
    dir: 'output',
    format: 'iife'
  },
  plugins: [nodeResolve(), css({'output': 'main.css'})]
};