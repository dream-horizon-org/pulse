import { Pulse } from '@dreamhorizonorg/pulse-react-native';
import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { ActivityIndicator, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { ShopProvider, useShop } from '../context/ShopContext';
import { ToastProvider } from '../context/ToastContext';

Pulse.start({
  autoDetectExceptions: true,
  autoDetectNavigation: true,
  autoDetectNetwork: true,
});

function RootNavigation() {
  const { ready } = useShop();
  if (!ready) {
    return (
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
        <ActivityIndicator size="large" />
      </View>
    );
  }
  return (
    <>
      <StatusBar style="dark" />
      <Stack
        screenOptions={{
          headerTintColor: '#0f766e',
          headerTitleStyle: { fontWeight: '700', fontSize: 17 },
          headerStyle: { backgroundColor: '#fafafa' },
          headerShadowVisible: false,
          headerBackButtonDisplayMode: 'minimal',
        }}
      >
        <Stack.Screen
          name="(tabs)"
          options={{
            headerShown: false,
            title: 'Shop',
          }}
        />
        <Stack.Screen
          name="category/[slug]"
          options={{ title: 'Category', headerShown: true }}
        />
        <Stack.Screen name="product/[id]" options={{ title: 'Product' }} />
        <Stack.Screen name="checkout" options={{ title: 'Checkout' }} />
        <Stack.Screen name="payment" options={{ title: 'Payment' }} />
        <Stack.Screen
          name="order-confirmation/[orderId]"
          options={{ title: 'Order placed', headerBackVisible: false }}
        />
        <Stack.Screen name="login" options={{ title: 'Sign in' }} />
        <Stack.Screen name="register" options={{ title: 'Create account' }} />
      </Stack>
    </>
  );
}

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <ShopProvider>
        <ToastProvider>
          <RootNavigation />
        </ToastProvider>
      </ShopProvider>
    </SafeAreaProvider>
  );
}
