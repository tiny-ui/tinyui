import { Column, Image, Text, VERSION } from "tinyui-core";
import { title } from "../lib/format.ts";
import dot from "../assets/dot.png";

interface Props { name: string }

export default function Home({ name }: Props) {
    const label = title(name);
    return <Column><Text text={label} /><Text text={VERSION} /><Text text={title(name)} /><Image src={dot} width={8} height={8} /></Column>;
}
