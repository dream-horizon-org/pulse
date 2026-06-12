import Cookies from "js-cookie";
import { COOKIES_KEY } from "../../constants";

export const removeAllCookies = () => {
  for (const key in COOKIES_KEY) {
    Cookies.remove(COOKIES_KEY[key]);
  }
};
