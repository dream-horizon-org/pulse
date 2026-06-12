import { useRef } from 'react';
import {
  NavigationContainer,
  type NavigationContainerRef,
} from '@react-navigation/native';
import { createStackNavigator } from '@react-navigation/stack';
import { Pulse } from '@dreamhorizonorg/pulse-react-native';
import HomeScreen from '../screens/Home';
import ProfileScreen from '../screens/Profile';
import SettingsScreen from '../screens/Settings';
import DetailsScreen from '../screens/Details';
import type { RootStackParamList } from './NavigationExample';

const Stack = createStackNavigator<RootStackParamList>();

export default function StackDemo() {
  const navigationRef =
    useRef<NavigationContainerRef<RootStackParamList>>(null);
  const onReady = Pulse.useNavigationTracking(navigationRef);

  return (
    <NavigationContainer ref={navigationRef} onReady={onReady}>
      <Stack.Navigator initialRouteName="Home">
        <Stack.Screen
          name="Home"
          component={HomeScreen}
          options={{ title: 'Home (Stack)' }}
        />
        <Stack.Screen
          name="Profile"
          component={ProfileScreen}
          options={{ title: 'Profile' }}
        />
        <Stack.Screen
          name="Settings"
          component={SettingsScreen}
          options={{ title: 'Settings' }}
        />
        <Stack.Screen
          name="Details"
          component={DetailsScreen}
          options={{ title: 'Details' }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
