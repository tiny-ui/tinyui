// Built-in component names and prop types come from schema/ (docs/components.md).
export * from "./generated/components.ts";
// The schema's `BottomSheet` is the node; pages get the component that builds its content per opening.
export { BottomSheet } from "./control.ts";
export type { BottomSheetProps } from "./control.ts";

export interface ClickPayload {}
