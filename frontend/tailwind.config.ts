import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        navy: '#123047',
        brand: '#0f766e'
      }
    }
  },
  plugins: []
} satisfies Config;
