import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  makeGetRequest(url: string): Promise<{
    status: number;
    body: string;
    headers: Object;
  }>;
  makePostRequest(
    url: string,
    body: string
  ): Promise<{
    status: number;
    body: string;
    headers: Object;
  }>;
}

export default TurboModuleRegistry.getEnforcing<Spec>(
  'NativePulseExampleModule'
);
