import React, {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
} from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { theme } from '../lib/theme';

type ToastContextValue = {
  show: (message: string) => void;
};

const ToastContext = createContext<ToastContextValue | null>(null);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const insets = useSafeAreaInsets();
  const [message, setMessage] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const show = useCallback((msg: string) => {
    if (timer.current) clearTimeout(timer.current);
    setMessage(msg);
    timer.current = setTimeout(() => {
      setMessage(null);
      timer.current = null;
    }, 3200);
  }, []);

  const dismiss = useCallback(() => {
    if (timer.current) clearTimeout(timer.current);
    setMessage(null);
  }, []);

  const value = useMemo(() => ({ show }), [show]);

  return (
    <ToastContext.Provider value={value}>
      {children}
      {message ? (
        <View style={styles.overlay} pointerEvents="box-none">
          <Pressable
            style={[styles.snack, { bottom: insets.bottom + 72 }]}
            onPress={dismiss}
          >
            <Text style={styles.snackText}>{message}</Text>
            <Text style={styles.dismiss}>Tap to dismiss</Text>
          </Pressable>
        </View>
      ) : null}
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx;
}

const styles = StyleSheet.create({
  overlay: {
    ...StyleSheet.absoluteFillObject,
    justifyContent: 'flex-end',
    alignItems: 'center',
    zIndex: 9999,
  },
  snack: {
    maxWidth: 360,
    marginHorizontal: 20,
    paddingVertical: 14,
    paddingHorizontal: 18,
    borderRadius: theme.radiusLg,
    backgroundColor: '#134e4a',
    ...theme.shadow,
  },
  snackText: {
    color: '#ecfdf5',
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '600',
    textAlign: 'center',
  },
  dismiss: {
    marginTop: 6,
    color: '#99f6e4',
    fontSize: 12,
    textAlign: 'center',
  },
});
