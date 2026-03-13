/**
 * Event Definitions Mock Responses
 * Matches backend EventDefinitionController.java endpoints
 */

export interface MockEventAttribute {
  id: number;
  attributeName: string;
  description: string;
  dataType: string;
  isRequired: boolean;
  isArchived: boolean;
}

export interface MockEventDefinition {
  id: number;
  eventName: string;
  displayName: string;
  description: string;
  category: string;
  isArchived: boolean;
  attributes: MockEventAttribute[];
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}

const now = new Date();
const daysAgo = (d: number) => new Date(now.getTime() - d * 86400000).toISOString();

let nextId = 16;
export const getNextEventDefId = () => nextId++;

export const mockEventDefinitions: MockEventDefinition[] = [
  {
    id: 1,
    eventName: "user_login",
    displayName: "User Login",
    description: "Fired when a user successfully logs in to the app",
    category: "authentication",
    isArchived: false,
    attributes: [
      { id: 1, attributeName: "login_method", description: "OAuth, email, biometric", dataType: "string", isRequired: true, isArchived: false },
      { id: 2, attributeName: "time_to_login_ms", description: "Duration from login start to completion", dataType: "integer", isRequired: false, isArchived: false },
    ],
    createdBy: "rahul.sharma@example.com",
    updatedBy: "rahul.sharma@example.com",
    createdAt: daysAgo(30),
    updatedAt: daysAgo(5),
  },
  {
    id: 2,
    eventName: "user_logout",
    displayName: "User Logout",
    description: "Fired when a user logs out of the app",
    category: "authentication",
    isArchived: false,
    attributes: [
      { id: 3, attributeName: "session_duration_ms", description: "Total session duration before logout", dataType: "integer", isRequired: false, isArchived: false },
    ],
    createdBy: "rahul.sharma@example.com",
    updatedBy: "rahul.sharma@example.com",
    createdAt: daysAgo(30),
    updatedAt: daysAgo(30),
  },
  {
    id: 3,
    eventName: "user_signup",
    displayName: "User Signup",
    description: "Fired when a new user completes registration",
    category: "authentication",
    isArchived: false,
    attributes: [
      { id: 4, attributeName: "signup_method", description: "Registration method used", dataType: "string", isRequired: true, isArchived: false },
      { id: 5, attributeName: "referral_code", description: "Referral code if provided", dataType: "string", isRequired: false, isArchived: false },
    ],
    createdBy: "priya.patel@example.com",
    updatedBy: "priya.patel@example.com",
    createdAt: daysAgo(28),
    updatedAt: daysAgo(10),
  },
  {
    id: 4,
    eventName: "add_to_cart",
    displayName: "Add to Cart",
    description: "Fired when a user adds an item to the shopping cart",
    category: "commerce",
    isArchived: false,
    attributes: [
      { id: 6, attributeName: "product_id", description: "Product identifier", dataType: "string", isRequired: true, isArchived: false },
      { id: 7, attributeName: "product_name", description: "Product display name", dataType: "string", isRequired: true, isArchived: false },
      { id: 8, attributeName: "price", description: "Product price at time of add", dataType: "double", isRequired: true, isArchived: false },
      { id: 9, attributeName: "quantity", description: "Quantity added", dataType: "integer", isRequired: true, isArchived: false },
      { id: 10, attributeName: "currency", description: "Currency code (INR, USD)", dataType: "string", isRequired: false, isArchived: false },
    ],
    createdBy: "priya.patel@example.com",
    updatedBy: "amit.kumar@example.com",
    createdAt: daysAgo(25),
    updatedAt: daysAgo(3),
  },
  {
    id: 5,
    eventName: "checkout_initiated",
    displayName: "Checkout Initiated",
    description: "Fired when a user starts the checkout process",
    category: "commerce",
    isArchived: false,
    attributes: [
      { id: 11, attributeName: "cart_total", description: "Total cart value", dataType: "double", isRequired: true, isArchived: false },
      { id: 12, attributeName: "item_count", description: "Number of items in cart", dataType: "integer", isRequired: true, isArchived: false },
      { id: 13, attributeName: "coupon_applied", description: "Whether a coupon was applied", dataType: "boolean", isRequired: false, isArchived: false },
    ],
    createdBy: "priya.patel@example.com",
    updatedBy: "priya.patel@example.com",
    createdAt: daysAgo(25),
    updatedAt: daysAgo(25),
  },
  {
    id: 6,
    eventName: "payment_completed",
    displayName: "Payment Completed",
    description: "Fired when a payment is successfully processed",
    category: "commerce",
    isArchived: false,
    attributes: [
      { id: 14, attributeName: "transaction_id", description: "Payment transaction ID", dataType: "string", isRequired: true, isArchived: false },
      { id: 15, attributeName: "payment_method", description: "UPI, card, wallet, etc.", dataType: "string", isRequired: true, isArchived: false },
      { id: 16, attributeName: "amount", description: "Payment amount", dataType: "double", isRequired: true, isArchived: false },
    ],
    createdBy: "amit.kumar@example.com",
    updatedBy: "amit.kumar@example.com",
    createdAt: daysAgo(24),
    updatedAt: daysAgo(7),
  },
  {
    id: 7,
    eventName: "screen_view",
    displayName: "Screen View",
    description: "Fired when a user navigates to a new screen",
    category: "navigation",
    isArchived: false,
    attributes: [
      { id: 17, attributeName: "screen_name", description: "Name of the screen viewed", dataType: "string", isRequired: true, isArchived: false },
      { id: 18, attributeName: "previous_screen", description: "Screen navigated from", dataType: "string", isRequired: false, isArchived: false },
      { id: 19, attributeName: "load_time_ms", description: "Time to render the screen", dataType: "integer", isRequired: false, isArchived: false },
    ],
    createdBy: "neha.singh@example.com",
    updatedBy: "neha.singh@example.com",
    createdAt: daysAgo(22),
    updatedAt: daysAgo(15),
  },
  {
    id: 8,
    eventName: "deep_link_opened",
    displayName: "Deep Link Opened",
    description: "Fired when a deep link is opened in the app",
    category: "navigation",
    isArchived: false,
    attributes: [
      { id: 20, attributeName: "link_url", description: "Deep link URL", dataType: "string", isRequired: true, isArchived: false },
      { id: 21, attributeName: "source", description: "Source of deep link (push, email, etc.)", dataType: "string", isRequired: false, isArchived: false },
    ],
    createdBy: "neha.singh@example.com",
    updatedBy: "neha.singh@example.com",
    createdAt: daysAgo(20),
    updatedAt: daysAgo(20),
  },
  {
    id: 9,
    eventName: "search_performed",
    displayName: "Search Performed",
    description: "Fired when a user submits a search query",
    category: "engagement",
    isArchived: false,
    attributes: [
      { id: 22, attributeName: "query", description: "Search query string", dataType: "string", isRequired: true, isArchived: false },
      { id: 23, attributeName: "result_count", description: "Number of results returned", dataType: "integer", isRequired: false, isArchived: false },
      { id: 24, attributeName: "search_type", description: "Type of search (product, contest, etc.)", dataType: "string", isRequired: false, isArchived: false },
    ],
    createdBy: "rahul.sharma@example.com",
    updatedBy: "rahul.sharma@example.com",
    createdAt: daysAgo(18),
    updatedAt: daysAgo(4),
  },
  {
    id: 10,
    eventName: "button_click",
    displayName: "Button Click",
    description: "Generic button click tracking event",
    category: "engagement",
    isArchived: false,
    attributes: [
      { id: 25, attributeName: "button_id", description: "Unique button identifier", dataType: "string", isRequired: true, isArchived: false },
      { id: 26, attributeName: "button_text", description: "Button label text", dataType: "string", isRequired: false, isArchived: false },
      { id: 27, attributeName: "screen_name", description: "Screen where button was clicked", dataType: "string", isRequired: true, isArchived: false },
    ],
    createdBy: "neha.singh@example.com",
    updatedBy: "neha.singh@example.com",
    createdAt: daysAgo(15),
    updatedAt: daysAgo(15),
  },
  {
    id: 11,
    eventName: "notification_received",
    displayName: "Notification Received",
    description: "Fired when a push notification is received by the device",
    category: "notifications",
    isArchived: false,
    attributes: [
      { id: 28, attributeName: "notification_id", description: "Unique notification identifier", dataType: "string", isRequired: true, isArchived: false },
      { id: 29, attributeName: "notification_type", description: "Type of notification (promo, transactional, etc.)", dataType: "string", isRequired: true, isArchived: false },
      { id: 30, attributeName: "campaign_id", description: "Marketing campaign ID if applicable", dataType: "string", isRequired: false, isArchived: false },
    ],
    createdBy: "amit.kumar@example.com",
    updatedBy: "amit.kumar@example.com",
    createdAt: daysAgo(12),
    updatedAt: daysAgo(12),
  },
  {
    id: 12,
    eventName: "app_crash",
    displayName: "App Crash",
    description: "Fired when the app encounters an unhandled exception",
    category: "errors",
    isArchived: false,
    attributes: [
      { id: 31, attributeName: "crash_type", description: "Java, Native, ANR", dataType: "string", isRequired: true, isArchived: false },
      { id: 32, attributeName: "exception_class", description: "Exception class name", dataType: "string", isRequired: true, isArchived: false },
      { id: 33, attributeName: "is_fatal", description: "Whether the crash was fatal", dataType: "boolean", isRequired: true, isArchived: false },
    ],
    createdBy: "rahul.sharma@example.com",
    updatedBy: "rahul.sharma@example.com",
    createdAt: daysAgo(10),
    updatedAt: daysAgo(2),
  },
  {
    id: 13,
    eventName: "network_error",
    displayName: "Network Error",
    description: "Fired when a network request fails",
    category: "errors",
    isArchived: false,
    attributes: [
      { id: 34, attributeName: "status_code", description: "HTTP status code", dataType: "integer", isRequired: true, isArchived: false },
      { id: 35, attributeName: "endpoint", description: "API endpoint path", dataType: "string", isRequired: true, isArchived: false },
      { id: 36, attributeName: "error_message", description: "Error description", dataType: "string", isRequired: false, isArchived: false },
    ],
    createdBy: "amit.kumar@example.com",
    updatedBy: "amit.kumar@example.com",
    createdAt: daysAgo(10),
    updatedAt: daysAgo(10),
  },
  {
    id: 14,
    eventName: "contest_joined",
    displayName: "Contest Joined",
    description: "Fired when a user successfully joins a contest",
    category: "engagement",
    isArchived: false,
    attributes: [
      { id: 37, attributeName: "contest_id", description: "Contest identifier", dataType: "string", isRequired: true, isArchived: false },
      { id: 38, attributeName: "entry_fee", description: "Contest entry fee", dataType: "double", isRequired: true, isArchived: false },
      { id: 39, attributeName: "contest_type", description: "Type of contest", dataType: "string", isRequired: false, isArchived: false },
    ],
    createdBy: "priya.patel@example.com",
    updatedBy: "priya.patel@example.com",
    createdAt: daysAgo(8),
    updatedAt: daysAgo(1),
  },
  {
    id: 15,
    eventName: "old_payment_flow",
    displayName: "Old Payment Flow",
    description: "Deprecated: old payment tracking event",
    category: "commerce",
    isArchived: true,
    attributes: [
      { id: 40, attributeName: "amount", description: "Payment amount", dataType: "double", isRequired: true, isArchived: false },
    ],
    createdBy: "amit.kumar@example.com",
    updatedBy: "amit.kumar@example.com",
    createdAt: daysAgo(60),
    updatedAt: daysAgo(15),
  },
];

export const mockEventCategories = [
  "authentication",
  "commerce",
  "navigation",
  "engagement",
  "notifications",
  "errors",
];
