import { Box, Loader, Stack, Title } from "@mantine/core";
import classes from "./LoaderWithMessage.module.css";
import { LoaderWithMessageProps } from "./LoaderWithMessage.interface";

export function LoaderWithMessage({
  className,
  width,
  height,
  loadingMessage,
  ...rest
}: LoaderWithMessageProps) {
  return (
    <Box
      w={width}
      h={height}
      mih={height}
      className={className || classes.loaderWithMessageContainer}
      pos={"relative"}
    >
      <Stack>
        <div className={classes.fetchingLoader}>
          <Loader type="bars" {...rest} />
        </div>
        <Title order={5}>{loadingMessage ?? ""}</Title>
      </Stack>
    </Box>
  );
}
