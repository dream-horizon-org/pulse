export type GetUserExperimentsParams = {
  pathParams: {
    phoneNo: string;
  };
};

export type UserExperimentsResponse = {
  experiments: Array<Experiment>;
};

export type Experiment = {
  experimentName: string;
  description: string;
  startTime: string;
  cohort: CohortList;
};

export type CohortList = {
  cohortList: Array<string>;
};
