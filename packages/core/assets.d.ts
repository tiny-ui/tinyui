// Types for what `tinyui build` loads besides code; reference with "types": ["tinyui-core/assets"] (docs/components.md §3).

/** An icon: the string `Icon` takes, made by the build from a single-colour SVG. */
declare module "*.svg" {
    const icon: string;
    export default icon;
}
