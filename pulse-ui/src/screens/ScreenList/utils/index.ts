import dayjs from "dayjs";

export const getDateFilterDetails = () => ({
  startTime: dayjs()
    .utc()
    .subtract(1, "days").startOf("day").toISOString(),
  endTime: dayjs().utc().endOf("day").toISOString(),
});
