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
import { makeRequest } from '../../helpers/makeRequest';
import { API_BASE_URL, API_METHODS } from '../../constants';
import { showNotification } from '../../helpers/showNotification';
import { IconCircleCheckFilled, IconSquareRoundedX } from '@tabler/icons-react';
import { useMantineTheme } from '@mantine/core';

type ViewState = 'list' | 'view' | 'edit';

export function SamplingConfig() {
  const theme = useMantineTheme();
  const [viewState, setViewState] = useState<ViewState>('list');
  const [selectedVersion, setSelectedVersion] = useState<number | null>(null);
  const [initialConfig, setInitialConfig] = useState<PulseConfig | undefined>(undefined);
  const [editorMode, setEditorMode] = useState<ConfigEditorMode>('create');

  // Load a specific version's configuration
  const loadVersionConfig = useCallback(async (version: number): Promise<PulseConfig | null> => {
    try {
      const response = await makeRequest<PulseConfig>({
        url: `${API_BASE_URL}/v1/sdk-config/versions/${version}`,
        init: { method: API_METHODS.GET },
      });

      if (response.data) {
        return response.data;
      }
      
      // If API doesn't work, try getting current config
      const currentResponse = await makeRequest<PulseConfig>({
        url: `${API_BASE_URL}/v1/sdk-config`,
        init: { method: API_METHODS.GET },
      });
      
      return currentResponse.data || null;
    } catch (err) {
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
    setSelectedVersion(null);
    setEditorMode('create');
    setViewState('edit');
  }, [loadVersionConfig]);

  // Handle save from editor
  const handleSave = useCallback((savedConfig: PulseConfig) => {
    showNotification(
      'Success',
      `Configuration v${savedConfig.version} saved successfully`,
      <IconCircleCheckFilled />,
      theme.colors.teal[6],
    );
    setViewState('list');
    setInitialConfig(undefined);
    setSelectedVersion(null);
  }, [theme.colors.teal]);

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
