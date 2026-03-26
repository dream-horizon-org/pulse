import Cookies from "js-cookie";

export const removeCookie = (name: string) => {
  Cookies.remove(name);
};
