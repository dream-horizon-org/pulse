export type CheckUserPermissionsRequestBody = {
  id: string;
  type: "experience" | "alerts";
  userEmail: string;
  operation: "delete" | "update";
};

export type CheckUserPermissionsResponse = {
  isAllowed: boolean;
};
