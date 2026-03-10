# Phase 1: Slack OAuth Integration - Foundation Complete ✅

## Branch: `feat/slack-oauth-integration`

## Overview
Phase 1 establishes the foundational infrastructure for Slack OAuth bot integration by updating type definitions, API routes, and creating new hooks to interact with the backend notification service API.

---

## Changes Made

### 1. ✅ Updated Type Definitions
**File:** `pulse-ui/src/hooks/useGetAlertNotificationChannels/useGetAlertNotificationChannels.interface.ts`

#### New Types Added:
- **`ChannelType`**: Supports `'SLACK' | 'SLACK_WEBHOOK' | 'EMAIL' | 'TEAMS' | 'ALL'`
- **`NotificationStatus`**: Full status enum for notification tracking
- **Polymorphic Channel Configs**:
  - `SlackChannelConfig` - OAuth-based Slack (with bot token, workspace ID, bot name, icon)
  - `SlackWebhookChannelConfig` - Webhook-based Slack
  - `EmailChannelConfig` - Email via SES
  - `TeamsChannelConfig` - Microsoft Teams integration
- **`NotificationChannelDto`**: New channel structure matching backend API
- **`CreateChannelRequestDto`** & **`UpdateChannelRequestDto`**: Request DTOs
- **Slack OAuth Types**:
  - `SlackOAuthResponseDto` - OAuth callback response
  - `SlackChannelListDto` - Slack workspace channels list

#### Backward Compatibility:
Legacy types preserved with `@deprecated` tags:
- `NotificationChannelType`
- `AlertNotificationChannelItem`
- `GetAlertNotificationChannelsResponse`
- `CreateNotificationChannelRequest`

---

### 2. ✅ Updated API Routes
**File:** `pulse-ui/src/constants/Constants.ts`

#### New Notification Service Routes:
```typescript
// New v1/notifications/* endpoints
GET_NOTIFICATION_CHANNELS      → /v1/notifications/channels
GET_NOTIFICATION_CHANNEL       → /v1/notifications/channels/{channelId}
CREATE_NOTIFICATION_CHANNEL_V2 → /v1/notifications/channels
UPDATE_NOTIFICATION_CHANNEL_V2 → /v1/notifications/channels/{channelId}
DELETE_NOTIFICATION_CHANNEL_V2 → /v1/notifications/channels/{channelId}

// Slack OAuth Integration
SLACK_INSTALL   → /v1/integrations/slack/install
SLACK_CALLBACK  → /v1/integrations/slack/callback
SLACK_CHANNELS  → /v1/integrations/slack/channels
```

#### Legacy Routes:
Old `/v1/alert/notificationChannels` routes preserved with deprecation comments for backward compatibility.

---

### 3. ✅ New React Hooks

#### **useSlackInstall**
**Location:** `pulse-ui/src/hooks/useSlackInstall/`

**Purpose:** Generate Slack OAuth installation URL

**Usage:**
```typescript
const { getInstallUrl, isLoading, error } = useSlackInstall({ 
  projectId: 'proj_abc123' 
});

// Redirect user to Slack OAuth
const url = await getInstallUrl();
if (url) {
  window.location.href = url;
}
```

**API:** `GET /v1/integrations/slack/install` with `X-Project-Id` header

---

#### **useSlackChannels**
**Location:** `pulse-ui/src/hooks/useSlackChannels/`

**Purpose:** Fetch list of Slack channels from connected workspace

**Usage:**
```typescript
const { data: channels, isLoading, error } = useSlackChannels('proj_abc123');

// Returns: SlackChannelListDto[]
// { id, name, isPrivate, isMember }
```

**API:** `GET /v1/integrations/slack/channels` with `X-Project-Id` header

**Features:**
- Query enabled only when `projectId` is provided
- Returns empty array on error
- Uses React Query for caching

---

## File Structure

```
pulse-ui/src/
├── constants/
│   └── Constants.ts (modified - added new routes)
├── hooks/
│   ├── useGetAlertNotificationChannels/
│   │   └── useGetAlertNotificationChannels.interface.ts (modified - comprehensive types)
│   ├── useSlackInstall/ (NEW)
│   │   ├── index.ts
│   │   ├── useSlackInstall.ts
│   │   └── useSlackInstall.interface.ts
│   └── useSlackChannels/ (NEW)
│       ├── index.ts
│       ├── useSlackChannels.ts
│       └── useSlackChannels.interface.ts
```

---

## Testing Instructions

### Prerequisites
1. Ensure backend notification service API is running
2. Have a test project ID ready
3. Backend should have Slack OAuth app configured

### Test 1: Type Definitions
```typescript
import { ChannelType, SlackChannelConfig, NotificationChannelDto } from './hooks/useGetAlertNotificationChannels';

// Should compile without errors
const channelType: ChannelType = 'SLACK';
const config: SlackChannelConfig = {
  type: 'SLACK',
  accessToken: 'xoxb-test',
  workspaceId: 'T123',
  botName: 'TestBot',
  iconEmoji: ':robot_face:'
};
```

### Test 2: Slack Install Hook
```typescript
import { useSlackInstall } from './hooks/useSlackInstall';

function TestComponent() {
  const { getInstallUrl, isLoading, error } = useSlackInstall({ 
    projectId: 'proj_test123' 
  });

  const handleConnect = async () => {
    const url = await getInstallUrl();
    console.log('Slack OAuth URL:', url);
    // Should return: https://slack.com/oauth/v2/authorize?client_id=...
  };

  return <button onClick={handleConnect}>Connect Slack</button>;
}
```

### Test 3: Slack Channels Hook
```typescript
import { useSlackChannels } from './hooks/useSlackChannels';

function ChannelSelector() {
  const { data: channels, isLoading } = useSlackChannels('proj_test123');

  if (isLoading) return <div>Loading...</div>;

  return (
    <select>
      {channels?.map(ch => (
        <option key={ch.id} value={ch.id}>
          {ch.name} {ch.isPrivate ? '🔒' : ''}
        </option>
      ))}
    </select>
  );
}
```

### Expected API Calls

1. **GET /v1/integrations/slack/install**
   - Headers: `X-Project-Id: proj_test123`
   - Response: OAuth URL string

2. **GET /v1/integrations/slack/channels**
   - Headers: `X-Project-Id: proj_test123`
   - Response: Array of `{ id, name, isPrivate, isMember }`

---

## Next Steps (Phase 2 & 3)

### Phase 2: Update UI Components
- Modify `NotificationChannels.tsx` to use new types
- Add Slack OAuth connection button
- Implement channel type selector (SLACK, SLACK_WEBHOOK, EMAIL, TEAMS)
- Create dynamic form based on selected channel type
- Add Slack channel browser/selector

### Phase 3: Implement Full OAuth Flow
- Create Slack OAuth callback handler page
- Handle OAuth success/failure states
- Display connected workspace info
- Test end-to-end OAuth flow
- Add workspace disconnect functionality

---

## Backward Compatibility

All changes are **backward compatible**:
- Legacy types remain with `@deprecated` tags
- Old API routes still defined in constants
- Existing components using old types will continue to work
- Migration to new types can be done incrementally

---

## Status: ✅ READY FOR TESTING

All Phase 1 changes are complete. No commits have been made per your request.

Test the foundation layer before proceeding to Phase 2 UI updates.
