import { Column, Text } from "tinyui-core";

/** The second package of the sample App: one page, its own key, delivered on its own (docs/updates.md §0). */
export default function Hello() {
    return (
        <Column padding={16}>
            <Text text="Hello from sample-extra" fontSize={24} />
            <Text text={`page ${import.meta.url}`} color="#888888" />
        </Column>
    );
}
