import dayjs from "dayjs";

export const getDateFilterDetails = () => ({
  startTime: dayjs()
    .utc()
    .subtract(1, "hour")
    .subtract(1, "minutes")
    .format("YYYY-MM-DD HH:mm:ss"),
  endTime: dayjs().utc().subtract(1, "minutes").format("YYYY-MM-DD HH:mm:ss"),
});
