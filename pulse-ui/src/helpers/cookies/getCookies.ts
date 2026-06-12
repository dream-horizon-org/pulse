import Cookies from "js-cookie";

export const getCookies = (name: string): string | undefined => {
  return Cookies.get(name);
};
