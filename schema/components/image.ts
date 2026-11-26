import { defineComponent, enumOf, string } from "tinyui-cli/schema";

export default defineComponent("Image", {
    doc: "A picture: an image imported from the package (`import hero from \"./hero.png\"`) or an https URL. Give it a width and a height: it takes that box before the picture arrives, so nothing moves when it does.",
    props: {
        src: string({ required: true, doc: "an imported image, or an https URL; anything else draws nothing and is reported" }),
        contentScale: enumOf(["fit", "crop", "fill"], { default: "fit", doc: "fit: whole picture inside the box; crop: fills the box, edges cut; fill: stretched to the box" }),
        label: string({ doc: "what a screen reader says; leave unset for a decorative picture" }),
    },
});
