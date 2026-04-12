import { Ionicons } from '@expo/vector-icons';
import Constants from 'expo-constants';
import { Tabs } from 'expo-router';
import { Platform, StatusBar as RNStatusBar } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useShop } from '../../context/ShopContext';
import { theme } from '../../lib/theme';

export default function TabsLayout() {
  const { cartCount } = useShop();
  const insets = useSafeAreaInsets();
  const androidBar =
    Platform.OS === 'android' ? (RNStatusBar.currentHeight ?? 0) : 0;
  const headerStatusBarHeight = Math.max(
    insets.top,
    Constants.statusBarHeight ?? 0,
    androidBar
  );
  const badge =
    cartCount > 99 ? '99+' : cartCount > 0 ? String(cartCount) : undefined;

  return (
    <Tabs
      screenOptions={{
        headerStatusBarHeight,
        tabBarActiveTintColor: theme.primaryDark,
        tabBarInactiveTintColor: theme.textMuted,
        tabBarStyle: {
          backgroundColor: theme.surface,
          borderTopColor: theme.border,
          paddingTop: 4,
          height: 60,
        },
        tabBarLabelStyle: { fontSize: 11, fontWeight: '600' },
        tabBarBadgeStyle: {
          backgroundColor: theme.accent,
          color: '#1c1917',
          fontSize: 11,
          fontWeight: '800',
        },
        headerStyle: { backgroundColor: '#fafafa' },
        headerShadowVisible: false,
        headerTitleStyle: {
          fontWeight: '700',
          fontSize: 18,
          color: theme.text,
        },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: 'Home',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="home-outline" color={color} size={size} />
          ),
        }}
      />
      <Tabs.Screen
        name="browse"
        options={{
          title: 'Shop',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="grid-outline" color={color} size={size} />
          ),
        }}
      />
      <Tabs.Screen
        name="wishlist"
        options={{
          title: 'Wishlist',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="heart-outline" color={color} size={size} />
          ),
        }}
      />
      <Tabs.Screen
        name="cart"
        options={{
          title: 'Cart',
          tabBarBadge: badge,
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="cart-outline" color={color} size={size} />
          ),
        }}
      />
      <Tabs.Screen
        name="account"
        options={{
          title: 'Account',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="person-outline" color={color} size={size} />
          ),
        }}
      />
    </Tabs>
  );
}
