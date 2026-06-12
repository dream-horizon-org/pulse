import { Group, Badge } from "@mantine/core";

interface AppState {
  foreground: boolean;
  screenName: string;
  orientation: string;
  lowMemoryWarning?: boolean;
  debugMode?: boolean;
}

interface AppStateInfoProps {
  appState: AppState;
}

export const AppStateInfo: React.FC<AppStateInfoProps> = ({ appState }) => {
  return (
    <Group gap="sm" wrap="wrap">
      <Badge size="md" variant="outline" color="teal">
        Screen: {appState.screenName}
      </Badge>
      <Badge size="md" variant="outline" color="gray">
        {appState.orientation}
      </Badge>
      <Badge
        size="md"
        variant="outline"
        color={appState.foreground ? "green" : "gray"}
      >
        {appState.foreground ? "Foreground" : "Background"}
      </Badge>
      {appState.lowMemoryWarning && (
        <Badge size="md" variant="filled" color="red">
          Low Memory Warning
        </Badge>
      )}
      {appState.debugMode && (
        <Badge size="md" variant="filled" color="orange">
          Debug Mode
        </Badge>
      )}
    </Group>
  );
};
