# Expo example (shop + Pulse)

Expo Router demo: **[DummyJSON](https://dummyjson.com)** (HTTPS), tabs, cart / wishlist / orders / recent views via **AsyncStorage**, plus **Pulse** (`Pulse.start()` in `app/_layout.tsx`).

Pulse ships **native** code. Use a **development build** — not Expo Go:

```bash
npm install
npm run prebuild
npm run ios   # or npm run android
```

`npx expo start` is for bundling against that dev client. Config plugin and setup: `../EXPO.md`.
