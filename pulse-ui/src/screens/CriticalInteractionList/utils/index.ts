import dayjs from "dayjs";

export const getDateFilterDetails = () => ({
  startTime: dayjs()
    .utc()
    .subtract(1, "day")
    .subtract(2, "minutes")
    .format("YYYY-MM-DD HH:mm:ss"),
  endTime: dayjs().utc().subtract(2, "minutes").format("YYYY-MM-DD HH:mm:ss"),
});
