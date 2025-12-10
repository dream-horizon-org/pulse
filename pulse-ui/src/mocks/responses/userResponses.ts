/**
 * User Management Mock Responses
 */

export const mockUserResponses = {
  getUserDetail: (phoneNo: string) => ({
    data: {
      teamName: "Frontend",
      userId: 1,
      emailId: "user@example.com",
      commEmailId: "user@example.com",
    },
    status: 200,
  }),
  getUserExperiments: (phoneNo: string) => ({
    data: ["experiment1", "experiment2", "experiment3"],
    status: 200,
  }),
  getUserLastActiveToday: (phoneNo: string) => ({
    data: { active: true },
    status: 200,
  }),
};
