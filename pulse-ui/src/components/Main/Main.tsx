import { AppShell } from "@mantine/core";
import { MainProps } from "./Main.interface";

export function Main({children}: MainProps) {
    return <AppShell.Main>{children}</AppShell.Main>
}