import React from 'react';
import { enableScreens } from 'react-native-screens';
import { NavigationContainer, type NavigationContainerRef } from '@react-navigation/native';
import { createNativeStackNavigator} from '@react-navigation/native-stack';
import HomeScreen from '../screens/Home';
import ProfileScreen from '../screens/Profile';
import SettingsScreen from '../screens/Settings';
import DetailsScreen from '../screens/Details';
import { Pulse } from '@d11/pulse-react-native-otel';

enableScreens(true);

export type RootStackParamList = {
  Home: undefined;
  Profile: { userId: string };
  Settings: undefined;
  Details: { itemId: number };
};

const Stack = createNativeStackNavigator<RootStackParamList>();

const navigationIntegration = Pulse.createNavigationIntegration();

export default function NavigationDemo() {
  const navigationRef = React.useRef<NavigationContainerRef<RootStackParamList>>(null);

  return (
    <NavigationContainer
      ref={navigationRef}
      onReady={() => {
        navigationIntegration.registerNavigationContainer(navigationRef);
      }}
    >
      <Stack.Navigator initialRouteName="Home">
        <Stack.Screen name="Home" component={HomeScreen} options={{ title: 'Home - Pulse Demo' }} />
        <Stack.Screen name="Profile" component={ProfileScreen} options={{ title: 'User Profile' }} />
        <Stack.Screen name="Settings" component={SettingsScreen} options={{ title: 'App Settings' }} />
        <Stack.Screen name="Details" component={DetailsScreen} options={{ title: 'Item Details' }} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
