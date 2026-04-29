import { AndroidConfig, type ConfigPlugin } from '@expo/config-plugins';

const PHONE_STATE_PERMISSIONS = [
  'android.permission.READ_PHONE_STATE',
  'android.permission.READ_BASIC_PHONE_STATE',
] as const;

export const withAndroidPhoneStatePermissions: ConfigPlugin = (config) => {
  return AndroidConfig.Permissions.withPermissions(config, [
    ...PHONE_STATE_PERMISSIONS,
  ]);
};
