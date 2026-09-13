import { nodeResolve } from '@rollup/plugin-node-resolve';
import css from "rollup-plugin-import-css";
import terser from "@rollup/plugin-terser";

export default [{
  context: 'window',
  input: 'main.js',
  output: {
    dir: 'output',
    format: 'iife',
    name: 'jsbundle',
  },
  plugins: [nodeResolve(), css({'output': 'main.css'}), terser()]
},
{
  context: 'window',
  input: 'early.js',
  output: {
    dir: 'output',
    format: 'iife',
    name: 'jsbundle',
  },
  plugins: [nodeResolve(), terser()]
}];