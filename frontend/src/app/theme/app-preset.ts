import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

export const AppAuraPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{blue.50}',
      100: '{blue.100}',
      200: '{blue.200}',
      300: '{blue.300}',
      400: '{blue.400}',
      500: '{blue.500}',
      600: '{blue.600}',
      700: '{blue.700}',
      800: '{blue.800}',
      900: '{blue.900}',
      950: '{blue.950}',
    },
    colorScheme: {
      light: {
        surface: {
          0: '#ffffff',
          50: '#fafcff',
          100: '#f5f8fc',
          200: '#eef3f9',
          300: '#e2eaf3',
          400: '#cdd8e6',
          500: '#b3c0d4',
          600: '#8fa0b3',
          700: '#6f7d90',
          800: '#505c6b',
          900: '#343b46',
          950: '#1e2229',
        },
        text: {
          color: '#111111',
          hoverColor: '#000000',
          mutedColor: '#4a4f58',
          hoverMutedColor: '#3d424a',
        },
        formField: {
          color: '#111111',
          placeholderColor: '#6b7280',
        },
      },
    },
  },
});
