import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import dts from 'vite-plugin-dts'

export default defineConfig({
  plugins: [react(), dts({ rollupTypes: true, exclude: ['**/*.stories.*'], compilerOptions: { skipLibCheck: true } })],
  build: {
    lib: {
      entry: 'src/index.ts',
      formats: ['es', 'cjs'],
      fileName: 'index',
    },
    rollupOptions: {
      external: ['react', 'react-dom', 'react/jsx-runtime', 'livekit-client', '@rtcstack/sdk'],
    },
    cssCodeSplit: false,
  },
})
