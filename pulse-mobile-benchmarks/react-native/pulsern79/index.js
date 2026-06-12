/**
 * @format
 */

import {AppRegistry} from 'react-native';
import App from './App';
import {name as appName} from './app.json';
import { Pulse } from '@dreamhorizonorg/pulse-react-native';

// Enable auto-instrumentation features
Pulse.start();

AppRegistry.registerComponent(appName, () => App);
