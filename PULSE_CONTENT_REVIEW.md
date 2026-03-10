# Pulse - Content Copy Review

**Last Updated:** March 10, 2026

---

## 1. Pricing & Plans

### Free Plan
**Perfect for getting started**

**$0 / month**

- 1 Project
- Up to 5 team members
- Basic analytics & monitoring
- 7 days data retention
- Community support
- SDK for Android, iOS, React Native

---

### Enterprise Plan
**Popular • For teams that need more**

**Custom**  
Tailored to your needs

- Unlimited projects
- Unlimited team members
- Advanced analytics & monitoring
- Custom data retention
- Priority support & SLA
- Custom integrations
- On-premise deployment option
- Dedicated account manager

**Button:** Contact Sales

---

### Pricing Page Copy

**Choose Your Plan**  
Start with our free plan and upgrade as you grow. No credit card required.

**Footer:** Need help choosing? Contact our team for personalized recommendations.

---

### Enterprise User (Already Subscribed)

**You're on Enterprise Plan**  
Unlock the full power of Pulse with unlimited projects and advanced features

**Your Enterprise Benefits**

Need to discuss your plan? Contact our support team for assistance.

---

## 2. Onboarding & Welcome Messages

### Initial Onboarding

**Welcome to Pulse!**  
Let's set up your organization and create your first project.

Signed in as **{email}**

**Button:** Create Project & Continue

---

### Project Created Successfully

**🎉 Project "{name}" Created Successfully!**

---

**SDK Initialization**  
Use this API key to initialize the Pulse SDK in your application. Keep it secure!

API key copied to clipboard

---

**Quick Start**  
Choose your platform and copy the initialization code:

**Tabs:** Android | iOS | React Native

---

**Invite Your Team** (Optional)  
Collaborate with your team by inviting members to this project.

Invitation sent to {email}

**Button:** Go to Dashboard

---

### Create New Project

**Create New Project**  
Set up a new project to start monitoring your application

Project created successfully!

---

## 3. Email Templates

### Organization Welcome Email

**Subject:** Welcome to {organization} on Pulse

```
Hi there,

{admin} has added you to the "{organization}" organization on Pulse.

Your role: {role}

To get started, simply log in to Pulse:
https://app.pulse.io/login

You'll have immediate access to all assigned projects!

Best regards,
The Pulse Team
```

---

### Project Access Email

**Subject:** You've been added to "{project}" project

```
Hi there,

{admin} has granted you access to the "{project}" project on Pulse.

Your role: {role}

Log in to start using the project:
https://app.pulse.io/projects/{projectId}

Best regards,
The Pulse Team
```

---

### Access Removed Email

**Subject:** Access removed from "{resource}"

```
Hi there,

{admin} has removed your access to "{resource}" on Pulse.

If you believe this was a mistake, please contact your administrator.

Best regards,
The Pulse Team
```

---

### Role Updated Email

**Subject:** Your role in "{resource}" has been updated

```
Hi there,

{admin} has updated your role in "{resource}" on Pulse.

Your new role: {role}

Log in to see your updated permissions:
https://app.pulse.io/login

Best regards,
The Pulse Team
```

---

### Project Created Email

**Subject:** Your project "{project}" has been created

```
Hi there,

Your project "{project}" has been successfully created on Pulse.

Project ID: {projectId}

Your API Key (save this - it won't be shown again):
{apiKey}

Use this API key to integrate Pulse SDK into your application.

Get started:
https://app.pulse.io/projects/{projectId}

Documentation:
https://docs.pulse.io/getting-started

Best regards,
The Pulse Team
```

---

## SDK Code Examples

### Android
```kotlin
// Initialize Pulse SDK in your Application class
import com.dreamhorizon.pulse.Pulse

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        Pulse.initialize(
            context = this,
            apiKey = "{projectApiKey}"
        )
    }
}
```

### iOS
```swift
// Initialize Pulse SDK in AppDelegate
import Pulse

func application(_ application: UIApplication,
                didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
    
    Pulse.initialize(apiKey: "{projectApiKey}")
    
    return true
}
```

### React Native
```javascript
// Initialize Pulse SDK in your App.tsx or index.js
import { Pulse } from '@dreamhorizon/pulse-react-native';

Pulse.initialize({
  apiKey: '{projectApiKey}',
});
```

---

## Contact Information

**Sales:** sales@pulse.io  
**Support:** support@pulse.io

---

**Document End**
