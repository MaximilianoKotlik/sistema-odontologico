import type { Config } from 'tailwindcss';

// Los tokens de design-system/tokens.css se mapean acá en C-06 (frontend-shell-pwa).
export default {
  content: ['./index.html', './src/**/*.ts'],
  theme: {
    extend: {},
  },
  plugins: [],
} satisfies Config;
