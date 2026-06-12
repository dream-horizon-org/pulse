/**
 * SDK Configuration Screen
 * 
 * Manages configuration versions with list and editor views:
 * - List view: Shows all configuration versions
 * - View mode: Read-only view of a specific version
 * - Edit mode: Create new configuration based on existing version
 */

import { useState, useCallback } from 'react';
import { ConfigVersionList } from './components/ConfigVersionList';
import { ConfigEditor } from './ConfigEditor';
import { PulseConfig, ConfigEditorMode } from './SamplingConfig.interface';
import { addUIIds } from './SamplingConfig.constants';
import { makeRequest } from '../../helpers/makeRequest';
import { API_BASE_URL, API_ROUTES } from '../../constants';
import { showNotification } from '../../helpers/showNotification';
import { IconSquareRoundedX } from '@tabler/icons-react';
import { useMantineTheme } from '@mantine/core';

type ViewState = 'list' | 'view' | 'edit';

export function SamplingConfig() {
  const theme = useMantineTheme();
  const [viewState, setViewState] = useState<ViewState>('list');
  const [selectedVersion, setSelectedVersion] = useState<number | null>(null);
  const [initialConfig, setInitialConfig] = useState<PulseConfig | undefined>(undefined);
  const [editorMode, setEditorMode] = useState<ConfigEditorMode>('create');

  // Load a specific version's configuration using correct API endpoint
  const loadVersionConfig = useCallback(async (version: number): Promise<PulseConfig | null> => {
    try {
      const apiPath = API_ROUTES.GET_SDK_CONFIG_BY_VERSION.apiPath.replace('{version}', String(version));
      const response = await makeRequest<PulseConfig>({
        url: `${API_BASE_URL}${apiPath}`,
        init: { method: API_ROUTES.GET_SDK_CONFIG_BY_VERSION.method },
      });

      if (response.data) {
        // Add UI tracking IDs to config items
        return addUIIds(response.data);
      }
      
      return null;
    } catch {
      showNotification(
        'Error',
        'Failed to load configuration',
        <IconSquareRoundedX />,
        theme.colors.red[6],
      );
      return null;
    }
  }, [theme.colors.red]);

  // Handle viewing a specific version
  const handleViewVersion = useCallback(async (version: number) => {
    const config = await loadVersionConfig(version);
    if (config) {
      setInitialConfig(config);
      setSelectedVersion(version);
      setEditorMode('view');
      setViewState('view');
    }
  }, [loadVersionConfig]);

  // Handle creating a new configuration (optionally based on existing version)
  const handleCreateNew = useCallback(async (baseVersion?: number) => {
    if (baseVersion) {
      const config = await loadVersionConfig(baseVersion);
      if (config) {
        setInitialConfig(config);
      } else {
        setInitialConfig(undefined);
      }
    } else {
      setInitialConfig(undefined);
    }
    setSelectedVersion(baseVersion ?? null);
    setEditorMode('create');
    setViewState('edit');
  }, [loadVersionConfig]);

  // Handle save from editor (notification is shown in ConfigEditor)
  const handleSave = useCallback(() => {
    setViewState('list');
    setInitialConfig(undefined);
    setSelectedVersion(null);
  }, []);

  // Handle cancel from editor
  const handleCancel = useCallback(() => {
    setViewState('list');
    setInitialConfig(undefined);
    setSelectedVersion(null);
  }, []);

  // Handle edit from view mode
  const handleEdit = useCallback(() => {
    setEditorMode('create'); // Creating new version based on viewed one
    setViewState('edit');
  }, []);

  // Render based on current view state
  if (viewState === 'list') {
    return (
      <ConfigVersionList
        onViewVersion={handleViewVersion}
        onCreateNew={handleCreateNew}
      />
    );
  }

  return (
    <ConfigEditor
      initialConfig={initialConfig}
      mode={editorMode}
      onSave={handleSave}
      onCancel={handleCancel}
      onEdit={editorMode === 'view' ? handleEdit : undefined}
      viewingVersion={selectedVersion}
    />
  );
}
