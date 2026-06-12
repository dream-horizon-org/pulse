import Cookies from "js-cookie";

export const setCookies = (name: string, value: string) => {
  Cookies.set(name, value, {
    sameSite: "strict",
  });
};
