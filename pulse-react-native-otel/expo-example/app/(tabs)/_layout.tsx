import { Ionicons } from '@expo/vector-icons';
import { PlatformPressable } from '@react-navigation/elements';
import Constants from 'expo-constants';
import { Tabs } from 'expo-router';
import { Platform, StatusBar as RNStatusBar } from 'react-native';
import type { BottomTabBarButtonProps } from '@react-navigation/bottom-tabs';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useShop } from '../../context/ShopContext';
import { theme } from '../../shared/theme';

export default function TabsLayout() {
  const { cartCount } = useShop();
  const insets = useSafeAreaInsets();
  const androidBar =
    Platform.OS === 'android' ? RNStatusBar.currentHeight ?? 0 : 0;
  const headerStatusBarHeight = Math.max(
    insets.top,
    Constants.statusBarHeight ?? 0,
    androidBar
  );
  const badge =
    cartCount > 99 ? '99+' : cartCount > 0 ? String(cartCount) : undefined;

  const tabBtn = (testID: string) => (props: BottomTabBarButtonProps) =>
    <PlatformPressable {...props} testID={testID} />;

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
          tabBarButton: tabBtn('home-tab'),
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="home-outline" color={color} size={size} />
          ),
        }}
      />
      <Tabs.Screen
        name="browse"
        options={{
          title: 'Shop',
          tabBarButton: tabBtn('browse-tab'),
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="grid-outline" color={color} size={size} />
          ),
        }}
      />
      <Tabs.Screen
        name="wishlist"
        options={{
          title: 'Wishlist',
          tabBarButton: tabBtn('wishlist-tab'),
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="heart-outline" color={color} size={size} />
          ),
        }}
      />
      <Tabs.Screen
        name="cart"
        options={{
          title: 'Cart',
          tabBarButton: tabBtn('cart-tab'),
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
          tabBarButton: tabBtn('account-tab'),
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="person-outline" color={color} size={size} />
          ),
        }}
      />
    </Tabs>
  );
}
