// Types for what `tinyui build` loads besides code; reference with "types": ["tinyui-core/assets"] (docs/components.md §3).

/** An icon: the string `Icon` takes, made by the build from a single-colour SVG. */
declare module "*.svg" {
    const icon: string;
    export default icon;
}

/** An image: the `src` Image takes, a reference to the file the build copies into the package (docs/build-chain.md §12). */
declare module "*.png" {
    const src: string;
    export default src;
}
declare module "*.jpg" {
    const src: string;
    export default src;
}
declare module "*.jpeg" {
    const src: string;
    export default src;
}
declare module "*.webp" {
    const src: string;
    export default src;
}
