import appJson from "./app.json";
import fs from "fs";
import path from "path";

const googleServicesBase64 = process.env.GOOGLE_SERVICES_JSON_BASE64;
if (googleServicesBase64) {
  fs.writeFileSync(
    path.join(__dirname, "google-services.json"),
    Buffer.from(googleServicesBase64, "base64").toString("utf8"),
    "utf8"
  );
}

export default {
  ...appJson.expo,
  android: {
    ...appJson.expo.android,
    config: {
      googleMaps: {
        apiKey: process.env.EXPO_PUBLIC_GOOGLE_MAPS_API_KEY,
      },
    },
  },
};