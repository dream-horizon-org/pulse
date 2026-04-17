import { useState } from 'react';
import { Image, type ImageStyle, type StyleProp, View } from 'react-native';

const FALLBACK = 'https://placehold.co/200x200/e2e8f0/64748b/png?text=No+image';

type Props = {
  uri: string;
  style?: StyleProp<ImageStyle>;
};

export function ProductImage({ uri, style }: Props) {
  const [failed, setFailed] = useState(false);
  const source = failed || !uri ? FALLBACK : uri;
  return (
    <View>
      <Image
        source={{ uri: source }}
        style={style}
        resizeMode="cover"
        onError={() => setFailed(true)}
      />
    </View>
  );
}
