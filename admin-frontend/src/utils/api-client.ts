import axios from "axios";

const token =
  "eyJhbGciOiJSUzUxMiJ9.eyJpc3MiOiJIYWxvIE93bmVyIiwic3ViIjoiYWRtaW4iLCJleHAiOjE2NTYyNTAzMzQsImlhdCI6MTY1NjE2MzkzNCwic2NvcGUiOlsiUk9MRV9zdXBlci1yb2xlIl19.ejC4wiZlpvbHc8jVkAJ-Ocx0CiLg1HYyr-QSsmvKb9-GC3qDGs1pUmHbdcH9bewsI7iFvhA6lGADZIjvWuRZmydSJfgi41d61O1jQB77N1WjFqi5kTeXXPbLqtwdAMQmHuFFplrXZPkRpKa1pyDlU-1jI9cADpLJ2WlLF7XkHLD5iezhnClKn8Nc98eH3QfVg1jhAV4iuEz6mmuAx_swBFVS6Xcoc_b9AnPKUvFQx3h-kSfwIKQBJ1zvlntf04RAqivDMyHi_VByaC1NLScvDEyuAPZW9OFPs6veXhwS-jPmwMp-pTIpdK9cFuoynCPP-8OlzmEKFJxBC1lkEyy48g";
const axiosInstance = axios.create({
  headers: {
    Authorization: `Bearer ${token}`,
  },
  baseURL: "http://localhost:8090",
});

export default axiosInstance;
