const v = (n) => `hsl(var(--${n}) / <alpha-value>)`
/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: { extend: { colors: {
    background: v('background'), foreground: v('foreground'), card: v('card'), muted: v('muted'), 'muted-foreground': v('muted-foreground'),
    border: v('border'), primary: v('primary'), 'primary-foreground': v('primary-foreground'), success: v('success'), warning: v('warning'), danger: v('danger'),
  } } },
  plugins: [],
}
