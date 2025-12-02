import { useRef, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Animated,
  Dimensions,
} from 'react-native';

const { width } = Dimensions.get('window');
const GRID_SIZE = 20;
const BOX_SIZE = width / GRID_SIZE;

// Heavy computation to cause frame drops (>700ms)
const heavyCompute = (iterations: number) => {
  let sum = 0;
  for (let i = 0; i < iterations; i++) {
    sum += Math.sqrt(i) * Math.sin(i) * Math.cos(i) * Math.pow(i, 0.3);
  }
  return sum;
};

const AnimatedBox = ({ index }: { index: number }) => {
  const rotate = useRef(new Animated.Value(0)).current;
  const scale = useRef(new Animated.Value(1)).current;
  const translateX = useRef(new Animated.Value(0)).current;
  const translateY = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    const anim = Animated.loop(
      Animated.parallel([
        Animated.sequence([
          Animated.timing(rotate, {
            toValue: 1,
            duration: 2000,
            useNativeDriver: true,
          }),
          Animated.timing(rotate, {
            toValue: 0,
            duration: 2000,
            useNativeDriver: true,
          }),
        ]),
        Animated.sequence([
          Animated.timing(scale, {
            toValue: 1.5,
            duration: 1500,
            useNativeDriver: true,
          }),
          Animated.timing(scale, {
            toValue: 1,
            duration: 1500,
            useNativeDriver: true,
          }),
        ]),
        Animated.sequence([
          Animated.timing(translateX, {
            toValue: 20,
            duration: 1800,
            useNativeDriver: true,
          }),
          Animated.timing(translateX, {
            toValue: 0,
            duration: 1800,
            useNativeDriver: true,
          }),
        ]),
        Animated.sequence([
          Animated.timing(translateY, {
            toValue: 20,
            duration: 1700,
            useNativeDriver: true,
          }),
          Animated.timing(translateY, {
            toValue: 0,
            duration: 1700,
            useNativeDriver: true,
          }),
        ]),
      ])
    );
    anim.start();
    return () => anim.stop();
  }, [rotate, scale, translateX, translateY]);

  const rotation = rotate.interpolate({
    inputRange: [0, 1],
    outputRange: ['0deg', '360deg'],
  });

  // Heavy computation during render to cause frame drops
  heavyCompute(50000 + index * 1000);

  return (
    <Animated.View
      style={[
        styles.box,
        {
          transform: [
            { rotate: rotation },
            { scale },
            { translateX },
            { translateY },
          ],
        },
      ]}
    />
  );
};

export default function FrozenFrameStressTest() {
  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.title}>🧊 Frozen Frame Stress Test</Text>
        <Text style={styles.subtitle}>
          {GRID_SIZE * GRID_SIZE} animated boxes with heavy CPU work
        </Text>
        <Text style={styles.info}>
          Frames should exceed 700ms - check logs for 'app.jank' events
        </Text>
      </View>
      <ScrollView style={styles.scrollView} contentContainerStyle={styles.grid}>
        {Array.from({ length: GRID_SIZE * GRID_SIZE }).map((_, i) => (
          <AnimatedBox key={i} index={i} />
        ))}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { padding: 16, backgroundColor: '#f5f5f5' },
  title: { fontSize: 20, fontWeight: 'bold', marginBottom: 8 },
  subtitle: { fontSize: 14, color: '#666', marginBottom: 4 },
  info: { fontSize: 12, color: '#ff9800', fontWeight: '600' },
  scrollView: { flex: 1 },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    padding: 4,
  },
  box: {
    width: BOX_SIZE - 8,
    height: BOX_SIZE - 8,
    margin: 4,
    backgroundColor: '#2196F3',
    borderRadius: 4,
  },
});
