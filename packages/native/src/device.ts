// docs/native-api.md §17: what the page is running on, for the few values that differ by platform.
import { internal } from "tinyui-core";

export interface DeviceInfo {
    /** the operating system: "android" | "ios" */
    os: "android" | "ios";
    /** its version as the system reports it, e.g. "14" or "17.4" */
    osVersion: string;
    /** the device model as the system reports it */
    model: string;
}

/** The platform this page runs on; answered from memory, so it is cheap to call. */
export function info(): DeviceInfo {
    return internal.query<DeviceInfo>("device.info");
}
