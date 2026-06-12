import { View, Text, Button, StyleSheet } from 'react-native';

type Props = {
  label: string;
  title: string;
  color?: string;
  onPress: () => void;
};

export default function ButtonWithTitle({
  label,
  title,
  color,
  onPress,
}: Props) {
  return (
    <View style={styles.container}>
      <Text style={styles.label}>{label}</Text>
      <Button title={title} color={color} onPress={onPress} />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginVertical: 15,
    width: '100%',
    maxWidth: 300,
  },
  label: {
    marginBottom: 8,
    fontSize: 14,
    fontWeight: '600',
    textAlign: 'center',
  },
});
