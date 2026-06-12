export type RequestBody = {
  phoneNo: string;
  fetchTime: string;
};

export type SuccessResponseEvent = {
  eventId: string;
  eventName: string;
  eventTimestamp: string;
  globalProps: { [key: string]: unknown };
  props: { [key: string]: unknown };
};

export type SuccessResponseBody = {
  events: SuccessResponseEvent[];
  count: number;
};

export type ErrorResponse = {
  data: null;
  error: {
    code: string;
    message: string;
    cause: string;
  };
  status: number;
};
