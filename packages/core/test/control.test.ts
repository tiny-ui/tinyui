import assert from "node:assert/strict";
import { afterEach, describe, it } from "node:test";
import { bridge } from "./host-stub.ts";
import { mount, tinyui, transaction, unmount } from "./helpers.ts";
import { BottomSheet, Button, Column, For, h, Keyed, onCleanup, Show, signal, Text, thunk } from "../src/index.ts";

const g = globalThis as Record<string, unknown>;
afterEach(() => unmount());

describe("Show", () => {
    it("is a slot between siblings: switching keeps sibling indices right", () => {
        mount(() => {
            const [on, setOn] = signal(false);
            g["__setOn"] = setOn;
            return h(Column, null,
                h(Text, { text: "head" }),
                h(Show, { when: thunk(() => on()), fallback: () => h(Text, { text: "off" }) }, () => h(Text, { text: "on" })),
                h(Text, { text: "tail" }),
            );
        });
        assert.deepEqual(bridge.ops().filter((o) => o[0] === "i"), [["i", 4, 1, 0], ["i", 4, 2, 1], ["i", 4, 3, 2], ["i", 0, 4, 0]]);
        const ops = transaction(() => (g["__setOn"] as (v: boolean) => void)(true));
        assert.deepEqual(ops, [["r", 2], ["c", 5, "Text"], ["p", 5, "text", "on"], ["i", 4, 5, 1]]);
        assert.deepEqual(transaction(() => (g["__setOn"] as (v: boolean) => void)(false)), [["r", 5], ["c", 6, "Text"], ["p", 6, "text", "off"], ["i", 4, 6, 1]]);
        unmount();
    });

    it("puts a static sibling after an empty slot at the right index", () => {
        mount(() => h(Column, null,
            h(Show, { when: false }, () => h(Text, { text: "never" })),
            h(Text, { text: "tail" }),
        ));
        assert.deepEqual(bridge.ops().filter((o) => o[0] === "i"), [["i", 2, 1, 0], ["i", 0, 2, 0]]);
        unmount();
    });

    it("switches only on truthiness and disposes the old branch", () => {
        const log: string[] = [];
        mount(() => {
            const [user, setUser] = signal<{ name: string } | null>({ name: "a" });
            g["__setUser"] = setUser;
            return h(Column, null, h(Show, { when: thunk(() => user()) }, () => {
                onCleanup(() => log.push("branch disposed"));
                return h(Text, { text: thunk(() => user()!.name) });
            }));
        });
        assert.deepEqual(transaction(() => (g["__setUser"] as (v: unknown) => void)({ name: "b" })), [["p", 1, "text", "b"]], "same truthiness: no rebuild");
        assert.deepEqual(transaction(() => (g["__setUser"] as (v: unknown) => void)(null)), [["r", 1]]);
        assert.deepEqual(log, ["branch disposed"]);
        unmount();
    });
});

interface Todo { id: number; title: string }

function TodoList(props: { todos: Todo[] }) {
    return h(Column, null,
        h(Text, { text: "head" }),
        h(For, { each: thunk(() => props.todos), key: (t: Todo) => t.id }, (item: () => Todo, index: () => number) =>
            h(Button, { text: thunk(() => `${index()}:${item().title}`), onClick: () => {} })),
    );
}

function mountList(initial: Todo[]) {
    return mount(() => {
        const [todos, setTodos] = signal(initial);
        g["__setTodos"] = setTodos;
        return h(TodoList, { todos: thunk(() => todos()) });
    });
}
const setTodos = (v: Todo[]) => transaction(() => (g["__setTodos"] as (v: Todo[]) => void)(v));

describe("For", () => {
    it("creates rows after the static sibling and keeps them keyed", () => {
        const ops = mountList([{ id: 1, title: "a" }, { id: 2, title: "b" }]);
        assert.deepEqual(ops.filter((o) => o[0] === "i"), [["i", 4, 1, 0], ["i", 4, 2, 1], ["i", 4, 3, 2], ["i", 0, 4, 0]]);
        assert.deepEqual(ops.filter((o) => o[0] === "p" && o[2] === "text"), [["p", 1, "text", "head"], ["p", 2, "text", "0:a"], ["p", 3, "text", "1:b"]]);
        unmount();
    });

    it("updates a kept row through the item accessor instead of rebuilding it", () => {
        mountList([{ id: 1, title: "a" }, { id: 2, title: "b" }]);
        assert.deepEqual(setTodos([{ id: 1, title: "a2" }, { id: 2, title: "b" }]), [["p", 2, "text", "0:a2"]]);
        unmount();
    });

    it("removes, inserts and moves with indices offset by the static sibling", () => {
        mountList([{ id: 1, title: "a" }, { id: 2, title: "b" }, { id: 3, title: "c" }]);
        assert.deepEqual(setTodos([{ id: 3, title: "c" }, { id: 1, title: "a" }, { id: 4, title: "d" }]), [
            ["r", 3],
            ["m", 5, 4, 1],
            ["c", 6, "Button"], ["p", 6, "text", "2:d"], ["p", 6, "onClick", true], ["i", 5, 6, 3],
            ["p", 4, "text", "0:c"],
            ["p", 2, "text", "1:a"],
        ]);
        unmount();
    });

    it("puts a static sibling after a multi-row For at the right index", () => {
        mount(() => h(Column, null,
            h(For, { each: [1, 2, 3], key: (n: number) => n }, (n: () => number) => h(Text, { text: thunk(() => String(n())) })),
            h(Text, { text: "tail" }),
        ));
        assert.deepEqual(bridge.ops().filter((o) => o[0] === "i").slice(-2), [["i", 5, 4, 3], ["i", 0, 5, 0]]);
        unmount();
    });

    it("disposes removed rows: their handlers are gone", () => {
        mountList([{ id: 1, title: "a" }]);
        setTodos([]);
        assert.deepEqual(transaction(() => tinyui().dispatch(2, "onClick", "{}")), []);
        unmount();
    });

    it("rejects duplicate keys and misplaced control flow", () => {
        assert.throws(() => mountList([{ id: 1, title: "a" }, { id: 1, title: "b" }]), /duplicate key/);
        unmount();
        assert.throws(() => mount(() => h(For, { each: [], key: (t: Todo) => t.id }, () => h(Text, { text: "x" })) as never), /page must return exactly one node/);
        unmount();
        assert.throws(() => mount(() => h(function Wrap() { return h(Show, { when: true }, () => h(Text, { text: "x" })); }, null)), /got a For \/ Show/);
        unmount();
    });
});

describe("Show children", () => {
    it("get the value as an accessor that follows it and never turns falsy", () => {
        const seen: unknown[] = [];
        mount(() => {
            const [user, setUser] = signal<{ name: string } | null>({ name: "a" });
            g["__setUser"] = setUser;
            return h(Column, null, h(Show, { when: thunk(() => user()) }, (u: () => { name: string }) => {
                onCleanup(() => seen.push(u()));
                return h(Text, { text: thunk(() => u().name) });
            }));
        });
        const setUser = g["__setUser"] as (v: unknown) => void;
        assert.deepEqual(transaction(() => setUser({ name: "b" })), [["p", 1, "text", "b"]]);
        assert.deepEqual(transaction(() => setUser(null)), [["r", 1]], "the binding did not run with null");
        assert.deepEqual(seen, [{ name: "b" }], "the branch still reads the last truthy value while it is disposed");
        assert.deepEqual(transaction(() => setUser({ name: "c" })), [["c", 3, "Text"], ["p", 3, "text", "c"], ["i", 2, 3, 0]]);
        unmount();
    });
});

describe("Keyed", () => {
    it("rebuilds its node when the value changes, falsy values included, and not otherwise", () => {
        const log: string[] = [];
        mount(() => {
            const [tab, setTab] = signal<number>(1);
            g["__setTab"] = setTab;
            return h(Column, null,
                h(Text, { text: "head" }),
                h(Keyed, { by: thunk(() => tab()) }, (t: number) => {
                    onCleanup(() => log.push(`tab ${t} disposed`));
                    return h(Text, { text: `tab ${t}` });
                }),
                h(Text, { text: "tail" }),
            );
        });
        const setTab = g["__setTab"] as (v: number) => void;
        assert.deepEqual(bridge.ops().filter((o) => o[0] === "i"), [["i", 4, 1, 0], ["i", 4, 2, 1], ["i", 4, 3, 2], ["i", 0, 4, 0]]);
        assert.deepEqual(transaction(() => setTab(1)), [], "same value: nothing");
        assert.deepEqual(transaction(() => setTab(0)), [["r", 2], ["c", 5, "Text"], ["p", 5, "text", "tab 0"], ["i", 4, 5, 1]], "0 is a value like any other");
        assert.deepEqual(log, ["tab 1 disposed"]);
        unmount();
        assert.deepEqual(log, ["tab 1 disposed", "tab 0 disposed"]);
    });

    it("builds once for an undefined value", () => {
        mount(() => h(Column, null, h(Keyed, { by: undefined }, () => h(Text, { text: "only" }))));
        assert.deepEqual(bridge.ops(), [["c", 1, "Text"], ["p", 1, "text", "only"], ["c", 2, "Column"], ["i", 2, 1, 0], ["i", 0, 2, 0]]);
        unmount();
    });
});

describe("BottomSheet", () => {
    const sheet = (props: Record<string, unknown>, render: (value: never) => unknown) =>
        h(Column, null, h(BottomSheet as never, props, render as never));

    it("builds nothing until it opens, and a new content at every opening", () => {
        const log: string[] = [];
        let built = 0;
        mount(() => {
            const [open, setOpen] = signal(false);
            g["__setOpen"] = setOpen;
            return sheet({ open: thunk(() => open()), onDismiss: () => setOpen(false) }, () => {
                const n = ++built;
                onCleanup(() => log.push(`content ${n} disposed`));
                return h(Text, { text: `content ${n}` });
            });
        });
        const setOpen = g["__setOpen"] as (v: boolean) => void;
        assert.deepEqual(bridge.ops(), [["c", 1, "BottomSheet"], ["p", 1, "open", false], ["p", 1, "onDismiss", true], ["c", 2, "Column"], ["i", 2, 1, 0], ["i", 0, 2, 0]]);
        assert.deepEqual(transaction(() => setOpen(true)), [["c", 3, "Text"], ["p", 3, "text", "content 1"], ["i", 1, 3, 0], ["p", 1, "open", true]]);
        assert.deepEqual(transaction(() => setOpen(false)), [["p", 1, "open", false]], "closing keeps the content for the hide animation");
        assert.deepEqual(log, []);
        assert.deepEqual(transaction(() => setOpen(true)), [["r", 3], ["c", 4, "Text"], ["p", 4, "text", "content 2"], ["i", 1, 4, 0], ["p", 1, "open", true]]);
        assert.deepEqual(log, ["content 1 disposed"]);
        unmount();
        assert.deepEqual(log, ["content 1 disposed", "content 2 disposed"]);
    });

    it("hands the content the value it opened with, and rebuilds when that value is replaced", () => {
        mount(() => {
            const [user, setUser] = signal<{ name: string } | null>(null);
            g["__setUser"] = setUser;
            return sheet({ open: thunk(() => user()) }, (u: { name: string }) => h(Text, { text: u.name }));
        });
        const setUser = g["__setUser"] as (v: unknown) => void;
        assert.deepEqual(transaction(() => setUser({ name: "a" })), [["c", 3, "Text"], ["p", 3, "text", "a"], ["i", 1, 3, 0], ["p", 1, "open", true]]);
        assert.deepEqual(transaction(() => setUser(null)), [["p", 1, "open", false]], "the snapshot outlives the data");
        assert.deepEqual(transaction(() => setUser({ name: "b" })), [["r", 3], ["c", 4, "Text"], ["p", 4, "text", "b"], ["i", 1, 4, 0], ["p", 1, "open", true]]);
        assert.deepEqual(transaction(() => setUser({ name: "c" })), [["r", 4], ["c", 5, "Text"], ["p", 5, "text", "c"], ["i", 1, 5, 0]], "new data while open: new content, still open");
        unmount();
    });

    it("builds the content with the node when it starts open", () => {
        mount(() => sheet({ open: true }, () => h(Text, { text: "now" })));
        assert.deepEqual(bridge.ops(), [["c", 1, "Text"], ["p", 1, "text", "now"], ["c", 2, "BottomSheet"], ["p", 2, "open", true], ["i", 2, 1, 0], ["c", 3, "Column"], ["i", 3, 2, 0], ["i", 0, 3, 0]]);
        unmount();
    });

    it("rejects static children", () => {
        assert.throws(() => mount(() => h(Column, null, h(BottomSheet as never, { open: false }, h(Text, { text: "static" })))), /children must be a function/);
    });
});
