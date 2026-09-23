import { host } from "tinyui-native";

export const billing = {
    prices: () => host.call("billing.prices"),
};

export const checkout = {
    start: (plan: string) => host.call(`checkout.start`, { plan }),
};
