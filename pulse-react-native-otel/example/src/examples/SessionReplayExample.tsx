import {
  Image,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { PulseMask, PulseUnmask } from '@dreamhorizonorg/pulse-react-native';

const MASKED_IMAGE_URI = 'https://picsum.photos/seed/pulse-mask/960/540';
const UNMASKED_IMAGE_URI = 'https://picsum.photos/seed/pulse-clear/960/540';

export default function SessionReplayExample() {
  return (
    <ScrollView style={styles.container}>
      <View style={styles.heroCard}>
        <Text style={styles.eyebrow}>Session Replay</Text>
        <Text style={styles.title}>Mask Inputs, Images, And Entire Views</Text>
        <Text style={styles.heroText}>
          This screen uses only wrapper components that attach recognized
          masking testIDs. Use it to verify how replay treats text fields,
          images, and arbitrary view containers.
        </Text>
      </View>

      <View style={styles.sectionCard}>
        <Text style={styles.sectionTitle}>Masked Inputs</Text>
        <Text style={styles.sectionDesc}>
          Everything inside PulseMask should be treated as masked in replay.
        </Text>
        <PulseMask>
          <TextInput
            style={styles.maskedInput}
            placeholder="Card Number"
            placeholderTextColor="#8b8b8b"
            keyboardType="numeric"
          />
        </PulseMask>
        <PulseUnmask>
          <TextInput
            style={styles.maskedInput}
            placeholder="CVV"
            placeholderTextColor="#8b8b8b"
            secureTextEntry
          />
        </PulseUnmask>
      </View>

      <View style={styles.sectionCard}>
        <Text style={styles.sectionTitle}>Masked Image + View</Text>
        <Text style={styles.sectionDesc}>
          This entire block is wrapped in PulseMask, including the image and
          the summary card below it.
        </Text>
        <PulseMask>
          <Image
            source={{ uri: MASKED_IMAGE_URI }}
            style={styles.previewImage}
          />
          <View style={styles.maskedSummary}>
            <Text style={styles.maskedSummaryTitle}>
              Masked Purchase Summary
            </Text>
            <Text style={styles.maskedSummaryText}>Order: #PULSE-4928</Text>
            <Text style={styles.maskedSummaryText}>Amount: $249.00</Text>
            <Text style={styles.maskedSummaryText}>Holder: Dev Test</Text>
          </View>
        </PulseMask>
      </View>

      <View style={styles.sectionCard}>
        <Text style={styles.sectionTitle}>Explicit Unmask</Text>
        <Text style={styles.sectionDesc}>
          Use PulseUnmask for content that should remain visible in replay.
        </Text>
        <PulseUnmask>
          <Image
            source={{ uri: UNMASKED_IMAGE_URI }}
            style={styles.previewImage}
          />
          <View style={styles.clearSummary}>
            <Text style={styles.clearSummaryTitle}>
              Visible Promotional Banner
            </Text>
            <Text style={styles.clearSummaryText}>
              This image and container should stay unmasked.
            </Text>
          </View>
        </PulseUnmask>
      </View>

      <View style={styles.sectionCard}>
        <Text style={styles.sectionTitle}>Native Config</Text>
        <Text style={styles.sectionDesc}>
          Configured in MainApplication.kt:
        </Text>
        <Text style={styles.codeText}>
          sessionReplay {'{'}
          {'\n  '}textAndInputPrivacy('MASK_SENSITIVE_INPUTS')
          {'\n  '}imagePrivacy('MASK_ALL')
          {'\n'}
          {'}'}
        </Text>
      </View>

      <View style={styles.noteCard}>
        <Text style={styles.noteTitle}>Expected Result</Text>
        <Text style={styles.noteText}>
          The masked sections should be obscured in replay. The explicitly
          unmasked section should remain visible, including its image and view
          content.
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 16,
    paddingVertical: 18,
    backgroundColor: '#f6efe8',
  },
  heroCard: {
    backgroundColor: '#1f2937',
    borderRadius: 18,
    padding: 18,
    marginBottom: 16,
  },
  eyebrow: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: '#f6c177',
    marginBottom: 8,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 8,
    color: '#fff7ed',
  },
  heroText: {
    fontSize: 14,
    lineHeight: 21,
    color: '#d1d5db',
  },
  sectionCard: {
    marginBottom: 24,
    padding: 16,
    borderRadius: 16,
    backgroundColor: '#fffaf5',
    borderWidth: 1,
    borderColor: '#eadfd2',
    shadowColor: '#8f5f3b',
    shadowOpacity: 0.08,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
    elevation: 2,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: '700',
    marginBottom: 8,
    color: '#3c2f2f',
  },
  sectionDesc: {
    fontSize: 13,
    lineHeight: 19,
    color: '#7a6a58',
    marginBottom: 12,
  },
  maskedInput: {
    borderWidth: 1,
    borderColor: '#e9d5c1',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 12,
    marginBottom: 10,
    fontSize: 14,
    backgroundColor: '#fff1e6',
    color: '#7c2d12',
  },
  previewImage: {
    width: '100%',
    height: 190,
    borderRadius: 14,
    marginBottom: 12,
    backgroundColor: '#ddd6ce',
  },
  maskedSummary: {
    backgroundColor: '#2f1e16',
    borderRadius: 14,
    padding: 14,
  },
  maskedSummaryTitle: {
    color: '#ffd9c2',
    fontSize: 15,
    fontWeight: '700',
    marginBottom: 6,
  },
  maskedSummaryText: {
    color: '#f7d9ca',
    fontSize: 13,
    marginBottom: 4,
  },
  clearSummary: {
    backgroundColor: '#eef8f0',
    borderRadius: 14,
    padding: 14,
    borderWidth: 1,
    borderColor: '#cce3d0',
  },
  clearSummaryTitle: {
    color: '#1f5132',
    fontSize: 15,
    fontWeight: '700',
    marginBottom: 6,
  },
  clearSummaryText: {
    color: '#35624a',
    fontSize: 13,
    lineHeight: 19,
  },
  codeText: {
    fontFamily: 'Menlo',
    fontSize: 11,
    backgroundColor: '#2b211d',
    padding: 12,
    borderRadius: 12,
    color: '#f7efe7',
  },
  noteCard: {
    backgroundColor: '#fff3d6',
    borderColor: '#f3d08b',
    borderWidth: 1,
    padding: 12,
    borderRadius: 14,
    marginTop: 4,
    marginBottom: 20,
  },
  noteTitle: {
    fontSize: 13,
    fontWeight: '700',
    color: '#8a5a00',
    marginBottom: 6,
  },
  noteText: {
    fontSize: 12,
    lineHeight: 18,
    color: '#8a5a00',
  },
});
