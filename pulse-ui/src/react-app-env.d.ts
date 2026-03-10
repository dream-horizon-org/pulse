/// <reference types="react-scripts" />

declare namespace NodeJS {
  interface ProcessEnv {
    readonly NODE_ENV: 'development' | 'production' | 'test';
    readonly PUBLIC_URL: string;
    readonly REACT_APP_FIREBASE_API_KEY?: string;
    readonly REACT_APP_FIREBASE_AUTH_DOMAIN?: string;
    readonly REACT_APP_FIREBASE_PROJECT_ID?: string;
    readonly REACT_APP_FIREBASE_STORAGE_BUCKET?: string;
    readonly REACT_APP_FIREBASE_MESSAGING_SENDER_ID?: string;
    readonly REACT_APP_FIREBASE_APP_ID?: string;
    readonly REACT_APP_FIREBASE_AUTH_EMULATOR?: string;
    readonly REACT_APP_GCP_MULTI_TENANT_ENABLED?: string;
    readonly REACT_APP_GOOGLE_CLIENT_ID?: string;
    readonly REACT_APP_PULSE_SERVER_URL?: string;
    readonly REACT_APP_GOOGLE_OAUTH_ENABLED?: string;
  }
}

declare module '*.module.css' {
  const classes: { readonly [key: string]: string };
  export default classes;
}

declare module '*.module.scss' {
  const classes: { readonly [key: string]: string };
  export default classes;
}

declare module '*.module.sass' {
  const classes: { readonly [key: string]: string };
  export default classes;
}
