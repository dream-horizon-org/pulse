import { Text, View, Button, StyleSheet } from "react-native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import type { RootStackParamList } from "../examples/NavigationExample";

// Define nested stack parameter types
export type DetailsStackParamList = {
  DetailsHome: { itemId: number };
  DetailsInfo: { infoId: string };
};

const DetailsStack = createNativeStackNavigator<DetailsStackParamList>();

// ---- Nested Screens ----
function DetailsHomeScreen({
  route,
  navigation,
}: NativeStackScreenProps<DetailsStackParamList, "DetailsHome">) {
  const { itemId } = route.params;

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Details Screen</Text>
      <Text style={styles.content}>Item ID: {itemId}</Text>
      <Text style={styles.content}>Showing details for item #{itemId}</Text>

      <View style={styles.buttonContainer}>
        <Button
          title="Go to Info"
          onPress={() => navigation.navigate("DetailsInfo", { infoId: `info-${itemId}` })}
        />
      </View>

      <Text style={styles.info}>📊 Nested navigation stack example</Text>
    </View>
  );
}

function DetailsInfoScreen({
  route,
  navigation,
}: NativeStackScreenProps<DetailsStackParamList, "DetailsInfo">) {
  const { infoId } = route.params;

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Details Info Screen</Text>
      <Text style={styles.content}>Info ID: {infoId}</Text>

      <View style={styles.buttonContainer}>
        <Button title="Go Back" onPress={() => navigation.goBack()} color="#f44336" />
      </View>
    </View>
  );
}

// ---- Main Details Screen ----
export default function DetailsScreen({
  route,
}: NativeStackScreenProps<RootStackParamList, "Details">) {
  const { itemId } = route.params;

  return (
    <DetailsStack.Navigator>
      <DetailsStack.Screen
        name="DetailsHome"
        component={DetailsHomeScreen}
        initialParams={{ itemId }}
        options={{ title: "Details Home" }}
      />
      <DetailsStack.Screen
        name="DetailsInfo"
        component={DetailsInfoScreen}
        options={{ title: "Details Info" }}
      />
    </DetailsStack.Navigator>
  );
}

// ---- Styles ----
const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    padding: 20,
    backgroundColor: "#fff",
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    marginBottom: 16,
    textAlign: "center",
    color: "#333",
  },
  content: {
    fontSize: 16,
    marginBottom: 12,
    textAlign: "center",
    color: "#444",
  },
  buttonContainer: {
    marginVertical: 8,
    width: "100%",
    maxWidth: 250,
  },
  info: {
    marginTop: 30,
    fontSize: 13,
    color: "#2196f3",
    textAlign: "center",
    fontWeight: "600",
  },
});
