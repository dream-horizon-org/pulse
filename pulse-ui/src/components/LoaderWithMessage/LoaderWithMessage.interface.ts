import { LoaderProps } from "@mantine/core";

export type LoaderWithMessageProps = LoaderProps & {
  loadingMessage?: string;
  width?: string | number;
  height?: string | number;
  className?: string;
};
